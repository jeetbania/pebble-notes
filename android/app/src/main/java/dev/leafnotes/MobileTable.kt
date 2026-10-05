package dev.leafnotes

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.*

fun Block.removeTableRow(index:Int)=if(cells.size>1 && index in cells.indices)copy(cells=cells.filterIndexed{i,_->i!=index})else this
fun Block.removeTableColumn(index:Int)=if((cells.firstOrNull()?.size ?: 0) > 1 && index in cells[0].indices)copy(cells=cells.map{row->row.filterIndexed{i,_->i!=index}})else this

@Composable fun MobileTable(store:Store,block:Block) {
    val c=LocalLeafColors.current
    var menu by remember(block.id){mutableStateOf<Pair<Boolean,Int>?>(null)}
    var focused by remember(block.id){mutableStateOf<Pair<Int,Int>?>(null)}
    val columnCount=block.cells.firstOrNull()?.size ?: return
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.fill).horizontalScroll(rememberScrollState())) {
            Row {
                Spacer(Modifier.width(44.dp))
                repeat(columnCount){col->Pressable(Modifier.width(120.dp).height(44.dp),"Column ${col+1} options",onClick={menu=false to col}){Label("${('A'.code+col).toChar()}  ⋯",13,color=c.secondary)}}
            }
            block.cells.forEachIndexed{row,cells->
                Row(Modifier.height(IntrinsicSize.Min)) {
                    Pressable(Modifier.width(44.dp).fillMaxHeight().heightIn(min=48.dp),"Row ${row+1} options",onClick={menu=true to row}){Label("${row+1}",13,color=c.secondary)}
                    cells.forEachIndexed{col,value->
                        BasicTextField(value,{new->store.changeTableCell(block.id,row,col,new)},textStyle=TextStyle(color=c.text,fontSize=16.sp,lineHeight=22.sp),cursorBrush=SolidColor(c.accent),modifier=Modifier.width(120.dp).fillMaxHeight().heightIn(min=48.dp).background(if(focused==(row to col))c.accent.copy(alpha=.08f)else if(row==0)c.text.copy(alpha=.04f)else androidx.compose.ui.graphics.Color.Transparent).border(.5.dp,c.separator).onFocusChanged{if(it.isFocused)focused=row to col}.padding(10.dp))
                    }
                }
            }
        }
        Row(Modifier.padding(top=10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Pressable(Modifier.heightIn(min=44.dp).border(.7.dp,c.separator,RoundedCornerShape(14.dp)).padding(horizontal=12.dp),"Add row",onClick={store.changeBlock(block.id){if(it.cells.size<100)it.copy(cells=it.cells+listOf(List(columnCount){""}))else it}}){Label("+ Row",15)}
            Pressable(Modifier.heightIn(min=44.dp).border(.7.dp,c.separator,RoundedCornerShape(14.dp)).padding(horizontal=12.dp),"Add column",onClick={store.changeBlock(block.id){if(columnCount<12)it.copy(cells=it.cells.map{r->r+""})else it}}){Label("+ Column",15)}
        }
    }
    menu?.let{(isRow,index)->IosSheet(if(isRow)"Row ${index+1}"else"Column ${index+1}",onDismiss={menu=null}) {
        val count=if(isRow)block.cells.size else columnCount
        fun insert(after:Boolean){store.changeBlock(block.id){b->val pos=(index+if(after)1 else 0).coerceIn(0,if(isRow)b.cells.size else b.cells[0].size);if(isRow && b.cells.size<100)b.copy(cells=b.cells.toMutableList().apply{add(pos,List(b.cells[0].size){""})})else if(!isRow && b.cells[0].size<12)b.copy(cells=b.cells.map{it.toMutableList().apply{add(pos,"")}})else b};menu=null}
        SheetRow(if(isRow)"Insert row above"else"Insert column before","plus"){insert(false)}
        SheetRow(if(isRow)"Insert row below"else"Insert column after","plus"){insert(true)}
        if(count>1)SheetRow(if(isRow)"Delete row"else"Delete column","trash",tint=c.danger){store.changeBlock(block.id){if(isRow)it.removeTableRow(index)else it.removeTableColumn(index)};focused=null;menu=null}
        else Label("Keep at least one ${if(isRow)"row"else"column"} in the table.",14,color=c.secondary,modifier=Modifier.padding(12.dp))
    }}
}
fun Store.changeTableCell(id:String,row:Int,col:Int,value:String){editing?.let{n->update(n.editBlock(id){b->b.copy(cells=b.cells.mapIndexed{r,line->line.mapIndexed{k,v->if(r==row&&k==col)value else v}})},"cell:$id:$row:$col")}}
