package dev.leafnotes

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.view.PixelCopy
import android.widget.ListView
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

fun Instrumentation.captureWindow(activity:Activity,name:String):File {
    val b=Bitmap.createBitmap(activity.window.decorView.width,activity.window.decorView.height,Bitmap.Config.ARGB_8888)
    val latch=CountDownLatch(1);var result=-1
    runOnMainSync{PixelCopy.request(activity.window,b,{result=it;latch.countDown()},android.os.Handler(android.os.Looper.getMainLooper()))}
    check(latch.await(5,TimeUnit.SECONDS)&&result==PixelCopy.SUCCESS){"Window capture failed $result"}
    val file=File(targetContext.cacheDir,"widget-ui-results/$name.png").apply{parentFile!!.mkdirs()};file.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle();return file
}
@Suppress("UNCHECKED_CAST")
fun <T:android.view.View> descendant(root:android.view.View,id:Int):T? {if(root.id==id)return root as T;if(root is android.view.ViewGroup)for(i in 0 until root.childCount)descendant<T>(root.getChildAt(i),id)?.let{return it};return null}
fun Instrumentation.hostIntent()=Intent().setClassName(targetContext.packageName,"dev.leafnotes.WidgetTestHostActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
fun <T> Instrumentation.mainValue(block:()->T):T {var result:T?=null;runOnMainSync{result=block()};@Suppress("UNCHECKED_CAST") return result as T}
fun Instrumentation.awaitUi(message:String,condition:()->Boolean){val end=System.currentTimeMillis()+12000;while(System.currentTimeMillis()<end){waitForIdleSync();if(condition())return;Thread.sleep(100)};error(message)}
fun findNode(root:AccessibilityNodeInfo?,match:(AccessibilityNodeInfo)->Boolean):AccessibilityNodeInfo?{if(root==null)return null;if(match(root))return root;for(i in 0 until root.childCount)findNode(root.getChild(i),match)?.let{return it};return null}
fun semantics(root:android.view.View):androidx.compose.ui.semantics.SemanticsNode? {
    root.javaClass.methods.firstOrNull{it.name=="getSemanticsOwner"&&it.parameterCount==0}?.let{return (it.invoke(root) as androidx.compose.ui.semantics.SemanticsOwner).unmergedRootSemanticsNode}
    if(root is android.view.ViewGroup)for(i in 0 until root.childCount)semantics(root.getChildAt(i))?.let{return it};return null
}
fun semanticNode(root:androidx.compose.ui.semantics.SemanticsNode?,match:(androidx.compose.ui.semantics.SemanticsNode)->Boolean):androidx.compose.ui.semantics.SemanticsNode? {if(root==null)return null;if(match(root))return root;for(child in root.children)semanticNode(child,match)?.let{return it};return null}
fun Instrumentation.clickLabel(activity:Activity,label:String){
    var node:androidx.compose.ui.semantics.SemanticsNode?=null
    awaitUi("Semantic control missing: $label"){node=mainValue{semanticNode(semantics(activity.window.decorView)){n->n.config.contains(androidx.compose.ui.semantics.SemanticsActions.OnClick)&&n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription){null}?.any{it.startsWith(label)}==true}};node!=null}
    check(mainValue{node!!.config[androidx.compose.ui.semantics.SemanticsActions.OnClick].action!!.invoke()});waitForIdleSync();Thread.sleep(150)
}

