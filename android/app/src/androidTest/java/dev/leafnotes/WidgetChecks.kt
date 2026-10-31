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
    val previews=File(root,"widget-previews").apply{mkdirs()}
    for(font in listOf(1f,2f))for(theme in listOf("glass","paper","midnight"))for(size in 0..2) {
        val config=Configuration(context.resources.configuration).apply{fontScale=font};val c=context.createConfigurationContext(config)
        val choice=WidgetChoice("checklist","check",theme);val content=widgetContent(all,choice)
        val density=c.resources.displayMetrics.density;val width=((if(size==0)140 else 250)*density).toInt();val height=((if(size==0)140 else if(size==1)184 else 280)*density).toInt()
        val view=WidgetViews.render(c,-1,choice,content,size).apply(c,null)
        view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));view.layout(0,0,width,height)
        val container=view.findViewById<LinearLayout>(R.id.widget_content)
        for(i in 0 until container.childCount){val row=container.getChildAt(i);check(row.bottom<=container.height){"Clipped widget rows: $theme size $size font $font"};if(row.id==R.id.widget_row)check(row.height>=44*density-1){"Compressed touch target: $theme size $size font $font"}}
        if(font==1f){val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap));File(previews,"$theme-$size.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
    }
    // Every variation must inflate, including empty and deleted-source states.
    for(kind in WidgetChoice.kinds.keys)for(size in 0..2) {
        val choice=WidgetChoice(kind,when(kind){"quote"->"quote";"note","checklist"->"check";"collection"->"Personal";else->""})
        val view=WidgetViews.render(context,-1,choice,widgetContent(all,choice),size).apply(context,null)
        val density=context.resources.displayMetrics.density;val width=((if(size==0)140 else 320)*density).toInt();val height=((if(size==0)140 else if(size==1)184 else 280)*density).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));view.layout(0,0,width,height)
        val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap));File(previews,"$kind-$size.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        WidgetViews.render(context,-1,choice,widgetContent(emptyList(),choice),size).apply(context,null)
    }
    return "PASS: widget content excludes private hidden states; completion is durable and stale taps are idempotent; all eight variations inflate in three sizes; light/dark surfaces and 200% type fit; previews at ${previews.path}\n"
}
