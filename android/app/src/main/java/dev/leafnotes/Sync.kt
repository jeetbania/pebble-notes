package dev.leafnotes

import android.content.Context
import androidx.work.*
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import android.util.AtomicFile

val googleScopes = listOf(Scope("https://www.googleapis.com/auth/drive.appdata"), Scope("openid"), Scope("email"))
fun authorizationRequest() = AuthorizationRequest.builder().setRequestedScopes(googleScopes).build()
data class DriveFile(val id: String, val name: String)
class HttpFailure(val status: Int) : Exception(if(status == 401) "Google sign-in needs reconnecting" else "Google request failed ($status). Notes are saved locally.")
class Drive(val token: String) {
    fun request(url: String, body: ByteArray? = null, type: String = "application/json"): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 30000; c.readTimeout = 45000; c.setRequestProperty("Authorization", "Bearer $token")
            if(body != null) { c.requestMethod = "POST"; c.doOutput = true; c.setRequestProperty("Content-Type", type); c.outputStream.use { it.write(body) } }
            if(c.responseCode !in 200..299) throw HttpFailure(c.responseCode)
            return c.inputStream.use { it.readBytes() }
        } finally { c.disconnect() }
    }
    fun files(): List<DriveFile> {
        val result = mutableListOf<DriveFile>(); var page: String? = null
        do {
            val query = mutableMapOf("spaces" to "appDataFolder", "q" to "trashed=false and appProperties has { key='leafApp' and value='1' }", "pageSize" to "1000", "fields" to "nextPageToken,files(id,name)")
            page?.let { query["pageToken"] = it }
            val url = "https://www.googleapis.com/drive/v3/files?" + query.map { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }.joinToString("&")
            val o = JSONObject(String(request(url), Charsets.UTF_8)); page = o.optString("nextPageToken").takeIf { it.isNotEmpty() }
            val files = o.getJSONArray("files"); for(i in 0 until files.length()) { val f = files.getJSONObject(i); result += DriveFile(f.getString("id"), f.getString("name")) }
        } while(page != null)
        return result
    }
    fun download(id: String) = request("https://www.googleapis.com/drive/v3/files/$id?alt=media")
    fun remove(id:String) {val c=URL("https://www.googleapis.com/drive/v3/files/$id").openConnection() as HttpURLConnection;try{c.connectTimeout=30000;c.readTimeout=45000;c.setRequestProperty("Authorization","Bearer $token");c.requestMethod="DELETE";if(c.responseCode !in listOf(204,404))throw HttpFailure(c.responseCode)}finally{c.disconnect()}}
    fun upload(name: String, bytes: ByteArray, mime: String) {
        val boundary = "leaf-${uid()}"
        val metadata = JSONObject().put("name", name).put("parents", org.json.JSONArray(listOf("appDataFolder"))).put("appProperties", JSONObject().put("leafApp", "1"))
        val start = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n--$boundary\r\nContent-Type: $mime\r\n\r\n".toByteArray(Charsets.UTF_8)
        request("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id", start + bytes + "\r\n--$boundary--\r\n".toByteArray(), "multipart/related; boundary=$boundary")
    }
}
object SyncEngine {
    fun run(store: Store, token: String) {
        synchronized(store) { if(store.busy) return; store.flush(); store.busy = true }
        try {
            store.status = "Syncing with Google Drive…"
            val drive = Drive(token)
            val subject = JSONObject(String(drive.request("https://www.googleapis.com/oauth2/v3/userinfo"), Charsets.UTF_8)).getString("sub")
            val binding = File(store.root, "google-account.txt")
            check(!binding.exists() || binding.readText() == subject) { "This library belongs to another Google account. Reconnect the original account." }
            val a = AtomicFile(binding); val out = a.startWrite(); try { out.write(subject.toByteArray()); a.finishWrite(out) } catch(e: Exception) { a.failWrite(out); throw e }
            val remote = drive.files(); val names = remote.map { it.name }.toMutableSet()
            for(r in store.revisions(pending = true)) {
                for(media in r.note.attachments) {
                    val name = "leaf-b-${media.id}"
                    if(name !in names) { val bytes = File(store.media, media.id).readBytes(); check(sha256(bytes) == media.id) { "Image integrity check failed" }; drive.upload(name, bytes, "application/octet-stream"); names += name }
                }
                val name = "leaf-r-${r.id}.json"
                if(name !in names) { drive.upload(name, r.raw.toByteArray(Charsets.UTF_8), "application/json"); names += name }
                store.acknowledge(r.id)
            }
            val known = store.revisions().map { it.id }.toMutableSet()
            for(file in remote.filter { it.name.startsWith("leaf-r-") && it.name.endsWith(".json") }) {
                val claimedId = file.name.removePrefix("leaf-r-").removeSuffix(".json"); if(claimedId in known) continue
                val raw = String(drive.download(file.id), Charsets.UTF_8)
                val validator = NativeStore.open(":memory:".toByteArray())
                try { check(validator != 0L && NativeStore.put(validator, raw.toByteArray(Charsets.UTF_8), true) != 0) { "Cloud note has an unsupported format" } } finally { if(validator != 0L) NativeStore.close(validator) }
                val r = Revision(raw); check(r.id == claimedId) { "Cloud revision identifier does not match" }
                if(store.heads.any{it.noteId==r.noteId && it.note.purgedAt!=null})continue
                for(m in r.note.attachments) {
                    val path = File(store.media, m.id)
                    if(!path.exists()) {
                        val blob = remote.firstOrNull { it.name == "leaf-b-${m.id}" } ?: error("An image upload is still arriving. Sync will retry.")
                        val bytes = drive.download(blob.id); check(sha256(bytes) == m.id) { "Image integrity check failed" }
                        val atomic = AtomicFile(path); val stream = atomic.startWrite(); try { stream.write(bytes); atomic.finishWrite(stream) } catch(e: Exception) { atomic.failWrite(stream); throw e }
                    }
                }
                store.ingest(raw, true); known += r.id
            }
            store.reload()
            val purged=store.heads.filter{it.note.purgedAt!=null}.map{it.noteId}.toSet()
            if(purged.isNotEmpty()){val current=drive.files();val local=store.revisions().associateBy{it.id};val candidates=mutableSetOf<String>();val referenced=mutableSetOf<String>();val erase=mutableListOf<DriveFile>()
                current.filter{it.name.startsWith("leaf-r-")&&it.name.endsWith(".json")}.forEach{file->val id=file.name.removePrefix("leaf-r-").removeSuffix(".json");val r=local[id] ?: Revision(String(drive.download(file.id),Charsets.UTF_8));if(r.noteId in purged && r.note.purgedAt==null){erase+=file;candidates+=r.note.attachments.map{it.id}}else referenced+=r.note.attachments.map{it.id}}
                erase.forEach{drive.remove(it.id)};current.filter{it.name.startsWith("leaf-b-")&&it.name.removePrefix("leaf-b-") in candidates-referenced}.forEach{drive.remove(it.id)};store.permanentlyDelete(emptyList())
            }
            store.status = "Synced · " + java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date())
        } catch(e: Exception) {
            store.status = "Saved locally · sync pending"
            if(e is HttpFailure && e.status == 401) { Identity.getAuthorizationClient(store.context).clearToken(ClearTokenRequest.builder().setToken(token).build()) }
            throw e
        } finally { store.busy = false }
    }
}
class SyncWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result {
        val store = Libraries.get(applicationContext)
        store.expireTrash()
        if(!store.preferences.getBoolean("connected", false)) return Result.success()
        return try {
            val result = Tasks.await(Identity.getAuthorizationClient(applicationContext).authorize(authorizationRequest()), 45, TimeUnit.SECONDS)
            if(result.hasResolution() || result.accessToken == null) { store.status = "Saved locally · reconnect Google"; Result.failure() }
            else { SyncEngine.run(store, result.accessToken!!); Result.success() }
        } catch(e: Exception) { store.status = "Saved locally · sync pending"; Result.retry() }
    }
}
fun scheduleSync(context: Context) {
    val manager = WorkManager.getInstance(context)
    val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    manager.enqueueUniqueWork("leaf-sync", ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(constraints).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
    manager.enqueueUniquePeriodicWork("leaf-periodic", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setConstraints(constraints).build())
}

class TrashWorker(context:Context,parameters:WorkerParameters):Worker(context,parameters){override fun doWork():Result=try{Libraries.get(applicationContext).expireTrash();Result.success()}catch(e:Exception){Result.retry()}}
fun scheduleTrash(context:Context){WorkManager.getInstance(context).enqueueUniquePeriodicWork("leaf-trash-retention",ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<TrashWorker>(1,TimeUnit.DAYS).build())}
