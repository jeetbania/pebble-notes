package dev.leafnotes

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews

/** Widget preferences belong to this home screen, never to the synced note. */
data class WidgetChoice(val kind:String="recent",val source:String="",val theme:String="glass") {
    companion object {
        val kinds=linkedMapOf("recent" to "Recent notes","pinned" to "Pinned notes","collection" to "Collection","note" to "Single note","checklist" to "Checklist","quote" to "Quote","tasks" to "Tasks","capture" to "Quick capture")
        fun read(c:Context,id:Int):WidgetChoice {val p=c.getSharedPreferences("pebble_widgets",0);return WidgetChoice(p.getString("$id.kind","recent")!!,p.getString("$id.source","")!!,p.getString("$id.theme","glass")!!)}
    }
    fun save(c:Context,id:Int){c.getSharedPreferences("pebble_widgets",0).edit().putString("$id.kind",kind).putString("$id.source",source).putString("$id.theme",theme).apply()}
}
data class WidgetNavigation(val mode:String,val id:String="",val token:String=uid())
data class WidgetLine(val title:String,val detail:String="",val noteId:String="",val blockId:String="",val checked:Boolean?=null,val revisionId:String="",val action:String="note")
data class WidgetContent(val heading:String,val lines:List<WidgetLine> = emptyList(),val featureTitle:String="",val featureBody:String="",val noteId:String="",val footer:String="",val empty:String="")

/** Pure projection: don't expose Trash, archives, folder markers or attachment paths. */
fun widgetContent(revisions:List<Revision>,choice:WidgetChoice):WidgetContent {
    val live=revisions.groupBy{it.noteId}.values.map{g->g.firstOrNull{!it.note.deleted}?:g.first()}.filter{it.note.recordType=="note"&&!it.note.deleted&&!it.note.archived&&it.note.purgedAt==null}.sortedByDescending{it.createdAt}
    val notes=live.filter{it.note.task==null}
    fun matching()=notes.firstOrNull{it.noteId==choice.source}
    return when(choice.kind) {
        "capture"->WidgetContent("Capture",listOf(WidgetLine("New note","A thought worth keeping",action="new"),WidgetLine("Checklist","Make a little progress",action="newChecklist"),WidgetLine("New task","Give it a next step",action="newTask")),footer="A quieter place to begin")
        "note","quote","checklist"->{
            val r=matching()
            if(r==null)WidgetContent(WidgetChoice.kinds[choice.kind]!!,empty="Choose a note in widget settings",footer="Tap to choose a note")
            else if(choice.kind=="checklist") {
                val checks=r.note.document.filter{it.kind=="check"}
                WidgetContent(r.note.displayTitle,checks.map{WidgetLine(it.text.ifBlank{"Untitled item"},noteId=r.noteId,blockId=it.id,checked=it.checked,revisionId=r.id)},noteId=r.noteId,footer="${checks.count{it.checked}} of ${checks.size} complete",empty=if(checks.isEmpty())"No checklist items yet. Open this note to add one." else "")
            } else if(choice.kind=="quote") {
                val quote=r.note.document.firstOrNull{it.kind=="quote"&&it.text.isNotBlank()}?.text
                WidgetContent("A thought to keep",emptyList(),featureTitle="“",featureBody=quote ?: "Add a Quote block to this note.",noteId=r.noteId,footer=r.note.displayTitle)
            } else WidgetContent("Note",emptyList(),featureTitle=r.note.displayTitle,featureBody=r.note.document.filter{it.isText}.joinToString("\n"){when(it.kind){"check"->(if(it.checked)"☑ " else "☐ ")+it.text;"bullet"->"• "+it.text;"quote"->"“${it.text}”";else->it.text}}.take(1800),noteId=r.noteId,footer=r.note.collection)
        }
        "tasks"->{
            val tasks=live.filter{it.note.task?.let{t->!t.completed&&(choice.source.isEmpty()||t.list==choice.source)}==true}.sortedWith(compareBy<Revision>{it.note.task!!.dueAt.takeIf{d->d>0}?:Long.MAX_VALUE}.thenByDescending{it.note.task!!.priority})
            WidgetContent(if(choice.source.isEmpty())"Tasks" else choice.source,tasks.map{WidgetLine(it.note.displayTitle,taskDate(it.note.task!!),it.noteId,checked=false,revisionId=it.id,action="task")},footer="${tasks.size} to do",empty=if(tasks.isEmpty())"Nothing waiting. A little room to breathe." else "")
        }
        else->{
            val records=notes.filter{when(choice.kind){"pinned"->it.note.pinned;"collection"->it.note.collection==choice.source||it.note.collection.startsWith(choice.source+"/");else->true}}
            val heading=when(choice.kind){"pinned"->"Pinned";"collection"->choice.source.substringAfterLast('/').ifBlank{"Collection"};else->"Notes"}
            WidgetContent(heading,records.map{WidgetLine(it.note.displayTitle,it.note.text.lineSequence().firstOrNull{t->t.isNotBlank()}?.take(120)?:"Open note",it.noteId)},footer="${records.size} ${if(records.size==1)"note" else "notes"}",empty=if(records.isNotEmpty())"" else when(choice.kind){"pinned"->"Pin a note to keep it close.";"collection"->"No notes in this collection yet.";else->"A little space for your next thought."})
        }
    }
}

