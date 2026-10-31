package dev.leafnotes

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView

class WidgetSetupActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val id=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(Activity.RESULT_CANCELED)
        if(AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider?.packageName!=packageName){finish();return}
        val store=Libraries.get(this)
        setContent {PebbleTheme(store){
            val c=LocalPebbleColors.current
            var choice by remember{mutableStateOf(WidgetChoice.read(this,id))}
            var selecting by remember{mutableStateOf(false)}
            val records=store.visibleHeads.filter{!it.note.deleted&&!it.note.archived}.sortedByDescending{it.createdAt}
            val needsSource=choice.kind in listOf("note","checklist","quote","collection")
            val sourceName=if(choice.kind in listOf("collection","tasks"))choice.source else records.firstOrNull{it.noteId==choice.source}?.note?.displayTitle.orEmpty()
            val valid= !needsSource || sourceName.isNotBlank()
            Column(Modifier.fillMaxSize().background(c.page).statusBarsPadding().navigationBarsPadding().padding(20.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Label("Your Pebble widget",26,FontWeight.SemiBold,modifier=Modifier.weight(1f));ChromeButton("close","Cancel widget setup",44){finish()}}
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    Label("Keep something useful close.",15,color=c.secondary)
                    AndroidView(factory={android.widget.FrameLayout(it)},update={host->host.removeAllViews();host.addView(WidgetViews.render(this@WidgetSetupActivity,-1,choice,widgetContent(records,choice),1).apply(this@WidgetSetupActivity,host))},modifier=Modifier.fillMaxWidth().height(184.dp))
                    Label("Show",14,FontWeight.SemiBold)
                    WidgetChoice.kinds.forEach{(kind,name)->Pressable(Modifier.fillMaxWidth().heightIn(min=48.dp).background(if(choice.kind==kind)c.fill else androidx.compose.ui.graphics.Color.Transparent,CircleShape).padding(horizontal=14.dp),"$name${if(choice.kind==kind)", selected" else ""}",onClick={choice=choice.copy(kind=kind,source="")}){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Label(name,16,modifier=Modifier.weight(1f));if(choice.kind==kind)Glyph("done",tint=c.text)}}}
                    if(choice.kind in listOf("note","checklist","quote","collection","tasks"))GhostAction(if(sourceName.isBlank())if(choice.kind=="tasks")"All lists" else "Choose ${if(choice.kind=="collection")"a collection" else "a note"}" else sourceName,"folder"){selecting=true}
                    Label("Surface",14,FontWeight.SemiBold)
                    IosSegments(listOf("Glass","Paper","Midnight"),listOf("glass","paper","midnight").indexOf(choice.theme).coerceAtLeast(0)){choice=choice.copy(theme=listOf("glass","paper","midnight")[it])}
                    Label("The selected content will be visible on your home screen. Tap the widget heading to change these choices.",13,color=c.secondary)
                    Spacer(Modifier.height(8.dp))
                }
                Pressable(Modifier.fillMaxWidth().height(52.dp).background(c.accent,CircleShape),"Save widget",enabled=valid,onClick={choice.save(this@WidgetSetupActivity,id);WidgetViews.update(this@WidgetSetupActivity,id);setResult(Activity.RESULT_OK,Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id));finish()}){Label("Save widget",17,FontWeight.SemiBold,color=accentInk(c.accent))}
            }
            if(selecting)IosSheet(if(choice.kind=="collection")"Choose a collection" else if(choice.kind=="tasks")"Choose a list" else "Choose a note",onDismiss={selecting=false}) {
                if(choice.kind=="collection")store.collections.forEach{folder->SheetRow(folder,"folder"){choice=choice.copy(source=folder);selecting=false}}
                else if(choice.kind=="tasks") {SheetRow("All lists","check"){choice=choice.copy(source="");selecting=false};records.mapNotNull{it.note.task?.list}.distinct().sorted().forEach{list->SheetRow(list,"check"){choice=choice.copy(source=list);selecting=false}}}
                else {
                    val eligible=records.filter{it.note.task==null&&(choice.kind!="checklist"||it.note.document.any{b->b.kind=="check"})&&(choice.kind!="quote"||it.note.document.any{b->b.kind=="quote"})}
                    if(eligible.isEmpty())Label(if(choice.kind=="quote")"Add a Quote block to a note first." else if(choice.kind=="checklist")"Create a note with checklist items first." else "Create a note first.",16,color=c.secondary)
                    eligible.forEach{r->SheetRow(r.note.displayTitle,if(choice.kind=="checklist")"check" else "notes"){choice=choice.copy(source=r.noteId);selecting=false}}
                }
            }
        }}
    }
}
