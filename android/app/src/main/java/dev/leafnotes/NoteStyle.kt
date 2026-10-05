package dev.leafnotes

import org.json.JSONObject
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import java.util.Locale

data class NoteStyle(val document:String?=null,val backdrop:String?=null,val backdropEnd:String?=null,val text:String?=null) {
    val framed get()=backdrop!=null
    val customised get()=document!=null||framed||text!=null
    fun json()=JSONObject().put("document",document).put("backdrop",backdrop).put("backdropEnd",backdropEnd).put("text",text)
    fun paper(c:LeafColors)=hexColour(document) ?: c.canvas
    fun ink(c:LeafColors)=hexColour(text) ?: document?.let{if(lightColour(hexColour(it)!!))Color(0xFF191919)else Color(0xFFF7F7F7)} ?: c.text
    fun brush(c:LeafColors)=Brush.linearGradient(listOf(hexColour(backdrop) ?: paper(c),hexColour(backdropEnd ?: backdrop) ?: paper(c)))
    fun documentColours(c:LeafColors):LeafColors { val ink=ink(c);val paper=paper(c);return c.copy(canvas=paper,paper=paper,text=ink,secondary=ink.copy(alpha=.65f),tertiary=ink.copy(alpha=.4f),separator=ink.copy(alpha=.1f),fill=ink.copy(alpha=.06f),dark=!lightColour(paper)) }
    companion object { fun from(o:JSONObject)=NoteStyle(validHex(o.optString("document")),validHex(o.optString("backdrop")),validHex(o.optString("backdropEnd")),validHex(o.optString("text"))) }
}
val notePalette=listOf("#FFFCF8","#F8F0FF","#E5EEFF","#DFF4EC","#FFE7E3","#FFE8B5","#FFBD19","#5F96DB","#7756AE","#185B51","#31343B","#191B20")
fun validHex(s:String)=s.takeIf{it.matches(Regex("#[0-9a-fA-F]{6}"))}?.uppercase(Locale.ROOT)
fun hexColour(s:String?):Color?=s?.let{validHex(it)?.let{hex->Color(android.graphics.Color.parseColor(hex))}}
fun lightColour(c:Color)=c.luminance()>.179f
fun colourHex(c:Color)=String.format(Locale.ROOT,"#%02X%02X%02X",(c.red*255).toInt(),(c.green*255).toInt(),(c.blue*255).toInt())

