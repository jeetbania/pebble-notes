package dev.leafnotes

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.text.*
import android.text.style.*
import android.graphics.Typeface
import android.graphics.BitmapFactory
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.google.android.gms.auth.api.identity.Identity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import java.io.File

class MainActivity : ComponentActivity() {
    private lateinit var store: Store
    private val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val resolution = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        try { val data = result.data ?: error("Google sign-in cancelled"); val auth = Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(data); connected(auth.accessToken ?: error("No Google access returned")) } catch(e: Exception) { store.error = "Google setup is needed: ${e.message}" }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window,false); store = Libraries.get(this)
        try { store.installStarterLibrary() } catch(e:Exception){store.error="Starter refresh needs attention: ${e.message}"}
        scheduleLocalBackup(this)
        scheduleUpdates(this)
        if(intent.getBooleanExtra("checkUpdates",false))lifecycleScope.launch { PebbleUpdater.get(this@MainActivity).check(manual=true) }
        handleShared(intent)
        intent.getStringExtra("taskId")?.let{store.select(it)}
        setContent { LeafTheme(store) { LeafLibrary(store, connect = { connect() }) } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); if(intent.getBooleanExtra("checkUpdates",false))lifecycleScope.launch {PebbleUpdater.get(this@MainActivity).check(manual=true)}; handleShared(intent); intent.getStringExtra("taskId")?.let{store.select(it)} }
    private fun handleShared(intent: Intent) {
        if(intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE) {
            store.create()
            try {
                val items = if(intent.action == Intent.ACTION_SEND_MULTIPLE) intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM) ?: arrayListOf() else arrayListOf<Uri>().apply { intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { add(it) } }
                intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.let{body->store.editing?.let{store.update(it.copy(title=intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: "",text=body.toString()))}}
                executor.execute { try { items.forEach { store.attach(it) } } catch(e:Exception){store.error=e.message} }
            } catch(e: Exception) { store.error = e.message }
        }
    }
    override fun onPause() { store.flush(); if(store.preferences.getBoolean("connected", false)) scheduleSync(this); super.onPause() }
    override fun onResume() { super.onResume(); if(::store.isInitialized && store.preferences.getBoolean("connected", false)) scheduleSync(this) }
    private fun connect() {
        Identity.getAuthorizationClient(this).authorize(authorizationRequest()).addOnSuccessListener { auth ->
            if(auth.hasResolution()) resolution.launch(IntentSenderRequest.Builder(auth.pendingIntent!!.intentSender).build())
            else connected(auth.accessToken ?: run { store.error = "Google did not return access"; return@addOnSuccessListener })
        }.addOnFailureListener { store.error = "Google connection needs setup. Register dev.leafnotes and its signing fingerprint in your Google Cloud project. ${it.message}" }
    }
    private fun connected(token: String) {
        store.preferences.edit().putBoolean("connected", true).apply()
        executor.execute { try { SyncEngine.run(store, token); scheduleSync(this) } catch(e: Exception) { runOnUiThread { store.error = e.message } } }
    }
}
fun attributed(note: Note, metrics: TextMetrics = TextMetrics()): SpannableString {
    val s = SpannableString(note.text)
    for(span in note.spans) {
        if(span.start < 0 || span.length <= 0 || span.start > s.length || span.length > s.length - span.start) continue
        val style: Any = when(span.kind) { "bold" -> StyleSpan(Typeface.BOLD); "italic" -> StyleSpan(Typeface.ITALIC); "highlight" -> android.text.style.BackgroundColorSpan(0x55FFCC00); "strike" -> StrikethroughSpan(); "underline" -> UnderlineSpan(); "title", "subtitle", "headline" -> SemanticSize(span.kind,metrics.size(span.kind)); else -> if(span.kind.startsWith("link:")) android.text.style.URLSpan(span.kind.removePrefix("link:")) else continue }
        s.setSpan(style, span.start, span.start + span.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }; return s
}
fun spansFrom(s: Spanned): List<Span> {
    val spans = mutableListOf<Span>()
    s.getSpans(0, s.length, Any::class.java).forEach { value ->
        val kinds = when(value) { is StyleSpan -> when(value.style) { Typeface.BOLD -> listOf("bold"); Typeface.ITALIC -> listOf("italic"); Typeface.BOLD_ITALIC -> listOf("bold", "italic"); else -> emptyList() }; is android.text.style.BackgroundColorSpan -> listOf("highlight"); is UnderlineSpan -> listOf("underline"); is CompletionStrike -> emptyList(); is StrikethroughSpan -> listOf("strike"); is SemanticSize -> listOf(value.kind); is android.text.style.AbsoluteSizeSpan -> listOf(when(value.size){30->"title";22->"subtitle";else->"headline"}); is android.text.style.URLSpan -> listOf("link:"+value.url); else -> emptyList() }
        for(kind in kinds) { val start = s.getSpanStart(value); val length = s.getSpanEnd(value) - start; if(start >= 0 && length > 0) spans += Span(start, length, kind) }
    }; return canonicalSpans(spans)
}
val typingStyles=java.util.WeakHashMap<EditText,MutableSet<String>>()
fun applyTypingStyle(view:EditText,start:Int,count:Int) { val styles=typingStyles[view] ?: return;if(count<=0)return;val spans=styles.map{Span(start,count,it)};val styled=attributed(Note(text=view.text.toString(),spans=spans), editorMetrics[view] ?: TextMetrics());styled.getSpans(start,start+count,Any::class.java).forEach{view.text.setSpan(it,start,start+count,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)} }
fun format(view: EditText, kind: String) {
    val text=view.text.toString();val cursor=view.selectionStart.coerceAtLeast(0);var start=cursor;var end=view.selectionEnd.coerceAtLeast(cursor)
    if(end==start && (kind !in listOf("title","subtitle","headline","body") || text.isEmpty())){val styles=typingStyles.getOrPut(view){mutableSetOf()};if(kind in listOf("title","subtitle","headline","body"))styles.removeAll(listOf("title","subtitle","headline","body"));if(kind!="body"){if(!styles.add(kind))styles.remove(kind)};return}
    if(end==start){start=if(cursor==0)0 else text.lastIndexOf('\n',cursor-1).let{if(it<0)0 else it+1};end=text.indexOf('\n',cursor).let{if(it<0)text.length else it}}
    if(end<=start)return
    val headings=listOf("title","subtitle","headline","body");val old=spansFrom(view.text);val remove=if(kind in headings)headings else listOf(kind)
    val exists=old.any{it.kind==kind&&it.start<end&&it.start+it.length>start}
    val result=old.flatMap{span->if(span.kind !in remove || span.start>=end || span.start+span.length<=start)listOf(span) else buildList{if(span.start<start)add(span.copy(length=start-span.start));if(span.start+span.length>end)add(span.copy(start=end,length=span.start+span.length-end))}}.toMutableList()
    if(kind!="body" && (kind in headings || !exists))result+=Span(start,end-start,kind)
    view.tag=true;view.setText(attributed(Note(text=text,spans=result), editorMetrics[view] ?: TextMetrics()));view.setSelection(start,end);view.tag=false
}

fun canonicalSpans(spans: List<Span>): List<Span> {
    val result = mutableListOf<Span>()
    for(kind in spans.map{it.kind}.distinct().sorted()) {
        val merged = mutableListOf<Span>()
        for(span in spans.filter { it.kind == kind && it.length > 0 }.sortedBy { it.start }) {
            val last = merged.lastOrNull()
            if(last != null && span.start <= last.start + last.length) {
                merged[merged.lastIndex] = last.copy(length = maxOf(last.start + last.length, span.start + span.length) - last.start)
            } else merged += span
        }
        result += merged
    }
    return result.sortedWith(compareBy<Span> { it.start }.thenBy { it.length }.thenBy { it.kind })
}

class CompletionStrike: android.text.style.StrikethroughSpan()
