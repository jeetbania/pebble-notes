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
import android.view.View
import android.widget.RemoteViews

/** Widget preferences belong to this home screen, never to the synced note. */
data class WidgetChoice(val kind:String="recent",val source:String="",val theme:String="notes") {
    companion object {
        val kinds=linkedMapOf("recent" to "Recent notes","pinned" to "Pinned notes","collection" to "Collection","note" to "Single note","checklist" to "Checklist","quote" to "Quote","tasks" to "Tasks","capture" to "Quick capture")
        fun read(c:Context,id:Int):WidgetChoice {val p=c.getSharedPreferences("pebble_widgets",0);return WidgetChoice(p.getString("$id.kind","recent")!!,p.getString("$id.source","")!!,p.getString("$id.theme","notes")!!)}
    }
    fun save(c:Context,id:Int){c.getSharedPreferences("pebble_widgets",0).edit().putString("$id.kind",kind).putString("$id.source",source).putString("$id.theme",theme).apply()}
}
data class WidgetNavigation(val mode:String,val id:String="",val token:String=uid())
data class WidgetLine(val title:String,val detail:String="",val noteId:String="",val blockId:String="",val checked:Boolean?=null,val revisionId:String="",val action:String="note",val modifiedAt:Long=0,val feature:Boolean=false)
data class WidgetContent(val heading:String,val lines:List<WidgetLine> = emptyList(),val featureTitle:String="",val featureBody:String="",val noteId:String="",val footer:String="",val empty:String="")

/** Keep excerpts within widget transport limits without splitting emoji surrogate pairs. */
fun widgetText(text:String,limit:Int):String=if(text.codePointCount(0,text.length)<=limit)text else text.substring(0,text.offsetByCodePoints(0,limit-1))+"…"

