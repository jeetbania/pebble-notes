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

// Measurements remain fixed for the entire gesture. Only layers move; the document
// and native text fields are not rebuilt until the drop is committed.
class ReorderGroup {
    val frames=mutableMapOf<String,Rect>()
    var draggedId by mutableStateOf<String?>(null)
    var delta by mutableFloatStateOf(0f)
    var offsets by mutableStateOf<Map<String,Float>>(emptyMap())
    var snapshot:Map<String,Rect> = emptyMap()
    var families:Map<String,Set<String>> = emptyMap()
    var initialOrder:List<String> = emptyList()
    var pendingOrder:List<String> = emptyList()
    fun begin(id:String,order:List<String>,members:Map<String,Set<String>>) {
        snapshot=frames.toMap();families=members;initialOrder=order;pendingOrder=order
        delta=0f;offsets=emptyMap();draggedId=id
    }
    fun move(amount:Float):Boolean {
        val id=draggedId ?: return false;delta+=amount
        val rect=snapshot[id] ?: return false
        val target=reorderInsertionIndex(initialOrder,id,snapshot,rect.center.y+delta)
        val next=initialOrder.filter{it!=id}.toMutableList().apply{add(target.coerceIn(0,size),id)}
        val changed=next!=pendingOrder;pendingOrder=next
        val displaced=mutableMapOf<String,Float>()
        // Derive complete family extents, including expanded toggle children.
        val extents=initialOrder.associateWith{key->(families[key] ?: setOf(key)).mapNotNull{snapshot[it]}.let{rs->Rect(0f,rs.minOfOrNull{it.top} ?: 0f,0f,rs.maxOfOrNull{it.bottom} ?: 0f)}}
        var y=extents[initialOrder.firstOrNull()]?.top ?: 0f
        next.forEachIndexed{index,key->
            val original=extents[key]!!
            (families[key] ?: setOf(key)).forEach{displaced[it]=if(key==id)delta else y-original.top}
            val originalIndex=initialOrder.indexOf(key)
            val gap=initialOrder.getOrNull(originalIndex+1)?.let{extents[it]!!.top-original.bottom} ?: 0f
            y+=original.height+gap
        }
        offsets=displaced;return changed
    }
    fun finish():List<String> {val result=pendingOrder;draggedId=null;offsets=emptyMap();delta=0f;return result}
}
fun Modifier.reorderTarget(id:String,group:ReorderGroup)=composed {
    val calm=LocalCalmMotion.current
    val dragging=group.draggedId?.let{id in (group.families[it] ?: setOf(it))} ?: false
    val target=group.offsets[id] ?: 0f
    val offset by animateFloatAsState(target,if(calm || dragging || group.draggedId==null)tween(0)else spring(dampingRatio=1f,stiffness=480f),label="block gap")
    DisposableEffect(id){onDispose{group.frames.remove(id)}}
    this.onGloballyPositioned { if(group.draggedId==null)group.frames[id]=it.boundsInRoot() }
        .zIndex(if(dragging)1f else 0f).graphicsLayer {translationY=if(dragging)target else offset;alpha=if(dragging).92f else 1f}
}
@Composable fun ReorderGrip(id:String,group:ReorderGroup,order:List<String>,families:Map<String,Set<String>> = emptyMap(),onDragging:(Boolean)->Unit={},onOptions:(()->Unit)?=null,onOrder:(List<String>)->Unit) {
    val currentOrder by rememberUpdatedState(order);val members by rememberUpdatedState(families)
    val change by rememberUpdatedState(onOrder);val dragState by rememberUpdatedState(onDragging);val haptic=LocalHapticFeedback.current
    Box(Modifier.width(if(onOptions!=null)44.dp else 24.dp).height(if(onOptions!=null)44.dp else 30.dp).clickable(enabled=onOptions!=null,onClick={onOptions?.invoke()}).pointerInput(id,group){detectDragGestures(
        onDragStart={group.begin(id,currentOrder,members);dragState(true);haptic.performHapticFeedback(HapticFeedbackType.LongPress)},
        onDragEnd={val old=group.initialOrder;val next=group.finish();if(next!=old)change(next);dragState(false)},
        onDragCancel={group.finish();dragState(false)},
        onDrag={event,amount->event.consume();if(group.move(amount.y))haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)}
    )},contentAlignment=Alignment.Center){Label("⋮⋮",13,color=LocalPebbleColors.current.tertiary)}
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
