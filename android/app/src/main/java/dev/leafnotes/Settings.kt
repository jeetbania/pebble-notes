package dev.leafnotes

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color

@Composable fun MobileSettings(store:Store,onTour:()->Unit,onBack:()->Unit,onWriting:()->Unit,onWhatsNew:()->Unit,onConnect:()->Unit,onExport:()->Unit,onImport:()->Unit) {
    val c=LocalLeafColors.current; val version=LocalPreferencesVersion.current
    val updater=remember { PebbleUpdater.get(store.context) };val scope=rememberCoroutineScope()
    val notificationPermission=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    FrostedHost {
        Box(Modifier.fillMaxSize().background(c.page)) {
            Column(Modifier.fillMaxSize().backdropSource().verticalScroll(rememberScrollState()).padding(horizontal=22.dp).padding(top=86.dp,bottom=48.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                Label("Settings",32,FontWeight.Bold)
                SettingsGroup("Appearance") {
                    Label("Theme",14,color=c.secondary)
                    val appearance=store.preferences.getString("appearance","system")
                    IosSegments(listOf("System","Light","Dark"),listOf("system","light","dark").indexOf(appearance).coerceAtLeast(0)){store.preferences.edit().putString("appearance",listOf("system","light","dark")[it]).apply()}
                    Label("Accent colour",14,color=c.secondary)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        accentChoices.forEach { name ->
                            val selected=store.preferences.getString("accentColor","Yellow")==name
                            Pressable(Modifier.weight(1f).height(60.dp),description="$name accent${if(selected) ", selected" else ""}",onClick={store.preferences.edit().putString("accentColor",name).apply()}) {
                                Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                    Box(Modifier.size(28.dp).background(accentColor(name,c.dark),CircleShape),contentAlignment=Alignment.Center) { if(selected)Glyph("done",size=16,tint=if(name in listOf("Yellow","Orange","Green"))Color.Black else Color.White) }
                                    Label(name,9,color=c.secondary,lines=1)
                                }
                            }
                        }
                    }
                    Label("Motion",14,color=c.secondary)
                    IosSegments(listOf("Fluid","Calmer"),if(store.preferences.getBoolean("calmMotion",false))1 else 0){store.preferences.edit().putBoolean("calmMotion",it==1).apply()}
                }
                SettingsGroup("Writing & capture") {
                    SheetRow("Writing & text sizes","format",onClick=onWriting)
                    Label("Clipboard suggestions",14,color=c.secondary)
                    IosSegments(listOf("On","Off"),if(store.preferences.getBoolean("clipboardSuggestions",true))0 else 1){store.preferences.edit().putBoolean("clipboardSuggestions",it==0).apply()}
                }
                SettingsGroup("Sync & backups") {
                    SheetRow("Connect Google / sync now","settings",onClick=onConnect)
                    Label(store.status,13,color=c.secondary)
                    SheetRow("Export backup","share",onClick=onExport)
                    SheetRow("Import backup","archive",onClick=onImport)
                }
                SettingsGroup("App updates") {
                    SheetRow(if(updater.checking)"Checking…" else "Check for updates","down"){scope.launch {updater.check(manual=true)}}
                    if(updater.message.isNotEmpty())Label(updater.message,13,color=c.secondary)
                    if(updater.available!=null)SheetRow("View available update","next"){updater.visible=true}
                    SheetRow("Enable update notifications","settings"){if(android.os.Build.VERSION.SDK_INT>=33)notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)}
                    Label("Updates are checked when you open Pebble and periodically while online. Battery restrictions can delay background checks.",13,color=c.secondary)
                }
                SettingsGroup("About Pebble Notes") {
                    Label("A quieter home for thoughts, images, and what comes next.",17,FontWeight.Medium)
                    Label("Your library saves on this device first. Write offline, keep original images, and plan tasks in one space. Optional Google Drive sync connects your devices. History, Trash, and backups give you room to change your mind.",14,color=c.secondary)
                    androidx.compose.material3.OutlinedTextField(value=store.preferences.getString("profileName","") ?: "",onValueChange={store.preferences.edit().putString("profileName",it.take(40)).apply()},label={androidx.compose.material3.Text("Your name · optional")},singleLine=true,modifier=Modifier.fillMaxWidth())
                    SheetRow("Replay welcome tour","next",onClick=onTour)
                    SheetRow("What’s New · "+ReleaseNotes.version(store.context),"done",onClick=onWhatsNew)
                }
            }
            Row(Modifier.fillMaxWidth().frosted(androidx.compose.ui.graphics.RectangleShape,light=true).padding(16.dp),verticalAlignment=Alignment.CenterVertically) { ChromeButton("back","Back to library",44,onClick=onBack);Spacer(Modifier.weight(1f));Label("Settings",15,FontWeight.Medium) }
        }
    }
}
@Composable private fun SettingsGroup(title:String,content:@Composable ColumnScope.()->Unit) {
    val c=LocalLeafColors.current
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {Label(title,13,FontWeight.Medium,c.secondary);Column(Modifier.fillMaxWidth().background(c.paper,RoundedCornerShape(22.dp)).padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)}
}
