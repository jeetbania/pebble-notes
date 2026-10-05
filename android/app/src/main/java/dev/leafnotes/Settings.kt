package dev.leafnotes

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import java.io.File

@Composable fun MobileSettings(store:Store,onTour:()->Unit,onBack:()->Unit,onWriting:()->Unit,onWhatsNew:()->Unit,onConnect:()->Unit,onExport:()->Unit,onImport:()->Unit) {
    val c=LocalLeafColors.current; val version=LocalPreferencesVersion.current
    var page by remember { mutableStateOf("Settings") }
    val updater=remember { PebbleUpdater.get(store.context) };val scope=rememberCoroutineScope()
    val notificationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val name=store.preferences.getString("profileName","") ?: ""
    val avatarFile=remember {File(store.context.filesDir,"profile-avatar.jpg")}
    var avatarVersion by remember {mutableIntStateOf(0)}
    val avatarColor=remember {store.preferences.getString("profileAvatarColor",null) ?: accentChoices.random().also{store.preferences.edit().putString("profileAvatarColor",it).apply()}}
    val choosePicture=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {uri->if(uri!=null)try {
        val bounds=android.graphics.BitmapFactory.Options().apply{inJustDecodeBounds=true}
        store.context.contentResolver.openInputStream(uri)?.use{android.graphics.BitmapFactory.decodeStream(it,null,bounds)}
        var sample=1;while(bounds.outWidth/sample>1024||bounds.outHeight/sample>1024)sample*=2
        val source=store.context.contentResolver.openInputStream(uri)?.use{android.graphics.BitmapFactory.decodeStream(it,null,android.graphics.BitmapFactory.Options().apply{inSampleSize=sample})} ?: error("Unable to read picture")
        val side=minOf(source.width,source.height)
        val crop=android.graphics.Bitmap.createBitmap(source,(source.width-side)/2,(source.height-side)/2,side,side)
        val thumbnail=android.graphics.Bitmap.createScaledBitmap(crop,256,256,true)
        avatarFile.outputStream().use{thumbnail.compress(android.graphics.Bitmap.CompressFormat.JPEG,85,it)}
        avatarVersion++
    }catch(e:Exception){store.error=e.message}}
    fun back(){if(page=="Settings")onBack()else page="Settings"}
    BackHandler {back()}
    @Composable fun Avatar(size:Int=56) {
        key(avatarVersion){if(avatarFile.exists())MediaImage(avatarFile,Modifier.size(size.dp).clip(CircleShape),radius=size/2)
        else Box(Modifier.size(size.dp).background(accentColor(avatarColor,c.dark).copy(alpha=.22f),CircleShape),contentAlignment=Alignment.Center){Label(name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "P",size/2,FontWeight.SemiBold,color=accentColor(avatarColor,c.dark))}}
    }
    FrostedHost {
    PublishDockBackdrop()
        Box(Modifier.fillMaxSize().background(c.page)) {
            Column(Modifier.fillMaxSize().backdropSource().verticalScroll(rememberScrollState()).padding(horizontal=22.dp).padding(top=92.dp+WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),bottom=40.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                Label(page,30,FontWeight.Bold)
                when(page) {
                    "Settings" -> {
                        Pressable(Modifier.fillMaxWidth().background(c.paper,RoundedCornerShape(22.dp)).padding(18.dp),"Profile",onClick={page="Profile"}) {Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){Avatar();Column(Modifier.weight(1f)){Label(name.ifBlank{"Your profile"},18,FontWeight.SemiBold)};Glyph("next",size=18,tint=c.secondary)}}
                        SettingsGroup("Preferences") {
                            SettingsRow("Appearance","sun"){page="Appearance"}
                            SettingsRow("Writing & text sizes","format",onWriting)
                            SettingsRow("Card layout","grid"){page="Card layout"}
                            SettingsRow("Clipboard suggestions","clip"){page="Clipboard"}
                        }
                        SettingsGroup("Your library") {
                            SettingsRow("Sync & backups","folder"){page="Sync & backups"}
                            SettingsRow("App updates","download"){page="App updates"}
                        }
                        SettingsGroup("Pebble Notes") {
                            SettingsRow("What’s new","sparkle",onWhatsNew)
                            SettingsRow("Welcome tour","next",onTour)
                            SettingsRow("About","info"){page="About"}
                        }

                    }
                    "Profile" -> {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(18.dp)){Avatar(72);Column{SheetRow("Choose picture","image"){choosePicture.launch("image/*")};if(avatarFile.exists())SheetRow("Remove picture","close"){avatarFile.delete();avatarVersion++}}}
                        SettingsGroup("Your name") {MobileField(name,"Name",singleLine=true){store.preferences.edit().putString("profileName",it.take(40)).apply()}}
                        SettingsGroup("Bio") {MobileField(store.preferences.getString("profileBio","") ?: "","Optional"){store.preferences.edit().putString("profileBio",it.take(280)).apply()}}
                    }
                    "Appearance" -> {
                        SettingsGroup("Theme") {
                            val appearance=store.preferences.getString("appearance","system")
                            IosSegments(listOf("System","Light","Dark"),listOf("system","light","dark").indexOf(appearance).coerceAtLeast(0)){store.preferences.edit().putString("appearance",listOf("system","light","dark")[it]).apply()}
                        }
                        SettingsGroup("Accent colour") {
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {accentChoices.forEach {accent->val selected=store.preferences.getString("accentColor","Yellow")==accent
                                Pressable(Modifier.weight(1f).height(64.dp),"$accent accent${if(selected) ", selected" else ""}",onClick={store.preferences.edit().putString("accentColor",accent).apply()}) {Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)){Box(Modifier.size(30.dp).background(accentColor(accent,c.dark),CircleShape),contentAlignment=Alignment.Center){if(selected)Glyph("done",size=17,tint=if(accent in listOf("Yellow","Orange","Green"))Color.Black else Color.White)};Label(accent,10,color=c.secondary)}}
                            }}
                        }
                        SettingsGroup("Motion") {IosSegments(listOf("Fluid","Calmer"),if(store.preferences.getBoolean("calmMotion",false))1 else 0){store.preferences.edit().putBoolean("calmMotion",it==1).apply()}}
                    }
                    "Clipboard" -> SettingsGroup("Clipboard suggestions") {IosSegments(listOf("On","Off"),if(store.preferences.getBoolean("clipboardSuggestions",true))0 else 1){store.preferences.edit().putBoolean("clipboardSuggestions",it==0).apply()}}
                    "Card layout" -> SettingsGroup("Note cards") {IosSegments(listOf("Masonry","Grid"),if(store.preferences.getString("cardLayout","masonry")=="grid")1 else 0){store.preferences.edit().putString("cardLayout",if(it==1)"grid"else "masonry").apply()}}
                    "Sync & backups" -> {
                        SettingsGroup("Google Drive") {SettingsRow(if(store.preferences.getBoolean("connected",false))"Sync now"else "Connect Google Drive","sync",onClick=onConnect);Label(store.status,13,color=c.secondary,modifier=Modifier.padding(start=35.dp,bottom=8.dp))}
                        SettingsGroup("Backups") {SettingsRow("Export backup","share",onExport);SubtleDivider();SettingsRow("Import backup","archive",onImport)}
                    }
                    "App updates" -> SettingsGroup("Updates") {
                        SettingsRow(if(updater.checking)"Checking…" else "Check for updates","download"){scope.launch{updater.check(manual=true)}}
                        if(updater.message.isNotEmpty())Label(updater.message,13,color=c.secondary)
                        if(updater.available!=null)SettingsRow("View update","next"){updater.visible=true}
                        SubtleDivider();SettingsRow("Update notifications","bell"){if(android.os.Build.VERSION.SDK_INT>=33)notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)}
                    }
                    "About" -> SettingsGroup("Pebble Notes") {Image(androidx.compose.ui.res.painterResource(R.drawable.leaf_logo),"Pebble Notes",Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)));Label("Notes, images and small plans.",17,FontWeight.Medium);Label("Version "+ReleaseNotes.version(store.context),13,color=c.secondary);SubtleDivider();SheetRow("What’s new","sparkle",onClick=onWhatsNew);SheetRow("Welcome tour","next",onClick=onTour)}
                }
            }
            ScrollHeader {Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically){ChromeButton("back",if(page=="Settings")"Back to library"else "Back to Settings",44,onClick={back()});Spacer(Modifier.weight(1f))}}
        }
    }
}
@Composable private fun SettingsGroup(title:String,content:@Composable ColumnScope.()->Unit) {
    val c=LocalLeafColors.current
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Label(title,12,FontWeight.Medium,c.secondary,modifier=Modifier.padding(start=8.dp));Column(Modifier.fillMaxWidth().background(c.paper,RoundedCornerShape(20.dp)).padding(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp),content=content)}
}
@Composable private fun SettingsRow(title:String,icon:String,onClick:()->Unit) {
    val c=LocalLeafColors.current
    Pressable(Modifier.fillMaxWidth().heightIn(min=54.dp),title,onClick=onClick){Row(Modifier.fillMaxWidth().padding(vertical=13.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){Glyph(icon,size=21,tint=c.accent);Label(title,16,modifier=Modifier.weight(1f));Glyph("next",size=17,tint=c.secondary)}}
}
