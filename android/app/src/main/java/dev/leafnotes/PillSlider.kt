package dev.leafnotes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

// Retain the platform's drag, keyboard and accessibility behavior, with Pebble geometry.
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PillSlider(value:Float,onValueChange:(Float)->Unit,valueRange:ClosedFloatingPointRange<Float>,modifier:Modifier=Modifier,steps:Int=0,description:String="") {
    val c=LocalLeafColors.current
    Slider(value=value,onValueChange=onValueChange,valueRange=valueRange,steps=steps,
        modifier=modifier.heightIn(min=48.dp).semantics {if(description.isNotEmpty())contentDescription=description},
        thumb={Box(Modifier.size(28.dp).shadow(3.dp,CircleShape).background(Color.White,CircleShape).border(.5.dp,Color.Black.copy(alpha=.08f),CircleShape))},
        track={state->Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            val fraction=((state.value-valueRange.start)/(valueRange.endInclusive-valueRange.start)).coerceIn(0f,1f)
            val radius=CornerRadius(size.height/2)
            drawRoundRect(c.text.copy(alpha=.12f),cornerRadius=radius)
            drawRoundRect(c.accent,size=Size(size.width*fraction,size.height),cornerRadius=radius)
        }})
}