/** Pure projection: don't expose Trash, archives, folder markers or attachment paths. */
fun widgetContent(revisions:List<Revision>,choice:WidgetChoice):WidgetContent {
    val live=revisions.groupBy{it.noteId}.values.map{g->g.firstOrNull{!it.note.deleted}?:g.first()}.filter{it.note.recordType=="note"&&!it.note.deleted&&!it.note.archived&&it.note.purgedAt==null}.sortedByDescending{it.createdAt}
    val notes=live.filter{it.note.task==null}
    fun matching()=notes.firstOrNull{it.noteId==choice.source}
    return when(choice.kind) {
        "capture"->WidgetContent("Capture",listOf(WidgetLine("New note","Write a note",action="new"),WidgetLine("Checklist","Start a checklist",action="newChecklist"),WidgetLine("New task","Add a task",action="newTask")),footer="A quieter place to begin")
        "note","quote","checklist"->{
            val r=matching()
            if(r==null)WidgetContent(WidgetChoice.kinds[choice.kind]!!,empty="Choose a note in widget settings",footer="Tap to choose a note")
            else if(choice.kind=="checklist") {
                val checks=r.note.document.filter{it.kind=="check"}
                WidgetContent(r.note.displayTitle,checks.map{WidgetLine(widgetText(it.text.ifBlank{"Untitled item"},400),noteId=r.noteId,blockId=it.id,checked=it.checked,revisionId=r.id)},noteId=r.noteId,footer="${checks.count{it.checked}} of ${checks.size} complete",empty=if(checks.isEmpty())"No checklist items yet. Open this note to add one." else "")
            } else if(choice.kind=="quote") {
                val quote=r.note.document.firstOrNull{it.kind=="quote"&&it.text.isNotBlank()}?.text?.let{widgetText(it,4000)}
                WidgetContent("A thought to keep",emptyList(),featureTitle="“",featureBody=quote ?: "Add a Quote block to this note.",noteId=r.noteId,footer=r.note.displayTitle)
            } else WidgetContent("Note",emptyList(),featureTitle=r.note.displayTitle,featureBody=r.note.document.filter{it.isText}.joinToString("\n"){when(it.kind){"check"->(if(it.checked)"☑ " else "☐ ")+it.text;"bullet"->"• "+it.text;"quote"->"“${it.text}”";else->it.text}}.let{widgetText(it,10000)},noteId=r.noteId,footer=r.note.collection)
        }
        "tasks"->{
            val tasks=live.filter{it.note.task?.let{t->!t.completed&&(choice.source.isEmpty()||t.list==choice.source)}==true}.sortedWith(compareBy<Revision>{it.note.task!!.dueAt.takeIf{d->d>0}?:Long.MAX_VALUE}.thenByDescending{it.note.task!!.priority})
            WidgetContent(if(choice.source.isEmpty())"Tasks" else choice.source,tasks.map{WidgetLine(widgetText(it.note.displayTitle,240),taskDate(it.note.task!!),it.noteId,checked=false,revisionId=it.id,action="task")},footer="${tasks.size} to do",empty=if(tasks.isEmpty())"No pending tasks" else "")
        }
        else->{
            val records=notes.filter{when(choice.kind){"pinned"->it.note.pinned;"collection"->it.note.collection==choice.source||it.note.collection.startsWith(choice.source+"/");else->true}}
            val heading=when(choice.kind){"pinned"->"Pinned";"collection"->choice.source.substringAfterLast('/').ifBlank{"Collection"};else->"Notes"}
            WidgetContent(heading,records.map{WidgetLine(widgetText(it.note.displayTitle,240),it.note.text.lineSequence().firstOrNull{t->t.isNotBlank()}?.let{widgetText(it,120)}?:"",it.noteId,modifiedAt=it.createdAt)},footer="${records.size} ${if(records.size==1)"note" else "notes"}",empty=if(records.isNotEmpty())"" else when(choice.kind){"pinned"->"Pin a note to keep it close.";"collection"->"No notes in this collection yet.";else->"No notes yet"})
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
    override fun onDeleted(context:Context,ids:IntArray){ids.forEach{WidgetViews.cache(context,it).delete()};val p=context.getSharedPreferences("pebble_widgets",0).edit();ids.forEach{id->listOf("kind","source","theme").forEach{p.remove("$id.$it")}};p.apply()}
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

/** A collection tap is delivered directly by the launcher to this explicit activity.
 * Completion has a transparent, preview-free window and never brings the editor forward. */
class WidgetTapActivity:android.app.Activity() {
    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        val id=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1)
        try { if(id in WidgetViews.ids(this)) {
            val mode=intent.getStringExtra("mode").orEmpty()
            if(mode=="complete")WidgetViews.complete(this,id,intent.getStringExtra("note").orEmpty(),intent.getStringExtra("block").orEmpty(),intent.getStringExtra("revision").orEmpty())
            else if(mode in setOf("note","task","new","newChecklist","newTask"))startActivity(WidgetViews.navigationIntent(this,id,mode,intent.getStringExtra("note").orEmpty()))
        }}catch(e:Exception){Libraries.get(this).error=e.message}
        finish();overridePendingTransition(0,0)
    }
}

/** Pre-Android 12 fallback, and a lazy source for larger libraries to avoid binder limits. */
class WidgetCollectionService:android.widget.RemoteViewsService() {
    override fun onGetViewFactory(intent:Intent)=WidgetCollectionFactory(applicationContext,intent)
}
class WidgetCollectionFactory(private val context:Context,private val intent:Intent,private val load:(()->List<Revision>)? = null):android.widget.RemoteViewsService.RemoteViewsFactory {
    private var choice=WidgetChoice();private var items=emptyList<WidgetLine>()
    override fun onCreate(){}
    override fun onDataSetChanged(){
        items=try {
            if(load!=null){choice=WidgetChoice.read(context,intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1));WidgetViews.items(widgetContent(load.invoke(),choice))}
            else {val o=org.json.JSONObject(String(WidgetViews.cache(context,intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1)).readFully(),Charsets.UTF_8));choice=WidgetChoice(o.getString("kind"),o.getString("source"),o.getString("theme"));val a=o.getJSONArray("items");(0 until a.length()).map{WidgetViews.readLine(a.getJSONObject(it))}}
        }catch(_:Exception){emptyList()}
    }
    override fun onDestroy(){items=emptyList()}
    override fun getCount()=items.size
    override fun getViewAt(position:Int):RemoteViews?=items.getOrNull(position)?.let{WidgetViews.row(context,choice,it,intent.getIntExtra("size",1))}
    override fun getLoadingView()=RemoteViews(context.packageName,R.layout.widget_loading)
    override fun getViewTypeCount()=2
    override fun getItemId(position:Int)=items.getOrNull(position)?.let{WidgetViews.stableId(it)}?:0
    override fun hasStableIds()=true
}

