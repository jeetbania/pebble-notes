package dev.leafnotes

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import java.net.URL
import java.net.HttpURLConnection

data class ClipboardValue(val text:String="",val uri:Uri?=null,val mime:String="",val sensitive:Boolean=false) {
    val link get()=text.trim().takeIf{Uri.parse(it).let{u->u.scheme in listOf("http","https") && !u.host.isNullOrBlank()}}
    val fingerprint get()=sha256((text+"|"+uri).toByteArray())
}
data class ClipPreview(val title:String="",val bitmap:Bitmap?=null,val imageBytes:ByteArray?=null)
fun clipboardValue(context:Context):ClipboardValue? {
    val manager=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val description=manager.primaryClipDescription ?: return null
    if(description.extras?.getBoolean("android.content.extra.IS_SENSITIVE",false)==true)return null
    val clip=manager.primaryClip ?: return null; if(clip.itemCount==0)return null
    val first=clip.getItemAt(0);val uri=first.uri;val mime=uri?.let{context.contentResolver.getType(it)} ?: ""
    if(uri!=null && mime.startsWith("image/"))return ClipboardValue(uri=uri,mime=mime)
    val text=first.text?.toString()?.trim() ?: uri?.takeIf{it.scheme in listOf("http","https")}?.toString() ?: return null
    return text.takeIf{it.isNotBlank() && it.toByteArray().size<=2_000_000}?.let{ClipboardValue(text=it)}
}
fun readLimited(input:java.io.InputStream,limit:Int):ByteArray {val result=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(result.size()<limit){val n=input.read(buffer,0,minOf(buffer.size,limit-result.size()));if(n<0)break;result.write(buffer,0,n)};return result.toByteArray()}
fun limitedDownload(url:URL,limit:Int):ByteArray {
    check(url.protocol in listOf("http","https"))
    val connection=url.openConnection() as HttpURLConnection
    connection.connectTimeout=4000;connection.readTimeout=4000;connection.setRequestProperty("User-Agent","LeafNotes/0.5 Link Preview")
    try {check(connection.responseCode in 200..299);return connection.inputStream.use{readLimited(it,limit)}}finally{connection.disconnect()}
}
fun linkPreview(address:String):ClipPreview {
    return try {
        val url=URL(address);val html=limitedDownload(url,256_000).toString(Charsets.UTF_8)
        fun decode(value:String)=android.text.Html.fromHtml(value,android.text.Html.FROM_HTML_MODE_LEGACY).toString()
        val metas=Regex("<meta\\b[^>]*>",RegexOption.IGNORE_CASE).findAll(html).map{it.value}.toList()
        fun property(name:String):String?=metas.firstOrNull{Regex("(?:property|name)\\s*=\\s*['\"]"+Regex.escape(name)+"['\"]",RegexOption.IGNORE_CASE).containsMatchIn(it)}?.let{Regex("content\\s*=\\s*(['\"])(.*?)\\1",setOf(RegexOption.IGNORE_CASE,RegexOption.DOT_MATCHES_ALL)).find(it)?.groupValues?.get(2)}?.let(::decode)
        val title=property("og:title") ?: Regex("<title[^>]*>(.*?)</title>",setOf(RegexOption.IGNORE_CASE,RegexOption.DOT_MATCHES_ALL)).find(html)?.groupValues?.get(1)?.let(::decode) ?: url.host
        val bitmap=property("og:image")?.let{image->val bytes=limitedDownload(URL(url,image),2_000_000);decodeClipImage(bytes)}
        ClipPreview(title.take(200),bitmap)
    }catch(e:Exception){ClipPreview()}
}
fun decodeClipImage(bytes:ByteArray):Bitmap? {
    val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
    var sample=1;while(bounds.outWidth/sample>400 || bounds.outHeight/sample>400)sample*=2
    return BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply{inSampleSize=sample})
}
@Composable fun ClipboardSuggestion(store:Store,modifier:Modifier=Modifier) {
    val preferenceVersion=LocalPreferencesVersion.current;val c=LocalLeafColors.current;val focused=LocalWindowInfo.current.isWindowFocused;val scope=rememberCoroutineScope();val calm=LocalCalmMotion.current
    var candidate by remember{mutableStateOf<ClipboardValue?>(null)};var seen by remember{mutableStateOf("")};var preview by remember{mutableStateOf(ClipPreview())};var saving by remember{mutableStateOf(false)}
    val enabled=store.preferences.getBoolean("clipboardSuggestions",true)
    LaunchedEffect(focused,enabled){
        if(!enabled){candidate=null;return@LaunchedEffect}
        if(focused){val value=try{clipboardValue(store.context)}catch(e:Exception){null};if(value!=null && value.fingerprint!=seen){seen=value.fingerprint;candidate=value;preview=ClipPreview();preview=withContext(Dispatchers.IO){if(value.uri!=null)try{val bytes=store.context.contentResolver.openInputStream(value.uri)?.use{readLimited(it,32_000_001)};if(bytes!=null && bytes.size<=32_000_000){val bitmap=decodeClipImage(bytes);if(bitmap!=null)ClipPreview("Copied image",bitmap,bytes)else ClipPreview("Image unavailable")}else ClipPreview("Image too large to preview")}catch(e:Exception){ClipPreview("Image unavailable")}else value.link?.let(::linkPreview) ?: ClipPreview("Copied text")}}}
    }
    LaunchedEffect(candidate?.fingerprint){val value=candidate; if(value!=null){delay(5000);if(candidate?.fingerprint==value.fingerprint&&!saving)candidate=null}}
    AnimatedVisibility(candidate!=null,modifier,enter=fadeIn(tween(if(calm)0 else 180))+slideInVertically(spring(dampingRatio=1f,stiffness=550f)){if(calm)0 else 20},exit=fadeOut(tween(if(calm)0 else 150))) {
        Column(Modifier.padding(horizontal=16.dp).padding(bottom=80.dp).widthIn(max=290.dp).shadow(18.dp,RoundedCornerShape(22.dp)).background(c.paper,RoundedCornerShape(22.dp)).border(.5.dp,c.separator,RoundedCornerShape(22.dp)).padding(12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically){Label("Clipboard",16,androidx.compose.ui.text.font.FontWeight.SemiBold,modifier=Modifier.weight(1f));Pressable(Modifier.size(44.dp),"Dismiss suggestion",onClick={candidate=null}){Glyph("close",size=18)}}
            Row(Modifier.padding(top=6.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                preview.bitmap?.let{Image(it.asImageBitmap(),"Clipboard preview",Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)),contentScale=ContentScale.Crop)}
                Column(Modifier.weight(1f)){Label(preview.title.ifBlank{if(candidate?.uri!=null)"Copied image" else if(candidate?.link!=null)"Copied link" else "Copied text"},14,lines=1);Label(candidate?.text ?: "",13,color=c.secondary,lines=1)}
            }
            Pressable(Modifier.align(Alignment.End).heightIn(min=44.dp).padding(top=6.dp),"Save clipboard as note",enabled=!saving && (candidate?.uri==null || preview.imageBytes!=null),onClick={
                val value=candidate ?: return@Pressable;val snapshot=preview;saving=true
                scope.launch{try{
                    val media=if(value.uri!=null){val bytes=snapshot.imageBytes ?: error("Image unavailable");withContext(Dispatchers.IO){val hash=sha256(bytes);val file=java.io.File(store.media,hash);if(!file.exists())file.writeBytes(bytes);Media(hash,"Copied image",value.mime)}}else null
                    store.create();val note=store.editing ?: error("Could not create note")
                    if(media!=null)store.update(note.copy(title="Copied image").insertMedia(listOf(media),null,null))else store.update(note.copy(title=snapshot.title.takeIf{value.link!=null && it.isNotBlank()} ?: value.link?.let{Uri.parse(it).host} ?: value.text.lineSequence().first().take(70),text=value.text,spans=value.link?.let{listOf(Span(0,value.text.length,"link:"+it))} ?: emptyList()))
                    store.flush();candidate=null
                }catch(e:Exception){store.error=e.message}finally{saving=false}}
            }){Label(if(saving)"Saving…" else "Save",15,androidx.compose.ui.text.font.FontWeight.SemiBold,c.accent)}
        }
    }
}
