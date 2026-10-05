package dev.leafnotes

import org.json.JSONObject
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.PopupPositionProvider
import java.util.Locale

data class NoteStyle(val document:String?=null,val backdrop:String?=null,val backdropEnd:String?=null,val text:String?=null,val backdropImage:String?=null,val blurImage:Boolean?=null,val gradientDirection:String?=null) {
    val framed get()=backdrop!=null||backdropImage!=null; val customised get()=document!=null||framed||text!=null
    fun json()=JSONObject().put("document",document).put("backdrop",backdrop).put("backdropEnd",backdropEnd).put("text",text).put("backdropImage",backdropImage).put("blurImage",blurImage).put("gradientDirection",gradientDirection)
    fun paper(c:LeafColors)=hexColour(document) ?: c.canvas
    fun ink(c:LeafColors)=hexColour(text) ?: document?.let{if(lightColour(hexColour(it)!!))Color(0xFF191919)else Color(0xFFF7F7F7)} ?: c.text
    fun brush(c:LeafColors)=Brush.verticalGradient(listOf(hexColour(backdrop) ?: paper(c),hexColour(backdropEnd ?: backdrop) ?: paper(c)).let{if(gradientDirection=="up")it.reversed()else it})
    fun documentColours(c:LeafColors):LeafColors {val ink=ink(c);val paper=paper(c);return c.copy(canvas=paper,paper=paper,text=ink,secondary=ink.copy(alpha=.65f),tertiary=ink.copy(alpha=.4f),separator=ink.copy(alpha=.1f),fill=ink.copy(alpha=.06f),dark=!lightColour(paper))}
    companion object {fun from(o:JSONObject)=NoteStyle(validHex(o.optString("document")),validHex(o.optString("backdrop")),validHex(o.optString("backdropEnd")),validHex(o.optString("text")),o.optString("backdropImage").takeIf{it.matches(Regex("[a-f0-9]{64}"))},if(o.has("blurImage"))o.optBoolean("blurImage")else null,o.optString("gradientDirection").takeIf{it=="up"})}
}
val backdropPalette=listOf("#FDD1A0","#FCB122","#4F97FD","#1F33AD","#1FB29C","#0C4534","#156989","#FB5F1F","#B60A18","#FDC2C3","#BDABFE","#9FBEFD")
val backdropGradients=listOf("#71CBA2" to "#BFE9A5","#CFF0B7" to "#F1BEB7","#DADFE2" to "#9DA9B3","#D5AC93" to "#AFC1BB","#A997E5" to "#E7B6D5","#E1C092" to "#D3AB7B","#CDB4B3" to "#807D9F","#CAEAC9" to "#E89990","#DFC7E2" to "#F1ACB3","#848CA2" to "#C0C7D5","#FEE9A5" to "#FDC990","#71A5D5" to "#D7E9F4")
val documentPalette=listOf("#FFFFFF","#1F1F1F","#F9E4FF","#D7E7FD","#C9F9FD","#C6FAEE","#CBF9E1","#FDF8BC","#FEDEDE","#FBE3F1")
val textPalette=listOf("#1D1F21","#F2F2F2","#66136C","#1E3181","#164458","#134541","#0E553C","#9A5514","#75181C","#79123C")
fun validHex(s:String)=s.takeIf{it.matches(Regex("#[0-9a-fA-F]{6}"))}?.uppercase(Locale.ROOT)
fun hexColour(s:String?):Color?=s?.let{validHex(it)?.let{hex->Color(android.graphics.Color.parseColor(hex))}}
fun lightColour(c:Color)=c.luminance()>.179f
fun colourHex(c:Color)=String.format(Locale.ROOT,"#%02X%02X%02X",(c.red*255).toInt(),(c.green*255).toInt(),(c.blue*255).toInt())

