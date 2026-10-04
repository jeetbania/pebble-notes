package dev.leafnotes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import java.util.Calendar

private val stages=listOf("todo","progress","review","done")
private val stageLabels=listOf("To Do","In Progress","In Review","Done")
private val stageIcons=listOf("todo","progress","review","done")

@Composable fun TaskIconStrip(labels:List<String>,icons:List<String>,selected:Int,tints:List<Color>,onSelect:(Int)->Unit){
    val c=LocalLeafColors.current
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
        Row(Modifier.fillMaxWidth().background(c.text.copy(alpha=.035f),CircleShape).padding(4.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)){
            labels.forEachIndexed{i,label->Pressable(Modifier.weight(1f).height(46.dp).semantics{this.selected=i==selected}.background(if(i==selected)tints[i].copy(alpha=.14f)else Color.Transparent,CircleShape).border(.5.dp,if(i==selected)tints[i].copy(alpha=.25f)else Color.Transparent,CircleShape),label,onClick={onSelect(i)}){Glyph(icons[i],size=21,tint=tints[i].copy(alpha=if(i==selected)1f else .8f))}}
        }
        Label(labels.getOrElse(selected){labels.first()},12,color=c.secondary,modifier=Modifier.align(Alignment.CenterHorizontally))
    }
}
@Composable fun TaskStatusControl(stage:String,onSelect:(String)->Unit){TaskIconStrip(stageLabels,stageIcons,stages.indexOf(stage).coerceAtLeast(0),stages.map{taskStageColor(it)}){onSelect(stages[it])}}
@Composable fun TaskPriorityControl(priority:Int,onSelect:(Int)->Unit){val c=LocalLeafColors.current;TaskIconStrip(listOf("No priority","Low priority","Medium priority","High priority"),listOf("minus","flag","flag","flag"),priority,listOf(c.secondary,taskStageColor("todo"),taskStageColor("progress"),c.danger),onSelect)}

@Composable fun TaskOptions(r:Revision,onDismiss:()->Unit,onEdit:()->Unit,onOpen:()->Unit,onChange:((Note)->Note)->Unit,onDuplicate:()->Unit){
    val c=LocalLeafColors.current;val task=r.note.task!!
    IosSheet("Task options",onDismiss=onDismiss){
        val finish=LocalSheetAction.current
        val change:((Note)->Note)->Unit={transform->finish{onChange(transform)}}
        Label("Status",13,color=c.secondary,modifier=Modifier.padding(bottom=8.dp))
        TaskStatusControl(task.stage){state->change{it.copy(task=it.task!!.moveTo(state))}}
        Spacer(Modifier.height(14.dp));SubtleDivider();Spacer(Modifier.height(14.dp))
        Label("Priority",13,color=c.secondary,modifier=Modifier.padding(bottom=8.dp))
        TaskPriorityControl(task.priority){priority->change{it.copy(task=it.task!!.copy(priority=priority))}}
        Spacer(Modifier.height(14.dp));SubtleDivider();Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            listOf("Today","Tomorrow","Choose date").forEachIndexed{i,label->Pressable(Modifier.weight(1f).height(68.dp).background(c.fill,CircleShape),label,onClick={if(i==2)finish(onEdit)else change{n->val old=n.task!!;val prior=Calendar.getInstance().apply{timeInMillis=old.dueAt};val chosen=Calendar.getInstance().apply{add(Calendar.DAY_OF_MONTH,i);set(Calendar.HOUR_OF_DAY,if(old.hasTime)prior.get(Calendar.HOUR_OF_DAY)else 0);set(Calendar.MINUTE,if(old.hasTime)prior.get(Calendar.MINUTE)else 0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)};n.copy(task=old.copy(dueAt=chosen.timeInMillis))}}){Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)){Glyph(if(i==2)"calendar" else "clock",size=20);Label(label,11,color=c.secondary)}}}
        }
        if(task.dueAt>0)SheetRow("Remove date","minus"){change{it.copy(task=it.task!!.copy(dueAt=0,hasTime=false,remind=false))}}
        Spacer(Modifier.height(14.dp));SubtleDivider();Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            listOf(Triple("Edit task","edit",onEdit),Triple("Task details","list",onOpen),Triple("Duplicate task","copy",onDuplicate),Triple("Convert to note","note",{onChange{it.copy(task=null)}}),Triple("Move to Trash","trash",{onChange{it.copy(deleted=true)}})).forEach{(label,icon,action)->Pressable(Modifier.weight(1f).height(48.dp).background(c.fill,CircleShape),label,onClick={finish(action)}){Glyph(icon,size=21,tint=if(icon=="trash")c.danger else c.text)}}
        }
    }
}

@Composable fun RepeatPicker(value:String,onSelect:(String)->Unit){
    val c=LocalLeafColors.current
    var expanded by androidx.compose.runtime.remember{androidx.compose.runtime.mutableStateOf(false)}
    val values=listOf("none","daily","weekly","monthly","yearly")
    val labels=listOf("Never","Every day","Every week","Every month","Every year")
    Box(Modifier.fillMaxWidth().padding(top=12.dp)){
        Pressable(Modifier.fillMaxWidth().heightIn(min=48.dp).background(c.fill,CircleShape).padding(horizontal=14.dp),"Repeat",onClick={expanded=true}){Row(verticalAlignment=Alignment.CenterVertically){Glyph("sync",size=20);Spacer(Modifier.width(10.dp));Label("Repeat",15,modifier=Modifier.weight(1f));Label(labels[values.indexOf(value).coerceAtLeast(0)],14,color=c.secondary);Spacer(Modifier.width(8.dp));Glyph("down",size=12)}}
        androidx.compose.material3.DropdownMenu(expanded,{expanded=false},containerColor=c.paper){values.forEachIndexed{i,rule->androidx.compose.material3.DropdownMenuItem(text={Label(labels[i],15)},leadingIcon={Glyph(if(rule==value)"done" else "sync",size=18,tint=if(rule==value)c.accent else c.secondary)},onClick={expanded=false;onSelect(rule)})}}
    }
}
