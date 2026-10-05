package dev.leafnotes

import android.content.SharedPreferences
import android.widget.EditText
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class TextMetrics(val body:Int=18,val headline:Int=21,val subtitle:Int=24,val title:Int=30,val scale:Double=1.0) {
    fun size(kind:String)=((when(kind){"title"->title;"subtitle"->subtitle;"headline"->headline;else->body})*scale).toInt().coerceIn(10,72)
    companion object { fun read(p:SharedPreferences)=TextMetrics(p.getInt("typeBody",18).coerceIn(14,26),p.getInt("typeHeadline",21).coerceIn(17,34),p.getInt("typeSubtitle",24).coerceIn(18,34),p.getInt("typeTitle",30).coerceIn(24,44)) }
}
val LocalTypeSizes=staticCompositionLocalOf { TextMetrics() }
val LocalCalmMotion=staticCompositionLocalOf { false }
val editorMetrics=java.util.WeakHashMap<EditText,TextMetrics>()
class SemanticSize(val kind:String,size:Int):android.text.style.AbsoluteSizeSpan(size,true) {
    private fun weight(paint:android.text.TextPaint){if(kind in listOf("title","subtitle","headline"))paint.typeface=android.graphics.Typeface.create(paint.typeface,(paint.typeface?.style ?: 0) or android.graphics.Typeface.BOLD)}
    override fun updateDrawState(paint:android.text.TextPaint){super.updateDrawState(paint);weight(paint)}
    override fun updateMeasureState(paint:android.text.TextPaint){super.updateMeasureState(paint);weight(paint)}
}

@Composable fun TypographySettings(store:Store,onDismiss:()->Unit) {
    val haptic=androidx.compose.ui.platform.LocalHapticFeedback.current
    IosSheet("Writing & text sizes",onDismiss) {
        val metrics=LocalTypeSizes.current
        listOf(Triple("Body","typeBody",14..26),Triple("Heading","typeHeadline",17..34),Triple("Subtitle","typeSubtitle",18..34),Triple("Title","typeTitle",24..44)).forEach{(name,key,range)->
            val value=store.preferences.getInt(key,when(key){"typeBody"->18;"typeHeadline"->21;"typeSubtitle"->24;else->30})
            Row(Modifier.fillMaxWidth().padding(top=18.dp)){Label(name,16,modifier=Modifier.weight(1f));Label("$value sp",14,color=LocalLeafColors.current.secondary)}
            PillSlider(value.toFloat(),{if(it.toInt()!=value){haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove);store.preferences.edit().putInt(key,it.toInt()).apply()}},valueRange=range.first.toFloat()..range.last.toFloat(),steps=range.last-range.first-1,description=name+" text size")
        }
        Label("A quieter place for your thoughts.",metrics.body,modifier=Modifier.padding(vertical=12.dp))
        GhostAction("Reset text sizes","undo"){store.preferences.edit().remove("typeBody").remove("typeHeadline").remove("typeSubtitle").remove("typeTitle").apply()}
    }
}
@Composable fun NoteSizeControl(store:Store) {
    val haptic=androidx.compose.ui.platform.LocalHapticFeedback.current
    val note=store.editing ?: return
    Row(Modifier.fillMaxWidth().padding(top=14.dp)){Label("Note text size",16,modifier=Modifier.weight(1f));Label("${(note.textScale*100).toInt()}%",14,color=LocalLeafColors.current.secondary)}
    PillSlider(note.textScale.toFloat(),{store.editing?.let{n->if(kotlin.math.abs(n.textScale-it)>.02)haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove);store.update(n.copy(textScale=it.toDouble().coerceIn(.8,1.6)),"textSize")}},valueRange=.8f..1.6f,steps=7,description="Note text size")
    SheetRow("Use default size","undo"){store.editing?.let{store.update(it.copy(textScale=1.0))}}
}