@Composable fun NoteStyleMenu(store:Store,colors:LeafColors=LocalLeafColors.current) {
    var open by remember(store.selected){mutableStateOf(false)};val density=LocalDensity.current
    Box {Pressable(Modifier.size(44.dp),"Note style",onClick={open=!open}){Glyph("paintbrush")}
        val position=remember(density){object:PopupPositionProvider {
            override fun calculatePosition(anchorBounds:IntRect,windowSize:IntSize,layoutDirection:LayoutDirection,popupContentSize:IntSize):IntOffset {
                val inset=with(density){0.dp.roundToPx()};val gap=with(density){0.dp.roundToPx()}
                return IntOffset((windowSize.width-popupContentSize.width-inset).coerceAtLeast(inset),(anchorBounds.bottom+gap).coerceIn(inset,(windowSize.height-popupContentSize.height-inset).coerceAtLeast(inset)))
            }
        }}
        if(open)Popup(popupPositionProvider=position,onDismissRequest={open=false},properties=PopupProperties(focusable=true,clippingEnabled=true)) {
            CompositionLocalProvider(LocalLeafColors provides colors) {Column(Modifier.padding(24.dp).width((LocalConfiguration.current.screenWidthDp-24).coerceAtMost(326).dp).shadow(16.dp,RoundedCornerShape(26.dp),clip=false,ambientColor=(hexColour(store.editing?.style?.backdrop)?:colors.text).copy(alpha=.24f),spotColor=(hexColour(store.editing?.style?.backdrop)?:colors.text).copy(alpha=.30f)).heightIn(max=(LocalConfiguration.current.screenHeightDp-150).coerceIn(300,620).dp).frosted(RoundedCornerShape(26.dp),surface=colors.paper.copy(alpha=.82f)).border(.7.dp,colors.separator,RoundedCornerShape(26.dp)).verticalScroll(rememberScrollState()).padding(18.dp)){NoteStylePicker(store){open=false}}}
        }
    }
}