object WidgetViews {
    private val providers=listOf(PebbleSmallWidget::class.java,PebbleMediumWidget::class.java,PebbleLargeWidget::class.java)
    fun ids(c:Context)=providers.flatMap{AppWidgetManager.getInstance(c).getAppWidgetIds(ComponentName(c,it)).toList()}
    private val handler=Handler(Looper.getMainLooper());private var pending:Runnable?=null
    @Synchronized fun request(c:Context){if(c.filesDir!=c.applicationContext.filesDir)return;pending?.let{handler.removeCallbacks(it)};pending=Runnable{refresh(c.applicationContext)}.also{handler.postDelayed(it,400)}}
    fun refresh(c:Context){val widgets=ids(c);if(widgets.isEmpty())return;val revisions=try{Libraries.get(c).revisions(headsOnly=true)}catch(_:Exception){null};widgets.forEach{update(c,it,revisions)}}
    fun update(c:Context,id:Int,revisions:List<Revision>?=null){
        val manager=AppWidgetManager.getInstance(c);val choice=WidgetChoice.read(c,id)
        val data=try{widgetContent(revisions?:Libraries.get(c).revisions(headsOnly=true),choice)}catch(_:Exception){WidgetContent("Pebble",empty="Open Pebble to restore access to your notes.")}
        // One current-size collection avoids triplicating hundreds of rows in the binder payload.
        // The provider refreshes this composition whenever the launcher resizes the widget.
        val o=manager.getAppWidgetOptions(id)
        val portrait=c.resources.configuration.orientation!=android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val width=o.getInt(if(portrait)AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        val height=o.getInt(if(portrait)AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        val size=if(height>=280)2 else if(width>=250)1 else 0
        val view=render(c,id,choice,data,size)
        if(Build.VERSION.SDK_INT<31)saveCache(c,id,choice,items(data)) else cache(c,id).delete()
        manager.updateAppWidget(id,view)
        if(Build.VERSION.SDK_INT<31)manager.notifyAppWidgetViewDataChanged(id,R.id.widget_list)
    }

    fun cache(c:Context,id:Int)=android.util.AtomicFile(java.io.File(java.io.File(c.filesDir,"widget-cache").apply{mkdirs()},"$id.json"))
    fun readLine(o:org.json.JSONObject)=WidgetLine(o.getString("title"),o.getString("detail"),o.getString("note"),o.getString("block"),if(o.isNull("checked"))null else o.getBoolean("checked"),o.getString("revision"),o.getString("action"),o.getLong("modified"),o.getBoolean("feature"))
    internal fun saveCache(c:Context,id:Int,choice:WidgetChoice,rows:List<WidgetLine>){
        val a=org.json.JSONArray(rows.map{org.json.JSONObject().put("title",it.title).put("detail",it.detail).put("note",it.noteId).put("block",it.blockId).put("checked",it.checked?:org.json.JSONObject.NULL).put("revision",it.revisionId).put("action",it.action).put("modified",it.modifiedAt).put("feature",it.feature)})
        val bytes=org.json.JSONObject().put("schema",1).put("kind",choice.kind).put("source",choice.source).put("theme",choice.theme).put("items",a).toString().toByteArray(Charsets.UTF_8)
        val f=cache(c,id);val out=f.startWrite();try{out.write(bytes);f.finishWrite(out)}catch(e:Exception){f.failWrite(out);throw e}
    }
    fun navigationIntent(c:Context,id:Int,mode:String,note:String="")=Intent(c,MainActivity::class.java).setAction("dev.leafnotes.WIDGET_OPEN").setData(Uri.Builder().scheme("pebble").authority("widget").appendPath(id.toString()).appendPath(mode).appendPath(note).build()).putExtra("pebbleWidgetAction",mode).putExtra("pebbleWidgetSource",note).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    private fun open(c:Context,id:Int,mode:String,note:String="")=PendingIntent.getActivity(c,0,navigationIntent(c,id,mode,note),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun configure(c:Context,id:Int)=PendingIntent.getActivity(c,id,Intent(c,WidgetSetupActivity::class.java).setData(Uri.parse("pebble://widget/$id/configure")).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun complete(c:Context,id:Int,note:String,block:String,revision:String):Boolean {
        if(id !in ids(c))return false
        val choice=WidgetChoice.read(c,id)
        if(choice.kind !in listOf("checklist","tasks")||choice.kind=="checklist"&&choice.source!=note)return false
        val store=Libraries.get(c)
        val allowed=widgetContent(store.revisions(headsOnly=true),choice).lines.any{it.noteId==note&&it.blockId==block&&it.revisionId==revision}
        val changed=allowed&&store.widgetComplete(note,revision,block);refresh(c);return changed
    }
    fun items(data:WidgetContent):List<WidgetLine> = when {
        data.empty.isNotEmpty()->emptyList()
        data.featureTitle.isNotEmpty()->listOf(WidgetLine(data.featureTitle,data.featureBody,data.noteId,feature=true))
        else->data.lines
    }
    fun stableId(line:WidgetLine)=java.nio.ByteBuffer.wrap(java.security.MessageDigest.getInstance("SHA-256").digest("${line.noteId}/${line.blockId}/${line.action}/${line.feature}".toByteArray())).long
    fun fillIn(line:WidgetLine,complete:Boolean=false)=Intent().putExtra("mode",if(complete)"complete" else line.action).putExtra("note",line.noteId).putExtra("block",line.blockId).putExtra("revision",line.revisionId)
    private fun icon(c:Context,kind:String):android.graphics.Bitmap {
        val px=(20*c.resources.displayMetrics.density).toInt().coerceAtLeast(20);val b=android.graphics.Bitmap.createBitmap(px,px,android.graphics.Bitmap.Config.ARGB_8888)
        val d=c.getDrawable(if(kind in listOf("tasks","checklist"))R.drawable.lucide_list_checks else if(kind=="quote")R.drawable.pebble_quote else R.drawable.lucide_folder)!!.mutate();d.setTint(android.graphics.Color.WHITE);d.setBounds(0,0,px,px);d.draw(android.graphics.Canvas(b));return b
    }
    fun row(c:Context,choice:WidgetChoice,line:WidgetLine,size:Int):RemoteViews {
        val dark=choice.theme=="midnight";val ink=if(dark)0xFFF5F5F5.toInt() else android.graphics.Color.BLACK;val secondary=if(dark)0xFFBEBEBE.toInt() else 0xFF858585.toInt()
        if(line.feature) return RemoteViews(c.packageName,R.layout.widget_feature).apply {
            setTextViewText(R.id.widget_feature_title,line.title);setTextColor(R.id.widget_feature_title,ink)
            setTextViewText(R.id.widget_feature_body,line.detail);setTextColor(R.id.widget_feature_body,ink)
            setTextViewTextSize(R.id.widget_feature_body,android.util.TypedValue.COMPLEX_UNIT_SP,if(choice.kind=="quote")18f else 14f)
            setOnClickFillInIntent(R.id.widget_feature,fillIn(line));setContentDescription(R.id.widget_feature,line.title+". "+line.detail)
        }
        return RemoteViews(c.packageName,R.layout.widget_row).apply {
            setTextViewText(R.id.widget_row_title,line.title);setTextColor(R.id.widget_row_title,ink)
            val detail=if(line.modifiedAt>0)java.text.SimpleDateFormat("MMM d",java.util.Locale.getDefault()).format(java.util.Date(line.modifiedAt))+if(line.detail.isBlank())"" else "  "+line.detail else line.detail
            setTextViewText(R.id.widget_row_detail,detail);setTextColor(R.id.widget_row_detail,secondary)
            setViewVisibility(R.id.widget_row_detail,if(detail.isBlank()||size==1&&line.checked==null&&choice.kind!="capture")View.GONE else View.VISIBLE)
            setInt(R.id.widget_row_separator,"setBackgroundColor",if(dark)0x33FFFFFF else 0x33000000)
            setOnClickFillInIntent(R.id.widget_row,fillIn(line));setContentDescription(R.id.widget_row,line.title+if(detail.isBlank())"" else ", $detail")
            if(line.checked!=null){setViewVisibility(R.id.widget_row_check,View.VISIBLE);setImageViewResource(R.id.widget_row_check,if(line.checked)R.drawable.widget_checked else if(dark)R.drawable.widget_unchecked_dark else R.drawable.widget_unchecked);setContentDescription(R.id.widget_row_check,if(line.checked)"Mark ${line.title} incomplete" else "Complete ${line.title}");setOnClickFillInIntent(R.id.widget_row_check,fillIn(line,true))}
        }
    }
    fun render(c:Context,id:Int,choice:WidgetChoice,data:WidgetContent,size:Int):RemoteViews {
        val dark=choice.theme=="midnight"
        val v=RemoteViews(c.packageName,R.layout.widget_pebble)
        v.setInt(R.id.widget_root,"setBackgroundResource",if(dark)R.drawable.widget_midnight else R.drawable.widget_paper)
        v.setInt(R.id.widget_header,"setBackgroundResource",R.drawable.widget_header)
        v.setImageViewBitmap(R.id.widget_heading_icon,icon(c,choice.kind))
        v.setTextViewText(R.id.widget_heading,if(choice.kind=="quote")"Quote" else data.heading)
        v.setTextViewText(R.id.widget_footer,data.footer);v.setTextColor(R.id.widget_footer,if(dark)0xFFBEBEBE.toInt() else 0xFF858585.toInt())
        v.setTextViewText(R.id.widget_empty,data.empty.ifBlank{"Open Pebble to refresh your notes"});v.setTextColor(R.id.widget_empty,if(dark)0xFFF5F5F5.toInt() else android.graphics.Color.BLACK)
        v.setEmptyView(R.id.widget_list,R.id.widget_empty)
        v.setViewVisibility(R.id.widget_footer,View.GONE)
        if(id>=0){
            v.setOnClickPendingIntent(R.id.widget_header,configure(c,id))
            v.setContentDescription(R.id.widget_heading,"${data.heading}. Widget settings")
            v.setOnClickPendingIntent(R.id.widget_empty,if(choice.kind in listOf("note","quote","checklist"))configure(c,id)else open(c,id,if(choice.kind=="tasks")"tasks" else "home",choice.source))
            val click=Intent(c,WidgetTapActivity::class.java).setData(Uri.parse("pebble://widget/$id/tap")).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id)
            v.setPendingIntentTemplate(R.id.widget_list,PendingIntent.getActivity(c,id,click,PendingIntent.FLAG_UPDATE_CURRENT or if(Build.VERSION.SDK_INT>=31)PendingIntent.FLAG_MUTABLE else 0))
        }
        val rows=items(data)
        if(Build.VERSION.SDK_INT>=31){
            val builder=RemoteViews.RemoteCollectionItems.Builder().setHasStableIds(true).setViewTypeCount(2)
            rows.forEach{builder.addItem(stableId(it),row(c,choice,it,size))}
            v.setRemoteAdapter(R.id.widget_list,builder.build())
        }else {
            val service=Intent(c,WidgetCollectionService::class.java).setData(Uri.parse("pebble://widget/$id/list/$size")).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id).putExtra("size",size)
            v.setRemoteAdapter(R.id.widget_list,service)
        }
        v.setViewVisibility(R.id.widget_list,if(rows.isEmpty())View.GONE else View.VISIBLE)
        v.setViewVisibility(R.id.widget_empty,if(rows.isEmpty())View.VISIBLE else View.GONE)
        return v
    }
}
