package dev.leafnotes

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import java.util.concurrent.TimeUnit

fun scheduleTaskAlerts(store:Store) {
    if(store.context.filesDir!=store.context.applicationContext.filesDir)return
    val manager=WorkManager.getInstance(store.context)
    val old=store.preferences.getStringSet("taskAlerts",emptySet()) ?: emptySet()
    val now=System.currentTimeMillis()
    val records=store.visibleHeads.filter{val t=it.note.task;t!=null&&t.remind&&!t.completed&&!it.note.deleted&&!it.note.archived&&t.dueAt>0}
    val keys=records.map{it.noteId+":"+it.note.task!!.alertAt+":"+sha256((it.note.title+it.note.task!!.list).toByteArray())}.toSet()
    (old-keys).forEach{manager.cancelUniqueWork("leaf-task-"+it)}
    records.forEach{r->val key=r.noteId+":"+r.note.task!!.alertAt+":"+sha256((r.note.title+r.note.task!!.list).toByteArray());if(key !in old){manager.enqueueUniqueWork("leaf-task-"+key,ExistingWorkPolicy.REPLACE,OneTimeWorkRequestBuilder<TaskAlertWorker>().setInputData(workDataOf("noteId" to r.noteId,"dueAt" to r.note.task!!.dueAt)).setInitialDelay((r.note.task!!.alertAt-now).coerceAtLeast(0),TimeUnit.MILLISECONDS).build())}}
    store.preferences.edit().putStringSet("taskAlerts",keys).apply()
}
class TaskAlertWorker(context:Context,params:WorkerParameters):Worker(context,params) {
    override fun doWork():Result {
        val store=Libraries.get(applicationContext)
        val r=store.revisions(headsOnly=true).firstOrNull{it.noteId==inputData.getString("noteId")} ?: return Result.success()
        val t=r.note.task ?: return Result.success()
        if(!t.remind||t.completed||r.note.deleted||r.note.archived||t.dueAt!=inputData.getLong("dueAt",0))return Result.success()
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(applicationContext,android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return Result.success()
        val manager=applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("leaf-tasks","Task reminders",NotificationManager.IMPORTANCE_DEFAULT))
        val open=PendingIntent.getActivity(applicationContext,r.noteId.hashCode(),Intent(applicationContext,MainActivity::class.java).putExtra("taskId",r.noteId),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=NotificationCompat.Builder(applicationContext,"leaf-tasks").setSmallIcon(android.R.drawable.ic_menu_agenda).setContentTitle(r.note.displayTitle).setContentText(t.list).setContentIntent(open).setAutoCancel(true).build()
        manager.notify(r.noteId.hashCode(),notification)
        return Result.success()
    }
}
