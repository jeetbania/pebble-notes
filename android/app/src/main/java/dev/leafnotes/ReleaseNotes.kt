package dev.leafnotes

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class LeafRelease(val version:String,val title:String,val changes:List<String>)
object ReleaseNotes {
    const val seenKey="acknowledgedRelease"
    fun version(context:Context):String=context.packageManager.getPackageInfo(context.packageName,0).versionName ?: ""
    fun identity(context:Context):String { val p=context.packageManager.getPackageInfo(context.packageName,0);return (p.versionName ?: "")+":"+androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(p) }
    fun needsPresentation(context:Context,prefs:SharedPreferences)=prefs.getString(seenKey,"")!=identity(context)
    fun acknowledge(context:Context,prefs:SharedPreferences){prefs.edit().putString(seenKey,identity(context)).apply()}
    fun entries(context:Context,prefs:SharedPreferences,history:Boolean):List<LeafRelease> {
        val raw=JSONArray(context.assets.open("releases.json").bufferedReader().use{it.readText()})
        val all=(0 until raw.length()).map{i->val r=raw.getJSONObject(i);val changes=r.getJSONArray("changes");LeafRelease(r.getString("version"),r.getString("title"),(0 until changes.length()).map{changes.getString(it)})}
        if(history)return all
        val last=prefs.getString(seenKey,"")?.substringBefore(":");val index=all.indexOfFirst{it.version==last}
        return all.take(if(index>0)index else 1)
    }
}
@Composable fun WhatsNew(store:Store,history:Boolean,onClose:()->Unit) {
    val releases=remember(history){ReleaseNotes.entries(store.context,store.preferences,history)}
    IosSheet("What’s New",onDismiss=onClose,translucent=true) {
        Label("Pebble Notes · "+ReleaseNotes.version(store.context),13,color=LocalLeafColors.current.secondary)
        releases.forEach{r->
            Label(r.version+" · "+r.title,18,FontWeight.SemiBold,modifier=Modifier.padding(top=24.dp,bottom=12.dp))
            r.changes.forEach{change->Row(Modifier.padding(bottom=12.dp)){Glyph("done",size=18,tint=LocalLeafColors.current.accent);Spacer(Modifier.width(10.dp));Label(change,15,modifier=Modifier.weight(1f))}}
        }
        SheetRow("Continue","done",onClick=onClose)
    }
}