/** Immutable revision token makes double taps and stale widget actions idempotent. */
fun Store.widgetComplete(noteId:String,revisionId:String,blockId:String):Boolean {
    flush();reload()
    val versions=heads.filter{it.noteId==noteId}
    val r=versions.singleOrNull()?.takeIf{it.id==revisionId&&!it.note.deleted&&!it.note.archived&&it.note.purgedAt==null}?:return false
    val changed=if(blockId.isEmpty())r.note.task?.let{r.note.copy(task=it.complete())}?:return false
        else {val block=r.note.document.firstOrNull{it.id==blockId&&it.kind=="check"}?:return false;r.note.editBlock(blockId){it.copy(checked=!block.checked)}}
    if(selected==noteId){update(changed);flush()}else taskChange(noteId){changed}
    return error==null
}

open class PebbleWidget:AppWidgetProvider() {
    override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray){ids.forEach{WidgetViews.update(context,it)}}
    override fun onAppWidgetOptionsChanged(context:Context,manager:AppWidgetManager,id:Int,options:Bundle){WidgetViews.update(context,id)}
    override fun onDeleted(context:Context,ids:IntArray){val p=context.getSharedPreferences("pebble_widgets",0).edit();ids.forEach{id->listOf("kind","source","theme").forEach{p.remove("$id.$it")}};p.apply()}
    override fun onReceive(context:Context,intent:Intent){
        super.onReceive(context,intent)
        if(intent.action==COMPLETE) {
            val id=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1)
            if(id !in WidgetViews.ids(context))return
            val choice=WidgetChoice.read(context,id);val note=intent.getStringExtra("note")?:return
            if(choice.kind=="checklist"&&choice.source!=note || choice.kind !in listOf("tasks","checklist"))return
            try {val store=Libraries.get(context);val allowed=widgetContent(store.revisions(headsOnly=true),choice).lines.any{it.noteId==note&&it.revisionId==intent.getStringExtra("revision")&&it.blockId==intent.getStringExtra("block").orEmpty()};if(allowed)store.widgetComplete(note,intent.getStringExtra("revision").orEmpty(),intent.getStringExtra("block").orEmpty())}catch(_:Exception){/* Never mutate a different item after a stale tap. */}
            WidgetViews.refresh(context)
        }
    }
    companion object {const val COMPLETE="dev.leafnotes.WIDGET_COMPLETE"}
}
class PebbleSmallWidget:PebbleWidget()
class PebbleMediumWidget:PebbleWidget()
class PebbleLargeWidget:PebbleWidget()