@Composable fun NoteStylePicker(store:Store,onClose:()->Unit) {
    val c=LocalLeafColors.current;var picker by remember{mutableStateOf<String?>(null)};var custom by remember{mutableStateOf(false)};var customEnd by remember{mutableStateOf(false)};var imageMode by remember{mutableStateOf(false)};var gradientMode by remember{mutableStateOf(false)};val style=store.editing?.style ?: NoteStyle()
    fun value(key:String)=when(key){"Document"->style.document;"Backdrop"->style.backdrop;"End"->style.backdropEnd;else->style.text}
    fun set(key:String,value:String?){store.editing?.let{n->val s=when(key){"Document"->style.copy(document=value);"Backdrop"->style.copy(backdrop=value,backdropEnd=if(value==null)null else style.backdropEnd,backdropImage=null);"End"->style.copy(backdropEnd=value);else->style.copy(text=value)};store.update(n.copy(style=s.takeIf{it.customised}),"noteStyle")}}
    fun setGradient(pair:Pair<String,String>){store.editing?.let{store.update(it.copy(style=style.copy(backdrop=pair.first,backdropEnd=pair.second,backdropImage=null)),"noteStyle")}}
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){if(picker!=null)Pressable(Modifier.size(40.dp),"Back to style",onClick={picker=null;custom=false}){Glyph("back",size=18)};Label(picker ?: "Style",22,FontWeight.SemiBold,modifier=Modifier.weight(1f));Pressable(Modifier.size(42.dp).background(c.fill,CircleShape),"Close",onClick={if(picker==null)onClose()else picker=null}){Glyph("close",size=19)}}
    Spacer(Modifier.height(14.dp));if(picker==null){StylePreview(style,c);Spacer(Modifier.height(18.dp))}
    if(picker==null){SectionTitle("Color");listOf("Backdrop","Document","Text").forEach{name->StyleRow(name,value(name),onClick={picker=name;imageMode=name=="Backdrop"&&style.backdropImage!=null;gradientMode=name=="Backdrop"&&style.backdropEnd!=null;custom=false})};SubtleDivider();Spacer(Modifier.height(9.dp));Pressable(Modifier.fillMaxWidth().height(48.dp).background(c.fill,RoundedCornerShape(12.dp)).border(.7.dp,c.separator,RoundedCornerShape(12.dp)).padding(horizontal=12.dp),"Reset style",onClick={store.editing?.let{store.update(it.copy(style=null),"noteStyle")}}){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){Glyph("undo",size=19);Label("Reset style",16,FontWeight.Medium)}}
    }else{SubtleDivider();Spacer(Modifier.height(14.dp));val key=picker!!
        if(key=="Backdrop")BackdropModes(style,gradientMode,imageMode,{mode->imageMode=mode==3;gradientMode=mode==2;when(mode){0->set("Backdrop",null);1->{if(style.backdrop==null)set("Backdrop",backdropPalette[1]);store.editing?.let{store.update(it.copy(style=(it.style?:NoteStyle()).copy(backdropEnd=null)),"noteStyle")}};2->if(style.backdropEnd==null)setGradient(backdropGradients[10])}})
        if(key=="Backdrop"&&imageMode){WallpaperChoices(store)}else {
        val solids=when(key){"Document"->documentPalette;"Text"->textPalette;else->backdropPalette}
        if(key!="Backdrop")Swatch(value(key)==null,null,if(key=="Backdrop")"No backdrop"else"Automatic",onClick={set(key,null)},automatic=true)
        val items:List<Any> = if(key=="Backdrop"&&gradientMode)backdropGradients else solids
        FlowSwatches(items){item->if(item is Pair<*,*>){@Suppress("UNCHECKED_CAST") val pair=item as Pair<String,String>;GradientSwatch(pair,style.backdrop==pair.first&&style.backdropEnd==pair.second){setGradient(pair)}}else{val hex=item as String;Swatch(value(key)==hex,hex,hex,{set(key,hex);if(key=="Backdrop")store.editing?.let{store.update(it.copy(style=(it.style?:NoteStyle()).copy(backdropEnd=null)),"noteStyle")};gradientMode=false})}}
        if(key=="Backdrop"&&style.backdropEnd!=null){Spacer(Modifier.height(12.dp));SectionTitle("Gradient");ColourDetail("Start Color",style.backdrop){custom=true;customEnd=false};ColourDetail("End Color",style.backdropEnd){custom=true;customEnd=true};Row(Modifier.fillMaxWidth().height(46.dp),verticalAlignment=Alignment.CenterVertically){Label("Direction",16,modifier=Modifier.weight(1f));Pressable(Modifier.background(c.fill,RoundedCornerShape(12.dp)).padding(horizontal=12.dp).height(44.dp),"Gradient direction",onClick={store.editing?.let{store.update(it.copy(style=style.copy(gradientDirection=if(style.gradientDirection=="up")"down"else"up")),"noteStyle")}}){Label(if(style.gradientDirection=="up")"Bottom to Top"else"Top to Bottom",15)}}}
        Spacer(Modifier.height(8.dp));SubtleDivider();Pressable(Modifier.fillMaxWidth().height(48.dp),"Custom color",onClick={custom=!custom;customEnd=false}){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Glyph("paintbrush",size=19);Label("Custom Color",16,modifier=Modifier.padding(start=12.dp).weight(1f));Glyph("next",size=14)}}
        if(custom){val target=if(customEnd)"End"else key;var hex by remember(target){mutableStateOf(value(target)?:"#FFFFFF")};LaunchedEffect(value(target)){hex=value(target)?:"#FFFFFF"};BasicTextField(hex,{hex=it.uppercase(Locale.ROOT);validHex(hex)?.let{v->set(target,v)}},singleLine=true,textStyle=TextStyle(color=c.text,fontSize=16.sp),modifier=Modifier.semantics{contentDescription="Hex color"}.fillMaxWidth().background(c.fill,RoundedCornerShape(12.dp)).padding(12.dp));val colour=hexColour(value(target))?:Color.White;listOf("Red","Green","Blue").forEachIndexed{i,label->Row(verticalAlignment=Alignment.CenterVertically){Label(label,13,modifier=Modifier.width(48.dp));PillSlider(listOf(colour.red,colour.green,colour.blue)[i],{v->val ch=mutableListOf(colour.red,colour.green,colour.blue);ch[i]=v;hex=colourHex(Color(ch[0],ch[1],ch[2]));set(target,hex)},valueRange=0f..1f,description="$label color",modifier=Modifier.weight(1f))}}}
        }
    }
}

