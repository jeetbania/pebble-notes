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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import kotlin.math.roundToInt

// A single progress value keeps the rounded reveal reversible, including edge-back.
fun morphBounds(origin:Rect,destination:Rect,progress:Float):Rect {
    val p=progress.coerceIn(0f,1f)
    fun mix(a:Float,b:Float)=a+(b-a)*p
    return Rect(mix(origin.left,destination.left),mix(origin.top,destination.top),mix(origin.right,destination.right),mix(origin.bottom,destination.bottom))
}
val LocalDockBackdrop=staticCompositionLocalOf<MutableState<Backdrop?>?>{null}
@Composable fun PublishDockBackdrop(){val source=LocalBackdrop.current;val sink=LocalDockBackdrop.current;SideEffect{sink?.value=source}}
val LocalNoteReveal=staticCompositionLocalOf<()->Float>{{1f}}

@Composable fun MobileDock(page:String,modifier:Modifier=Modifier,onPage:(String)->Unit,onSearch:()->Unit,onCompose:()->Unit) {
    val c=LocalLeafColors.current;val calm=LocalCalmMotion.current
    val pages=listOf("Notes","Tasks","Settings","Search");val icons=listOf("home","check","settings","search")
    val selected=pages.indexOf(page).coerceAtLeast(0)
    val position=remember{Animatable(selected.toFloat())};val scope=rememberCoroutineScope()
    var dragging by remember{mutableStateOf(false)}
    val stretch by animateFloatAsState(if(dragging && !calm)1.045f else 1f,spring(dampingRatio=.75f,stiffness=450f),label="pill elasticity")
    val currentSelected by rememberUpdatedState(selected)
    val navigate by rememberUpdatedState<(Int)->Unit>({i->if(pages[i]=="Search")onSearch()else onPage(pages[i])})
    LaunchedEffect(selected,calm){if(!dragging){if(calm)position.snapTo(selected.toFloat())else position.animateTo(selected.toFloat(),spring(dampingRatio=.74f,stiffness=420f))}}
    Row(modifier.navigationBarsPadding().padding(horizontal=22.dp,vertical=18.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
        ChromePill(Modifier.weight(1f).height(58.dp)) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val slot=maxWidth/4
                val slotPixels=with(LocalDensity.current){slot.toPx()}
                Box(Modifier.offset{androidx.compose.ui.unit.IntOffset((slotPixels*position.value).roundToInt(),0)}.width(slot).fillMaxHeight().graphicsLayer{scaleX=stretch;scaleY=2f-stretch}.padding(vertical=5.dp).background(c.text.copy(alpha=.10f),CircleShape))
                Row(Modifier.fillMaxSize().pointerInput(slotPixels,calm){val velocity=VelocityTracker();var travel=0f;detectHorizontalDragGestures(
                    onDragStart={_->dragging=true;travel=currentSelected.toFloat();velocity.resetTracking();scope.launch(start=CoroutineStart.UNDISPATCHED){position.stop();position.snapTo(currentSelected.toFloat())}},
                    onHorizontalDrag={change,amount->change.consume();velocity.addPosition(change.uptimeMillis,change.position);scope.launch(start=CoroutineStart.UNDISPATCHED){travel+=amount/slotPixels*.82f;position.snapTo(dockDragPosition(travel))}},
                    onDragCancel={scope.launch{if(calm)position.snapTo(currentSelected.toFloat())else position.animateTo(currentSelected.toFloat(),spring(dampingRatio=1f,stiffness=550f));dragging=false}},
                    onDragEnd={val speed=velocity.calculateVelocity().x/slotPixels;val target=(position.value+(speed*.06f).coerceIn(-.25f,.25f)).roundToInt().coerceIn(0,3);navigate(target);scope.launch{if(calm)position.snapTo(target.toFloat())else position.animateTo(target.toFloat(),spring(dampingRatio=.74f,stiffness=420f),initialVelocity=speed);dragging=false}}
                )}){pages.forEachIndexed{i,name->
                    Pressable(Modifier.weight(1f).fillMaxHeight(),name,onClick={if(name=="Search")onSearch()else onPage(name)}) {
                        Glyph(icons[i],size=24,tint=if(i==selected)c.accent else c.secondary)
                    }
                }}
            }
        }
        ChromeButton("compose","New note",52,onClick=onCompose)
    }
}

// Retain a little finger resistance, with diminishing travel beyond either end.
fun dockDragPosition(travel:Float):Float=when {travel<0f->-.22f*(1f-1f/(1f-travel));travel>3f->3f+.22f*(1f-1f/(1f+travel-3f));else->travel}
