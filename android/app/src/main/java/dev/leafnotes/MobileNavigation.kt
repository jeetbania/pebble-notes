package dev.leafnotes

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

// A single progress value keeps the rounded reveal reversible, including edge-back.
fun morphBounds(origin:Rect,destination:Rect,progress:Float):Rect {
    val p=progress.coerceIn(0f,1f)
    fun mix(a:Float,b:Float)=a+(b-a)*p
    return Rect(mix(origin.left,destination.left),mix(origin.top,destination.top),mix(origin.right,destination.right),mix(origin.bottom,destination.bottom))
}
val LocalDockBackdrop=staticCompositionLocalOf<MutableState<Backdrop?>?>{null}
@Composable fun PublishDockBackdrop(){val source=LocalBackdrop.current;val sink=LocalDockBackdrop.current;SideEffect{sink?.value=source}}
val LocalNoteReveal=staticCompositionLocalOf{1f}

@Composable fun MobileDock(page:String,modifier:Modifier=Modifier,onPage:(String)->Unit,onSearch:()->Unit,onCompose:()->Unit) {
    val c=LocalLeafColors.current;val calm=LocalCalmMotion.current
    val pages=listOf("Notes","Tasks","Settings");val icons=listOf("grid","calendar","settings")
    val selected=pages.indexOf(page).coerceAtLeast(0)
    val position by animateFloatAsState(selected.toFloat(),if(calm)snap()else spring(dampingRatio=.82f,stiffness=520f),label="navigation selection")
    Row(modifier.navigationBarsPadding().padding(horizontal=22.dp,vertical=18.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
        ChromePill(Modifier.weight(1f).height(58.dp).pointerInput(selected){var drag=0f;detectHorizontalDragGestures(onDragStart={drag=0f},onHorizontalDrag={change,amount->change.consume();drag+=amount},onDragEnd={if(kotlin.math.abs(drag)>32.dp.toPx())onPage(pages[(selected+if(drag<0)1 else -1).coerceIn(0,2)])})}) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val slot=maxWidth/3
                Box(Modifier.offset(x=slot*position).width(slot).fillMaxHeight().padding(vertical=5.dp).background(c.text.copy(alpha=.10f),CircleShape))
                Row(Modifier.fillMaxSize()){pages.forEachIndexed{i,name->
                    Pressable(Modifier.weight(1f).fillMaxHeight(),name,onClick={onPage(name)}) {
                        Glyph(icons[i],size=24,tint=if(i==selected)c.accent else c.secondary)
                    }
                }}
            }
        }
        ChromeButton("search","Search notes",52,onClick=onSearch)
        ChromeButton("compose","New note",52,onClick=onCompose)
    }
}