object WidgetViews {
    private val providers=listOf(PebbleSmallWidget::class.java,PebbleMediumWidget::class.java,PebbleLargeWidget::class.java)
    fun ids(c:Context)=providers.flatMap{AppWidgetManager.getInstance(c).getAppWidgetIds(ComponentName(c,it)).toList()}
    private val handler=Handler(Looper.getMainLooper());private var pending:Runnable?=null
    @Synchronized fun request(c:Context){if(c.filesDir!=c.applicationContext.filesDir)return;pending?.let{handler.removeCallbacks(it)};pending=Runnable{refresh(c.applicationContext)}.also{handler.postDelayed(it,400)}}
    fun refresh(c:Context){val widgets=ids(c);if(widgets.isEmpty())return;val revisions=try{Libraries.get(c).revisions(headsOnly=true)}catch(_:Exception){null};widgets.forEach{update(c,it,revisions)}}
    fun update(c:Context,id:Int,revisions:List<Revision>?=null){
        val manager=AppWidgetManager.getInstance(c);val choice=WidgetChoice.read(c,id)
        val content=try{widgetContent(revisions?:Libraries.get(c).revisions(headsOnly=true),choice)}catch(_:Exception){WidgetContent("Pebble",empty="Open Pebble to restore access to your notes.")}
        val view=if(Build.VERSION.SDK_INT>=31)RemoteViews(mapOf(SizeF(140f,140f) to render(c,id,choice,content,0),SizeF(250f,184f) to render(c,id,choice,content,1),SizeF(250f,280f) to render(c,id,choice,content,2)))
        else {val o=manager.getAppWidgetOptions(id);render(c,id,choice,content,if(o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)>=280)2 else if(o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)>=250&&o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)>=184)1 else 0)}
        manager.updateAppWidget(id,view)
    }
    private fun open(c:Context,id:Int,mode:String,note:String=""):PendingIntent {
        val intent=Intent(c,MainActivity::class.java).setAction("dev.leafnotes.WIDGET_OPEN").setData(Uri.Builder().scheme("pebble").authority("widget").appendPath(id.toString()).appendPath(mode).appendPath(note).build()).putExtra("pebbleWidgetAction",mode).putExtra("pebbleWidgetSource",note).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(c,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    private fun configure(c:Context,id:Int)=PendingIntent.getActivity(c,id,Intent(c,WidgetSetupActivity::class.java).setData(Uri.parse("pebble://widget/$id/configure")).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun complete(c:Context,id:Int,line:WidgetLine):PendingIntent {
        val provider=AppWidgetManager.getInstance(c).getAppWidgetInfo(id)?.provider?:ComponentName(c,PebbleSmallWidget::class.java)
        val intent=Intent(PebbleWidget.COMPLETE).setComponent(provider).setData(Uri.Builder().scheme("pebble").authority("widget").appendPath(id.toString()).appendPath(line.noteId).appendPath(line.blockId).appendPath(line.revisionId).build()).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id).putExtra("note",line.noteId).putExtra("block",line.blockId).putExtra("revision",line.revisionId)
        return PendingIntent.getBroadcast(c,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    private fun checkIcon(c:Context,checked:Boolean,accent:Int):android.graphics.Bitmap {
        val px=(24*c.resources.displayMetrics.density).toInt().coerceAtLeast(24)
        val b=android.graphics.Bitmap.createBitmap(px,px,android.graphics.Bitmap.Config.ARGB_8888)
        val canvas=android.graphics.Canvas(b);canvas.scale(px/24f,px/24f)
        val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{color=accent;strokeWidth=1.6f;style=if(checked)android.graphics.Paint.Style.FILL else android.graphics.Paint.Style.STROKE}
        canvas.drawCircle(12f,12f,9f,paint)
        if(checked){paint.style=android.graphics.Paint.Style.STROKE;paint.strokeWidth=2f;paint.strokeCap=android.graphics.Paint.Cap.ROUND;paint.strokeJoin=android.graphics.Paint.Join.ROUND;paint.color=if(accent==android.graphics.Color.parseColor("#FFDC75"))android.graphics.Color.parseColor("#191B21") else android.graphics.Color.WHITE;val path=android.graphics.Path().apply{moveTo(7.8f,12f);lineTo(10.7f,15f);lineTo(16.5f,9f)};canvas.drawPath(path,paint)}
        return b
    }
    private fun headingIcon(c:Context,kind:String,accent:Int):android.graphics.Bitmap {
        val px=(20*c.resources.displayMetrics.density).toInt().coerceAtLeast(20);val bitmap=android.graphics.Bitmap.createBitmap(px,px,android.graphics.Bitmap.Config.ARGB_8888)
        val drawable=c.getDrawable(if(kind in listOf("tasks","checklist"))R.drawable.lucide_list_checks else if(kind=="quote")R.drawable.pebble_quote else R.drawable.lucide_folder)!!.mutate();drawable.setTint(accent);drawable.setBounds(0,0,px,px);drawable.draw(android.graphics.Canvas(bitmap));return bitmap
    }
    fun render(c:Context,id:Int,choice:WidgetChoice,data:WidgetContent,size:Int):RemoteViews {
        val dark=choice.theme=="midnight";val ink=android.graphics.Color.parseColor(if(dark)"#FAF7EE" else "#24211C");val secondary=android.graphics.Color.parseColor(if(dark)"#D1CDC3" else "#5B5750");val accent=android.graphics.Color.parseColor(if(dark)"#FFDC75" else "#765B06")
        val scale=c.resources.configuration.fontScale;val rows=if(size==2)if(scale>1f)3 else 4 else if(size==1&&scale<=1.3)2 else 1
        val v=RemoteViews(c.packageName,R.layout.widget_pebble)
        v.setInt(R.id.widget_root,"setBackgroundResource",when(choice.theme){"midnight"->R.drawable.widget_midnight;"paper"->R.drawable.widget_paper;else->R.drawable.widget_glass})
        v.setImageViewBitmap(R.id.widget_heading_icon,headingIcon(c,choice.kind,accent));v.setViewVisibility(R.id.widget_heading_icon,if(size==0)View.GONE else View.VISIBLE);v.setTextViewText(R.id.widget_heading,if(size==0&&choice.kind=="quote")"Quote" else data.heading);v.setTextColor(R.id.widget_heading,accent);v.setTextColor(R.id.widget_add,accent);v.setTextColor(R.id.widget_footer,secondary)
        v.setTextViewText(R.id.widget_footer,data.footer);v.setViewVisibility(R.id.widget_footer,if(scale>1.3||size==0&&choice.kind=="quote")View.GONE else View.VISIBLE)
        val route=when(choice.kind){"tasks"->"tasks";"collection"->"collection";else->"home"}
        if(id>=0){v.setOnClickPendingIntent(R.id.widget_root,open(c,id,if(data.noteId.isNotEmpty())"note" else route,data.noteId.ifEmpty{choice.source}));v.setOnClickPendingIntent(R.id.widget_add,open(c,id,when(choice.kind){"tasks"->"newTask";"checklist"->"newChecklist";"collection"->"newCollection";else->"new"},choice.source));v.setOnClickPendingIntent(R.id.widget_heading,configure(c,id));v.setContentDescription(R.id.widget_heading,"${data.heading}. Widget settings")}
        v.setContentDescription(R.id.widget_add,when(choice.kind){"tasks"->"Create a task";"checklist"->"Create a checklist";"collection"->"Create a note in ${choice.source}";else->"Create a note"})
        v.setTextViewTextSize(R.id.widget_add,android.util.TypedValue.COMPLEX_UNIT_DIP,24f)
        v.removeAllViews(R.id.widget_content)
        if(data.empty.isNotEmpty() || data.featureTitle.isNotEmpty()) {
            val f=RemoteViews(c.packageName,R.layout.widget_feature)
            f.setTextViewText(R.id.widget_feature_title,if(size==0)data.empty.ifEmpty{if(choice.kind=="quote")data.featureBody else data.featureTitle} else if(data.empty.isNotEmpty())"A little space" else data.featureTitle)
            f.setTextViewTextSize(R.id.widget_feature_title,android.util.TypedValue.COMPLEX_UNIT_SP,if(size==0)if(data.empty.isNotEmpty())14f else if(choice.kind=="quote")16f else 18f else 19f)
            f.setTextViewText(R.id.widget_feature_body,data.empty.ifEmpty{data.featureBody});f.setTextColor(R.id.widget_feature_title,ink);f.setTextColor(R.id.widget_feature_body,secondary)
            f.setTextViewTextSize(R.id.widget_feature_body,android.util.TypedValue.COMPLEX_UNIT_SP,if(choice.kind=="quote")if(size==2)23f else 18f else 14f)
            f.setInt(R.id.widget_feature_title,"setMaxLines",if(size==1 || size==0&&scale>1.3)1 else if(size==0&&choice.kind=="quote")3 else 2);f.setInt(R.id.widget_feature_body,"setMaxLines",if(scale>1f)1 else if(size==1)2 else if(choice.kind=="quote")4 else 5);if(size==0)f.setViewVisibility(R.id.widget_feature_body,View.GONE);f.setContentDescription(R.id.widget_feature,data.featureTitle+". "+data.empty.ifEmpty{data.featureBody})
            if(id>=0)f.setOnClickPendingIntent(R.id.widget_feature,if(data.noteId.isEmpty()&&choice.kind in listOf("note","quote","checklist"))configure(c,id)else open(c,id,if(data.noteId.isNotEmpty())"note" else route,data.noteId.ifEmpty{choice.source}))
            v.addView(R.id.widget_content,f)
        } else data.lines.take(if(choice.kind=="capture")if(size==2)3 else if(size==1)2 else 1 else rows).forEachIndexed{index,line->
            if(index>0){val separator=RemoteViews(c.packageName,R.layout.widget_separator);separator.setInt(R.id.widget_separator,"setBackgroundColor",if(dark)0x22FFFFFF else 0x1524211C);v.addView(R.id.widget_content,separator)}
            val r=RemoteViews(c.packageName,R.layout.widget_row);r.setTextViewText(R.id.widget_row_title,line.title);r.setTextColor(R.id.widget_row_title,ink);r.setTextViewText(R.id.widget_row_detail,line.detail);r.setTextColor(R.id.widget_row_detail,secondary);r.setViewVisibility(R.id.widget_row_detail,if(line.detail.isBlank()||size==0||scale>1f)View.GONE else View.VISIBLE)
            r.setContentDescription(R.id.widget_row,line.title+if(line.detail.isNotEmpty())", ${line.detail}" else "")
            if(line.checked!=null){r.setViewVisibility(R.id.widget_row_check,View.VISIBLE);r.setImageViewBitmap(R.id.widget_row_check,checkIcon(c,line.checked,accent));r.setContentDescription(R.id.widget_row_check,if(line.checked)"Mark ${line.title} incomplete" else "Complete ${line.title}");if(id>=0)r.setOnClickPendingIntent(R.id.widget_row_check,complete(c,id,line))}
            if(id>=0)r.setOnClickPendingIntent(R.id.widget_row,open(c,id,line.action,line.noteId))
            v.addView(R.id.widget_content,r)
        }
        return v
    }
}
