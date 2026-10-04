package dev.leafnotes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Decorative previews, not loading skeletons: no shimmer or pretend interactive controls.
@Composable fun GhostArtwork(kind:String,modifier:Modifier=Modifier) {
    val c=LocalLeafColors.current
    Box(modifier.widthIn(max=340.dp).fillMaxWidth().height(176.dp).clearAndSetSemantics{},contentAlignment=Alignment.Center) {
        listOf(Triple(-34,-22,-7f),Triple(40,19,8f),Triple(-12,12,-3f)).forEachIndexed { i,(x,y,angle) ->
            val shape=RoundedCornerShape(14.dp)
            Canvas(Modifier.offset(x.dp,y.dp).width(232.dp).height(112.dp).graphicsLayer{rotationZ=angle}.shadow(14.dp,shape,ambientColor=c.text.copy(alpha=.04f),spotColor=c.text.copy(alpha=.04f)).background(if(c.dark)Color(0xFF17171A) else Color(0xFFF9F9FB),shape).border(.7.dp,c.text.copy(alpha=.075f),shape).padding(16.dp)) {
                val ink=c.text.copy(alpha=.08f);val tint=Color(0xFF9451E8).copy(alpha=if(c.dark).15f else .09f)
                fun line(x:Float,y:Float,w:Float)=drawRoundRect(ink,Offset(x,y),Size(w,5.dp.toPx()),cornerRadius=androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
                val unit=1.dp.toPx();val h=size.height;val w=size.width
                if(kind=="images") {
                    drawRoundRect(tint,Offset.Zero,Size(w*.34f,h),cornerRadius=androidx.compose.ui.geometry.CornerRadius(7*unit))
                    drawCircle(c.text.copy(alpha=.12f),6*unit,Offset(w*.24f,h*.28f))
                    val mountain=Path().apply{moveTo(5*unit,h*.82f);lineTo(w*.12f,h*.46f);lineTo(w*.2f,h*.66f);lineTo(w*.28f,h*.42f);lineTo(w*.32f,h*.82f);close()};drawPath(mountain,ink)
                    line(w*.42f,h*.2f,w*.48f);line(w*.42f,h*.45f,w*.55f);line(w*.42f,h*.7f,w*.35f)
                } else if(kind in listOf("tasks","checklists")) {
                    repeat(3){r->val cy=(r+.5f)*h/3;drawCircle(tint,7*unit,Offset(8*unit,cy));drawCircle(c.text.copy(alpha=.14f),7*unit,Offset(8*unit,cy),style=Stroke(unit));line(26*unit,cy-2*unit,w*(if(r==1).58f else .72f))}
                } else {
                    drawRoundRect(tint,Offset.Zero,Size(28*unit,28*unit),cornerRadius=androidx.compose.ui.geometry.CornerRadius(7*unit))
                    line(40*unit,7*unit,w*.57f);line(40*unit,21*unit,w*.4f);line(0f,h*.63f,w*.9f);line(0f,h*.88f,w*.65f)
                }
            }
        }
    }
}

@Composable fun GhostEmpty(kind:String,title:String,detail:String,actionLabel:String?=null,onAction:(()->Unit)?=null,modifier:Modifier=Modifier) {
    val c=LocalLeafColors.current
    Column(modifier.fillMaxWidth().padding(vertical=24.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        GhostArtwork(kind)
        Label(title,21,FontWeight.SemiBold,modifier=Modifier.padding(top=12.dp))
        androidx.compose.material3.Text(detail,color=c.secondary,fontSize=androidx.compose.ui.unit.TextUnit(15f,androidx.compose.ui.unit.TextUnitType.Sp),textAlign=androidx.compose.ui.text.style.TextAlign.Center,modifier=Modifier.padding(top=8.dp,bottom=20.dp))
        if(actionLabel!=null && onAction!=null)Pressable(Modifier.heightIn(min=48.dp).background(c.text.copy(alpha=.065f),CircleShape).padding(horizontal=22.dp),actionLabel,onClick=onAction){Label(actionLabel,16,FontWeight.Medium)}
    }
}

@Composable fun LibraryEmpty(section:String,search:String,onClear:()->Unit,onNew:()->Unit,onBrowse:()->Unit) {
    val kind=if(search.isNotEmpty())"search"else section.lowercase()
    val title=when(kind){"search"->"No matching notes";"images"->"A place for inspiration";"trash"->"Trash is empty";"archive"->"Nothing archived yet";"pinned"->"Keep favourites close";"checklists"->"One thing at a time";else->"Room for a new thought"}
    val detail=when(kind){"search"->"Try another word.";"images"->"Add a photo to a note to see it here.";"trash"->"Deleted notes will appear here.";"archive"->"Archived notes stay here for later.";"pinned"->"Pin a note to find it here.";"checklists"->"Start a note with a checklist.";else->"Keep a thought, an image, a little idea."}
    GhostEmpty(kind,title,detail,when(kind){"trash","archive"->null;"search"->"Clear search";"pinned"->"Browse notes";else->"Create a note"},when(kind){"search"->onClear;"pinned"->onBrowse;"trash","archive"->null;else->onNew})
}
