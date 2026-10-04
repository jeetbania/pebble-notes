package dev.leafnotes

import android.content.Context
import android.util.AtomicFile
import androidx.work.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

fun scheduleLocalBackup(context:Context) { if(context.filesDir!=context.applicationContext.filesDir)return; WorkManager.getInstance(context).enqueueUniqueWork("leaf-local-backup",ExistingWorkPolicy.KEEP,OneTimeWorkRequestBuilder<LocalBackupWorker>().build()) }
class LocalBackupWorker(context:Context,params:WorkerParameters):Worker(context,params) {
    override fun doWork():Result=try {
        val store=Libraries.get(applicationContext);val folder=File(store.root,"backups").apply{mkdirs()};val file=File(folder,SimpleDateFormat("yyyy-MM-dd",Locale.US).format(Date())+".leafbackup")
        if(!file.exists() || System.currentTimeMillis()-file.lastModified()>3600000) { val data=store.backup();val atomic=AtomicFile(file);val out=atomic.startWrite();try{out.write(data);atomic.finishWrite(out)}catch(e:Exception){atomic.failWrite(out);throw e};folder.listFiles()?.filter{it.extension=="leafbackup"}?.sortedByDescending{it.name}?.drop(7)?.forEach{it.delete()} }
        Result.success()
    }catch(e:Exception){Result.retry()}
}
fun markdown(note:Note):String {
    val lines=mutableListOf("# "+note.displayTitle,"");if(note.tags.isNotEmpty())lines.add(note.tags.joinToString(" "){"#"+it});var number=0
    note.document.forEach{b->val prefix="  ".repeat(b.indent+note.depth(b));when(b.kind){
        "toggle"->lines.add(prefix+"> "+b.text)
        "divider"->lines.add("---")
        "bullet"->lines.add(prefix+"- "+b.text)
        "number"->lines.add(prefix+"${++number}. "+b.text)
        "check"->lines.add(prefix+(if(b.checked)"- [x] " else "- [ ] ")+b.text)
        "image","file"->lines.add((if(b.kind=="image")"!" else "")+"["+b.caption.replace("]","\\]")+"](assets/"+b.mediaId+")")
        "table"->b.cells.forEachIndexed{i,row->lines.add("| "+row.joinToString(" | "){it.replace("|","\\|").replace("\n","<br>")}+" |");if(i==0)lines.add("| "+row.joinToString(" | "){"---"}+" |")}
        else->{lines.add(prefix+b.text);lines.add("")}
    }};return lines.joinToString("\n")
}
fun exportNote(store:Store,note:Note,output:java.io.OutputStream) {
    ZipOutputStream(output).use{zip->fun entry(name:String,bytes:ByteArray){zip.putNextEntry(ZipEntry(name));zip.write(bytes);zip.closeEntry()};entry("note.md",markdown(note).toByteArray());entry("note.leaf.json",note.json().toString().toByteArray());note.attachments.forEach{entry("assets/"+it.id,File(store.media,it.id).readBytes())}}
}
