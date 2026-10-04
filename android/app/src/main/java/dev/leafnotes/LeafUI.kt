package dev.leafnotes

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.os.Build
import android.text.*
import android.view.Gravity
import android.widget.EditText
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

val LocalPreferencesVersion = staticCompositionLocalOf { 0 }
val LocalLeafColors = staticCompositionLocalOf { lightLeafColors }
@Composable fun LeafTheme(store: Store, content: @Composable () -> Unit) {
    var preferenceVersion by remember { mutableIntStateOf(0) }
    val appearance = store.preferences.getString("appearance", "system")
    val typeSizes = remember(preferenceVersion) { TextMetrics.read(store.preferences) }
    val calm = remember(preferenceVersion) { store.preferences.getBoolean("calmMotion",false) || android.provider.Settings.Global.getFloat(store.context.contentResolver,android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f }
    DisposableEffect(store.preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key -> preferenceVersion++ }
        store.preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { store.preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val dark = appearance == "dark" || appearance == "system" && isSystemInDarkTheme()
    val c = (if(dark) darkLeafColors else lightLeafColors).copy(accent=accentColor(store.preferences.getString("accentColor","Yellow") ?: "Yellow",dark))
    val view = LocalView.current
    SideEffect {
        (view.context as? android.app.Activity)?.let { activity ->
            androidx.core.view.WindowCompat.getInsetsController(activity.window, view).apply { isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark }; activity.window.statusBarColor=android.graphics.Color.TRANSPARENT; activity.window.navigationBarColor=android.graphics.Color.TRANSPARENT; activity.window.isStatusBarContrastEnforced=false; activity.window.isNavigationBarContrastEnforced=false
        }
    }
    CompositionLocalProvider(LocalLeafColors provides c, LocalTypeSizes provides typeSizes, LocalCalmMotion provides calm, LocalPreferencesVersion provides preferenceVersion) {
        MaterialTheme(colorScheme = if(dark) darkColorScheme(primary=c.accent, background=c.page, surface=c.paper, onSurface=c.text) else lightColorScheme(primary=c.accent, background=c.page, surface=c.paper, onSurface=c.text), content=content)
    }
}
@Composable fun Label(text: String, size: Int = 17, weight: FontWeight = FontWeight.Normal, color: Color = LocalLeafColors.current.text, modifier: Modifier = Modifier, lines: Int = Int.MAX_VALUE) {
    androidx.compose.material3.Text(text, modifier, color=color, fontSize=size.sp, fontWeight=weight, lineHeight=(size*1.32).sp, letterSpacing=if(size>=28) (-0.7).sp else (-0.15).sp, maxLines=lines, overflow=TextOverflow.Ellipsis)
}
fun iconResource(name: String): Int = when(name) {
    "calendar" -> R.drawable.leaf_calendar; "clock" -> R.drawable.leaf_clock; "minus" -> R.drawable.leaf_minus; "flag" -> R.drawable.leaf_flag
    "check" -> R.drawable.lucide_list_checks; "edit" -> R.drawable.lucide_square_pen; "next" -> R.drawable.lucide_chevron_right
    "sun" -> R.drawable.lucide_sun; "moon" -> R.drawable.lucide_moon; "sort" -> R.drawable.lucide_arrow_down_up
    "forward" -> R.drawable.lucide_chevron_right; "back" -> R.drawable.lucide_chevron_left; "down" -> R.drawable.lucide_chevron_down; "more" -> R.drawable.lucide_ellipsis
    "compose" -> R.drawable.lucide_square_pen; "search" -> R.drawable.lucide_search; "folder" -> R.drawable.lucide_folder
    "folderPlus" -> R.drawable.lucide_folder_plus; "image" -> R.drawable.lucide_image; "images" -> R.drawable.lucide_images
    "list" -> R.drawable.lucide_list; "grid" -> R.drawable.lucide_layout_grid; "archive" -> R.drawable.lucide_archive
    "trash" -> R.drawable.lucide_trash_2; "settings" -> R.drawable.lucide_settings_2; "pin" -> R.drawable.lucide_pin
    "share" -> R.drawable.lucide_arrow_up_from_line; "undo" -> R.drawable.lucide_undo_2; "clip" -> R.drawable.lucide_paperclip
    "done" -> R.drawable.lucide_check; "close" -> R.drawable.lucide_x; "number" -> R.drawable.lucide_list_ordered
    "download" -> R.drawable.pebble_download; "bell" -> R.drawable.pebble_bell; "info" -> R.drawable.pebble_info; "sparkle" -> R.drawable.pebble_sparkle; "sync" -> R.drawable.lucide_arrow_left_right
    "plus" -> R.drawable.lucide_plus; else -> R.drawable.lucide_folder
}
@Composable fun Glyph(name: String, description: String? = null, size: Int = 23, tint: Color = LocalLeafColors.current.text) {
    if(name == "format") Label("Aa", size, FontWeight.Medium, tint)
    else Icon(painterResource(iconResource(name)), description, tint=tint, modifier=Modifier.size(size.dp))
}
@OptIn(ExperimentalFoundationApi::class)
@Composable fun Pressable(modifier: Modifier = Modifier, description: String? = null, enabled: Boolean = true, onClick: () -> Unit, onLongClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val haptic=androidx.compose.ui.platform.LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }; val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if(pressed && !LocalCalmMotion.current) .98f else 1f, spring(dampingRatio=1f,stiffness=900f), label="press")
    Box(modifier.graphicsLayer { scaleX=scale; scaleY=scale; alpha=if(enabled) 1f else .35f }.then(if(description!=null) Modifier.semantics { contentDescription=description } else Modifier).combinedClickable(interactionSource=interaction, indication=null, enabled=enabled, onClick={haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove);onClick()}, onLongClick=onLongClick), contentAlignment=Alignment.Center) { content() }
}
@Composable fun ChromeButton(name: String, label: String, size: Int = 48, prominent: Boolean = false, onClick: () -> Unit) {
    val c=LocalLeafColors.current
    Pressable(Modifier.size(size.dp).shadow(14.dp,CircleShape, ambientColor=Color.Black.copy(alpha=.035f),spotColor=Color.Black.copy(alpha=.06f)).then(if(prominent)Modifier.background(c.accent,CircleShape) else Modifier.frosted(CircleShape)).border(.5.dp,c.text.copy(alpha=.025f),CircleShape),label,onClick=onClick) { Glyph(name, size=23, tint=if(prominent) Color.White else c.text) }
}
@Composable fun ChromePill(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val c=LocalLeafColors.current
    Row(modifier.shadow(18.dp, CircleShape, ambientColor=Color.Black.copy(alpha=.04f),spotColor=Color.Black.copy(alpha=.08f)).frosted(CircleShape).border(.6.dp,c.text.copy(alpha=.03f),CircleShape).padding(horizontal=8.dp), verticalAlignment=Alignment.CenterVertically, content=content)
}

