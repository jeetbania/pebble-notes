package dev.leafnotes

import android.widget.NumberPicker
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.util.Calendar

@Composable fun RollingTimePicker(time:Long,onChange:(Long)->Unit) {
    val c=LocalLeafColors.current
    val calendar=Calendar.getInstance().apply{timeInMillis=time}
    fun change(hour:Int=calendar.get(Calendar.HOUR_OF_DAY),minute:Int=calendar.get(Calendar.MINUTE)) {
        onChange(Calendar.getInstance().apply{timeInMillis=time;set(Calendar.HOUR_OF_DAY,hour);set(Calendar.MINUTE,minute);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)}.timeInMillis)
    }
    @Composable fun Wheel(values:List<String>,selected:Int,label:String,modifier:Modifier,onSelect:(Int)->Unit) {
        AndroidView(factory={context->NumberPicker(context).apply{
            minValue=0;maxValue=values.lastIndex;displayedValues=values.toTypedArray();wrapSelectorWheel=values.size>2
            descendantFocusability=NumberPicker.FOCUS_BLOCK_DESCENDANTS
            isVerticalFadingEdgeEnabled=true;setFadingEdgeLength((48*resources.displayMetrics.density).toInt())
            selectionDividerHeight=1
        }},update={view->view.value=selected;view.setTextColor(c.text.toArgb());view.contentDescription=label+": "+values[selected];view.setOnValueChangedListener{_,_,value->onSelect(value)}},modifier=modifier.height(160.dp))
    }
    Row(Modifier.fillMaxWidth().background(c.fill,RoundedCornerShape(14.dp)).padding(horizontal=14.dp)) {
        Wheel((1..12).map{it.toString()},(calendar.get(Calendar.HOUR_OF_DAY)+11)%12,"Hour",Modifier.weight(1f)){change((it+1)%12+calendar.get(Calendar.AM_PM)*12)}
        Wheel((0..59).map{it.toString().padStart(2,'0')},calendar.get(Calendar.MINUTE),"Minute",Modifier.weight(1f)){change(minute=it)}
        Wheel(listOf("AM","PM"),calendar.get(Calendar.AM_PM),"Period",Modifier.weight(1f)){change(calendar.get(Calendar.HOUR_OF_DAY)%12+it*12)}
    }
}
