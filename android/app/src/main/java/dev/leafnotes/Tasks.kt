package dev.leafnotes

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.util.Calendar
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale

data class TaskDetails(val dueAt:Long=0,val hasTime:Boolean=false,val priority:Int=0,val repeatRule:String="none",val list:String="Reminders",val completed:Boolean=false,val remind:Boolean=false,val status:String?=null) {
    val stage get()=if(completed)"done" else status ?: "todo"
    fun moveTo(value:String)=if(value=="done"&&!completed)complete() else copy(status=value,completed=value=="done")
    val alertAt get()=if(hasTime)dueAt else Calendar.getInstance().apply{timeInMillis=dueAt;set(Calendar.HOUR_OF_DAY,9);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)}.timeInMillis
    fun json()=JSONObject().put("dueAt",dueAt).put("hasTime",hasTime).put("priority",priority).put("repeatRule",repeatRule).put("list",list).put("completed",completed).put("remind",remind).apply{status?.let{put("status",it)}}
    fun complete(now:Long=System.currentTimeMillis()):TaskDetails {
        if(repeatRule=="none" || dueAt==0L)return copy(completed=!completed,status=if(completed)"todo" else "done")
        val c=Calendar.getInstance().apply{timeInMillis=dueAt};val field=if(repeatRule=="monthly")Calendar.MONTH else Calendar.DAY_OF_MONTH;val amount=if(repeatRule=="weekly")7 else 1
        c.add(field,amount);while(c.timeInMillis<=now)c.add(field,amount)
        return copy(dueAt=c.timeInMillis,completed=false,status="todo")
    }
    companion object { fun from(o:JSONObject)=TaskDetails(o.getLong("dueAt"),o.getBoolean("hasTime"),o.getInt("priority"),o.getString("repeatRule"),o.getString("list"),o.getBoolean("completed"),o.getBoolean("remind"),o.optString("status").takeIf{it.isNotEmpty()}) }
}
fun dayStart(time:Long)=Calendar.getInstance().apply{timeInMillis=time;set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)}.timeInMillis
fun nextDayStart(time:Long)=Calendar.getInstance().apply{timeInMillis=dayStart(time);add(Calendar.DAY_OF_MONTH,1)}.timeInMillis
fun taskDate(t:TaskDetails)=if(t.dueAt==0L)"Anytime" else SimpleDateFormat(if(t.hasTime)"MMM d · h:mm a" else "MMM d",Locale.getDefault()).format(Date(t.dueAt))
fun Store.taskChange(id:String,change:(Note)->Note){val previous=selected;select(id);editing?.let{update(change(it));flush()};select(previous)}

