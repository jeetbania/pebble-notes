package dev.leafnotes

import androidx.compose.animation.core.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.zIndex
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp

class ReorderGroup {
    val frames=mutableStateMapOf<String,Rect>()
    var draggedId by mutableStateOf<String?>(null)
    var pointerY by mutableFloatStateOf(0f)
    var grabY=0f
}
fun Modifier.reorderTarget(id:String,group:ReorderGroup)=composed {
    val scope=rememberCoroutineScope();val displacement=remember{Animatable(0f)}
    val calm=LocalCalmMotion.current
    var animationJob by remember { mutableStateOf<Job?>(null) }
    var previousY by remember{mutableStateOf<Float?>(null)}
    var settledFrame by remember{mutableStateOf(Rect.Zero)}
    var wasDragged by remember{mutableStateOf(false)}
    val dragging=group.draggedId==id
    LaunchedEffect(dragging){
        if(!dragging && wasDragged){animationJob?.cancel();val release=group.pointerY-group.grabY-settledFrame.top;displacement.snapTo(release);if(calm)displacement.snapTo(0f)else displacement.animateTo(0f,spring(dampingRatio=1f,stiffness=550f))}
        wasDragged=dragging
    }
    DisposableEffect(id){onDispose{group.frames.remove(id)}}
    this.onPlaced { coordinates->
        val y=coordinates.positionInParent().y;val old=previousY;previousY=y
        settledFrame=coordinates.boundsInRoot();group.frames[id]=settledFrame
        if(old!=null && old!=y && !dragging){animationJob?.cancel();animationJob=scope.launch{displacement.snapTo(displacement.value+old-y);if(calm)displacement.snapTo(0f)else displacement.animateTo(0f,spring(dampingRatio=1f,stiffness=480f))}}
    }.zIndex(if(dragging)1f else 0f).graphicsLayer {
        translationY=if(dragging)group.pointerY-group.grabY-settledFrame.top else displacement.value
        alpha=if(dragging).88f else 1f
    }
}
@Composable fun ReorderGrip(id:String,group:ReorderGroup,order:List<String>,onDragging:(Boolean)->Unit={},onOptions:(()->Unit)?=null,onOrder:(List<String>)->Unit) {
    val currentOrder by rememberUpdatedState(order);val change by rememberUpdatedState(onOrder);val dragState by rememberUpdatedState(onDragging);val haptic=LocalHapticFeedback.current
    var bounds by remember{mutableStateOf(Rect.Zero)}
    Box(Modifier.width(if(onOptions!=null)44.dp else 24.dp).height(if(onOptions!=null)44.dp else 30.dp).clickable(enabled=onOptions!=null,onClick={onOptions?.invoke()}).onGloballyPositioned{bounds=it.boundsInRoot()}.pointerInput(id){var pointer=Offset.Zero;detectDragGestures(
        onDragStart={point->pointer=bounds.topLeft+point;group.pointerY=pointer.y;group.grabY=pointer.y-(group.frames[id]?.top ?: bounds.top);group.draggedId=id;dragState(true);haptic.performHapticFeedback(HapticFeedbackType.LongPress)},
        onDragEnd={group.draggedId=null;dragState(false)},onDragCancel={group.draggedId=null;dragState(false)},
        onDrag={event,amount->event.consume();pointer+=amount;group.pointerY=pointer.y
            val ids=currentOrder.toMutableList();val from=ids.indexOf(id)
            val dragged=group.frames[id]
            val center=pointer.y-group.grabY+(dragged?.height ?: 0f)/2f
            val to=reorderInsertionIndex(ids,id,group.frames,center);if(from>=0 && to>=0 && from!=to){ids.removeAt(from);ids.add(to,id);change(ids);haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)}
        }
    )},contentAlignment=Alignment.Center){Label("⋮⋮",13,color=LocalLeafColors.current.tertiary)}
}
@Composable fun CollectionRows(store:Store,onOpen:(String)->Unit) {
    val preferenceVersion=LocalPreferencesVersion.current;val group=remember{ReorderGroup()}
    val saved=store.preferences.getString("folderOrder","")!!.split("\n")
    val names=store.collections.sortedWith(compareBy<String>{saved.indexOf(it).takeIf{index->index>=0} ?: Int.MAX_VALUE}.thenBy{it})
    names.forEachIndexed{i,name->key(name){Row(Modifier.fillMaxWidth().reorderTarget(name,group),verticalAlignment=Alignment.CenterVertically){ReorderGrip(name,group,names){store.preferences.edit().putString("folderOrder",it.joinToString("\n")).apply()};Box(Modifier.weight(1f)){FolderRow("  ".repeat(name.count{it=='/'})+(store.folderHeads.firstOrNull{it.note.collection==name}?.note?.folderEmoji?.let{if(it.isEmpty())"" else "$it "} ?: "")+name.substringAfterLast('/'),"folder",store.visibleHeads.count{it.note.collection==name&&!it.note.deleted&&!it.note.archived},i>0){onOpen(name)}}}}}
}

// A drag can cross any number of peers in a single pointer event, including gaps.
fun reorderInsertionIndex(order:List<String>,id:String,frames:Map<String,Rect>,centerY:Float):Int {
    if(id !in order)return -1
    return order.filter{it!=id}.count{key->frames[key]?.let{centerY>it.center.y} ?: (order.indexOf(key)<order.indexOf(id))}
}
