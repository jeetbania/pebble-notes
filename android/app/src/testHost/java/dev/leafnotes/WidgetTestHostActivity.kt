package dev.leafnotes

import android.appwidget.*
import android.content.*
import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File

/** Opt-in instrumentation host, excluded from normal APKs. No personal library is used. */
class WidgetTestHostActivity:ComponentActivity() {
    lateinit var fixture:Store
    var widgetId=-1
    private var host:AppWidgetHost?=null
    private val hostIdentity=(100000+System.nanoTime()%1000000000).toInt()
    private var previous:Store?=null
    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        val root=File(cacheDir,"widget-ui-${uid()}").apply{mkdirs()}
        fixture=Store(object:ContextWrapper(this){override fun getFilesDir()=root;override fun getSharedPreferences(name:String,mode:Int)=super.getSharedPreferences("fixture-${root.name}-$name",mode)})
        File(fixture.root,"starter-20261004.complete").writeText("Isolated widget test library")
        fixture.preferences.edit().putBoolean("onboardingComplete",true).putString("appearance",intent.getStringExtra("appearance")?:"light").putString("profileName","Jeet").apply()
        val field=Libraries::class.java.getDeclaredField("instance").apply{isAccessible=true};previous=field.get(null) as? Store;field.set(null,fixture)
        if(intent.getStringExtra("mode")=="widget") {
            val count=intent.getIntExtra("count",24)
            for(n in 1..count){fixture.create();fixture.update(Note(title=if(n==count)"Things to do in San Francisco" else if(n==count-1)"The best places to visit in Paris" else "Note $n · a thought worth keeping",blocks=listOf(Block(text="One idea, written down")),pinned=n%2==0));fixture.flush();fixture.select(null)}
            fixture.create();fixture.update(Note(title="Little things",blocks=listOf(Block(kind="check",text="Pick up a sketchbook"),Block(kind="check",text="Take a walk",checked=true),Block(kind="quote",text="Pay attention. Be astonished. Tell about it."))));fixture.flush();val checkId=fixture.selected!!;fixture.select(null)
            fixture.create();fixture.update(Note(title="Send the first draft",task=TaskDetails()));fixture.flush();fixture.select(null)
            val size=intent.getIntExtra("size",1);val manager=AppWidgetManager.getInstance(this)
            host=AppWidgetHost(this,hostIdentity).also{it.startListening()};widgetId=host!!.allocateAppWidgetId()
            val provider=ComponentName(this,when(size){0->PebbleSmallWidget::class.java;2->PebbleLargeWidget::class.java;else->PebbleMediumWidget::class.java})
            check(manager.bindAppWidgetIdIfAllowed(widgetId,provider)){"Grant the emulator test host widget binding before running this check"}
            val choice=WidgetChoice(intent.getStringExtra("kind")?:"recent",if(intent.getStringExtra("kind") in listOf("checklist","quote","note"))checkId else if(intent.getStringExtra("kind")=="collection")"Personal" else "")
            choice.save(this,widgetId)
            val info=manager.getAppWidgetInfo(widgetId);val view=host!!.createView(this,widgetId,info)
            val width=if(size==0)158 else 338;val height=if(size==2)354 else 158
            manager.updateAppWidgetOptions(widgetId,Bundle().apply{putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,width);putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,width);putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,height);putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,height)})
            val parent=FrameLayout(this).apply{setBackgroundColor(0xFFE1DDD7.toInt());setPadding(30,120,30,30)}
            parent.addView(view,FrameLayout.LayoutParams((width*resources.displayMetrics.density).toInt(),(height*resources.displayMetrics.density).toInt()))
            setContentView(parent);WidgetViews.update(this,widgetId)
        } else setContent {val density=androidx.compose.ui.platform.LocalDensity.current;androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density,intent.getFloatExtra("font",1f))){PebbleTheme(fixture){Box((if(intent.getBooleanExtra("narrow",false))Modifier.width(320.dp).fillMaxHeight() else Modifier.fillMaxSize()).background(LocalPebbleColors.current.page).navigationBarsPadding().padding(bottom=84.dp)){MobileSettings(fixture,{}, {finish()}, {}, {}, {}, {}, {})}}}}
    }
    override fun onDestroy(){host?.let{if(widgetId>=0){it.deleteAppWidgetId(widgetId);PebbleWidget().onDeleted(this,intArrayOf(widgetId))};it.stopListening();it.deleteHost()};super.onDestroy()}
}