@Composable fun LeafLibrary(store: Store, connect: () -> Unit) {
    val updater=remember { PebbleUpdater.get(store.context) }
    LaunchedEffect(Unit) { updater.check() }
    val settingsVersion=LocalPreferencesVersion.current
    var onboarding by rememberSaveable { mutableStateOf(!store.preferences.getBoolean("onboardingComplete",false)) }
    var whatsNew by rememberSaveable { mutableStateOf(ReleaseNotes.needsPresentation(store.context,store.preferences)) }
    var releaseHistory by rememberSaveable { mutableStateOf(false) }
    var section by remember { mutableStateOf("Notes") }; var search by remember { mutableStateOf("") }; var folders by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }; var formatPanel by remember { mutableStateOf(false) }; var conflicts by remember { mutableStateOf(false) }; var preview by remember { mutableStateOf<Media?>(null) }
    var moving by remember { mutableStateOf(false) }; var folderName by remember { mutableStateOf("") }
    var batch by remember { mutableStateOf(false) }; var selectedNotes by remember { mutableStateOf(setOf<String>()) }; var oldFolder by remember { mutableStateOf("") }; var deletingFolder by remember { mutableStateOf<String?>(null) }
    var linking by remember { mutableStateOf(false) }; var linkUrl by remember { mutableStateOf("https://") }
    var typographySettings by remember { mutableStateOf(false) }
    var dailyTools by remember { mutableStateOf(false) }; var newFolder by remember { mutableStateOf(false) }; var folderEmoji by remember { mutableStateOf("") }
    var gallery by remember { mutableStateOf(store.preferences.getBoolean("gallery",false)) }
    var editor by remember(store.selected) { mutableStateOf<EditText?>(null) }
    val calm=LocalCalmMotion.current
    val scope=rememberCoroutineScope(); val keyboard=LocalSoftwareKeyboardController.current; val c=LocalLeafColors.current
    val images=androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> scope.launch { try { withContext(Dispatchers.IO) { uris.forEach { store.attach(it) } } } catch(e: Exception) { store.error=e.message } } }
    val backup=androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if(uri!=null) scope.launch { try { val bytes=withContext(Dispatchers.IO){store.backup()}; withContext(Dispatchers.IO){store.context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Could not write backup")}; store.status="Backup exported" }catch(e:Exception){store.error=e.message} } }
    val restore=androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null) scope.launch { try { withContext(Dispatchers.IO){store.context.contentResolver.openInputStream(uri)?.use { store.restore(it.readBytes()) } ?: error("Could not read backup") } }catch(e:Exception){store.error=e.message} } }
    val noteExport=androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")){uri->if(uri!=null){store.flush();val value=store.editing;scope.launch{try{withContext(Dispatchers.IO){if(value!=null)store.context.contentResolver.openOutputStream(uri)?.use{exportNote(store,value,it)}}}catch(e:Exception){store.error=e.message}}}}
    val records=store.visibleHeads.filter { r -> val n=r.note; (n.task==null || section in listOf("Trash","Archive")) && (if(section=="Trash")n.deleted else !n.deleted) && (if(section=="Archive")n.archived else section=="Trash" || !n.archived) && (section!="Images" || n.attachments.any{it.mime.startsWith("image/")}) && (section in listOf("Notes","Everything","Images","Archive","Trash") || section=="Pinned" && n.pinned || section=="Checklists" && n.document.any{it.kind=="check"} || section.startsWith("#") && section.removePrefix("#") in n.tags || n.collection==section || n.collection.startsWith(section+"/")) && (search.isEmpty() || "${n.title} ${n.text} ${n.collection} ${n.tags.joinToString(" "){"#"+it}}".contains(search,true)) }.sortedWith(compareByDescending<Revision>{it.note.pinned}.thenByDescending{it.createdAt})
    var lastNote by remember { mutableStateOf<Note?>(null) }; var lastId by remember { mutableStateOf<String?>(null) }
    SideEffect { if(store.editing!=null) {lastNote=store.editing;lastId=store.selected} }
    val width=with(LocalDensity.current){LocalConfiguration.current.screenWidthDp.dp.toPx()}
    val navigation=remember { Animatable(width) }
    var gesture by remember { mutableStateOf(false) }
    LaunchedEffect(store.selected) {
        formatPanel=false
        if(calm)navigation.snapTo(if(store.selected!=null)0f else width)
        else if(!gesture) navigation.animateTo(if(store.selected!=null)0f else width, spring(dampingRatio=1f,stiffness=420f))
    }
    fun back() { keyboard?.hide(); store.select(null); formatPanel=false }
    BackHandler(enabled=store.selected!=null || folders || formatPanel || section=="Settings") { if(formatPanel)formatPanel=false else if(store.selected!=null)back() else if(section=="Settings")section="Notes" else folders=false }
    if(onboarding) { Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()){PebbleOnboarding(store,onFinish={onboarding=false},onConnect=connect)};return }
    val libraryHome=store.selected==null && section !in listOf("Settings","Tasks")
    val statusHeight=WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxSize().background(if(store.selected!=null)c.canvas else c.page).drawBehind {if(libraryHome){drawRect(homeGlowColor(c.dark),size=androidx.compose.ui.geometry.Size(size.width,statusHeight.toPx()))}}.statusBarsPadding().navigationBarsPadding().imePadding()) {
        Box(Modifier.fillMaxSize().graphicsLayer{val progress=(1f-navigation.value/width).coerceIn(0f,1f);translationX=if(calm)0f else -20f*progress;scaleX=if(calm)1f else 1f-.015f*progress;scaleY=scaleX}) { if(section=="Settings") MobileSettings(store,onTour={onboarding=true},onBack={section="Notes"},onWriting={typographySettings=true},onWhatsNew={releaseHistory=true;whatsNew=true},onConnect=connect,onExport={backup.launch("Pebble Notes Backup.leafbackup")},onImport={restore.launch(arrayOf("*/*"))}) else if(section=="Tasks"&&!folders) TasksHome(store){section="Notes"} else LibraryScreen(store,section,records,search,{search=it},folders,{folders=it},gallery,{gallery=!gallery;store.preferences.edit().putBoolean("gallery",gallery).apply()},enabled=store.selected==null,onSection={section=it;folders=false},onMore={menu=true},onOpen={if(store.selected==null)store.select(it)},onNew={store.create();if(section in store.collections)store.editing?.let{store.update(it.copy(collection=section))}},onImage={if(store.selected==null)preview=it}) }
        if(updater.visible && !whatsNew) UpdateDialog(updater)
        val note=store.editing ?: lastNote
        if(note!=null && (store.selected!=null || navigation.value<width-.5f)) {
            Box(Modifier.fillMaxSize().graphicsLayer { translationX=navigation.value }.shadow(16.dp)) {
                EditorScreen(store,note,lastId,onBack={back()},onMore={menu=true},onFormat={keyboard?.hide();formatPanel=!formatPanel},onImages={store.insertionOffset=editor?.selectionStart;images.launch(arrayOf("*/*"))},onCompose={store.create()},onPreview={preview=it},onEditor={editor=it},onConflict={conflicts=true},onShare={
                    store.flush(); val intent=Intent(Intent.ACTION_SEND).apply { type="text/plain"; putExtra(Intent.EXTRA_TEXT,note.displayTitle+"\n\n"+note.text) }; store.context.startActivity(Intent.createChooser(intent,"Share note").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                })
                // This recognizer is limited to the edge; horizontal text selection remains native.
                Box(Modifier.align(Alignment.CenterStart).width(20.dp).fillMaxHeight().pointerInput(store.selected,width) {
                    val velocity=VelocityTracker()
                    detectHorizontalDragGestures(onDragStart={gesture=true;velocity.resetTracking();scope.launch {navigation.stop()}}, onHorizontalDrag={change,amount ->
                        change.consume();velocity.addPosition(change.uptimeMillis,change.position);scope.launch(start=CoroutineStart.UNDISPATCHED){navigation.snapTo((navigation.value+amount).coerceIn(0f,width))}
                    },onDragCancel={gesture=false;scope.launch{navigation.animateTo(0f,spring(dampingRatio=.85f,stiffness=450f))}},onDragEnd={
                        val v=velocity.calculateVelocity().x;val leave=navigation.value+v*.12f>width*.3f
                        scope.launch {navigation.animateTo(if(leave)width else 0f,spring(dampingRatio=if(leave)1f else .85f,stiffness=420f),initialVelocity=v);if(leave)back();gesture=false}
                    })
                })
            }
        }
        ClipboardSuggestion(store,Modifier.align(Alignment.BottomEnd))
        AnimatedVisibility(visible=formatPanel && store.selected!=null,modifier=Modifier.align(Alignment.BottomCenter),enter=slideInVertically(if(calm)snap() else spring(dampingRatio=1f,stiffness=460f)){if(calm)0 else it}+fadeIn(tween(if(calm)0 else 160)),exit=slideOutVertically(if(calm)snap() else spring(dampingRatio=1f,stiffness=650f)){if(calm)0 else it}+fadeOut(tween(if(calm)0 else 120))) {
            FormatPanel(Modifier,onClose={formatPanel=false},onStyle={kind -> if(kind=="link"){linking=true}else editor?.let { view -> format(view,kind); store.activeBlock?.let { id -> store.editBlock(id,view.text.toString(),spansFrom(view.text));if(kind in listOf("body","title","subtitle","headline"))store.changeBlock(id){it.copy(textStyle=kind)} } } },onList={kind -> store.setList(kind) })
        }
    }
    if(whatsNew) WhatsNew(store,releaseHistory){ReleaseNotes.acknowledge(store.context,store.preferences);whatsNew=false}
    var eraseTrash by remember { mutableStateOf<List<String>>(emptyList()) }
    if(eraseTrash.isNotEmpty())IosSheet("Delete permanently?",onDismiss={eraseTrash=emptyList()}){Label("These items and their saved versions will be removed. This cannot be undone.",15,color=c.secondary);SheetRow("Delete permanently","trash",tint=c.danger){store.permanentlyDelete(eraseTrash);eraseTrash=emptyList()};SheetRow("Cancel","close"){eraseTrash=emptyList()}}
    if(menu) IosSheet(if(store.selected!=null)"Note options" else "Library options",onDismiss={menu=false}) {
        if(store.selected!=null) {
            val note=store.editing!!
            SheetRow(if(note.pinned)"Unpin note" else "Pin note","pin") {store.update(note.copy(pinned=!note.pinned));menu=false}
            NoteSizeControl(store)
            SheetRow("Find, tags & history","search"){dailyTools=true;menu=false}
            SheetRow("Insert table","list"){store.addBlock("table");menu=false}
            SheetRow("Redo","undo"){store.redo();menu=false}
            SheetRow("Export note & attachments","share"){noteExport.launch(note.displayTitle+".zip");menu=false}
            SheetRow("Move to collection","folder") {folderName=note.collection;moving=true;menu=false}
            SheetRow(if(note.archived)"Unarchive" else "Archive","archive") {store.update(note.copy(archived=!note.archived));store.select(null);menu=false}
            if(note.deleted)SheetRow("Delete permanently…","trash",tint=c.danger){eraseTrash=listOf(store.selected!!);menu=false}
            SheetRow(if(note.deleted)"Restore note" else "Move to Trash","trash",tint=c.danger) {store.update(note.copy(deleted=!note.deleted));store.select(null);menu=false}
        } else {
            if(section=="Trash") {Label("Items are permanently removed after 30 days.",13,color=c.secondary);SheetRow("Empty Trash…","trash",tint=c.danger){eraseTrash=store.visibleHeads.filter{it.note.deleted}.map{it.noteId};menu=false}}
            IosSegments(listOf("List","Gallery"),if(gallery)1 else 0) {gallery=it==1;store.preferences.edit().putBoolean("gallery",gallery).apply()}
            SheetRow("Manage folders","folder"){folderName="";folderEmoji="";oldFolder="";newFolder=true;menu=false}
            SheetRow("Select notes","check"){selectedNotes=emptySet();batch=true;menu=false}
            Spacer(Modifier.height(12.dp)); SheetRow("Collections","folder") {folders=true;menu=false}
        }
        SheetRow("Settings","settings"){back();section="Settings";folders=false;menu=false}

    }
    if(batch)IosSheet("Select notes",onDismiss={batch=false}){
        records.forEach{r->SheetRow((if(r.noteId in selectedNotes)"✓ " else "○ ")+r.note.displayTitle,"compose"){selectedNotes=if(r.noteId in selectedNotes)selectedNotes-r.noteId else selectedNotes+r.noteId}}
        SheetRow("Archive selected","archive"){val previous=store.selected;selectedNotes.forEach{id->store.select(id);store.editing?.let{store.update(it.copy(archived=true))};store.flush()};store.select(previous);batch=false}
        SheetRow("Move selected to Trash","trash",tint=c.danger){val previous=store.selected;selectedNotes.forEach{id->store.select(id);store.editing?.let{store.update(it.copy(deleted=true))};store.flush()};store.select(previous);batch=false}
    }
    if(linking)IosSheet("Add link",onDismiss={linking=false}){
        BasicTextField(linkUrl,{linkUrl=it},textStyle=TextStyle(color=c.text,fontSize=17.sp),modifier=Modifier.fillMaxWidth().background(c.fill,RoundedCornerShape(14.dp)).padding(14.dp))
        SheetRow("Add link","done"){val uri=android.net.Uri.parse(linkUrl);if(uri.scheme in listOf("http","https","mailto")){editor?.let{view->val start=view.selectionStart.coerceAtLeast(0);var end=view.selectionEnd.coerceAtLeast(start);if(end==start){view.text.insert(start,linkUrl);end=start+linkUrl.length};view.text.setSpan(android.text.style.URLSpan(linkUrl),start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);store.activeBlock?.let{id->store.editBlock(id,view.text.toString(),spansFrom(view.text))}};linking=false}else store.error="Use an http, https, or mailto link"}
    }
    if(typographySettings)TypographySettings(store){typographySettings=false}
    if(dailyTools)DailySheet(store){dailyTools=false}
    deletingFolder?.let{name->IosSheet("Delete Collection?",onDismiss={deletingFolder=null},translucent=true){Label("Your notes will be kept in the library. Only this collection and its subcollections will be removed.",16,color=c.secondary);Row(Modifier.fillMaxWidth().padding(top=22.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){Pressable(Modifier.weight(1f).padding(12.dp),"Cancel",onClick={deletingFolder=null}){Label("Cancel",16)};Pressable(Modifier.weight(1f).background(c.danger.copy(alpha=.14f),RoundedCornerShape(22.dp)).padding(12.dp),"Delete",onClick={store.deleteFolder(name);oldFolder="";folderName="";deletingFolder=null}){Label("Delete",16,color=c.danger)}}}}
    if(newFolder)IosSheet("New folder",onDismiss={newFolder=false}){
        BasicTextField(folderName,{folderName=it},textStyle=TextStyle(color=c.text,fontSize=18.sp),modifier=Modifier.fillMaxWidth().background(c.fill,RoundedCornerShape(14.dp)).padding(16.dp),decorationBox={inner->if(folderName.isEmpty())Label("Folder / Subfolder",18,color=c.tertiary);inner()})
        Spacer(Modifier.height(12.dp));BasicTextField(folderEmoji,{folderEmoji=it},textStyle=TextStyle(color=c.text,fontSize=20.sp),modifier=Modifier.fillMaxWidth().background(c.fill,RoundedCornerShape(14.dp)).padding(16.dp),decorationBox={inner->if(folderEmoji.isEmpty())Label("Emoji icon (optional)",17,color=c.tertiary);inner()})
        if(oldFolder.isNotEmpty() && oldFolder!="Personal")SheetRow("Delete Collection","trash",tint=c.danger){deletingFolder=oldFolder;newFolder=false}
        SheetRow("Save folder","done"){if(oldFolder.isNotEmpty()&&oldFolder!=folderName)store.renameFolder(oldFolder,folderName);store.createFolder(folderName,folderEmoji);newFolder=false}
        store.collections.forEach{name->SheetRow("Edit "+name,"folder"){oldFolder=name;folderName=name;folderEmoji=store.folderHeads.firstOrNull{it.note.collection==name}?.note?.folderEmoji ?: ""}}
    }
    if(moving) IosSheet("Move to collection",onDismiss={moving=false}) {
        BasicTextField(folderName,{folderName=it},textStyle=TextStyle(color=c.text,fontSize=18.sp),singleLine=true,cursorBrush=SolidColor(c.accent),modifier=Modifier.fillMaxWidth().background(c.fill,RoundedCornerShape(14.dp)).padding(16.dp))
        store.collections.forEach {name -> SheetRow(name,"folder"){store.editing?.let{store.update(it.copy(collection=name))};moving=false}}
        SheetRow("Done","done"){store.editing?.let{store.update(it.copy(collection=folderName.ifBlank{"Personal"}))};moving=false}
    }
    if(conflicts) IosSheet("Both versions are safe",onDismiss={conflicts=false}) {
        Label("Choose a version to keep working with. Earlier versions remain in your history.",15,color=c.secondary)
        store.heads.filter {it.noteId==store.selected}.forEach {r -> Label(r.note.displayTitle,18,FontWeight.SemiBold,modifier=Modifier.padding(top=16.dp));Label(if(r.note.deleted)"Deleted on another device" else r.note.text.take(300),15,color=c.secondary);SheetRow("Continue with this version","done"){store.resolve(r);conflicts=false} }
    }
    preview?.let {photo->val photos=store.editing?.let{n->viewerPhotos(n,store.selected ?: "")} ?: records.flatMap{r->viewerPhotos(r.note,r.noteId)};PhotoPreview(store,photo,photos,onClose={preview=null})}
    store.error?.let {error -> IosSheet("Leaf needs attention",onDismiss={store.error=null}){Label(error,16,color=c.secondary);SheetRow("OK","done"){store.error=null}}}
    LaunchedEffect(Unit){while(true){delay(45000);store.expireTrash();if(store.preferences.getBoolean("connected",false))scheduleSync(store.context)}}
}

@Composable fun LibraryScreen(store:Store,section:String,records:List<Revision>,search:String,onSearch:(String)->Unit,folders:Boolean,onFolders:(Boolean)->Unit,gallery:Boolean,onGallery:()->Unit,enabled:Boolean,onSection:(String)->Unit,onMore:()->Unit,onOpen:(String)->Unit,onNew:()->Unit,onImage:(Media)->Unit) {
    val c=LocalLeafColors.current; val headerDensity=LocalDensity.current; var headerHeight by remember { mutableStateOf(146.dp) }
    var eraseNote by remember { mutableStateOf<String?>(null) };var held by remember { mutableStateOf<String?>(null) }; var choosingMove by remember { mutableStateOf(false) }
    FrostedHost {
    Box(Modifier.fillMaxSize().background(c.page).then(if(!enabled)Modifier.clearAndSetSemantics{} else Modifier)) {
            Box(Modifier.fillMaxWidth().height(540.dp).background(homeGlowBrush(c.dark)))
            Crossfade(targetState=Pair(folders,gallery),animationSpec=tween(if(LocalCalmMotion.current)0 else 180),label="library layout") { (folderMode,galleryMode) ->
            if(folderMode) {
                LazyColumn(modifier=Modifier.backdropSource(),contentPadding=PaddingValues(start=22.dp,top=headerHeight+12.dp,end=22.dp,bottom=120.dp)) {
                    item {Label("Your library",20,FontWeight.SemiBold,modifier=Modifier.padding(top=12.dp,bottom=10.dp))}
                    item {Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.paper)) {listOf("Notes" to "list","Pinned" to "pin","Checklists" to "check","Tasks" to "calendar","Images" to "images","Archive" to "archive","Trash" to "trash","Settings" to "settings").forEachIndexed {i,(label,icon)-> FolderRow(label,icon,store.visibleHeads.count{val n=it.note;if(label=="Trash")n.deleted else if(label=="Archive")n.archived&&!n.deleted else if(label=="Images")!n.deleted&&!n.archived&&n.attachments.any{it.mime.startsWith("image/")} else if(label=="Pinned")!n.deleted&&!n.archived&&n.pinned else if(label=="Tasks")!n.deleted&&!n.archived&&n.task!=null else if(label=="Checklists")!n.deleted&&!n.archived&&n.document.any{it.kind=="check"} else !n.deleted&&!n.archived},i>0){if(enabled)onSection(label)} }} }
                    if(store.visibleHeads.any{it.note.tags.isNotEmpty()})item{Label("Tags",20,FontWeight.SemiBold,modifier=Modifier.padding(top=24.dp,bottom=10.dp));Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.paper)){store.visibleHeads.filter{!it.note.deleted}.flatMap{it.note.tags}.distinct().sorted().forEachIndexed{i,tag->FolderRow("#"+tag,"number",store.visibleHeads.count{tag in it.note.tags&&!it.note.deleted},i>0){onSection("#"+tag)}}}}
                    item {Label("Collections",20,FontWeight.SemiBold,modifier=Modifier.padding(top=28.dp,bottom=10.dp))}
                    item {Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.paper)){CollectionRows(store){name->if(enabled)onSection(name)}}}
                }
            } else if(section=="Images" || galleryMode) {
                LazyVerticalStaggeredGrid(modifier=Modifier.backdropSource(),columns=StaggeredGridCells.Fixed(2),contentPadding=PaddingValues(22.dp,headerHeight+12.dp,22.dp,120.dp),horizontalArrangement=Arrangement.spacedBy(14.dp),verticalItemSpacing=16.dp) {
                    if(records.isEmpty())item(span=StaggeredGridItemSpan.FullLine){LibraryEmpty(section,search,{onSearch("")},onNew,{onSection("Notes")})}
                    if(section=="Images") records.forEach {r->items(r.note.attachments.filter{it.mime.startsWith("image/")},key={r.noteId+it.id}){a->Pressable(Modifier.clip(RoundedCornerShape(23.dp)).background(c.paper),description="View ${a.name}",onClick={if(enabled)onImage(a)},onLongClick={if(enabled)held=r.noteId}){Column(Modifier.padding(8.dp)){MediaImage(File(store.media,a.id),Modifier.fillMaxWidth().aspectRatio(1f),radius=17);Label(r.note.collection,11,color=c.secondary,modifier=Modifier.align(Alignment.CenterHorizontally).padding(top=7.dp,bottom=3.dp),lines=1)}}}}
                    else items(records,key={it.noteId}){r->GalleryCard(r,store,onHold={if(enabled)held=r.noteId}){if(enabled)onOpen(r.noteId)}}
                }
            } else {
                val groups=records.groupBy {if(it.note.pinned)"Pinned" else dateGroup(it.createdAt)}
                LazyColumn(modifier=Modifier.backdropSource(),contentPadding=PaddingValues(start=22.dp,top=headerHeight+12.dp,end=22.dp,bottom=120.dp)) {
                    if(records.isEmpty())item{LibraryEmpty(section,search,{onSearch("")},onNew,{onSection("Notes")})}
                    groups.forEach {(heading,items)->
                        item(key=heading+"header") {Label(heading,20,FontWeight.SemiBold,modifier=Modifier.padding(top=if(heading==groups.keys.first())8.dp else 26.dp,bottom=10.dp))}
                        itemsIndexed(items,key={_,r->r.noteId}){index,r->
                            val shape=RoundedCornerShape(topStart=if(index==0)24.dp else 0.dp,topEnd=if(index==0)24.dp else 0.dp,bottomStart=if(index==items.lastIndex)24.dp else 0.dp,bottomEnd=if(index==items.lastIndex)24.dp else 0.dp)
                            SwipeNoteRow(r,store,index>0,shape,enabled,onOpen={onOpen(r.noteId)},onHold={if(enabled)held=r.noteId})
                        }
                    }
                }
            }
            }
        ScrollHeader(Modifier.onSizeChanged { headerHeight=with(headerDensity){it.height.toDp()} },homeGlow=true) {
            Row(Modifier.fillMaxWidth().padding(horizontal=22.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
                if(folders) Image(painterResource(R.drawable.leaf_logo),"Pebble Notes",Modifier.size(48.dp).clip(RoundedCornerShape(13.dp))) else ChromeButton("back","Collections") {if(enabled)onFolders(true)}
                Spacer(Modifier.weight(1f));ChromeButton("more","More options") {if(enabled)onMore()}
            }
            Label(if(folders)"Folders" else section,34,FontWeight.Bold,modifier=Modifier.padding(start=22.dp,top=12.dp))
            Label(if(folders)"" else (if(section in listOf("Notes","Everything") && !store.preferences.getString("profileName","").isNullOrBlank()) "Hello, ${store.preferences.getString("profileName","")} · " else "") + "${records.size} ${if(records.size==1)"Note" else "Notes"}",13,color=c.secondary,modifier=Modifier.padding(start=23.dp,bottom=14.dp))
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(110.dp).background(Brush.verticalGradient(listOf(c.page.copy(alpha=0f),c.page.copy(alpha=.92f)))))
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal=22.dp).padding(top=12.dp,bottom=24.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            if(folders) {
                Label(store.status,12,color=c.secondary,modifier=Modifier.weight(1f),lines=1)
                ChromeButton("compose","New note",52){if(enabled){onFolders(false);onNew()}}
            } else {
                ChromePill(Modifier.weight(1f).height(52.dp)) {
                    Glyph("search",size=20,tint=c.secondary);Spacer(Modifier.width(9.dp))
                    BasicTextField(search,{if(enabled)onSearch(it)},singleLine=true,textStyle=TextStyle(fontSize=17.sp,color=c.text),cursorBrush=SolidColor(c.accent),modifier=Modifier.weight(1f),decorationBox={inner->if(search.isEmpty())Label("Search",17,color=c.secondary);inner()})
                    Pressable(Modifier.size(38.dp),if(search.isEmpty())"Change view" else "Clear search",onClick={if(enabled){if(search.isNotEmpty())onSearch("") else onGallery()}}){Glyph(if(search.isNotEmpty())"close" else if(gallery)"list" else "grid",size=19,tint=c.secondary)}
                }
                ChromeButton("calendar","Tasks",52){if(enabled)onSection("Tasks")};ChromeButton("settings","Settings",52){if(enabled)onSection("Settings")};ChromeButton("compose","New note",52){if(enabled)onNew()}
            }
        }
    }
    eraseNote?.let{id->IosSheet("Delete permanently?",onDismiss={eraseNote=null}){Label("The item and its saved versions will be removed. This cannot be undone.",15,color=c.secondary);SheetRow("Delete permanently","trash",tint=c.danger){store.permanentlyDelete(listOf(id));eraseNote=null};SheetRow("Cancel","close"){eraseNote=null}}}
    held?.let { id -> val revision=store.visibleHeads.firstOrNull{it.noteId==id}; if(revision!=null) IosSheet(if(choosingMove)"Move to collection" else revision.note.displayTitle,onDismiss={held=null;choosingMove=false}) {
        fun change(transform:(Note)->Note) { val previous=store.selected; store.select(id);store.editing?.let{store.update(transform(it))};store.flush();store.select(previous);held=null;choosingMove=false }
        if(choosingMove) store.collections.forEach{name->SheetRow(name,"folder"){change{it.copy(collection=name)}}}
        else {if(revision.note.deleted)SheetRow("Delete permanently","trash",tint=c.danger){choosingMove=false;held=null;store.error=null;eraseNote=id};SheetRow("Open note","compose"){held=null;onOpen(id)};SheetRow(if(revision.note.pinned)"Unpin" else "Pin","pin"){change{it.copy(pinned=!it.pinned)}};SheetRow("Move…","folder"){choosingMove=true};SheetRow(if(revision.note.archived)"Unarchive" else "Archive","archive"){change{it.copy(archived=!it.archived)}};SheetRow(if(revision.note.deleted)"Restore" else "Move to Trash","trash",tint=c.danger){change{it.copy(deleted=!it.deleted)}}}
    } }

}
}
@Composable fun FolderRow(name:String,icon:String,count:Int,divider:Boolean,onClick:()->Unit) {
    val c=LocalLeafColors.current
    if(divider)Box(Modifier.fillMaxWidth().padding(start=52.dp).height(.5.dp).background(c.separator))
    Pressable(Modifier.fillMaxWidth(),description=name,onClick=onClick){Row(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=17.dp),verticalAlignment=Alignment.CenterVertically){Glyph(icon,tint=c.accent);Label(name,17,modifier=Modifier.padding(start=12.dp).weight(1f));Label(count.toString(),16,color=c.secondary);Spacer(Modifier.width(10.dp));Glyph("forward",size=16,tint=c.tertiary)}}
}
fun dateGroup(millis:Long):String {
    val day=Calendar.getInstance().apply{timeInMillis=millis;set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)}
    val today=Calendar.getInstance().apply{set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)}
    val days=((today.timeInMillis-day.timeInMillis)/86400000).toInt()
    return when {days<=0->"Today";days==1->"Yesterday";days<7->"Previous 7 Days";else->SimpleDateFormat("MMMM",Locale.getDefault()).format(Date(millis))}
}
@Composable fun SwipeNoteRow(r:Revision,store:Store,divider:Boolean,shape:RoundedCornerShape,enabled:Boolean,onOpen:()->Unit,onHold:()->Unit) {
    val c=LocalLeafColors.current; val scope=rememberCoroutineScope();val x=remember(r.noteId){Animatable(0f)};val actionWidth=with(LocalDensity.current){78.dp.toPx()}
    Box(Modifier.fillMaxWidth().clip(shape).background(c.paper)) {
        Row(Modifier.matchParentSize(),horizontalArrangement=Arrangement.SpaceBetween){Pressable(Modifier.width(78.dp).fillMaxHeight().background(c.accent),"Pin note",enabled,onClick={store.select(r.noteId);store.editing?.let{store.update(it.copy(pinned=!it.pinned))};store.select(null);scope.launch{x.animateTo(0f)}}){Glyph("pin",tint=Color.White)};Pressable(Modifier.width(78.dp).fillMaxHeight().background(c.danger),"Move to Trash",enabled,onClick={store.select(r.noteId);store.editing?.let{store.update(it.copy(deleted=!it.deleted))};store.select(null);scope.launch{x.animateTo(0f)}}){Glyph(if(r.note.deleted)"undo" else "trash",tint=Color.White)}}
        Column(Modifier.offset{IntOffset(x.value.roundToInt(),0)}.background(c.paper).pointerInput(r.noteId,enabled){
            if(!enabled)return@pointerInput
            val tracker=VelocityTracker()
            detectHorizontalDragGestures(onDragStart={tracker.resetTracking();scope.launch{x.stop()}},onHorizontalDrag={change,amount->change.consume();tracker.addPosition(change.uptimeMillis,change.position);scope.launch(start=CoroutineStart.UNDISPATCHED){val raw=x.value+amount;val damped=if(abs(raw)>actionWidth*1.4f)sign(raw)*(actionWidth*1.4f+(abs(raw)-actionWidth*1.4f)*.2f) else raw;x.snapTo(damped)}},onDragCancel={scope.launch{x.animateTo(0f,spring(dampingRatio=.85f,stiffness=500f))}},onDragEnd={val v=tracker.calculateVelocity().x;val predicted=x.value+v*.08f;val target=if(abs(predicted)>actionWidth*.5f)sign(predicted)*actionWidth else 0f;scope.launch{x.animateTo(target,spring(dampingRatio=.86f,stiffness=500f),initialVelocity=v)}})
        }) {
            if(divider)Box(Modifier.fillMaxWidth().padding(start=30.dp).height(.5.dp).background(c.separator))
            Pressable(Modifier.fillMaxWidth(),r.note.displayTitle,enabled,onClick={if(abs(x.value)>5f)scope.launch{x.animateTo(0f)} else onOpen()},onLongClick={scope.launch{x.animateTo(0f)};onHold()}){
                Row(Modifier.fillMaxWidth().padding(start=30.dp,end=13.dp,top=12.dp,bottom=12.dp).heightIn(min=42.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)){Label(r.note.displayTitle,17,FontWeight.SemiBold,lines=1);Label(SimpleDateFormat("M/d/yy",Locale.getDefault()).format(Date(r.createdAt))+"  "+r.note.text.replace('\n',' ').ifBlank{"New note"},15,color=c.secondary,modifier=Modifier.padding(top=2.dp),lines=1)}
                    r.note.attachments.firstOrNull{it.mime.startsWith("image/")}?.let{MediaImage(File(store.media,it.id),Modifier.size(43.dp),radius=8)}
                }
            }
        }
    }
}
@Composable fun GalleryCard(r:Revision,store:Store,onHold:()->Unit,onClick:()->Unit) {
    val c=LocalLeafColors.current
    Pressable(Modifier.fillMaxWidth().shadow(8.dp,RoundedCornerShape(23.dp),ambientColor=Color.Black.copy(alpha=.04f),spotColor=Color.Black.copy(alpha=.06f)).clip(RoundedCornerShape(23.dp)).background(c.paper),r.note.displayTitle,onClick=onClick,onLongClick=onHold) {
        Column(Modifier.fillMaxWidth().padding(17.dp).heightIn(min=150.dp,max=300.dp)) {
            Label(r.note.displayTitle,19,FontWeight.SemiBold,lines=2);Spacer(Modifier.height(10.dp))
            r.note.attachments.firstOrNull{it.mime.startsWith("image/")}?.let{MediaImage(File(store.media,it.id),Modifier.fillMaxWidth().height(110.dp),radius=12);Spacer(Modifier.height(10.dp))}
            Label(r.note.text,13,color=c.secondary,lines=if(r.note.attachments.isEmpty())8 else 3)
            Label(r.note.collection,11,color=c.tertiary,modifier=Modifier.padding(top=14.dp),lines=1)
        }
    }
}
@Composable fun EditorScreen(store:Store,note:Note,noteId:String?,onBack:()->Unit,onMore:()->Unit,onFormat:()->Unit,onImages:()->Unit,onCompose:()->Unit,onPreview:(Media)->Unit,onEditor:(EditText)->Unit,onConflict:()->Unit,onShare:()->Unit) {
    val c=LocalLeafColors.current;val metrics=LocalTypeSizes.current.copy(scale=note.textScale);val keyboard=LocalSoftwareKeyboardController.current;val focus=LocalFocusManager.current;var editing by remember(noteId){mutableStateOf(false)}
    FrostedHost {
    Box(Modifier.fillMaxSize().background(c.canvas)) {
            Column(Modifier.fillMaxSize().backdropSource().verticalScroll(rememberScrollState()).padding(horizontal=28.dp).padding(top=100.dp,bottom=130.dp)) {
                BasicTextField(note.title,{store.editing?.let{n->store.update(n.copy(title=it))}},singleLine=true,keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(imeAction=androidx.compose.ui.text.input.ImeAction.Next),keyboardActions=androidx.compose.foundation.text.KeyboardActions(onNext={focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Next)}),textStyle=TextStyle(fontSize=metrics.size("title").sp,fontWeight=FontWeight.SemiBold,color=c.text,lineHeight=(metrics.size("title")*1.2).sp,letterSpacing=(-.6).sp),cursorBrush=SolidColor(c.accent),modifier=Modifier.fillMaxWidth().onFocusChanged{editing=it.isFocused},decorationBox={inner->if(note.title.isEmpty())Label("Untitled note",30,FontWeight.SemiBold,c.tertiary);inner()})
                if(store.heads.count{it.noteId==store.selected}>1)Pressable(Modifier.padding(top=12.dp),"Review versions",onClick=onConflict){Label("Edits from both devices · review",13,color=c.accent)}
                Spacer(Modifier.height(24.dp))
                DocumentBlocks(store,note,noteId,onEditor,{editing=it},onPreview)
            }
            ScrollHeader(surface=c.canvas) { Row(Modifier.fillMaxWidth().padding(horizontal=22.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                ChromeButton("back","Back to notes",onClick=onBack);Spacer(Modifier.weight(1f))
                ChromePill(Modifier.height(48.dp)) {Pressable(Modifier.size(44.dp),"Share note",onClick=onShare){Glyph("share")};Pressable(Modifier.size(44.dp),"Note options",onClick=onMore){Glyph("more")}}
                if(editing)ChromeButton("done","Done editing",prominent=true){keyboard?.hide();activeTextEditor?.clearFocus();focus.clearFocus();editing=false}
            }}
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(105.dp).background(Brush.verticalGradient(listOf(c.canvas.copy(alpha=0f),c.canvas.copy(alpha=.96f)))))
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal=22.dp).padding(top=12.dp,bottom=24.dp),verticalAlignment=Alignment.CenterVertically) {
            ChromePill(Modifier.height(50.dp)) {
                Pressable(Modifier.size(48.dp),"Format",onClick=onFormat){Glyph("format")}
                Pressable(Modifier.size(48.dp),"Add images",onClick=onImages){Glyph("clip")}
                Pressable(Modifier.size(48.dp),"Undo",onClick={onEditorUndo(store)}){Glyph("undo")}
            }
            Spacer(Modifier.weight(1f));ChromeButton("compose","New note",50,onClick=onCompose)
        }
    }
}
}
// A store revision is durable history; use the native text view's undo for a typing gesture.
var activeTextEditor: EditText?=null
fun onEditorUndo(store:Store){store.undo()}
fun prefixLine(view:EditText,prefix:String) {
    val text=view.text?:return;val cursor=view.selectionStart.coerceAtLeast(0)
    val start=if(cursor==0)0 else text.toString().lastIndexOf('\n',cursor-1).let{if(it<0)0 else it+1}
    val end=text.toString().indexOf('\n',cursor).let{if(it<0)text.length else it}
    if(text.subSequence(start,end).startsWith(prefix))text.delete(start,start+prefix.length) else text.insert(start,prefix)
}
@Composable fun FormatPanel(modifier:Modifier,onClose:()->Unit,onStyle:(String)->Unit,onList:(String)->Unit) {
    val c=LocalLeafColors.current
    Column(modifier.fillMaxWidth().padding(start=12.dp,end=12.dp,bottom=22.dp).shadow(25.dp,RoundedCornerShape(32.dp),ambientColor=Color.Black.copy(alpha=.05f),spotColor=Color.Black.copy(alpha=.1f)).background(c.paper,RoundedCornerShape(32.dp)).border(.5.dp,c.text.copy(alpha=.04f),RoundedCornerShape(32.dp)).padding(22.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Label("Format",25,FontWeight.SemiBold,modifier=Modifier.weight(1f));Pressable(Modifier.size(38.dp).background(c.fill,CircleShape),"Close format",onClick=onClose){Glyph("close")}}
        Label("Select text to change its style.",14,color=c.secondary,modifier=Modifier.padding(top=8.dp,bottom=18.dp))
        var style by remember { mutableIntStateOf(3) }
        IosSegments(listOf("Title","Subtitle","Heading","Body"),style){style=it;onStyle(listOf("title","subtitle","headline","body")[it])}
        Row(horizontalArrangement=Arrangement.spacedBy(20.dp)){SheetRowCompact("Highlight"){onStyle("highlight")};SheetRowCompact("Link"){onStyle("link")}}
        Row(Modifier.fillMaxWidth().clip(CircleShape).background(c.fill)) {
            listOf("B" to "bold","I" to "italic","U" to "underline","S" to "strike").forEachIndexed{i,(label,kind)->
                Pressable(Modifier.weight(1f).height(48.dp),kind,onClick={onStyle(kind)}){androidx.compose.material3.Text(label,color=c.text,fontSize=22.sp,fontWeight=if(kind=="bold")FontWeight.Bold else FontWeight.Medium,fontStyle=if(kind=="italic")androidx.compose.ui.text.font.FontStyle.Italic else null,textDecoration=when(kind){"underline"->androidx.compose.ui.text.style.TextDecoration.Underline;"strike"->androidx.compose.ui.text.style.TextDecoration.LineThrough;else->null})}
                if(i<3)Box(Modifier.width(.5.dp).height(48.dp).background(c.separator))
            }
        }
        SheetRow("Checklist","check"){onList("check")}
        Row(Modifier.padding(top=14.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Pressable(Modifier.weight(1f).height(48.dp).background(c.fill,CircleShape),"Bulleted line",onClick={onList("bullet")}){Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){Glyph("list");Label("Bullets",15)}}
            Pressable(Modifier.weight(1f).height(48.dp).background(c.fill,CircleShape),"Numbered line",onClick={onList("number")}){Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){Glyph("number");Label("Numbers",15)}}
        }
    }
}
@Composable fun IosSegments(labels:List<String>,selected:Int,onSelect:(Int)->Unit) {
    val c=LocalLeafColors.current
    val haptic=androidx.compose.ui.platform.LocalHapticFeedback.current
    val calm=LocalCalmMotion.current;val scope=rememberCoroutineScope();var width by remember{mutableFloatStateOf(1f)};var dragging by remember{mutableStateOf(false)}
    val currentSelected by rememberUpdatedState(selected);val select by rememberUpdatedState(onSelect)
    val thumb=remember(labels){Animatable(selected.toFloat())};val velocity=remember{VelocityTracker()};val last=(labels.size-1).toFloat()
    LaunchedEffect(selected,calm){if(!dragging){if(calm)thumb.snapTo(selected.toFloat())else thumb.animateTo(selected.toFloat(),spring(dampingRatio=1f,stiffness=550f))}}
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical=9.dp).height(44.dp).background(c.fill,CircleShape).padding(3.dp).onSizeChanged{width=it.width.toFloat()}) {
        val segment=width/labels.size
        Box(Modifier.offset{IntOffset((thumb.value*segment).roundToInt(),0)}.width(maxWidth/labels.size).fillMaxHeight().graphicsLayer{scaleX=if(dragging && !calm)1.015f else 1f}.background(if(c.dark)c.text.copy(alpha=.14f) else c.paper,CircleShape).border(.5.dp,c.separator,CircleShape))
        Row(Modifier.fillMaxSize().pointerInput(labels,width,calm){detectHorizontalDragGestures(
            onDragStart={dragging=true;velocity.resetTracking();scope.launch{thumb.stop()}},
            onHorizontalDrag={change,amount->change.consume();velocity.addPosition(change.uptimeMillis,change.position);scope.launch(start=CoroutineStart.UNDISPATCHED){thumb.snapTo((thumb.value+amount/segment).coerceIn(-.12f,last+.12f))}},
            onDragCancel={scope.launch{if(calm)thumb.snapTo(currentSelected.toFloat())else thumb.animateTo(currentSelected.toFloat(),spring(dampingRatio=1f,stiffness=550f));dragging=false}},
            onDragEnd={val speed=velocity.calculateVelocity().x/segment;val target=(thumb.value+speed*.08f).roundToInt().coerceIn(0,labels.lastIndex);if(target!=currentSelected)haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove);select(target);scope.launch{if(calm)thumb.snapTo(target.toFloat())else thumb.animateTo(target.toFloat(),spring(dampingRatio=.9f,stiffness=550f),initialVelocity=speed);dragging=false}}
        )}) {labels.forEachIndexed{i,name->Pressable(Modifier.weight(1f).fillMaxHeight().semantics{this.selected=i==selected},name,onClick={onSelect(i)}){Label(name,14,if(i==selected)FontWeight.SemiBold else FontWeight.Normal)}}}
    }
}
@Composable fun SheetRow(label:String,icon:String,tint:Color=LocalLeafColors.current.text,onClick:()->Unit) {
    Pressable(Modifier.fillMaxWidth(),label,onClick=onClick){Row(Modifier.fillMaxWidth().padding(vertical=13.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(17.dp)){Glyph(icon,size=22,tint=tint);Label(label,17,color=tint)}}
}
@Composable fun IosSheet(title:String,onDismiss:()->Unit,translucent:Boolean=false,footer:(@Composable ()->Unit)?=null,content:@Composable ColumnScope.()->Unit) {
    val c=LocalLeafColors.current;val calm=LocalCalmMotion.current;var visible by remember{mutableStateOf(false)};val scope=rememberCoroutineScope()
    val close:()->Unit={visible=false;scope.launch{delay(if(calm)0 else 180);onDismiss()}}
    LaunchedEffect(Unit){visible=true}
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        val view=LocalView.current
        SideEffect {(view.parent as? DialogWindowProvider)?.window?.let{window->window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));window.setDimAmount(.18f);if(Build.VERSION.SDK_INT>=31){window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND);window.attributes=window.attributes.apply{blurBehindRadius=if(translucent)32 else 16}}}}
        Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(bottom=20.dp),contentAlignment=Alignment.BottomCenter) {
            Box(Modifier.fillMaxSize().clickable(indication=null,interactionSource=remember{MutableInteractionSource()}){close()})
            AnimatedVisibility(visible,enter=fadeIn(tween(if(calm)0 else 180))+slideInVertically(spring(dampingRatio=1f,stiffness=550f)){if(calm)0 else 36},exit=fadeOut(tween(if(calm)0 else 150))+slideOutVertically(tween(if(calm)0 else 180)){if(calm)0 else 24}) {
            Column(Modifier.fillMaxWidth().padding(start=10.dp,end=10.dp,bottom=10.dp).heightIn(max=650.dp).shadow(24.dp,RoundedCornerShape(32.dp)).background(if(translucent)c.paper.copy(alpha=.94f) else c.paper,RoundedCornerShape(32.dp)).padding(start=24.dp,end=24.dp,top=20.dp,bottom=48.dp)) {
                Box(Modifier.align(Alignment.CenterHorizontally).width(34.dp).height(4.dp).background(c.tertiary.copy(alpha=.25f),CircleShape))
                Row(Modifier.padding(top=18.dp,bottom=16.dp),verticalAlignment=Alignment.CenterVertically){Label(title,25,FontWeight.SemiBold,modifier=Modifier.weight(1f));Pressable(Modifier.size(36.dp).background(c.fill,CircleShape),"Close",onClick=close){Glyph("close",size=21)}}
                Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()),content=content)
                footer?.invoke()
            }
            }
        }
    }
}
@Composable fun MediaImage(file:File,modifier:Modifier,fit:Boolean=false,radius:Int=14) {
    val c=LocalLeafColors.current
    val bitmap by produceState<android.graphics.Bitmap?>(null,file,fit){value=withContext(Dispatchers.IO){val b=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(file.path,b);var sample=1;val max=if(fit)1800 else 650;while(b.outWidth/sample>max||b.outHeight/sample>max)sample*=2;BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply{inSampleSize=sample})}}
    Box(modifier.clip(RoundedCornerShape(radius.dp)).background(c.fill)){bitmap?.let{Image(it.asImageBitmap(),"Attached image",Modifier.fillMaxSize(),contentScale=if(fit)ContentScale.Fit else ContentScale.Crop)}}
}
object WorkCanceller {fun cancel(context:android.content.Context){androidx.work.WorkManager.getInstance(context).cancelUniqueWork("leaf-sync");androidx.work.WorkManager.getInstance(context).cancelUniqueWork("leaf-periodic")}}

@Composable fun SheetRowCompact(label:String,onClick:()->Unit){Pressable(Modifier.padding(horizontal=12.dp,vertical=10.dp),label,onClick=onClick){Label(label,15,color=LocalLeafColors.current.secondary)}}

@Composable fun MobileField(value:String,placeholder:String,modifier:Modifier=Modifier,singleLine:Boolean=false,onChange:(String)->Unit) {
    val c=LocalLeafColors.current
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.text.copy(alpha=if(c.dark).055f else .035f)).border(.7.dp,c.text.copy(alpha=.12f),RoundedCornerShape(12.dp)).padding(horizontal=14.dp,vertical=14.dp)) {
        BasicTextField(value,onChange,singleLine=singleLine,textStyle=TextStyle(fontSize=16.sp,color=c.text),cursorBrush=SolidColor(c.accent),modifier=Modifier.fillMaxWidth(),decorationBox={inner->if(value.isEmpty())Label(placeholder,16,color=c.secondary);inner()})
    }
}
@Composable fun SubtleDivider() { val c=LocalLeafColors.current;Box(Modifier.fillMaxWidth().height(.5.dp).background(c.text.copy(alpha=.08f))) }
