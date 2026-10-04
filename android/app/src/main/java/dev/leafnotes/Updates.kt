package dev.leafnotes

import android.content.*
import android.content.pm.PackageManager
import android.app.*
import android.os.Build
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.work.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.net.URL
import java.security.*
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.TimeUnit

const val UPDATE_FEED="https://github.com/jeetbania/pebble-notes/releases/latest/download/updates.json"
data class UpdateAsset(val url:String,val sha256:String,val size:Long,val build:Long)
data class PebbleUpdate(val version:String,val releases:List<LeafRelease>,val asset:UpdateAsset)
fun updateHash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
fun parseUpdate(context:Context,bytes:ByteArray):PebbleUpdate {
    require(bytes.size<=1024*1024){"Update information is too large"}
    val envelope=JSONObject(String(bytes,Charsets.UTF_8));val payload=android.util.Base64.decode(envelope.getString("payload"),android.util.Base64.DEFAULT)
    val pem=context.assets.open("update-public.pem").bufferedReader().use{it.readText()}.replace(Regex("-----[^-]+-----|\\s"),"")
    val key=KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(android.util.Base64.decode(pem,android.util.Base64.DEFAULT)))
    val signature=Signature.getInstance("SHA256withRSA");signature.initVerify(key);signature.update(payload)
    require(signature.verify(android.util.Base64.decode(envelope.getString("signature"),android.util.Base64.DEFAULT))){"Update signature could not be verified"}
    val data=JSONObject(String(payload,Charsets.UTF_8));require(data.getInt("schema")==1)
    val a=data.getJSONObject("android");val url=a.getString("url")
    require(url.startsWith("https://github.com/jeetbania/pebble-notes/releases/download/")){"Unexpected update location"}
    val asset=UpdateAsset(url,a.getString("sha256"),a.getLong("size"),a.getLong("build"))
    require(asset.sha256.matches(Regex("[a-f0-9]{64}")) && asset.size in 1..200_000_000 && asset.build>0)
    val history=data.getJSONArray("releases");val releases=(0 until history.length()).map{i->val r=history.getJSONObject(i);val c=r.getJSONArray("changes");LeafRelease(r.getString("version"),r.getString("title"),(0 until c.length()).map{c.getString(it)})}
    val version=data.getString("version");require(releases.firstOrNull()?.version==version)
    return PebbleUpdate(version,releases,asset)
}
fun fetchUpdate(context:Context):PebbleUpdate {
    val connection=URL(UPDATE_FEED).openConnection() as java.net.HttpURLConnection
    connection.connectTimeout=15000;connection.readTimeout=20000
    try { require(connection.responseCode==200){"Could not check for updates (${connection.responseCode})"};val bytes=connection.inputStream.use{input->val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(true){val count=input.read(buffer);if(count<0)break;require(out.size()+count<=1024*1024){"Update information is too large"};out.write(buffer,0,count)};out.toByteArray()};return parseUpdate(context,bytes) } finally {connection.disconnect()}
}
fun installedBuild(context:Context)=androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName,0))
fun unseenReleases(update:PebbleUpdate,current:String):List<LeafRelease> {val index=update.releases.indexOfFirst{it.version==current};return if(index>=0)update.releases.take(index) else update.releases}
class PebbleUpdater private constructor(val context:Context) {
    var available by mutableStateOf<PebbleUpdate?>(null);private set
    var visible by mutableStateOf(false)
    var checking by mutableStateOf(false);private set
    var downloading by mutableStateOf(false);private set
    var progress by mutableStateOf(0f);private set
    var message by mutableStateOf("");private set
    var ready by mutableStateOf<File?>(null);private set
    private val prefs=context.getSharedPreferences("pebbleUpdates",Context.MODE_PRIVATE)
    suspend fun check(manual:Boolean=false) {
        if(checking || downloading)return
        if(!manual && System.currentTimeMillis()-prefs.getLong("lastCheck",0)<TimeUnit.HOURS.toMillis(6))return
        checking=true;message="Checking for updates…"
        try {
            val update=withContext(Dispatchers.IO){fetchUpdate(context)}
            prefs.edit().putLong("lastCheck",System.currentTimeMillis()).apply()
            if(update.asset.build>installedBuild(context)) {if(available?.asset?.build!=update.asset.build)ready=null;available=update;message="Pebble Notes ${update.version} is available";if(manual || prefs.getLong("laterBuild",0)!=update.asset.build)visible=true}
            else {available=null;message="You’re up to date · ${ReleaseNotes.version(context)}"}
        } catch(e:Exception){message=if(manual)"Could not check: ${e.message}" else "Check for updates when you’re online."} finally {checking=false}
    }
    fun later(){available?.let{prefs.edit().putLong("laterBuild",it.asset.build).apply()};visible=false}
    suspend fun download() {
        val update=available ?: return;if(downloading)return
        downloading=true;progress=0f;message="Downloading…";ready=null
        try {
            val file=withContext(Dispatchers.IO) {
                val dir=File(context.cacheDir,"updates").apply{mkdirs()};val partial=File(dir,"pebble.apk.part");val target=File(dir,"pebble.apk")
                val connection=URL(update.asset.url).openConnection() as java.net.HttpURLConnection;connection.connectTimeout=15000;connection.readTimeout=30000
                try {require(connection.responseCode==200){"Download failed (${connection.responseCode})"};require(connection.url.protocol=="https");val digest=MessageDigest.getInstance("SHA-256");var total=0L
                    connection.inputStream.use{input->partial.outputStream().use{out->val buffer=ByteArray(65536);while(true){val count=input.read(buffer);if(count<0)break;total+=count;require(total<=update.asset.size){"Unexpected download size"};out.write(buffer,0,count);digest.update(buffer,0,count);withContext(Dispatchers.Main){progress=total.toFloat()/update.asset.size}}}}
                    require(total==update.asset.size && digest.digest().joinToString(""){"%02x".format(it)}==update.asset.sha256){"Downloaded update did not pass verification"}
                    verifyApk(partial,update);target.delete();require(partial.renameTo(target));target
                } finally {connection.disconnect();partial.delete()}
            };ready=file;message="Verified. Ready to install."
        }catch(e:Exception){message="Could not download: ${e.message}"}finally{downloading=false}
    }
    fun verifyApk(file:File,update:PebbleUpdate) {
        val flags=PackageManager.GET_SIGNING_CERTIFICATES
        val apk=context.packageManager.getPackageArchiveInfo(file.path,flags) ?: error("Invalid app package")
        val current=context.packageManager.getPackageInfo(context.packageName,flags)
        require(apk.packageName==context.packageName && androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(apk)==update.asset.build && apk.versionName==update.version){"Update does not match Pebble Notes"}
        val trusted=current.signingInfo?.apkContentsSigners?.map{updateHash(it.toByteArray())}?.toSet() ?: error("Missing installed signing identity")
        require(apk.signingInfo?.apkContentsSigners?.map{updateHash(it.toByteArray())}?.toSet()==trusted){"App signing identity changed"}
    }
    fun install() {
        val file=ready ?: return
        try {
            if(!context.packageManager.canRequestPackageInstalls()){context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));message="Allow updates from Pebble Notes, then return and tap Install.";return}
            available?.let{verifyApk(file,it)} ?: return
            val uri=FileProvider.getUriForFile(context,context.packageName+".updates",file)
            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        }catch(e:Exception){message="Could not open installer: ${e.message}"}
    }
    companion object { @Volatile private var shared:PebbleUpdater?=null;fun get(context:Context):PebbleUpdater=shared ?: synchronized(this){shared ?: PebbleUpdater(context.applicationContext).also{shared=it}} }
}
@Composable fun UpdateDialog(updater:PebbleUpdater) {
    val update=updater.available ?: return;val scope=rememberCoroutineScope();val c=LocalLeafColors.current
    IosSheet("Pebble Notes ${update.version}",onDismiss={if(!updater.downloading)updater.later()},translucent=true) {
        Label("A new update is ready",17)
        unseenReleases(update,ReleaseNotes.version(updater.context)).forEach{r->Label(r.version+" · "+r.title,17,modifier=Modifier.padding(top=18.dp));r.changes.forEach{Label("• $it",14,color=c.secondary,modifier=Modifier.padding(vertical=5.dp))}}
        Label(updater.message,14,color=c.secondary,modifier=Modifier.padding(vertical=16.dp))
        if(updater.downloading)androidx.compose.material3.LinearProgressIndicator(progress={updater.progress},modifier=Modifier.fillMaxWidth(),color=c.accent)
        else {SheetRow(if(updater.ready!=null)"Install update" else "Download now","down"){if(updater.ready!=null)updater.install() else scope.launch{updater.download()}};SheetRow("Later","close"){updater.later()}}
    }
}
class UpdateWorker(context:Context,params:WorkerParameters):Worker(context,params) {
    override fun doWork():Result {
        return try {
            val update=fetchUpdate(applicationContext);val prefs=applicationContext.getSharedPreferences("pebbleUpdates",Context.MODE_PRIVATE)
            if(update.asset.build>installedBuild(applicationContext) && prefs.getLong("notifiedBuild",0)!=update.asset.build && prefs.getBoolean("notifications",true)) {
                val manager=applicationContext.getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(NotificationChannel("pebble-updates","App updates",NotificationManager.IMPORTANCE_DEFAULT))
                if(androidx.core.app.NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
                    val intent=Intent(applicationContext,MainActivity::class.java).putExtra("checkUpdates",true)
                    val pending=PendingIntent.getActivity(applicationContext,413,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    manager.notify(413,NotificationCompat.Builder(applicationContext,"pebble-updates").setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle("Pebble Notes ${update.version} is ready").setContentText("See what’s new and choose when to download.").setContentIntent(pending).setAutoCancel(true).build())
                    prefs.edit().putLong("notifiedBuild",update.asset.build).apply()
                }
            };Result.success()
        }catch(e:Exception){Result.retry()}
    }
}
fun scheduleUpdates(context:Context) {
    val constraints=Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork("pebble-app-updates",ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<UpdateWorker>(6,TimeUnit.HOURS).setConstraints(constraints).build())
}
