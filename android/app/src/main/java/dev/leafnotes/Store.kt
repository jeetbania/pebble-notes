package dev.leafnotes

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

object NativeStore {
    init { System.loadLibrary("leaf") }
    external fun open(path: ByteArray): Long
    external fun put(handle: Long, payload: ByteArray, remote: Boolean): Int
    external fun list(handle: Long, pending: Boolean, heads: Boolean): ByteArray?
    external fun error(handle: Long): ByteArray
    external fun ack(handle: Long, id: ByteArray): Int
    external fun close(handle: Long)
}
fun uid() = UUID.randomUUID().toString()
fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
data class Span(val start: Int, val length: Int, val kind: String) {
    fun json() = JSONObject().put("start", start).put("length", length).put("kind", kind)
}
data class Media(val id: String, val name: String, val mime: String) {
    fun json() = JSONObject().put("id", id).put("name", name).put("mime", mime)
}
data class Block(val id:String=uid(),val kind:String="text",val text:String="",val spans:List<Span> = emptyList(),val checked:Boolean=false,val indent:Int=0,val mediaId:String="",val caption:String="",val presentation:String="large",val cells:List<List<String>> = emptyList(),val parentId:String?=null,val collapsed:Boolean?=null) {
    val isText get()=kind in listOf("text","bullet","number","check","toggle")
    fun json()=JSONObject().put("id",id).put("kind",kind).put("text",text).put("spans",JSONArray(spans.map{it.json()})).put("checked",checked).put("indent",indent).put("mediaId",mediaId).put("caption",caption).put("presentation",presentation).put("cells",JSONArray(cells.map{JSONArray(it)})).put("parentId",parentId).put("collapsed",collapsed)
    companion object { fun from(o:JSONObject)=Block(o.getString("id"),o.getString("kind"),o.getString("text"),readSpans(o.getJSONArray("spans")),o.getBoolean("checked"),o.getInt("indent"),o.getString("mediaId"),o.getString("caption"),o.getString("presentation"),(0 until o.getJSONArray("cells").length()).map{r->val row=o.getJSONArray("cells").getJSONArray(r);(0 until row.length()).map{row.getString(it)}},o.optString("parentId").takeIf{it.isNotEmpty()},if(o.has("collapsed"))o.optBoolean("collapsed")else null) }
}
fun readSpans(array:JSONArray)=(0 until array.length()).map { val s=array.getJSONObject(it); Span(s.getInt("start"),s.getInt("length"),s.getString("kind")) }
fun clipSpans(spans:List<Span>,start:Int,length:Int)=spans.mapNotNull{val a=maxOf(it.start,start);val b=minOf(it.start+it.length,start+length);if(b>a)Span(a-start,b-a,it.kind) else null}
data class Note(val title:String="",val text:String="",val spans:List<Span> = emptyList(),val attachments:List<Media> = emptyList(),val collection:String="Personal",val pinned:Boolean=false,val archived:Boolean=false,val deleted:Boolean=false,val blocks:List<Block>?=null,val tags:List<String> = emptyList(),val recordType:String="note",val folderEmoji:String="",val folderImage:String="",val textScale:Double=1.0,val task:TaskDetails?=null,val deletedAt:Long?=null,val purgedAt:Long?=null) {
    val displayTitle get()=title.ifBlank{"Untitled note"}
    val document get()=blocks ?: if(recordType=="folder") emptyList() else listOf(Block(id="body",text=text,spans=spans))+attachments.map{Block(id="media-"+it.id,kind=if(it.mime.startsWith("image/"))"image" else "file",mediaId=it.id)}
    fun prepared(previous:Note?=null):Note {
        if(purgedAt!=null)return copy(title="",text="",spans=emptyList(),attachments=emptyList(),blocks=emptyList(),tags=emptyList(),task=null,folderEmoji="",folderImage="",deleted=true)
        val trashDate=if(deleted)deletedAt ?: System.currentTimeMillis() else null
        var content=document
        if(previous!=null && blocks==previous.blocks && (text!=previous.text || spans!=previous.spans) && blocks!=null && content.count{it.isText}==1) content=content.map{if(it.isText)it.copy(text=text,spans=spans) else it}
        if(previous!=null && blocks==previous.blocks && attachments!=previous.attachments){content=content.filter{it.kind !in listOf("image","file") || attachments.any{a->a.id==it.mediaId}};content=content+attachments.filter{a->content.none{it.mediaId==a.id}}.map{a->Block(kind=if(a.mime.startsWith("image/"))"image" else "file",mediaId=a.id)}}
        if(recordType=="note" && content.none{it.isText})content=content+Block()
        val cleanTags=tags.map{it.trim().removePrefix("#").lowercase(java.util.Locale.ROOT)}.filter{it.isNotEmpty()}.distinct().sorted()
        if(recordType=="folder")return copy(deletedAt=trashDate,blocks=content,tags=cleanTags)
        val plain=StringBuilder();val styles=mutableListOf<Span>();var number=0
        for(block in content){if(plain.isNotEmpty())plain.append('\n');val prefix=when(block.kind){"bullet"->"• ";"number"->"${++number}. ";"check"->if(block.checked)"☑ " else "☐ ";else->""};val start=plain.length+prefix.length;plain.append(prefix).append(if(block.kind=="table")block.cells.joinToString("\n"){it.joinToString(" | ")} else if(block.isText)block.text else block.caption);if(block.isText)styles+=block.spans.map{it.copy(start=start+it.start)}}
        return copy(deletedAt=trashDate,blocks=content,tags=cleanTags,text=plain.toString(),spans=styles,attachments=attachments.filter{a->content.any{it.mediaId==a.id}})
    }
    fun editBlock(id:String,change:(Block)->Block)=copy(blocks=document.map{if(it.id==id)change(it) else it}).prepared()
    fun insertMedia(media:List<Media>,at:String?,offset:Int?):Note {
        val content=document.toMutableList();var index=content.indexOfFirst{it.id==at}.let{if(it<0)content.size else it+1}
        if(offset!=null && index>0 && content[index-1].isText){val old=content[index-1];val point=offset.coerceIn(0,old.text.length);content[index-1]=old.copy(text=old.text.substring(0,point),spans=clipSpans(old.spans,0,point));content.add(index,old.copy(id=uid(),text=old.text.substring(point),spans=clipSpans(old.spans,point,old.text.length-point)))}
        media.forEach{content.add(index++,Block(kind=if(it.mime.startsWith("image/"))"image" else "file",mediaId=it.id))}
        return copy(blocks=content,attachments=(attachments+media).distinctBy{it.id}).prepared()
    }
    fun json()=JSONObject().put("title",title).put("text",text).put("spans",JSONArray(spans.map{it.json()})).put("attachments",JSONArray(attachments.map{it.json()})).put("collection",collection).put("pinned",pinned).put("archived",archived).put("deleted",deleted).put("blocks",blocks?.let{JSONArray(it.map{b->b.json()})}).put("tags",JSONArray(tags)).put("recordType",recordType).put("folderEmoji",folderEmoji).put("folderImage",folderImage).put("textScale",textScale).put("task",task?.json()).put("deletedAt",deletedAt).put("purgedAt",purgedAt)
    companion object {
        fun from(o:JSONObject):Note {
            val media=o.getJSONArray("attachments");val blocks=o.optJSONArray("blocks");val tags=o.optJSONArray("tags")
            return Note(o.getString("title"),o.getString("text"),readSpans(o.getJSONArray("spans")),(0 until media.length()).map{val m=media.getJSONObject(it);Media(m.getString("id"),m.getString("name"),m.getString("mime"))},o.getString("collection"),o.getBoolean("pinned"),o.getBoolean("archived"),o.getBoolean("deleted"),blocks?.let{(0 until it.length()).map{i->Block.from(it.getJSONObject(i))}},tags?.let{(0 until it.length()).map{i->it.getString(i)}} ?: emptyList(),o.optString("recordType","note"),o.optString("folderEmoji",""),o.optString("folderImage",""),o.optDouble("textScale",1.0).takeIf{it.isFinite()}?.coerceIn(.8,1.6) ?: 1.0,o.optJSONObject("task")?.let{TaskDetails.from(it)},o.optLong("deletedAt").takeIf{it>0},o.optLong("purgedAt").takeIf{it>0})
        }
    }
}
data class Revision(val raw: String) {
    val json = JSONObject(raw)
    val id = json.getString("id"); val noteId = json.getString("noteId"); val note = Note.from(json.getJSONObject("note")); val createdAt = json.getLong("createdAt")
}
object Libraries {
    private var instance: Store? = null
    @Synchronized fun get(context: Context) = instance ?: Store(context.applicationContext).also { instance = it }
}
class Store(val context: Context) {
    val root = File(context.filesDir, "LeafNotes").apply { mkdirs() }
    val media = File(root, "media").apply { mkdirs() }
    val preferences = context.getSharedPreferences("leaf", Context.MODE_PRIVATE)
    val deviceId = preferences.getString("deviceId", null) ?: uid().also { preferences.edit().putString("deviceId", it).apply() }
    private val db = NativeStore.open(File(root, "notes.sqlite").path.toByteArray(Charsets.UTF_8)).also { check(it != 0L) { "Could not open library" } }
    private val draftFile = AtomicFile(File(root, "pending-draft.json"))
    private val handler = Handler(Looper.getMainLooper())
    private val commit = Runnable { flush() }
    private var draft: JSONObject? = null
    var selected by mutableStateOf<String?>(null)
    var editing by mutableStateOf<Note?>(null)
    var heads by mutableStateOf<List<Revision>>(emptyList())
    var status by mutableStateOf("Saved on this phone")
    var error by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
    val allHeads get() = heads.groupBy { it.noteId }.values.map { group -> group.firstOrNull { !it.note.deleted } ?: group.first() }
    val visibleHeads get()=allHeads.filter{it.note.recordType=="note" && it.note.purgedAt==null}
    val folderHeads get()=allHeads.filter{it.note.recordType=="folder" && !it.note.deleted}
    val collections get()=(allHeads.filter{!it.note.deleted}.flatMap{r-> val parts=r.note.collection.split('/');parts.indices.map{parts.take(it+1).joinToString("/")}}+"Personal").distinct().sorted()
    var activeBlock:String?=null
    var insertionOffset:Int?=null
    private val undoNotes=mutableListOf<Note>();private val redoNotes=mutableListOf<Note>();private var lastUndoKey="";private var lastUndoTime=0L
    var canUndo by mutableStateOf(false);var canRedo by mutableStateOf(false)
    init {
        reload(); scheduleTrash(context)
        if (draftFile.baseFile.exists()) try { draft = JSONObject(String(draftFile.readFully(), Charsets.UTF_8)); selected = draft!!.getString("noteId"); editing = Note.from(draft!!.getJSONObject("note")); flush() } catch (e: Exception) { error = "Could not recover pending edit: ${e.message}" }
    }
    @Synchronized fun installStarterLibrary() {
        val marker=File(root,"starter-20261004.complete");if(marker.exists())return
        flush();check(draft==null){"Save pending edits before refreshing starter notes"}
        val records=JSONArray(context.assets.open("Starter/library.json").bufferedReader().use{it.readText()});val validator=NativeStore.open(":memory:".toByteArray());val assets=mutableMapOf<String,ByteArray>()
        try { for(i in 0 until records.length()){val raw=records.getJSONObject(i).toString();check(NativeStore.put(validator,raw.toByteArray(Charsets.UTF_8),false)!=0){"Invalid starter library"};Revision(raw).note.attachments.forEach{a->val bytes=context.assets.open("Starter/"+a.id).use{it.readBytes()};check(sha256(bytes)==a.id){"Starter image integrity check failed"};assets[a.id]=bytes} } } finally {NativeStore.close(validator)}
        if(revisions().isNotEmpty()){val file=AtomicFile(File(root,"before-starter-refresh.leafbackup"));val out=file.startWrite();try{out.write(backup());file.finishWrite(out)}catch(e:Exception){file.failWrite(out);throw e}}
        assets.forEach{(hash,bytes)->val a=AtomicFile(File(media,hash));val out=a.startWrite();try{out.write(bytes);a.finishWrite(out)}catch(e:Exception){a.failWrite(out);throw e}}
        val old=visibleHeads.filter{!it.note.deleted&&!it.noteId.startsWith("leaf-starter-")}.map{it.noteId}
        old.forEach{id->select(id);editing?.let{update(it.copy(deleted=true))};flush();check(draft==null){"Could not preserve previous note"}}
        for(i in 0 until records.length())ingest(records.getJSONObject(i).toString(),false)
        marker.writeText("Starter library refreshed; earlier notes remain in Trash and history.");select(null);reload();scheduleLocalBackup(context)
    }
    @Synchronized fun revisions(pending: Boolean = false, headsOnly: Boolean = false): List<Revision> {
        val bytes = NativeStore.list(db, pending, headsOnly) ?: error("Could not read library")
        val array = JSONArray(String(bytes, Charsets.UTF_8)); return (0 until array.length()).map { Revision(array.getJSONObject(it).toString()) }
    }
    @Synchronized fun ingest(raw: String, remote: Boolean) { check(NativeStore.put(db, raw.toByteArray(Charsets.UTF_8), remote) != 0) { String(NativeStore.error(db), Charsets.UTF_8) } }
    @Synchronized fun acknowledge(id: String) { check(NativeStore.ack(db, id.toByteArray(Charsets.UTF_8)) != 0) { "Could not save sync progress" } }
    @Synchronized fun reload() { heads = revisions(headsOnly = true); expireTrash(); scheduleTaskAlerts(this); if (draft == null && selected != null) editing = allHeads.firstOrNull { it.noteId == selected }?.note }
    @Synchronized fun select(id: String?) { flush(); undoNotes.clear();redoNotes.clear();canUndo=false;canRedo=false;activeBlock=null;insertionOffset=null; if (draft != null) return; selected = id; editing = allHeads.firstOrNull { it.noteId == id }?.note }
    @Synchronized fun create() { flush(); if (draft != null) return; selected = uid(); editing = Note().prepared(); undoNotes.clear();redoNotes.clear();canUndo=false;canRedo=false;activeBlock=null;insertionOffset=null; draft = JSONObject().put("noteId", selected).put("parents", JSONArray()).put("note", editing!!.json()); saveDraft(); flush() }
    @Synchronized fun update(value: Note, undoKey:String="", remember:Boolean=true) {
        val note=value.prepared(editing)
        val id = selected ?: return; if (editing == note) return
        if(remember){val now=System.currentTimeMillis();if(undoKey.isEmpty()||undoKey!=lastUndoKey||now-lastUndoTime>800)undoNotes.add(editing!!);if(undoNotes.size>100)undoNotes.removeAt(0);redoNotes.clear();lastUndoKey=undoKey;lastUndoTime=now;canUndo=undoNotes.isNotEmpty();canRedo=false}
        val parents = draft?.getJSONArray("parents") ?: JSONArray(listOfNotNull(allHeads.firstOrNull { it.noteId == id }?.id))
        draft = JSONObject().put("noteId", id).put("parents", parents).put("note", note.json()); editing = note; saveDraft(); handler.removeCallbacks(commit); handler.postDelayed(commit, 700)
    }
    @Synchronized fun undo() { val value=undoNotes.lastOrNull() ?: return; undoNotes.removeAt(undoNotes.lastIndex);editing?.let{redoNotes.add(it)};update(value,remember=false);canUndo=undoNotes.isNotEmpty();canRedo=true;lastUndoKey="" }
    @Synchronized fun redo() { val value=redoNotes.lastOrNull() ?: return;redoNotes.removeAt(redoNotes.lastIndex);editing?.let{undoNotes.add(it)};update(value,remember=false);canUndo=true;canRedo=redoNotes.isNotEmpty();lastUndoKey="" }
    @Synchronized fun editBlock(id:String,text:String,spans:List<Span>) { editing?.let{update(it.editBlock(id){b->b.copy(text=text,spans=spans)},"typing:"+id)} }
    @Synchronized fun changeBlock(id:String,change:(Block)->Block) { editing?.let{update(it.editBlock(id,change))} }
    @Synchronized fun setList(kind:String) { val id=activeBlock ?: editing?.document?.firstOrNull{it.isText}?.id ?: return; changeBlock(id){it.copy(kind=if(it.kind==kind)"text" else kind)} }
    @Synchronized fun addBlock(kind:String) { editing?.let{n->val b=Block(kind=kind,cells=if(kind=="table")listOf(listOf("",""),listOf("",""))else emptyList());val blocks=n.document.toMutableList();var anchor=activeBlock;while(n.document.firstOrNull{it.id==anchor}?.parentId!=null){anchor=n.document.first{it.id==anchor}.parentId};val ids=anchor?.let{n.descendants(it)}?:emptySet();val pos=blocks.indexOfLast{it.id in ids};blocks.add(if(pos<0)blocks.size else pos+1,b);update(n.copy(blocks=blocks));activeBlock=b.id} }
    @Synchronized fun addChild(id:String) { editing?.let{n->val child=Block(parentId=id);val ids=n.descendants(id);val content=n.document.map{if(it.id==id)it.copy(collapsed=false)else it}.toMutableList();content.add(content.indexOfLast{it.id in ids}+1,child);update(n.copy(blocks=content));activeBlock=child.id} }
    @Synchronized fun moveBlock(id:String,delta:Int) { editing?.let{n->val source=n.document.firstOrNull{it.id==id}?:return;val peers=n.document.filter{it.parentId==source.parentId};val i=peers.indexOfFirst{it.id==id};val j=(i+delta).coerceIn(0,peers.lastIndex);if(i!=j){val ids=n.descendants(id);val group=n.document.filter{it.id in ids};val content=n.document.filter{it.id !in ids}.toMutableList();val target=if(delta<0)content.indexOfFirst{it.id==peers[j].id}else content.indexOfLast{it.id in n.descendants(peers[j].id)}+1;content.addAll(target,group);update(n.copy(blocks=content))}} }
    @Synchronized fun removeBlock(id:String) { editing?.let{n->val ids=n.descendants(id);update(n.copy(blocks=n.document.filter{it.id !in ids}))} }
    @Synchronized fun duplicateBlock(id:String) { editing?.let{n->val ids=n.descendants(id);val content=n.document.toMutableList();val group=content.filter{it.id in ids};val map=group.associate{it.id to uid()};val copies=group.map{it.copy(id=map[it.id]!!,parentId=map[it.parentId] ?: it.parentId)};content.addAll(content.indexOfLast{it.id in ids}+1,copies);update(n.copy(blocks=content))} }
    @Synchronized fun permanentlyDelete(ids:List<String>) {
        flush()
        ids.forEach{id->val old=visibleHeads.firstOrNull{it.noteId==id && it.note.deleted}?:return@forEach;val now=System.currentTimeMillis();val marker=Note(collection=old.note.collection,deleted=true,deletedAt=old.note.deletedAt ?: old.createdAt,purgedAt=now).prepared();ingest(JSONObject().put("schema",2).put("id",uid()).put("noteId",id).put("deviceId",deviceId).put("parents",JSONArray()).put("createdAt",now).put("note",marker.json()).toString(),false);if(selected==id){selected=null;editing=null}}
        heads=revisions(headsOnly=true)
        val referenced=revisions().flatMap{it.note.attachments.map{a->a.id}}.toSet();media.listFiles()?.filter{it.name !in referenced}?.forEach{it.delete()}
    }
    fun expireTrash(now:Long=System.currentTimeMillis()) { val ids=visibleHeads.filter{it.note.deleted && now-(it.note.deletedAt ?: it.createdAt)>=30L*86400000}.map{it.noteId};if(ids.isNotEmpty())permanentlyDelete(ids) }
    @Synchronized fun createFolder(path:String,emoji:String="") { val name=path.split('/').map{it.trim()}.filter{it.isNotEmpty()}.joinToString("/");if(name.isBlank())return;flush();if(draft!=null)return;val previous=selected;val id=folderHeads.firstOrNull{it.note.collection==name}?.noteId ?: "folder-"+sha256(name.toByteArray());select(id);val note=Note(title=name,collection=name,blocks=emptyList(),recordType="folder",folderEmoji=emoji).prepared();draft=JSONObject().put("noteId",id).put("parents",JSONArray(listOfNotNull(allHeads.firstOrNull{it.noteId==id}?.id))).put("note",note.json());editing=note;saveDraft();flush();select(previous) }
    @Synchronized fun deleteFolder(path:String) {if(path=="Personal")return;flush();if(draft!=null)return;val previous=selected;allHeads.filter{it.note.collection==path||it.note.collection.startsWith(path+"/")}.forEach{r->select(r.noteId);update(if(r.note.recordType=="folder")r.note.copy(deleted=true) else r.note.copy(collection="Personal"));flush()};select(previous)}
    @Synchronized fun renameFolder(old:String,name:String){val previous=selected;val clean=name.trim('/',' ');if(clean.isEmpty())return;allHeads.filter{it.note.collection==old||it.note.collection.startsWith(old+"/")}.forEach{r->select(r.noteId);val next=clean+r.note.collection.removePrefix(old);update(r.note.copy(collection=next,title=if(r.note.recordType=="folder")next else r.note.title));flush()};select(previous)}
    @Synchronized fun splitList(id:String,position:Int):String? { val n=editing?:return null;val blocks=n.document.toMutableList();val i=blocks.indexOfFirst{it.id==id};if(i<0)return null;val b=blocks[i];if(b.text.isEmpty()){changeBlock(id){it.copy(kind="text")};return id};val p=position.coerceIn(0,b.text.length);val next=b.copy(id=uid(),text=b.text.substring(p),spans=clipSpans(b.spans,p,b.text.length-p),checked=false);blocks[i]=b.copy(text=b.text.substring(0,p),spans=clipSpans(b.spans,0,p));blocks.add(i+1,next);update(n.copy(blocks=blocks));activeBlock=next.id;return next.id }
    private fun saveDraft() {
        var stream: java.io.FileOutputStream? = null
        try { stream = draftFile.startWrite(); stream.write(draft.toString().toByteArray(Charsets.UTF_8)); draftFile.finishWrite(stream); status = "Saved on this phone" }
        catch (e: Exception) { if (stream != null) draftFile.failWrite(stream); error = "Saving needs attention: ${e.message}"; status = "Save needs attention" }
    }
    @Synchronized fun flush() {
        handler.removeCallbacks(commit); val d = draft ?: return
        try {
            d.put("note", Note.from(d.getJSONObject("note")).prepared().json())
            val r = JSONObject().put("schema", 2).put("id", uid()).put("noteId", d.getString("noteId")).put("deviceId", deviceId).put("parents", d.getJSONArray("parents")).put("createdAt", System.currentTimeMillis()).put("note", d.getJSONObject("note"))
            ingest(r.toString(), false); draftFile.delete(); draft = null; reload(); scheduleLocalBackup(context)
            if(preferences.getBoolean("connected", false)) scheduleSync(context)
        } catch (e: Exception) { error = "Local saving failed: ${e.message}" }
    }
    @Synchronized fun resolve(r: Revision) {
        flush(); if (draft != null) return
        selected = r.noteId; editing = r.note
        draft = JSONObject().put("noteId", r.noteId).put("parents", JSONArray(heads.filter { it.noteId == r.noteId }.map { it.id })).put("note", r.note.json()); saveDraft(); flush()
    }
    @Synchronized fun stageAttachment(uri: Uri): Media {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Could not read image")
        val mime=context.contentResolver.getType(uri) ?: "application/octet-stream"
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }; android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        check(!mime.startsWith("image/") || bounds.outWidth > 0) { "Unsupported image" }
        val hash = sha256(bytes); val file = File(media, hash); if (!file.exists()) AtomicFile(file).let { a -> val stream = a.startWrite(); try { stream.write(bytes); a.finishWrite(stream) } catch (e: Exception) { a.failWrite(stream); throw e } }
        val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if(c.moveToFirst()) c.getString(0) else null } ?: "Image"
        return Media(hash,name,mime)
    }
    @Synchronized fun attach(uri: Uri) {
        val media=stageAttachment(uri)
        if (selected == null) create(); val n = editing ?: error("Open a note first")
        update(n.insertMedia(listOf(media),activeBlock,insertionOffset)); insertionOffset=null; flush()
    }
    @Synchronized fun backup(): ByteArray {
        flush(); val records = revisions(); val files = JSONObject()
        records.flatMap { it.note.attachments }.distinctBy { it.id }.forEach { a -> files.put(a.id, android.util.Base64.encodeToString(File(media, a.id).readBytes(), android.util.Base64.NO_WRAP)) }
        return JSONObject().put("format", "leaf-backup-1").put("revisions", JSONArray(records.map { it.json })).put("media", files).toString().toByteArray(Charsets.UTF_8)
    }
    @Synchronized fun restore(bytes: ByteArray) {
        flush(); val backup = JSONObject(String(bytes, Charsets.UTF_8)); check(backup.getString("format") == "leaf-backup-1") { "Unsupported backup" }
        val records = backup.getJSONArray("revisions"); val files = backup.getJSONObject("media")
        val validator = NativeStore.open(":memory:".toByteArray())
        try {
        // Isolated validation includes existing revisions so ID collisions cannot partially import notes.
        for (r in revisions()) check(NativeStore.put(validator, r.raw.toByteArray(Charsets.UTF_8), false) != 0)
        for (i in 0 until records.length()) {
            val raw = records.getJSONObject(i).toString(); check(NativeStore.put(validator, raw.toByteArray(Charsets.UTF_8), false) != 0) { String(NativeStore.error(validator), Charsets.UTF_8) }
            for(a in Revision(raw).note.attachments) check(sha256(android.util.Base64.decode(files.getString(a.id), android.util.Base64.DEFAULT)) == a.id) { "Backup has damaged or missing images" }
        }
        for(hash in files.keys()) { check(hash.matches(Regex("[a-f0-9]{64}"))) { "Invalid image identifier" }; val data = android.util.Base64.decode(files.getString(hash), android.util.Base64.DEFAULT); check(sha256(data) == hash) { "Damaged backup image" }; val a = AtomicFile(File(media, hash)); val out = a.startWrite(); try { out.write(data); a.finishWrite(out) } catch(e: Exception) { a.failWrite(out); throw e } }
        for(i in 0 until records.length()) ingest(records.getJSONObject(i).toString(), false)
        reload(); status = "Backup imported"
        } finally { NativeStore.close(validator) }
    }
}

fun Note.descendants(id:String):Set<String> { val ids=mutableSetOf(id);repeat(2000){val before=ids.size;val children=document.filter{it.parentId in ids}.map{it.id};ids.addAll(children);if(ids.size==before)return ids};return ids }
fun Note.blockVisible(block:Block,dragging:String?=null):Boolean { var parent=block.parentId;val seen=mutableSetOf<String>();while(parent!=null && seen.add(parent)){val b=document.firstOrNull{it.id==parent}?:break;if(b.collapsed==true || dragging==b.id)return false;parent=b.parentId};return true }
fun Note.depth(block:Block):Int {var parent=block.parentId;val seen=mutableSetOf<String>();while(parent!=null && seen.size<8 && seen.add(parent)){parent=document.firstOrNull{it.id==parent}?.parentId};return seen.size}