@Composable fun NoteStyleMenu(store:Store,colors:LeafColors=LocalLeafColors.current) {
    var open by remember(store.selected){mutableStateOf(false)}
    Box {
        Pressable(Modifier.size(44.dp),"Note style",onClick={open=!open}){Glyph("paintbrush")}
        if(open)Popup(alignment=Alignment.TopEnd,offset=IntOffset(0,with(LocalDensity.current){52.dp.roundToPx()}),onDismissRequest={open=false},properties=PopupProperties(focusable=true)) {
            val c=colors
            CompositionLocalProvider(LocalLeafColors provides colors) {
            Column(Modifier.widthIn(max=330.dp).width(304.dp).shadow(16.dp,RoundedCornerShape(24.dp)).heightIn(max=(LocalConfiguration.current.screenHeightDp-160).coerceIn(240,540).dp).clip(RoundedCornerShape(24.dp)).background(c.paper).border(.7.dp,c.separator,RoundedCornerShape(24.dp)).verticalScroll(rememberScrollState()).padding(18.dp)) { NoteStylePicker(store){open=false} }
            }
        }
    }
}
@Composable fun NoteStylePicker(store:Store,onClose:()->Unit) {
    val c=LocalLeafColors.current
    var picker by remember{mutableStateOf<String?>(null)}
    var custom by remember{mutableStateOf(false)}
    val style=store.editing?.style ?: NoteStyle()
    fun value(key:String)=when(key){"Document"->style.document;"Backdrop"->style.backdrop;"Fade"->style.backdropEnd;else->style.text}
    fun set(key:String,value:String?){store.editing?.let{n->val s=when(key){"Document"->style.copy(document=value);"Backdrop"->style.copy(backdrop=value,backdropEnd=if(value==null)null else style.backdropEnd);"Fade"->style.copy(backdropEnd=value);else->style.copy(text=value)};store.update(n.copy(style=s.takeIf{it.customised}),"noteStyle")}}
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        if(picker!=null)Pressable(Modifier.size(44.dp),"Back to style",onClick={picker=null;custom=false}){Glyph("back",size=18)}
        Label(picker ?: "Style",20,FontWeight.SemiBold,modifier=Modifier.weight(1f))
        Pressable(Modifier.size(44.dp),"Close style",onClick=onClose){Glyph("close",size=19)}
    }
    Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(16.dp)).background(style.brush(c)).padding(if(style.framed)12.dp else 0.dp)) {
        Column(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(style.paper(c)).padding(16.dp)) {Label("Note",18,FontWeight.SemiBold,color=style.ink(c));Spacer(Modifier.height(12.dp));Box(Modifier.fillMaxWidth().height(5.dp).background(style.ink(c).copy(alpha=.18f),CircleShape));Spacer(Modifier.height(8.dp));Box(Modifier.width(95.dp).height(5.dp).background(style.ink(c).copy(alpha=.12f),CircleShape))}
    }
    Spacer(Modifier.height(14.dp))
    val key=picker
    if(key==null) {
        listOf("Document","Backdrop","Text").forEach { name->Pressable(Modifier.fillMaxWidth().heightIn(min=52.dp),"$name colour",onClick={picker=name;custom=false}) { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {Glyph(if(name=="Document")"compose"else if(name=="Backdrop")"images"else "format",size=20);Label(name,17,modifier=Modifier.weight(1f));Box(Modifier.size(34.dp).background(hexColour(value(name)) ?: c.fill,RoundedCornerShape(10.dp)),contentAlignment=Alignment.Center){if(value(name)==null)Glyph("minus",size=15)};Glyph("next",size=14)} } }
        Box(Modifier.fillMaxWidth().padding(vertical=10.dp).height(.5.dp).background(c.separator))
        Pressable(Modifier.fillMaxWidth().heightIn(min=44.dp),"Reset style",onClick={store.editing?.let{store.update(it.copy(style=null),"noteStyle")}}){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){Glyph("undo",size=19);Label("Reset style",16)}}
    } else {
        (listOf<String?>(null)+notePalette).chunked(4).forEach{row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){row.forEach{hex->Pressable(Modifier.size(52.dp),hex ?: if(key=="Backdrop")"No backdrop"else"Automatic",onClick={set(key,hex)}){Box(Modifier.size(42.dp).background(hexColour(hex) ?: c.fill,CircleShape).border(if(value(key)==hex)2.dp else .5.dp,if(value(key)==hex)c.accent else c.separator,CircleShape),contentAlignment=Alignment.Center){if(value(key)==hex)Glyph("done",size=18,tint=hexColour(hex)?.let{if(lightColour(it))Color.Black else Color.White} ?: c.text) else if(hex==null)Glyph("minus",size=17)}}};repeat(4-row.size){Spacer(Modifier.size(52.dp))}}}
        Pressable(Modifier.fillMaxWidth().heightIn(min=48.dp),"Custom colour",onClick={custom=!custom}){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Glyph("paintbrush",size=20);Label("Custom colour",16,modifier=Modifier.padding(start=12.dp).weight(1f));Glyph("next",size=14)}}
        if(custom) {
            var hex by remember(key){mutableStateOf(value(key) ?: "#FFFFFF")}
            LaunchedEffect(value(key)){hex=value(key) ?: "#FFFFFF"}
            BasicTextField(hex,{hex=it.uppercase(Locale.ROOT);validHex(hex)?.let{v->set(key,v)}},singleLine=true,textStyle=TextStyle(color=c.text,fontSize=16.sp),modifier=Modifier.semantics{contentDescription="Hex colour"}.fillMaxWidth().background(c.fill,RoundedCornerShape(12.dp)).padding(12.dp))
            val colour=hexColour(value(key)) ?: Color.White
            listOf("Red","Green","Blue").forEachIndexed{i,label->Row(verticalAlignment=Alignment.CenterVertically){Label(label,13,modifier=Modifier.width(48.dp));PillSlider(listOf(colour.red,colour.green,colour.blue)[i],{v->val channels=mutableListOf(colour.red,colour.green,colour.blue);channels[i]=v;hex=colourHex(Color(channels[0],channels[1],channels[2]));set(key,hex)},valueRange=0f..1f,description="$label colour",modifier=Modifier.weight(1f))}}
        }
        if(key=="Backdrop"&&style.framed) {
            Pressable(Modifier.fillMaxWidth().heightIn(min=48.dp),"Gradient backdrop",onClick={set("Fade",if(style.backdropEnd==null)(style.document ?: "#F8F0FF")else null)}){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Label("Gradient",16,modifier=Modifier.weight(1f));Glyph(if(style.backdropEnd!=null)"done"else"plus",size=20)}}
            if(style.backdropEnd!=null)Pressable(Modifier.fillMaxWidth().heightIn(min=48.dp),"Fade colour",onClick={picker="Fade";custom=false}){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Label("Fade colour",16,modifier=Modifier.weight(1f));Box(Modifier.size(30.dp).background(hexColour(style.backdropEnd)!!,CircleShape))}}
        }
    }
}