@Composable private fun StylePreview(style:NoteStyle,c:LeafColors){Box(Modifier.fillMaxWidth().height(138.dp).clip(RoundedCornerShape(18.dp)).background(style.brush(c)).wallpaper(style),contentAlignment=Alignment.BottomCenter){Column(Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal=if(style.framed)12.dp else 0.dp).padding(top=if(style.framed)12.dp else 0.dp).offset(y=if(style.framed)7.dp else 0.dp).clip(RoundedCornerShape(14.dp)).background(style.paper(c)).padding(16.dp)){Label("Note",18,FontWeight.SemiBold,color=style.ink(c));Spacer(Modifier.height(15.dp));Box(Modifier.fillMaxWidth().height(5.dp).background(style.ink(c).copy(alpha=.20f),CircleShape));Spacer(Modifier.height(8.dp));Box(Modifier.width(90.dp).height(5.dp).background(style.ink(c).copy(alpha=.13f),CircleShape))}}}
@Composable private fun SectionTitle(text:String){Label(text.uppercase(),12,FontWeight.SemiBold,color=LocalLeafColors.current.text.copy(alpha=.60f),modifier=Modifier.padding(top=8.dp,bottom=5.dp))}
@Composable private fun StyleRow(name:String,hex:String?,onClick:()->Unit){val c=LocalLeafColors.current;Pressable(Modifier.fillMaxWidth().height(54.dp),"$name color",onClick=onClick){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){Glyph(if(name=="Document")"compose"else if(name=="Backdrop")"images"else"format",size=20);Label(if(name=="Document")"Document Color"else if(name=="Text")"Text Color"else name,17,FontWeight.Medium,modifier=Modifier.weight(1f));Box(Modifier.size(width=38.dp,height=34.dp).background(hexColour(hex)?:c.fill,RoundedCornerShape(11.dp)).border(.6.dp,c.separator,RoundedCornerShape(11.dp)),contentAlignment=Alignment.Center){if(hex==null)Glyph(if(name=="Backdrop")"minus"else"progress",size=15)};Glyph("next",size=14)}}}
@Composable private fun BackdropModes(style:NoteStyle,gradient:Boolean,image:Boolean,onMode:(Int)->Unit){val c=LocalLeafColors.current;Row(Modifier.fillMaxWidth().height(48.dp).background(c.fill,CircleShape).padding(3.dp)){(0..3).forEach{i->val selected=if(i==0)!style.framed&&!gradient&&!image else if(i==1)style.backdrop!=null&&!gradient&&!image else if(i==2)gradient else image;Pressable(Modifier.weight(1f).fillMaxHeight().then(if(selected)Modifier.background(c.paper,CircleShape).shadow(4.dp,CircleShape)else Modifier),"${listOf("No","Solid","Gradient","Image")[i]} backdrop",onClick={onMode(i)}){BackdropModeIcon(i)}}};Spacer(Modifier.height(14.dp))}
@Composable private fun FlowSwatches(items:List<Any>,content:@Composable (Any)->Unit){items.chunked(6).forEach{row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){row.forEach{item->content(item)};repeat(6-row.size){Spacer(Modifier.size(45.dp))}};Spacer(Modifier.height(8.dp))}}
@Composable private fun Swatch(selected:Boolean,hex:String?,label:String,onClick:()->Unit,automatic:Boolean=false){val c=LocalLeafColors.current;Pressable(Modifier.size(45.dp),label,onClick=onClick){Box(Modifier.size(39.dp).then(if(automatic)Modifier.background(Brush.linearGradient(listOf(Color.White,Color.Black)),CircleShape)else Modifier.background(hexColour(hex)?:c.fill,CircleShape)).border(if(selected)3.dp else .7.dp,if(selected)c.accent else c.separator,CircleShape))}}
@Composable private fun GradientSwatch(pair:Pair<String,String>,selected:Boolean,onClick:()->Unit){val c=LocalLeafColors.current;Pressable(Modifier.size(45.dp),"Gradient ${pair.first} ${pair.second}",onClick=onClick){Box(Modifier.size(39.dp).background(Brush.verticalGradient(listOf(hexColour(pair.first)!!,hexColour(pair.second)!!)),CircleShape).border(if(selected)3.dp else .7.dp,if(selected)c.accent else c.separator,CircleShape))}}
@Composable private fun ColourDetail(label:String,hex:String?,onClick:()->Unit){val c=LocalLeafColors.current;Row(Modifier.fillMaxWidth().height(44.dp).clickable(onClick=onClick),verticalAlignment=Alignment.CenterVertically){Label(label,16,modifier=Modifier.weight(1f));Box(Modifier.size(width=40.dp,height=32.dp).background(hexColour(hex)?:c.fill,RoundedCornerShape(10.dp)).border(.6.dp,c.separator,RoundedCornerShape(10.dp)))}}

