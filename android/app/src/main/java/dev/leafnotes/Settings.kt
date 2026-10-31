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
    val largeType=androidx.compose.ui.platform.LocalDensity.current.fontScale>1.3f
    val c=LocalPebbleColors.current; val version=LocalPreferencesVersion.current
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
            Column(Modifier.fillMaxSize().backdropSource().verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(top=(if(largeType)112.dp else 80.dp)+WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),bottom=40.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                when(page) {
                    "Settings" -> {
                        Pressable(Modifier.fillMaxWidth().background(c.paper,RoundedCornerShape(24.dp)).padding(16.dp),"Edit profile, ${name.ifBlank{"Your profile"}}",onClick={page="Profile"}) {
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                                Avatar(48);Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){Label(name.ifBlank{"Your profile"},18,FontWeight.Medium);Label("Edit profile",13,color=c.secondary)};Glyph("next",size=16,tint=c.secondary)
                            }
                        }
                        SettingsLinks("General",listOf(
                            SettingsLink("Appearance","sun",store.preferences.getString("appearance","system")!!.replaceFirstChar{it.uppercase()}){page="Appearance"},
                            SettingsLink("Writing & text sizes","format",action=onWriting),
                            SettingsLink("Card layout","grid",if(store.preferences.getString("cardLayout","masonry")=="grid")"Grid" else "Masonry"){page="Card layout"},
                            SettingsLink("Clipboard suggestions","clip",if(store.preferences.getBoolean("clipboardSuggestions",true))"On" else "Off"){page="Clipboard"}
                        ))
                        SettingsLinks("Library",listOf(
                            SettingsLink("Sync & backups","sync",if(store.preferences.getBoolean("connected",false))"Connected" else "On device"){page="Sync & backups"}
                        ))
                        SettingsLinks("Pebble Notes",listOf(
                            SettingsLink("App updates","download"){page="App updates"},
                            SettingsLink("What’s new","sparkle",action=onWhatsNew),
                            SettingsLink("Welcome tour","next",action=onTour),
                            SettingsLink("About","info"){page="About"}
                        ))

                    }
                    "Profile" -> {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(18.dp)){Avatar(72);Column{SheetRow("Choose picture","image"){choosePicture.launch("image/*")};if(avatarFile.exists())SheetRow("Remove picture","close"){avatarFile.delete();avatarVersion++}}}
                        SettingsGroup("Your name") {MobileField(name,"Name",singleLine=true){store.preferences.edit().putString("profileName",it.take(40)).apply()}}
                        SettingsGroup("Bio") {MobileField(store.preferences.getString("profileBio","") ?: "","Optional"){store.preferences.edit().putString("profileBio",it.take(280)).apply()}}
                    }
                    "Appearance" -> {
                        SettingsGroup("Theme") {
                            val appearance=store.preferences.getString("appearance","system")
                            SettingsSegments(listOf("System","Light","Dark"),listOf("system","light","dark").indexOf(appearance).coerceAtLeast(0)){store.preferences.edit().putString("appearance",listOf("system","light","dark")[it]).apply()}
                        }
                        SettingsGroup("Accent colour") {
                            accentChoices.chunked(if(largeType)3 else 6).forEach{choices->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){choices.forEach{accent->val selected=store.preferences.getString("accentColor","Yellow")==accent
                                Pressable(Modifier.weight(1f).heightIn(min=76.dp).padding(vertical=8.dp),"$accent accent${if(selected)", selected" else ""}",onClick={store.preferences.edit().putString("accentColor",accent).apply()}){Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)){Box(Modifier.size(30.dp).background(accentColor(accent,c.dark),CircleShape),contentAlignment=Alignment.Center){if(selected)Glyph("done",size=17,tint=accentInk(accentColor(accent,c.dark)))};Label(accent,12,color=c.secondary)}}
                            }}}
                        }

                        SettingsGroup("Refresh rate") {
                            val high=store.preferences.getBoolean("highRefreshRate",true)
                            SettingsSegments(listOf("Standard","Up to 120 Hz"),if(high)1 else 0){store.preferences.edit().putBoolean("highRefreshRate",it==1).apply()}
                            Label("Uses supported display rates. Your phone’s power and display settings still apply.",13,color=c.secondary)
                        }
                        SettingsGroup("Motion") {SettingsSegments(listOf("Fluid","Calmer"),if(store.preferences.getBoolean("calmMotion",false))1 else 0){store.preferences.edit().putBoolean("calmMotion",it==1).apply()}}
                    }
                    "Clipboard" -> SettingsGroup("Clipboard suggestions") {SettingsSegments(listOf("On","Off"),if(store.preferences.getBoolean("clipboardSuggestions",true))0 else 1){store.preferences.edit().putBoolean("clipboardSuggestions",it==0).apply()}}
                    "Card layout" -> SettingsGroup("Note cards") {SettingsSegments(listOf("Masonry","Grid"),if(store.preferences.getString("cardLayout","masonry")=="grid")1 else 0){store.preferences.edit().putString("cardLayout",if(it==1)"grid"else "masonry").apply()}}
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
                    "About" -> SettingsGroup("Pebble Notes") {
                        Image(androidx.compose.ui.res.painterResource(R.drawable.pebble_logo),"Pebble Notes",Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)))
                        Label("Pebble Notes",28,FontWeight.Bold)
                        listOf("On this device, with optional Drive sync" to "sync","Notes, images and tasks" to "note","History, Trash and backups" to "clock").forEach{(title,icon)->Row(Modifier.fillMaxWidth().padding(vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically){Glyph(icon,size=21,tint=c.secondary);Label(title,16,color=c.secondary)}}
                        Label("Version "+ReleaseNotes.version(store.context),13,color=c.secondary)
                        SubtleDivider();SheetRow("What’s new","sparkle",onClick=onWhatsNew);SheetRow("Replay welcome tour","next",onClick=onTour)
                    }
                }
            }
            ScrollHeader {Box(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp)){Label(page,17,FontWeight.SemiBold,modifier=Modifier.align(Alignment.Center).padding(horizontal=52.dp));ChromeButton("back",if(page=="Settings")"Back to library"else "Back to Settings",44,onClick={back()})}}
        }
    }
}
@Composable private fun SettingsGroup(title:String,content:@Composable ColumnScope.()->Unit) {
    val c=LocalPebbleColors.current
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Label(title,12,FontWeight.Medium,c.secondary,modifier=Modifier.padding(start=8.dp));Column(Modifier.fillMaxWidth().background(c.paper,RoundedCornerShape(20.dp)).padding(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp),content=content)}
}
private data class SettingsLink(val title:String,val icon:String,val value:String="",val action:()->Unit)
@Composable private fun SettingsLinks(title:String,links:List<SettingsLink>) {
    val c=LocalPebbleColors.current
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Label(title,16,FontWeight.SemiBold,modifier=Modifier.padding(start=4.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.paper)) {
            links.forEachIndexed{index,link->
                SettingsLinkedRow(link.title,link.icon,link.action,link.value,Modifier.padding(horizontal=16.dp))
                if(index<links.lastIndex)Box(Modifier.fillMaxWidth().padding(start=64.dp).height(.5.dp).background(c.text.copy(alpha=.08f)))
            }
        }
    }
}
@Composable private fun SettingsRow(title:String,icon:String,onClick:()->Unit){SettingsLinkedRow(title,icon,onClick)}
@Composable private fun SettingsLinkedRow(title:String,icon:String,onClick:()->Unit,value:String="",modifier:Modifier=Modifier) {
    val c=LocalPebbleColors.current
    val showValue=androidx.compose.ui.platform.LocalDensity.current.fontScale<=1.3f
    Pressable(modifier.fillMaxWidth().heightIn(min=60.dp),title+if(value.isBlank())"" else ", $value",onClick=onClick){
        Row(Modifier.fillMaxWidth().padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(36.dp).background(c.text.copy(alpha=.035f),RoundedCornerShape(10.dp)),contentAlignment=Alignment.Center){Glyph(icon,size=20,tint=c.text.copy(alpha=.78f))}
            Label(title,16,modifier=Modifier.weight(1f))
            if(value.isNotBlank()&&showValue)Label(value,13,color=c.secondary,modifier=Modifier.widthIn(max=88.dp),lines=2)
            Glyph("next",size=16,tint=c.text.copy(alpha=.32f))
        }
    }
}

@Composable private fun SettingsSegments(labels:List<String>,selected:Int,onSelect:(Int)->Unit) {
    if(androidx.compose.ui.platform.LocalDensity.current.fontScale<=1.3f){IosSegments(labels,selected,onSelect);return}
    val c=LocalPebbleColors.current
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        labels.forEachIndexed{index,label->Pressable(Modifier.fillMaxWidth().heightIn(min=52.dp).background(if(index==selected)c.fill else Color.Transparent,RoundedCornerShape(12.dp)).padding(12.dp),label+if(index==selected)", selected" else "",onClick={onSelect(index)}){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){Label(label,16,modifier=Modifier.weight(1f));if(index==selected)Glyph("done",size=20,tint=c.text)}}}
    }
}
