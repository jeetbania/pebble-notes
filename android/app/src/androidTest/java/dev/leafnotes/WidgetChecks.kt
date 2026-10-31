package dev.leafnotes

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.LinearLayout
import android.widget.RemoteViews
import java.io.File
import org.json.JSONObject
import org.json.JSONArray

fun widgetChecks(context:Context,root:File):String {
    fun revision(id:String,note:Note,time:Long=1000)=Revision(JSONObject().put("schema",2).put("id",id+"-r").put("noteId",id).put("deviceId","widget-test").put("parents",JSONArray()).put("createdAt",time).put("note",note.prepared().json()).toString())
    val check=Block(id="check-one",kind="check",text="Pick up a sketchbook")
    val checks=revision("check",Note(title="Little things",blocks=listOf(check,Block(id="check-two",kind="check",text="Take a walk",checked=true),Block(kind="check",text="Call the bookshop"),Block(kind="check",text="Finish one small thing"))),3000)
    val quote=revision("quote",Note(title="Words to keep",pinned=true,blocks=listOf(Block(kind="quote",text="Pay attention. Be astonished. Tell about it."))),2000)
    val task=revision("task",Note(title="Send the first draft",task=TaskDetails(dueAt=System.currentTimeMillis(),repeatRule="daily")))
    val hidden=revision("hidden",Note(title="Never show this",deleted=true))
    val archived=revision("archive",Note(title="Not on the home screen",archived=true))
    val folder=revision("folder",Note(title="A folder",recordType="folder"))
    val all=listOf(checks,quote,task,hidden,archived,folder)
    check(widgetContent(all,WidgetChoice()).empty.isEmpty())
    check(widgetContent(all,WidgetChoice("tasks")).empty.isEmpty())
    check(widgetContent(emptyList(),WidgetChoice()).empty.isNotBlank())
    check(widgetContent(all,WidgetChoice()).lines.map{it.noteId}==listOf("check","quote"))
    check(widgetContent(all,WidgetChoice("pinned")).lines.single().noteId=="quote")
    check(widgetContent(all,WidgetChoice("quote","quote")).featureBody=="Pay attention. Be astonished. Tell about it.")
    check(widgetContent(all,WidgetChoice("checklist","check")).lines.size==4)
    check(widgetContent(all,WidgetChoice("tasks")).lines.single().noteId=="task")
    check(widgetContent(all,WidgetChoice("note","hidden")).noteId.isEmpty())
    check(widgetContent(all,WidgetChoice("collection","Personal")).lines.size==2)
    val store=Store(object:ContextWrapper(context){override fun getFilesDir()=File(root,"widget-store").apply{mkdirs()}})
    store.ingest(checks.raw,false);store.ingest(task.raw,false);store.reload()
    check(store.widgetComplete("check",checks.id,"check-one"))
    check(!store.widgetComplete("check",checks.id,"check-one"))
    check(store.revisions(headsOnly=true).first{it.noteId=="check"}.note.document.first{it.id=="check-one"}.checked)
    check(store.widgetComplete("task",task.id,""))
    check(!store.widgetComplete("task",task.id,""))
    check(store.revisions(headsOnly=true).first{it.noteId=="task"}.note.task!!.dueAt>task.note.task!!.dueAt)
    check(android.appwidget.AppWidgetManager.getInstance(context).installedProviders.count{it.provider.packageName==context.packageName}==3)
    val many=(1..220).map{revision("note-$it",Note(title="Note $it · 日本語 🌿",blocks=listOf(Block(text="A thought to keep"))),it.toLong())}
    val projected=widgetContent(many,WidgetChoice())
    check(projected.lines.size==220&&projected.lines.first().noteId=="note-220")
    check(WidgetViews.items(projected).map{WidgetViews.stableId(it)}.distinct().size==220)
    var maxPayload=0
    for(size in 0..2){val rv=WidgetViews.render(context,-1,WidgetChoice(),projected,size);check(rv.layoutId==R.layout.widget_pebble);val parcel=android.os.Parcel.obtain();rv.writeToParcel(parcel,0);maxPayload=maxOf(maxPayload,parcel.dataSize());parcel.recycle()};check(maxPayload<900000){"Widget payload too large: $maxPayload"}
    check(!widgetText("🌿".repeat(500),240).contains('\uFFFD'))
    val cached=object:ContextWrapper(context){override fun getFilesDir()=File(root,"widget-snapshot-test").apply{mkdirs()}}
    WidgetViews.saveCache(cached,43,WidgetChoice(),projected.lines)
    val persisted=WidgetCollectionFactory(cached,android.content.Intent().putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID,43))
    persisted.onDataSetChanged();check(persisted.count==220)
    WidgetViews.cache(cached,43).baseFile.writeText("damaged cache");persisted.onDataSetChanged();check(persisted.count==0);WidgetViews.cache(cached,43).delete()
    val factory=WidgetCollectionFactory(context,android.content.Intent().putExtra("size",2)){many}
    factory.onDataSetChanged();check(factory.count==220&&factory.getViewAt(219)!=null&&factory.getViewAt(220)==null)
    val first= factory.getItemId(0);factory.onDataSetChanged();check(factory.getItemId(0)==first);factory.onDestroy();check(factory.count==0)
    for(kind in WidgetChoice.kinds.keys)for(size in 0..2){val choice=WidgetChoice(kind,when(kind){"quote"->"quote";"note","checklist"->"check";"collection"->"Personal";else->""});WidgetViews.render(context,-1,choice,widgetContent(all,choice),size).apply(context,null)}
    val line=WidgetViews.items(widgetContent(all,WidgetChoice("checklist","check"))).first()
    check(WidgetViews.fillIn(line).getStringExtra("mode")=="note")
    check(WidgetViews.fillIn(line,true).getStringExtra("revision")==checks.id)
    return "PASS: scrollable widgets retain all 220 recent notes in every size (maximum payload $maxPayload bytes); legacy factory reaches the final note with stable IDs; hidden states are excluded, stale completion is rejected and recurrence is preserved\n"
}