@Composable private fun WallpaperChoices(store:Store){
    val c=LocalLeafColors.current;val context=androidx.compose.ui.platform.LocalContext.current
    val items=remember{org.json.JSONArray(context.assets.open("wallpapers/index.json").bufferedReader().use{it.readText()}).let{a->(0 until a.length()).map{val o=a.getJSONObject(it);Wallpaper(o.getString("id"),o.getString("name"))}}}
    val upload=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()){uri->if(uri!=null)try{store.uploadBackdrop(uri)}catch(e:Exception){store.status=e.message?:"Could not read image"}}
    items.chunked(3).forEach{row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){row.forEach{w->Pressable(Modifier.weight(1f).height(72.dp).clip(RoundedCornerShape(12.dp)).wallpaper(NoteStyle(backdropImage=w.id)).border(if(store.editing?.style?.backdropImage==w.id)3.dp else .7.dp,c.text.copy(alpha=.3f),RoundedCornerShape(12.dp)),w.name,onClick={store.useBackdrop(context.assets.open("wallpapers/${w.id}.webp").use{it.readBytes()},w.name)}){}};repeat(3-row.size){Spacer(Modifier.weight(1f))}};Spacer(Modifier.height(8.dp))}
    Pressable(Modifier.fillMaxWidth().height(48.dp).background(c.fill,RoundedCornerShape(12.dp)).border(.7.dp,c.separator,RoundedCornerShape(12.dp)),"Upload image",onClick={upload.launch("image/*")}){Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){Glyph("image");Label("Upload image",16)}}
    Row(Modifier.fillMaxWidth().height(52.dp),verticalAlignment=Alignment.CenterVertically){Label("Blur Image",16,modifier=Modifier.weight(1f));IosSegments(listOf("Off","On"),if(store.editing?.style?.blurImage==true)1 else 0){v->store.editing?.let{n->store.update(n.copy(style=(n.style?:NoteStyle()).copy(blurImage=v==1)),"noteStyle")}}}
}

@Composable private fun BackdropModeIcon(mode:Int){
    val ink=LocalLeafColors.current.text
    if(mode==3){Glyph("image",size=21);return}
    Canvas(Modifier.size(21.dp)){
        val inset=2.dp.toPx();val extent=size.width-inset*2;val corner=androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
        if(mode==1)drawRoundRect(ink,androidx.compose.ui.geometry.Offset(inset,inset),androidx.compose.ui.geometry.Size(extent,extent),corner)
        else {
            if(mode==2)drawRoundRect(Brush.verticalGradient(listOf(ink.copy(alpha=.18f),ink)),androidx.compose.ui.geometry.Offset(inset+2,inset+2),androidx.compose.ui.geometry.Size(extent-4,extent-4),corner)
            drawRoundRect(ink,androidx.compose.ui.geometry.Offset(inset,inset),androidx.compose.ui.geometry.Size(extent,extent),corner,style=androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
            if(mode==0)drawLine(ink,androidx.compose.ui.geometry.Offset(1f,1f),androidx.compose.ui.geometry.Offset(size.width-1,size.height-1),2.dp.toPx())
        }
    }
}
