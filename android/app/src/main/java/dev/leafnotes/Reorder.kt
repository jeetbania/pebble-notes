package dev.leafnotes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp

class ReorderGroup { val frames=mutableStateMapOf<String,Rect>() }
fun Modifier.reorderTarget(id:String,group:ReorderGroup)=onGloballyPositioned{group.frames[id]=it.boundsInRoot()}
@Composable fun ReorderGrip(id:String,group:ReorderGroup,order:List<String>,onDragging:(Boolean)->Unit={},onOptions:(()->Unit)?=null,onOrder:(List<String>)->Unit) {
    val currentOrder by rememberUpdatedState(order);val change by rememberUpdatedState(onOrder);val haptic=LocalHapticFeedback.current
    var bounds by remember{mutableStateOf(Rect.Zero)}
    Box(Modifier.width(if(onOptions!=null)44.dp else 24.dp).height(if(onOptions!=null)44.dp else 30.dp).clickable(enabled=onOptions!=null,onClick={onOptions?.invoke()}).onGloballyPositioned{bounds=it.boundsInRoot()}.pointerInput(id){var pointer=Offset.Zero;detectDragGesturesAfterLongPress(
        onDragStart={point->onDragging(true);pointer=bounds.topLeft+point;haptic.performHapticFeedback(HapticFeedbackType.LongPress)},
        onDragEnd={onDragging(false)},onDragCancel={onDragging(false)},
        onDrag={event,amount->event.consume();pointer+=amount;val target=group.frames.entries.firstOrNull{it.key!=id&&it.value.contains(pointer)}?.key;if(target!=null){val ids=currentOrder.toMutableList();val from=ids.indexOf(id);val to=ids.indexOf(target);if(from>=0&&to>=0){ids.removeAt(from);ids.add(to,id);change(ids);haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)}}}
    )},contentAlignment=Alignment.Center){Label("⋮⋮",13,color=LocalLeafColors.current.tertiary)}
}
@Composable fun CollectionRows(store:Store,onOpen:(String)->Unit) {
    val preferenceVersion=LocalPreferencesVersion.current;val group=remember{ReorderGroup()}
    val saved=store.preferences.getString("folderOrder","")!!.split("\n")
    val names=store.collections.sortedWith(compareBy<String>{saved.indexOf(it).takeIf{index->index>=0} ?: Int.MAX_VALUE}.thenBy{it})
    names.forEachIndexed{i,name->key(name){Row(Modifier.fillMaxWidth().reorderTarget(name,group),verticalAlignment=Alignment.CenterVertically){ReorderGrip(name,group,names){store.preferences.edit().putString("folderOrder",it.joinToString("\n")).apply()};Box(Modifier.weight(1f)){FolderRow("  ".repeat(name.count{it=='/'})+(store.folderHeads.firstOrNull{it.note.collection==name}?.note?.folderEmoji?.let{if(it.isEmpty())"" else "$it "} ?: "")+name.substringAfterLast('/'),"folder",store.visibleHeads.count{it.note.collection==name&&!it.note.deleted&&!it.note.archived},i>0){onOpen(name)}}}}}
}
