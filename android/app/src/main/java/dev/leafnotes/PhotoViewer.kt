package dev.leafnotes

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import java.io.File
import kotlin.math.abs
import kotlin.math.min

 data class ViewerPhoto(val noteId:String,val blockId:String,val attachment:Media,val caption:String){val id get()=noteId+":"+blockId}
fun viewerPhotos(note:Note,noteId:String)=note.document.filter{it.kind=="image"}.mapNotNull{b->note.attachments.firstOrNull{it.id==b.mediaId&&it.mime.startsWith("image/")}?.let{ViewerPhoto(noteId,b.id,it,b.caption)}}
@Composable fun PhotoPreview(store:Store,initial:Media,items:List<ViewerPhoto>,onClose:()->Unit) {
    val photos=remember{items}
    val c=LocalLeafColors.current;val calm=LocalCalmMotion.current;val density=LocalDensity.current
    var selection by remember{mutableIntStateOf(photos.indexOfFirst{it.attachment.id==initial.id}.coerceAtLeast(0))}
    var zoom by remember{mutableFloatStateOf(1f)};var animateZoom by remember{mutableStateOf(false)};val displayedZoom by animateFloatAsState(zoom,if(calm||!animateZoom)snap() else tween(200),label="Image zoom");var pan by remember{mutableStateOf(Offset.Zero)}
    var caption by remember{mutableStateOf(photos.getOrNull(selection)?.caption ?: "")};var menu by remember{mutableStateOf(false)}
    val current=photos.getOrNull(selection)
    fun commit(){val p=current ?: return;val note=store.visibleHeads.firstOrNull{it.noteId==p.noteId}?.note ?: return;if(note.document.firstOrNull{it.id==p.blockId}?.caption!=caption){if(store.selected==p.noteId)store.update(note.editBlock(p.blockId){it.copy(caption=caption)}) else store.taskChange(p.noteId){it.editBlock(p.blockId){b->b.copy(caption=caption)}}}}
    fun choose(index:Int){if(index in photos.indices&&index!=selection){commit();selection=index;caption=store.visibleHeads.firstOrNull{it.noteId==photos[index].noteId}?.note?.document?.firstOrNull{it.id==photos[index].blockId}?.caption ?: photos[index].caption;zoom=1f;pan=Offset.Zero}}
    Dialog(onDismissRequest={commit();onClose()},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        val view=LocalView.current
        SideEffect{(view.parent as? DialogWindowProvider)?.window?.let{window->window.setDimAmount(.12f);if(android.os.Build.VERSION.SDK_INT>=31){window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND);window.attributes=window.attributes.apply{blurBehindRadius=18}}}}
        Box(Modifier.fillMaxSize().background(c.canvas.copy(alpha=.45f)).statusBarsPadding().navigationBarsPadding().imePadding().padding(bottom=20.dp)) {
            BoxWithConstraints(Modifier.fillMaxSize().clipToBounds().backdropSource().pointerInput(selection){var swipe=0f;detectTransformGestures{_,delta,magnification,_->animateZoom=false;zoom=(zoom*magnification).coerceIn(1f,5f);if(zoom>1.01f){pan+=delta;swipe=0f}else{pan=Offset.Zero;swipe+=delta.x;if(abs(swipe)>with(density){64.dp.toPx()}){choose(selection+if(swipe<0)1 else -1);swipe=0f}}}},contentAlignment=Alignment.Center) {
                val viewportWidth=constraints.maxWidth.toFloat();val viewportHeight=constraints.maxHeight.toFloat()
                photos.forEachIndexed{i,p->
                    val distance by animateFloatAsState((i-selection).toFloat(),if(calm)snap() else spring(dampingRatio=.78f,stiffness=420f),label="Image position")
                    val file=File(store.media,p.attachment.id)
                    val ratio=remember(file){val o=android.graphics.BitmapFactory.Options().apply{inJustDecodeBounds=true};android.graphics.BitmapFactory.decodeFile(file.path,o);if(o.outWidth>0&&o.outHeight>0)o.outWidth.toFloat()/o.outHeight else 1f}
                    val fitWidth=min(viewportWidth*.76f,viewportHeight*.92f*ratio);val fitHeight=fitWidth/ratio
                    val scale=1f-.86f*abs(distance).coerceAtMost(1f)
                    val primary=i==selection
                    Box(Modifier.width(with(density){fitWidth.toDp()}).height(with(density){fitHeight.toDp()}).zIndex(2f-abs(distance)).graphicsLayer {
                        translationX=distance.coerceIn(-1f,1f)*viewportWidth*.40f+(if(primary)pan.x else 0f)
                        translationY=if(primary)pan.y else 0f
                        scaleX=scale*(if(primary)displayedZoom else 1f);scaleY=scaleX;rotationY=distance.coerceIn(-1f,1f)*-18f
                        alpha=if(abs(distance)>1.1f || zoom>1.01f&&!primary)0f else if(primary)1f else .72f
                    }.clip(RoundedCornerShape(10.dp)).pointerInput(selection,primary){detectTapGestures(onTap={if(!primary&&abs(i-selection)==1)choose(i)},onDoubleTap={if(primary){animateZoom=true;zoom=if(zoom>1.01f)1f else 2f;pan=Offset.Zero}})}) {
                        MediaImage(file,Modifier.fillMaxSize(),fit=true,radius=10)
                    }
                }
            }
            Row(Modifier.align(Alignment.TopCenter).padding(horizontal=16.dp).padding(top=76.dp).frosted(CircleShape).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){ChromeButton("minus","Zoom out",size=40){animateZoom=true;zoom=(zoom-.25f).coerceAtLeast(1f);if(zoom==1f)pan=Offset.Zero};PillSlider(zoom,{animateZoom=false;zoom=it;if(zoom==1f)pan=Offset.Zero},valueRange=1f..5f,modifier=Modifier.width(150.dp),description="Image zoom");ChromeButton("plus","Zoom in",size=40){animateZoom=true;zoom=(zoom+.25f).coerceAtMost(5f)};Pressable(Modifier.padding(10.dp),"Fit image",onClick={zoom=1f;pan=Offset.Zero}){Label("Fit",14)}}
            if(zoom<=1.01f)Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal=24.dp,vertical=18.dp),horizontalAlignment=Alignment.CenterHorizontally){
                Box(Modifier.fillMaxWidth().padding(8.dp)){if(caption.isEmpty())Label("Add caption",14,color=c.secondary,modifier=Modifier.align(Alignment.Center));BasicTextField(caption,{caption=it;commit()},textStyle=TextStyle(fontSize=14.sp,color=c.text,textAlign=TextAlign.Center),modifier=Modifier.fillMaxWidth(),maxLines=3)}
                Row(horizontalArrangement=Arrangement.spacedBy(18.dp),verticalAlignment=Alignment.CenterVertically){ChromeButton("back","Back to library",size=40){commit();store.select(null);onClose()};Label("${selection+1} / ${photos.size}",12,color=c.secondary);ChromeButton("next","Next image",size=40){choose(selection+1)}}
            }
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){ChromeButton("back","Back to library",size=44){commit();store.select(null);onClose()};Spacer(Modifier.width(10.dp));Label(current?.attachment?.name ?: initial.name,13,color=c.secondary,lines=1,modifier=Modifier.weight(1f));ChromeButton("more","Image options",size=44){menu=true};Spacer(Modifier.width(10.dp));ChromeButton("close","Close image",size=44){commit();onClose()}}
        }
        if(menu)IosSheet("Image options",onDismiss={menu=false}){current?.let{p->SheetRow("Open note","edit"){commit();store.select(p.noteId);menu=false;onClose()};SheetRow("Fit image","images"){zoom=1f;pan=Offset.Zero;menu=false}}}
    }
}
