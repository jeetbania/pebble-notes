package dev.leafnotes

import android.graphics.BitmapFactory
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import java.io.File
import org.json.JSONArray

val LocalNoteMedia=staticCompositionLocalOf<File?>{null}
data class Wallpaper(val id:String,val name:String)
// Decode on a worker and share bounded thumbnails across recycled cards.
private val wallpaperBitmaps=object:android.util.LruCache<String,android.graphics.Bitmap>(24*1024*1024){
    override fun sizeOf(key:String,value:android.graphics.Bitmap)=value.allocationByteCount
}
private val wallpaperDecodeLock=kotlinx.coroutines.sync.Mutex()
fun Modifier.wallpaper(style:NoteStyle,maxSide:Int=1600):Modifier=composed {
    val context=LocalContext.current;val media=LocalNoteMedia.current;val id=style.backdropImage
    val key="${media?.path}:$id:$maxSide:${style.blurImage}"
    val bitmap by produceState<android.graphics.Bitmap?>(wallpaperBitmaps.get(key),key){
        value=wallpaperBitmaps.get(key)
        if(id!=null)value=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){
            wallpaperDecodeLock.lock()
            try{wallpaperBitmaps.get(key)?:run {
                val f=media?.let{File(it,id)};val bytes=if(f?.exists()==true)f.readBytes()else context.assets.open("wallpapers/$id.webp").use{it.readBytes()}
                val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
                var sample=1;while(maxOf(bounds.outWidth,bounds.outHeight)/sample>maxSide)sample*=2
                BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply{inSampleSize=sample})?.let{decoded->
                    val result=if(style.blurImage==true)android.graphics.Bitmap.createScaledBitmap(decoded,36,(36f*decoded.height/decoded.width).toInt().coerceAtLeast(1),true)else decoded
                    if(result!==decoded)decoded.recycle()
                    wallpaperBitmaps.put(key,result);result
                }
            }}catch(e:Exception){null}finally{wallpaperDecodeLock.unlock()}
        }
    }
    if(bitmap==null)this else paint(BitmapPainter(bitmap!!.asImageBitmap(),filterQuality=FilterQuality.Medium),sizeToIntrinsics=false,contentScale=ContentScale.Crop)
}

fun Store.useBackdrop(bytes:ByteArray,name:String){val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size)?:error("Choose a supported image");val tone=prominentWallpaperColour(bitmap);bitmap.recycle();val id=sha256(bytes);val f=File(media,id);if(!f.exists()){val atomic=android.util.AtomicFile(f);val out=atomic.startWrite();try{out.write(bytes);atomic.finishWrite(out)}catch(e:Exception){atomic.failWrite(out);throw e}};editing?.let{n->update(n.copy(style=(n.style?:NoteStyle()).copy(backdrop=null,backdropEnd=null,backdropImage=id).lightDocument(tone,when(preferences.getString("appearance","system")){"dark"->true;"light"->false;else->context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK==android.content.res.Configuration.UI_MODE_NIGHT_YES}),attachments=n.attachments+Media(id,name,"application/x-pebble-backdrop")),"noteStyle");flush()}}
fun Store.uploadBackdrop(uri:android.net.Uri){val staged=stageAttachment(uri);val bytes=File(media,staged.id).readBytes();useBackdrop(bytes,staged.name)}

// Quantised colour populations favour the wallpaper's prominent tone instead
// of blending contrasting colours into an unrelated average.
fun prominentWallpaperColour(bitmap:android.graphics.Bitmap):Color {
    val sample=android.graphics.Bitmap.createScaledBitmap(bitmap,48,48,true)
    val counts=IntArray(512);val red=IntArray(512);val green=IntArray(512);val blue=IntArray(512)
    for(y in 0 until sample.height)for(x in 0 until sample.width){val p=sample.getPixel(x,y);if(android.graphics.Color.alpha(p)<128)continue
        val r=android.graphics.Color.red(p);val g=android.graphics.Color.green(p);val b=android.graphics.Color.blue(p);val bin=((r shr 5) shl 6)or((g shr 5)shl 3)or(b shr 5)
        counts[bin]++;red[bin]+=r;green[bin]+=g;blue[bin]+=b
    }
    val winner=counts.indices.maxByOrNull{counts[it]}?:0;val count=counts[winner]
    return if(count==0)Color.Transparent else Color(android.graphics.Color.rgb(red[winner]/count,green[winner]/count,blue[winner]/count))
}
private val wallpaperTones=android.util.LruCache<String,Color>(24)
@Composable fun noteEdgeColour(style:NoteStyle,c:LeafColors,top:Boolean=true,media:File?=LocalNoteMedia.current):Color {
    val context=LocalContext.current
    val tone=remember(style.backdropImage,media){style.backdropImage?.let{id->wallpaperTones.get(id)?:try{
        val file=media?.let{File(it,id)};val bytes=if(file?.exists()==true)file.readBytes()else context.assets.open("wallpapers/$id.webp").use{it.readBytes()}
        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        var scale=1;while(maxOf(bounds.outWidth,bounds.outHeight)/scale>96)scale*=2
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply{inSampleSize=scale})?.let{bitmap->prominentWallpaperColour(bitmap).also{if(it!=Color.Transparent)wallpaperTones.put(id,it);bitmap.recycle()}}
    }catch(e:Exception){null}}}
    val first=hexColour(style.backdrop)?:style.paper(c);val last=hexColour(style.backdropEnd?:style.backdrop)?:style.paper(c)
    return tone?.takeIf{it!=Color.Transparent}?:if(top != (style.gradientDirection=="up"))first else last
}