@Composable fun TasksHome(store:Store,onBack:()->Unit) {
    val c=LocalLeafColors.current;val haptic=androidx.compose.ui.platform.LocalHapticFeedback.current
    var filter by remember{mutableIntStateOf(0)};var layout by remember{mutableIntStateOf(store.preferences.getInt("taskView",0))};var list by remember{mutableStateOf("All lists")};var chooseList by remember{mutableStateOf(false)}
    var editing by remember{mutableStateOf<Revision?>(null)};var adding by remember{mutableStateOf(false)};var addingStage by remember{mutableStateOf("todo")};var options by remember{mutableStateOf<Revision?>(null)}
    val tasks=store.visibleHeads.filter{it.note.task!=null&&!it.note.deleted&&!it.note.archived}
    val states=listOf("todo","progress","review","done");val names=listOf("To Do","In Progress","In Review","Done")
    val visible=tasks.filter{r->val t=r.note.task!!;(list=="All lists"||t.list==list)&&(filter==0||t.stage==listOf("todo","progress","done")[filter-1])}.sortedWith(compareBy<Revision>{if(it.note.task!!.dueAt==0L)Long.MAX_VALUE else it.note.task!!.dueAt}.thenByDescending{it.note.task!!.priority})
    val columns=remember{mutableStateMapOf<String,Rect>()};var dragged by remember{mutableStateOf<String?>(null)};var delta by remember{mutableStateOf(Offset.Zero)}
    @Composable fun TaskCard(r:Revision,modifier:Modifier=Modifier){val t=r.note.task!!
        Row(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.paper.copy(alpha=.6f)).border(.5.dp,c.separator,RoundedCornerShape(20.dp)).padding(16.dp),verticalAlignment=Alignment.CenterVertically){
            Pressable(Modifier.size(32.dp),"Complete task",onClick={store.taskChange(r.noteId){it.copy(task=t.complete())}}){Box(Modifier.size(24.dp).then(if(t.completed)Modifier.background(c.accent,CircleShape)else Modifier.border(1.3.dp,c.tertiary,CircleShape)),contentAlignment=Alignment.Center){if(t.completed)Glyph("done",size=16,tint=androidx.compose.ui.graphics.Color.White)}}
            Spacer(Modifier.width(12.dp))
            Pressable(Modifier.weight(1f),"Edit ${r.note.displayTitle}",onClick={editing=r},onLongClick={options=r}){Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp)){androidx.compose.material3.Text(r.note.displayTitle,style=TextStyle(fontSize=17.sp,fontWeight=FontWeight.Medium,color=if(t.completed)c.secondary else c.text,textDecoration=if(t.completed)TextDecoration.LineThrough else null));Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)){Glyph("clock",size=14,tint=c.tertiary);Label(taskDate(t),12,color=if(t.dueAt>0&&t.dueAt<System.currentTimeMillis()&&!t.completed)c.danger else c.secondary)};if(layout==1)Label(t.list,11,color=c.tertiary)}}
            Pressable(Modifier.size(32.dp),"Task options",onClick={options=r}){Label(if(t.priority>0)"⚑" else "⚐",22,color=if(t.priority==3)c.danger else if(t.priority>0)c.accent else c.tertiary)}
        }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().frosted(androidx.compose.ui.graphics.RectangleShape,light=true).padding(horizontal=20.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically){ChromeButton("back","Back",onClick=onBack);Spacer(Modifier.weight(1f));ChromeButton("plus","New task",onClick={addingStage="todo";adding=true})}
        Column(Modifier.padding(horizontal=24.dp)){Label("My Tasks",30,FontWeight.Bold);Label(SimpleDateFormat("EEEE, MMM d",Locale.getDefault()).format(Date()),13,color=c.secondary,modifier=Modifier.padding(top=4.dp,bottom=12.dp));IosSegments(listOf("List","Board"),layout){layout=it;store.preferences.edit().putInt("taskView",it).apply()};IosSegments(listOf("All","To Do","In Progress","Done"),filter){filter=it};Pressable(onClick={chooseList=true}){Label(list+" ▾",13,color=c.secondary,modifier=Modifier.padding(vertical=6.dp))}}
        if(layout==0)LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(24.dp,16.dp,24.dp,90.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            if(visible.isEmpty())item{Label("A little room for what’s next.",16,color=c.secondary,modifier=Modifier.padding(vertical=28.dp))}
            items(visible,key={it.noteId}){r->TaskCard(r,Modifier.animateItem())}
            item{Pressable(Modifier.fillMaxWidth().background(c.fill,RoundedCornerShape(20.dp)).padding(20.dp),"Add task",onClick={addingStage="todo";adding=true}){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)){Glyph("plus",tint=c.tertiary);Label("Add a new task…",16,color=c.secondary)}}}
        } else Row(Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=16.dp),horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            states.forEachIndexed{i,state->Column(Modifier.width(290.dp).fillMaxHeight().onGloballyPositioned{columns[state]=it.boundsInRoot()}.background(c.fill.copy(alpha=.5f),RoundedCornerShape(24.dp)).padding(14.dp)){
                Row(Modifier.fillMaxWidth().padding(bottom=16.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(6.dp).background(c.accent.copy(alpha=.7f),CircleShape));Spacer(Modifier.width(8.dp));Label(names[i],15,FontWeight.SemiBold,modifier=Modifier.weight(1f));Label(visible.count{it.note.task!!.stage==state}.toString(),12,color=c.tertiary)}
                LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp)){items(visible.filter{it.note.task!!.stage==state},key={it.noteId}){r->var bounds by remember{mutableStateOf(Rect.Zero)};Column(Modifier.animateItem().onGloballyPositioned{bounds=it.boundsInRoot()}.graphicsLayer{if(dragged==r.noteId){translationX=delta.x;translationY=delta.y;scaleX=.95f;scaleY=.95f}}){
                    TaskCard(r)
                    Box(Modifier.fillMaxWidth().height(28.dp).pointerInput(r.noteId){var origin=Offset.Zero;detectDragGesturesAfterLongPress(onDragStart={origin=bounds.center;dragged=r.noteId;delta=Offset.Zero;haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)},onDrag={change,amount->change.consume();delta+=amount},onDragEnd={val center=origin+delta;columns.entries.firstOrNull{it.value.contains(center)}?.key?.let{target->store.taskChange(r.noteId){it.copy(task=it.task!!.moveTo(target))}};dragged=null;delta=Offset.Zero},onDragCancel={dragged=null;delta=Offset.Zero})},contentAlignment=Alignment.Center){Label("⋮⋮",13,color=c.tertiary)}
                }};item{Pressable(Modifier.fillMaxWidth().padding(14.dp),"Add task",onClick={addingStage=state;adding=true}){Label("+ Add task",14,color=c.secondary)}}}
            }}
        }
    }
    if(chooseList)IosSheet("Lists",onDismiss={chooseList=false}){(listOf("All lists")+tasks.map{it.note.task!!.list}.distinct().sorted()).forEach{name->SheetRow(name,"folder"){list=name;chooseList=false}}}
    if(adding||editing!=null)TaskComposer(store,editing?.note ?: Note(task=TaskDetails(completed=addingStage=="done",status=addingStage)),onCancel={adding=false;editing=null}){n->val r=editing;if(r==null){store.create();store.update(n);store.flush();store.select(null)}else store.taskChange(r.noteId){n};adding=false;editing=null}
    options?.let{r->IosSheet("Task options",onDismiss={options=null}){SheetRow("Edit task","edit"){editing=r;options=null};listOf("todo","progress","review","done").forEachIndexed{i,state->SheetRow("Move to "+listOf("To Do","In Progress","In Review","Done")[i],"list"){store.taskChange(r.noteId){it.copy(task=it.task!!.moveTo(state))};options=null}};SheetRow("Open details & subtasks","list"){store.select(r.noteId);options=null};SheetRow("Duplicate","plus"){store.create();store.update(r.note.copy(task=r.note.task!!.copy(completed=false,status="todo")));store.flush();store.select(null);options=null};SheetRow("Convert to note","edit"){store.taskChange(r.noteId){it.copy(task=null)};options=null};SheetRow("Move to Trash","trash"){store.taskChange(r.noteId){it.copy(deleted=true)};options=null}}}
}
@Composable fun TaskComposer(store:Store,initial:Note,onCancel:()->Unit,onSave:(Note)->Unit) {
    val c=LocalLeafColors.current
    var taskEditorId by remember{mutableStateOf<String?>(null)};var body by remember{mutableStateOf(initial.prepared())};var tagText by remember{mutableStateOf(initial.tags.joinToString(", "))}
    var title by remember{mutableStateOf(initial.title)};var t by remember{mutableStateOf(initial.task ?: TaskDetails())};var dated by remember{mutableStateOf(t.dueAt>0)};var date by remember{mutableLongStateOf(if(t.dueAt>0)t.dueAt else System.currentTimeMillis())};var subs by remember{mutableStateOf("")}
    val attachments=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()){uris->try{body=body.insertMedia(uris.map{store.stageAttachment(it)},null,null)}catch(e:Exception){store.error=e.message}}
    val focusManager=androidx.compose.ui.platform.LocalFocusManager.current
    var alertMessage by remember{mutableStateOf("")}
    val permission=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()){granted->t=t.copy(remind=granted);if(!granted)alertMessage="Allow notifications for Pebble Notes in Android settings."}
    val activityContext=androidx.compose.ui.platform.LocalContext.current
    IosSheet(if(initial.title.isEmpty())"New task" else "Task details",onDismiss=onCancel) {
        TaskField(title,"What would you like to do?"){title=it}
        IosSegments(listOf("To Do","In Progress","In Review","Done"),listOf("todo","progress","review","done").indexOf(t.stage)){t=t.moveTo(listOf("todo","progress","review","done")[it])}
        Label("List",13,color=c.secondary,modifier=Modifier.padding(top=16.dp));TaskField(t.list,"Personal"){t=t.copy(list=it.take(128))}
        TaskField(tagText,"Tags, separated by commas"){tagText=it};Box(Modifier.fillMaxWidth().height(.5.dp).background(c.separator).padding(vertical=12.dp))
        IosSegments(listOf("Anytime","Date"),if(dated)1 else 0){dated=it==1}
        if(dated){
            SheetRow(taskDate(t.copy(dueAt=date)),"calendar"){val cal=Calendar.getInstance().apply{timeInMillis=date};DatePickerDialog(activityContext,{_,y,m,d->val next=Calendar.getInstance().apply{timeInMillis=date;set(Calendar.YEAR,y);set(Calendar.MONTH,m);set(Calendar.DAY_OF_MONTH,d)};date=next.timeInMillis},cal.get(Calendar.YEAR),cal.get(Calendar.MONTH),cal.get(Calendar.DAY_OF_MONTH)).show()}
            IosSegments(listOf("All day","Time"),if(t.hasTime)1 else 0){t=t.copy(hasTime=it==1)}
            if(t.hasTime)SheetRow(SimpleDateFormat("h:mm a",Locale.getDefault()).format(Date(date)),"clock"){val cal=Calendar.getInstance().apply{timeInMillis=date};TimePickerDialog(activityContext,{_,h,m->date=Calendar.getInstance().apply{timeInMillis=date;set(Calendar.HOUR_OF_DAY,h);set(Calendar.MINUTE,m);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)}.timeInMillis},cal.get(Calendar.HOUR_OF_DAY),cal.get(Calendar.MINUTE),false).show()}
            IosSegments(listOf("No alert","Notify me"),if(t.remind)1 else 0){index->if(index==0)t=t.copy(remind=false) else if(android.os.Build.VERSION.SDK_INT>=33&&androidx.core.content.ContextCompat.checkSelfPermission(activityContext,android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)permission.launch(android.Manifest.permission.POST_NOTIFICATIONS) else t=t.copy(remind=true)}
            Label("Android may delay alerts while saving battery. All-day tasks notify at 9 AM. Repeats advance when completed.",11,color=c.secondary,modifier=Modifier.padding(vertical=8.dp))
            if(alertMessage.isNotEmpty())Label(alertMessage,12,color=c.danger)
            Label("Repeat",13,color=c.secondary,modifier=Modifier.padding(top=12.dp,bottom=8.dp));IosSegments(listOf("None","Daily","Weekly","Monthly"),listOf("none","daily","weekly","monthly").indexOf(t.repeatRule).coerceAtLeast(0)){t=t.copy(repeatRule=listOf("none","daily","weekly","monthly")[it])}
        }
        Label("Priority",13,color=c.secondary,modifier=Modifier.padding(top=12.dp,bottom=8.dp));IosSegments(listOf("None","Low","Medium","High"),t.priority){t=t.copy(priority=it)}
        Box(Modifier.fillMaxWidth().height(.5.dp).background(c.separator));Label("Description",18,FontWeight.SemiBold,modifier=Modifier.padding(top=20.dp,bottom=10.dp))
        Row {listOf("bold" to "Bold","italic" to "Italic","underline" to "Underline").forEach{(style,label)->Pressable(Modifier.heightIn(min=44.dp).padding(horizontal=10.dp),label,onClick={activeTextEditor?.let{format(it,style);taskEditorId?.let{id->body=body.editBlock(id){b->b.copy(text=it.text.toString(),spans=spansFrom(it.text))}}}}){Label(label,14)}}}
        body.document.filter{it.isText&&it.kind!="check"}.forEach{b->key(b.id){androidx.compose.ui.viewinterop.AndroidView(factory={context->android.widget.EditText(context).apply{setBackgroundColor(android.graphics.Color.TRANSPARENT);minLines=3;hint="Add a description…";inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE;setText(attributed(Note(text=b.text,spans=b.spans)));setOnFocusChangeListener{_,focused->if(focused){activeTextEditor=this;taskEditorId=b.id}};addTextChangedListener(object:android.text.TextWatcher{override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){};override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){if(tag!=true)applyTypingStyle(this@apply,start,count)};override fun afterTextChanged(s:android.text.Editable?){if(s!=null&&tag!=true)body=body.editBlock(b.id){it.copy(text=s.toString(),spans=spansFrom(s))}}})}},update={it.setTextColor(c.text.toArgb());it.setHintTextColor(c.secondary.toArgb())},modifier=Modifier.fillMaxWidth().heightIn(min=90.dp))}}
        if(body.document.none{it.isText&&it.kind!="check"})SheetRow("Add description","plus"){body=body.copy(blocks=body.document+Block())}
        Box(Modifier.fillMaxWidth().height(.5.dp).background(c.separator));Label("Subtasks",18,FontWeight.SemiBold,modifier=Modifier.padding(top=20.dp,bottom=8.dp))
        body.document.filter{it.kind=="check"}.forEach{b->Row(verticalAlignment=Alignment.CenterVertically){Pressable(Modifier.size(44.dp),"Complete subtask",onClick={body=body.editBlock(b.id){it.copy(checked=!it.checked)}}){Glyph(if(b.checked)"done" else "check")};Box(Modifier.weight(1f)){TaskField(b.text,"Subtask"){value->body=body.editBlock(b.id){it.copy(text=value)}}};Pressable(Modifier.size(44.dp),"Remove subtask",onClick={body=body.copy(blocks=body.document.filter{it.id!=b.id})}){Glyph("close")}}}
        TaskField(subs,"Add subtasks, one per line"){subs=it}
        Label("Attachments",18,FontWeight.SemiBold,modifier=Modifier.padding(top=16.dp));SheetRow("Add files…","plus"){focusManager.clearFocus();attachments.launch(arrayOf("*/*"))};body.attachments.forEach{a->Row(verticalAlignment=Alignment.CenterVertically){Label(a.name,14,color=c.secondary,modifier=Modifier.weight(1f).padding(vertical=8.dp));Pressable(Modifier.size(44.dp),"Remove attachment",onClick={body=body.copy(blocks=body.document.filter{it.mediaId!=a.id}).prepared()}){Glyph("close")}}}

        SheetRow("Save task","check"){focusManager.clearFocus();if(title.isNotBlank()){val meta=t.copy(list=t.list.trim().ifBlank{"Personal"},dueAt=if(dated){if(t.hasTime)date else dayStart(date)}else 0,hasTime=dated&&t.hasTime,repeatRule=if(dated)t.repeatRule else "none",remind=dated&&t.remind);onSave(body.copy(title=title.trim(),task=meta,tags=tagText.split(','),blocks=body.document+subs.lines().map{it.trim()}.filter{it.isNotBlank()}.map{Block(kind="check",text=it)}).prepared())}}
    }
}
@Composable private fun TaskField(value:String,placeholder:String,onChange:(String)->Unit){val c=LocalLeafColors.current;Box(Modifier.fillMaxWidth().padding(vertical=10.dp).clip(RoundedCornerShape(14.dp)).background(c.paper).padding(14.dp)){if(value.isEmpty())Label(placeholder,16,color=c.secondary);BasicTextField(value,onChange,textStyle=TextStyle(fontSize=16.sp,color=c.text),modifier=Modifier.fillMaxWidth())}}