fun Instrumentation.runUiChecks(mode:String) {
    val report=StringBuilder()
    uiAutomation.serviceInfo=uiAutomation.serviceInfo.apply{flags=flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS}
    try {
        if(mode=="widgets") {
            mainValue{android.appwidget.AppWidgetHost(targetContext,6823).deleteHost()}
            for(size in 0..2) {
                val activity=startActivitySync(hostIntent().putExtra("mode","widget").putExtra("size",size))
                var list:ListView?=null
                try{awaitUi("Widget list did not load for size $size"){list=mainValue{descendant<ListView>(activity.window.decorView,R.id.widget_list)};mainValue{list?.adapter?.count==25&&list!!.childCount>0}}}catch(e:Exception){captureWindow(activity,"widget-error");throw IllegalStateException("${e.message}; adapter=${mainValue{list?.adapter?.count}}, children=${mainValue{list?.childCount}}, notes=${(activity.javaClass.getMethod("getFixture").invoke(activity) as Store).visibleHeads.size}",e)}
                captureWindow(activity,"notes-$size")
                runOnMainSync{list!!.setSelection(18)};awaitUi("Widget does not scroll in size $size"){mainValue{list!!.firstVisiblePosition>=18}}
                if(size==1) {
                    val title=mainValue{list!!.getChildAt(0).findViewById<android.widget.TextView>(R.id.widget_row_title).text.toString()}
                    val store=activity.javaClass.getMethod("getFixture").invoke(activity) as Store
                    val expected=mainValue{store.visibleHeads.first{it.note.displayTitle==title}.noteId}
                    val monitor=addMonitor("dev.leafnotes.MainActivity",null,false)
                    runOnMainSync{list!!.performItemClick(list!!.getChildAt(0),list!!.firstVisiblePosition,list!!.adapter.getItemId(list!!.firstVisiblePosition))}
                    val opened=waitForMonitorWithTimeout(monitor,6000);check(opened!=null){"Scrolled widget row did not open the app"}
                    awaitUi("Widget opened the wrong note"){mainValue{store.selected==expected}}
                    runOnMainSync{opened.finish()};removeMonitor(monitor)
                }
                if(size==2){
                    val monitor=addMonitor("dev.leafnotes.WidgetSetupActivity",null,false)
                    runOnMainSync{descendant<android.view.View>(activity.window.decorView,R.id.widget_header)!!.performClick()}
                    val setup=waitForMonitorWithTimeout(monitor,5000);check(setup!=null){"Widget header did not open settings"}
                    awaitUi("Configuration preview did not show notes"){mainValue{descendant<ListView>(setup.window.decorView,R.id.widget_list)?.adapter?.count==25}}
                    runOnMainSync{setup.finish()};removeMonitor(monitor)
                }
                runOnMainSync{activity.finish()};waitForIdleSync()
                report.append("PASS: size $size loads all 25 notes and scrolls past note 18\n")
            }
            val large=startActivitySync(hostIntent().putExtra("mode","widget").putExtra("size",2).putExtra("count",220))
            var list:ListView?=null
            awaitUi("Large widget list did not load"){list=mainValue{descendant<ListView>(large.window.decorView,R.id.widget_list)};mainValue{list?.adapter?.count==221}}
            runOnMainSync{list!!.setSelection(220)};awaitUi("Last note is unreachable"){mainValue{list!!.lastVisiblePosition==220}}
            captureWindow(large,"notes-last")
            runOnMainSync{large.finish()};waitForIdleSync();report.append("PASS: widget collection scrolls to the final item in a 221-note library\n")
            val checks=startActivitySync(hostIntent().putExtra("mode","widget").putExtra("kind","checklist").putExtra("size",1))
            awaitUi("Checklist did not load"){list=mainValue{descendant<ListView>(checks.window.decorView,R.id.widget_list)};mainValue{list?.childCount==2}}
            val store=checks.javaClass.getMethod("getFixture").invoke(checks) as Store
            val note=mainValue{store.visibleHeads.first{it.note.title=="Little things"}}
            runOnMainSync{list!!.getChildAt(0).findViewById<android.view.View>(R.id.widget_row_check).performClick()}
            awaitUi("Widget completion did not persist"){mainValue{store.visibleHeads.first{it.noteId==note.noteId}.note.document.first().checked}}
            check(mainValue{store.selected==null}){"Completing a widget item opened the editor"}
            captureWindow(checks,"checklist-complete")
            runOnMainSync{checks.finish()};waitForIdleSync();report.append("PASS: native check-circle tap persists completion without opening the editor\n")
            for(kind in listOf("pinned","collection","note","quote","tasks","capture")) {
                val a=startActivitySync(hostIntent().putExtra("mode","widget").putExtra("kind",kind).putExtra("size",2))
                val expected=when(kind){"pinned"->12;"collection"->25;"capture"->3;else->1}
                awaitUi("$kind widget failed to load"){list=mainValue{descendant<ListView>(a.window.decorView,R.id.widget_list)};mainValue{list?.adapter?.count==expected&&list!!.childCount>0}}
                if(kind=="tasks") {
                    val tasks=a.javaClass.getMethod("getFixture").invoke(a) as Store
                    runOnMainSync{list!!.getChildAt(0).findViewById<android.view.View>(R.id.widget_row_check).performClick()}
                    awaitUi("Task completion failed"){mainValue{tasks.visibleHeads.first{it.note.task!=null}.note.task!!.completed}}
                    try {awaitUi("Completed task remained visible in pending widget"){mainValue{val empty=descendant<android.widget.TextView>(a.window.decorView,R.id.widget_empty);empty?.isShown==true&&empty.text.toString()=="No pending tasks"}}}catch(e:Exception){captureWindow(a,"task-error");throw IllegalStateException("${e.message}; error=${tasks.error}; destroyed=${a.isDestroyed}; empty=${mainValue{descendant<android.widget.TextView>(a.window.decorView,R.id.widget_empty)?.text}}; visible=${mainValue{descendant<android.widget.TextView>(a.window.decorView,R.id.widget_empty)?.visibility}}",e)}
                    captureWindow(a,"tasks-complete")
                }
                if(kind=="capture") {
                    val store=a.javaClass.getMethod("getFixture").invoke(a) as Store
                    val monitor=addMonitor("dev.leafnotes.MainActivity",null,false)
                    runOnMainSync{list!!.performItemClick(list!!.getChildAt(1),1,list!!.adapter.getItemId(1))}
                    val opened=waitForMonitorWithTimeout(monitor,6000);check(opened!=null)
                    awaitUi("Quick capture did not create a checklist"){mainValue{store.selected!=null&&store.editing?.document?.firstOrNull()?.kind=="check"}}
                    runOnMainSync{opened.finish()};removeMonitor(monitor)
                }
                runOnMainSync{a.finish()};waitForIdleSync();report.append("PASS: $kind widget renders its real content and expected row count\n")
            }

        } else {
            val activity=startActivitySync(hostIntent().putExtra("appearance",if(mode=="dark")"dark" else "light").putExtra("font",if(mode=="large-type")2f else 1f).putExtra("narrow",mode=="large-type"))
            waitForIdleSync();Thread.sleep(700);val file=captureWindow(activity,"settings-$mode")
            if(mode=="light") {
                clickLabel(activity,"Appearance");awaitUi("Appearance page did not open"){mainValue{semanticNode(semantics(activity.window.decorView)){it.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text){null}?.any{t->t.text=="Theme"}==true}!=null}}
                clickLabel(activity,"Back to Settings");clickLabel(activity,"Clipboard suggestions");clickLabel(activity,"Off")
                val store=activity.javaClass.getMethod("getFixture").invoke(activity) as Store
                awaitUi("Clipboard preference did not save"){!store.preferences.getBoolean("clipboardSuggestions",true)}
                clickLabel(activity,"Back to Settings")
                report.append("PASS: accessible Settings rows navigate and the clipboard control saves its real preference\n")
            }
            if(mode=="large-type"){clickLabel(activity,"Appearance");captureWindow(activity,"settings-large-appearance")}
            runOnMainSync{activity.finish()};waitForIdleSync();report.append("PASS: actual Settings window rendered at ${file.path}\n")
        }
        finish(0,android.os.Bundle().apply{putString("results",report.toString())})
    }catch(e:Throwable){finish(1,android.os.Bundle().apply{putString("results",report.toString()+e.stackTraceToString())})}
}
