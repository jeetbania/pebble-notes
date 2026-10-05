package dev.leafnotes

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.text.*
import android.view.Gravity
import android.view.KeyEvent
import android.widget.EditText
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable fun DocumentBlocks(store:Store,sourceNote:Note,noteId:String?,onEditor:(EditText)->Unit,onEditing:(Boolean)->Unit,onPreview:(Media)->Unit) {
    var previewNote by remember(noteId){mutableStateOf<Note?>(null)}
    val note=previewNote ?: sourceNote
    val c=LocalLeafColors.current; val metrics=LocalTypeSizes.current.copy(scale=note.textScale)
    var filePreview by remember { mutableStateOf<Media?>(null) }
    val saveFile=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream")){uri->if(uri!=null){val media=filePreview;if(media!=null)try{store.context.contentResolver.openOutputStream(uri)?.use{it.write(File(store.media,media.id).readBytes())}}catch(e:Exception){store.error=e.message}}}
    val reorder=remember{ReorderGroup()};var draggingBlock by remember{mutableStateOf<String?>(null)}
    var blockMenu by remember { mutableStateOf<String?>(null) };var focusNext by remember { mutableStateOf<String?>(null) }
    var slashBlock by remember { mutableStateOf<String?>(null) };var slashQuery by remember { mutableStateOf("") };var slashRange by remember { mutableStateOf(0..0) }
    fun suggestions(query:String)=listOf("text" to "Text","title" to "Heading 1","subtitle" to "Heading 2","headline" to "Heading 3","bullet" to "Bulleted list","number" to "Numbered list","check" to "Checklist","toggle" to "Toggle list","quote" to "Quote","callout" to "Idea","table" to "Table","divider" to "Divider").filter{val q=query.lowercase().filter{it.isLetterOrDigit()};val alias=when(it.first){"title"->"h1 heading1";"subtitle"->"h2 heading2";"headline"->"h3 heading3";"check"->"todo checkbox";else->""};q.isEmpty()||(it.first+" "+it.second+" "+alias).lowercase().split(" ").any{word->word.startsWith(q)}||it.second.lowercase().filter{ch->ch.isLetterOrDigit()}.startsWith(q)}
    fun applyCommand(id:String,command:String) {
        val live=store.editing?.document?.firstOrNull{it.id==id}?:return
        val start=slashRange.first.coerceIn(0,live.text.length);val end=(slashRange.last+1).coerceIn(start,live.text.length)
        val text=live.text.removeRange(start,end)
        store.changeBlock(id){it.copy(text=text,spans=clipSpans(it.spans,0,start)+clipSpans(it.spans,end,it.text.length-end).map{s->s.copy(start=s.start+start)},textStyle=if(command in listOf("title","subtitle","headline"))command else "body",kind=if(command in listOf("title","subtitle","headline"))"text"else command,cells=if(command=="table")listOf(listOf("",""),listOf("",""))else it.cells,collapsed=false)}
        activeTextEditor?.let{typingStyles[it]=if(command in listOf("title","subtitle","headline"))mutableSetOf(command)else mutableSetOf()};slashBlock=null
    }
    fun enter(id:String,position:Int) {
        if(slashBlock==id){suggestions(slashQuery).firstOrNull()?.let{applyCommand(id,it.first);return}}
        if(store.editing?.document?.firstOrNull{it.id==id}?.kind=="toggle"){store.addChild(id);focusNext=store.activeBlock}else focusNext=store.splitList(id,position)
    }
    note.document.forEachIndexed { index,block -> if(note.blockVisible(block,draggingBlock))key(noteId,block.id) {
        var wraps by remember{mutableStateOf(false)}
        Column(Modifier.fillMaxWidth().reorderTarget(block.id,reorder).background(if(block.kind=="callout")c.text.copy(alpha=.065f)else Color.Transparent,RoundedCornerShape(12.dp)).padding(if(block.kind=="callout")10.dp else 0.dp).padding(top=if(index==0)0.dp else if(block.textStyle in listOf("headline","title","subtitle"))12.dp else if(block.parentId!=null || block.kind in listOf("check","bullet","number"))2.dp else 5.dp)) {
            if(block.isText) Row(Modifier.fillMaxWidth().padding(start=(block.indent*18+note.depth(block)*36).dp),verticalAlignment=if(wraps)Alignment.Top else Alignment.CenterVertically) {
                if(block.kind=="quote")Box(Modifier.width(3.dp).heightIn(min=32.dp).height((metrics.size("body")*1.6f).dp).background(c.text.copy(alpha=.35f),RoundedCornerShape(2.dp)).padding(end=6.dp))
                if(block.kind=="callout")Glyph("callout",size=23)
                if(block.kind=="toggle")Pressable(Modifier.width(36.dp).height(if(wraps)(metrics.size(block.textStyle ?: "body")*1.25f).dp else 32.dp),"Expand or collapse toggle",onClick={store.changeBlock(block.id){it.copy(collapsed=it.collapsed!=true)}}){Box(Modifier.graphicsLayer{rotationZ=if(block.collapsed==true || draggingBlock==block.id)0f else 90f}){Glyph("next",size=14)}}
                if(block.kind=="check") Pressable(Modifier.size(36.dp),if(block.checked)"Uncheck item" else "Check item",onClick={store.changeBlock(block.id){it.copy(checked=!it.checked)}}){Box(Modifier.size(22.dp).then(if(block.checked)Modifier.background(c.accent,CircleShape)else Modifier.border(1.3.dp,c.tertiary,CircleShape)),contentAlignment=Alignment.Center){if(block.checked)Glyph("done",size=15,tint=Color.White)}}
                else if(block.kind in listOf("bullet","number")) Label(if(block.kind=="bullet")"•" else "${note.document.take(index+1).count{it.kind=="number"}}.",18,color=c.secondary,modifier=Modifier.width(30.dp).padding(top=4.dp))
                AndroidView(factory={context->EditText(context).apply {
                    textSize=metrics.size("body").toFloat();editorMetrics[this]=metrics;gravity=Gravity.TOP;setBackgroundColor(android.graphics.Color.TRANSPARENT);setPadding(0,0,0,0);minimumHeight=0;minHeight=0;includeFontPadding=false;minLines=1;setLineSpacing(2*resources.displayMetrics.density,1f);typeface=Typeface.create("sans-serif",Typeface.NORMAL)
                    inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                    var insertedBreak=-1;var inlineBreak=false
                    hint="Press / for commands…";tag=false;setText(attributed(block.renderedNote,metrics))
                    setOnFocusChangeListener{_,hasFocus->if(hasFocus){activeTextEditor=this;store.activeBlock=block.id;onEditor(this)};onEditing(hasFocus)}
                    setOnKeyListener{_,key,event->if(key==KeyEvent.KEYCODE_ENTER && event.action==KeyEvent.ACTION_DOWN){if(event.isAltPressed){inlineBreak=true;false}else {enter(block.id,selectionStart.coerceAtLeast(0));true}}else false}
                    addTextChangedListener(object:TextWatcher {
                        override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
                        override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){if(tag!=true){applyTypingStyle(this@apply,start,count);insertedBreak=if(count==1 && s?.get(start)=='\n' && !inlineBreak)start else -1;inlineBreak=false}}
                        override fun afterTextChanged(s:Editable?){if(tag!=true && s!=null){val split=insertedBreak;insertedBreak=-1;if(split>=0){tag=true;s.delete(split,split+1);tag=false;store.editBlock(block.id,s.toString(),spansFrom(s));enter(block.id,split)}else {store.editBlock(block.id,s.toString(),spansFrom(s));val caret=selectionStart.coerceIn(0,s.length);val start=s.substring(0,caret).lastIndexOf('\n')+1;val prefix=s.substring(start,caret);if(prefix.startsWith("/")&&prefix.length<64){slashBlock=block.id;slashQuery=prefix.drop(1);slashRange=start until caret}else if(slashBlock==block.id)slashBlock=null}}}
                    })
                }},update={view->
                    val live=store.editing?.document?.firstOrNull{it.id==block.id} ?: block
                    view.post { wraps=view.lineCount>1;view.gravity=if(wraps)Gravity.TOP else Gravity.CENTER_VERTICAL };val metricsChanged=editorMetrics[view]!=metrics;editorMetrics[view]=metrics;view.textSize=metrics.size(live.textStyle ?: "body").toFloat();view.typeface=Typeface.create("sans-serif",if(live.textStyle in listOf("title","subtitle","headline"))Typeface.BOLD else Typeface.NORMAL);view.setTextColor(c.text.toArgb());view.textCursorDrawable?.setTint(c.accent.toArgb());view.setHintTextColor(c.tertiary.toArgb());view.alpha=if(live.checked).55f else 1f
                    view.tag=true;view.text.getSpans(0,view.text.length,CompletionStrike::class.java).forEach{view.text.removeSpan(it)};if(live.kind=="check" && live.checked && view.text.isNotEmpty())view.text.setSpan(CompletionStrike(),0,view.text.length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);view.tag=false
                    if(metricsChanged || view.text.toString()!=live.text || spansFrom(view.text)!=canonicalSpans(live.renderedNote.spans)){val pos=view.selectionStart.coerceIn(0,live.text.length);view.tag=true;view.setText(attributed(live.renderedNote,metrics));view.setSelection(pos);view.tag=false}
                    view.tag=true;view.text.getSpans(0,view.text.length,CompletionStrike::class.java).forEach{view.text.removeSpan(it)};if(live.kind=="check" && live.checked && view.text.isNotEmpty())view.text.setSpan(CompletionStrike(),0,view.text.length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);view.tag=false
                    if(view.hasFocus()){store.activeBlock=block.id;onEditor(view)}
                    if(focusNext==block.id){view.requestFocus();view.setSelection(0);focusNext=null}
                },modifier=Modifier.weight(1f).heightIn(min=32.dp).padding(start=if(block.kind in listOf("quote","callout"))10.dp else 0.dp))
                Box(Modifier.width(44.dp)) { ReorderGrip(block.id,reorder,note.document.filter{it.parentId==block.parentId}.map{it.id},onOptions={blockMenu=block.id},onDragging={active->if(active){previewNote=sourceNote;draggingBlock=block.id}else{previewNote?.let{if(it.document!=sourceNote.document)store.update(it)};previewNote=null;draggingBlock=null}}){ids->previewNote?.let{n->val peers=n.document.filter{it.parentId==block.parentId}.map{it.id};val old=peers.indexOf(block.id);val next=ids.indexOf(block.id);if(old!=next)previewNote=n.movingBlock(block.id,next-old)}} }
            }
            else if(block.kind=="divider")Box(Modifier.fillMaxWidth().height(.5.dp).background(c.separator))
            else if(block.kind=="table") {
                MobileTable(store,block)
            } else note.attachments.firstOrNull{it.id==block.mediaId}?.let{a->
                if(block.kind=="image") {
                    val ratio=remember(a.id){val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(File(store.media,a.id).path,bounds);if(bounds.outHeight>0)bounds.outWidth.toFloat()/bounds.outHeight else 1f}
                    val width=minOf((LocalConfiguration.current.screenWidthDp-56).toFloat(),if(block.presentation=="large")540f*ratio else 220f).coerceAtLeast(1f)
                    Pressable(Modifier.fillMaxWidth(),"View ${a.name}",onClick={onPreview(a)}){MediaImage(File(store.media,a.id),Modifier.width(width.dp).aspectRatio(ratio),fit=true,radius=10)}
                } else Pressable(Modifier.fillMaxWidth(),"Open "+a.name,onClick={filePreview=a}){Label(a.name,17,modifier=Modifier.fillMaxWidth().background(c.fill,RoundedCornerShape(16.dp)).padding(18.dp))}
                BasicTextField(block.caption,{value->store.editing?.let{store.update(it.editBlock(block.id){b->b.copy(caption=value)},"caption:"+block.id)}},textStyle=TextStyle(color=c.secondary,fontSize=15.sp),cursorBrush=SolidColor(c.accent),modifier=Modifier.fillMaxWidth().padding(top=8.dp),decorationBox={inner->if(block.caption.isEmpty())Label("Add a caption…",15,color=c.tertiary);inner()})
            }
            if(slashBlock==block.id){val commands=suggestions(slashQuery)
                Column(Modifier.fillMaxWidth().padding(top=8.dp).clip(RoundedCornerShape(16.dp)).background(c.paper).border(.7.dp,c.separator,RoundedCornerShape(16.dp)).heightIn(max=260.dp).verticalScroll(rememberScrollState()).padding(horizontal=12.dp,vertical=8.dp)) {
                    Label("Blocks",14,color=c.secondary,modifier=Modifier.padding(start=8.dp,bottom=4.dp))
                    commands.forEachIndexed{i,(command,title)->Pressable(Modifier.fillMaxWidth().heightIn(min=48.dp).background(if(i==0)c.accent.copy(alpha=.10f)else Color.Transparent,RoundedCornerShape(10.dp)),title,onClick={applyCommand(block.id,command)}) {
                        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                            Box(Modifier.size(24.dp),contentAlignment=Alignment.Center){when(command){"text"->Label("T",19);"title"->Label("H1",16);"subtitle"->Label("H2",16);"headline"->Label("H3",16);"toggle"->Label("▸",20);"table"->Glyph("grid",size=21);"divider"->Glyph("minus",size=21);else->Glyph(if(command=="bullet")"list"else command,size=21)}}
                            Label(title,18,modifier=Modifier.weight(1f))
                        }
                    }}
                    if(commands.isEmpty())Label("No matching blocks",14,color=c.secondary,modifier=Modifier.padding(12.dp))
                    SubtleDivider();SheetRow("Close commands","close"){slashBlock=null}
                }
            }
            if(!block.isText)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) { Pressable(Modifier.size(44.dp),"Block options",onClick={store.activeBlock=block.id;blockMenu=block.id}){Glyph("more",size=15,tint=c.tertiary)} }
        }
    }}
    Row(Modifier.fillMaxWidth().padding(top=8.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)){Pressable(Modifier.padding(8.dp),"Add text",onClick={store.addBlock("text")}){Label("+ Text",17,color=c.secondary)};Pressable(Modifier.padding(8.dp),"Add toggle",onClick={store.addBlock("toggle")} ){Label("+ Toggle",17,color=c.secondary)};Pressable(Modifier.padding(8.dp),"Insert table",onClick={store.addBlock("table")}){Label("+ Table",17,color=c.secondary)}}
    filePreview?.let{a->IosSheet(a.name,onDismiss={filePreview=null}){
        if(a.mime=="application/pdf")PdfPages(File(store.media,a.id)) else Label("Original file saved on this device",15,color=c.secondary)
        SheetRow("Save original file","share"){saveFile.launch(a.name)}
    }}
    blockMenu?.let{id->note.document.firstOrNull{it.id==id}?.let{b->IosSheet("Block options",onDismiss={blockMenu=null}) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){listOf("Move up","Move down","Duplicate").forEachIndexed{index,title->Pressable(Modifier.weight(1f).height(48.dp).background(c.fill,CircleShape),title,onClick={if(index==2)store.duplicateBlock(id)else store.moveBlock(id,if(index==0)-1 else 1);blockMenu=null}){Box(Modifier.graphicsLayer{rotationZ=if(index==0)90f else if(index==1)-90f else 0f}){Glyph(if(index==2)"copy"else "back",description=title)}}}}
        Spacer(Modifier.height(12.dp))
        if(b.isText){Label("Turn into",14,color=c.secondary);listOf("text" to "Text","check" to "Checklist","bullet" to "Bullets","number" to "Numbers","toggle" to "Toggle","quote" to "Quote","callout" to "Idea").forEach{(kind,title)->SheetRow(title,if(kind=="text")"format"else if(kind=="bullet")"list"else kind){store.setList(kind);blockMenu=null}}}
        if(b.isText)Row(Modifier.fillMaxWidth().padding(vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){listOf(-1 to "Outdent",1 to "Indent").forEach{(delta,title)->Pressable(Modifier.weight(1f).height(48.dp).background(c.fill,CircleShape),title,onClick={store.changeBlock(id){it.copy(indent=(it.indent+delta).coerceIn(0,8))};blockMenu=null}){Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){Glyph(if(delta<0)"back"else "next",size=18);Label(title,15)}}}}
        if(b.kind=="image")SheetRow(if(b.presentation=="large")"Small image" else "Large image","images"){store.changeBlock(id){it.copy(presentation=if(it.presentation=="large")"small" else "large")};blockMenu=null}
        if(b.kind=="table"){SheetRow("Remove last row","trash"){store.changeBlock(id){if(it.cells.size>1)it.copy(cells=it.cells.dropLast(1))else it};blockMenu=null};SheetRow("Remove last column","trash"){store.changeBlock(id){if(it.cells[0].size>1)it.copy(cells=it.cells.map{r->r.dropLast(1)})else it};blockMenu=null}}
        GhostAction("Remove block","trash",tint=c.danger){store.removeBlock(id);blockMenu=null}
    }}}
}
@Composable fun DailySheet(store:Store,onDismiss:()->Unit) {
    val c=LocalLeafColors.current;var query by remember{mutableStateOf("")};var tags by remember{mutableStateOf(store.editing?.tags?.joinToString(", ") ?: "")};val history=remember(store.selected){store.flush();store.revisions().filter{it.noteId==store.selected}.sortedByDescending{it.createdAt}}
    IosSheet("Find, Tags & History",onDismiss=onDismiss){
        BasicTextField(query,{query=it},textStyle=TextStyle(color=c.text,fontSize=17.sp),cursorBrush=SolidColor(c.accent),modifier=Modifier.fillMaxWidth().background(c.fill,RoundedCornerShape(14.dp)).padding(14.dp),decorationBox={inner->if(query.isEmpty())Label("Find in this note",17,color=c.tertiary);inner()})
        if(query.isNotEmpty())store.editing?.document?.filter{(it.text+it.caption+it.cells.flatten().joinToString(" ")).contains(query,true)}?.forEach{Label(if(it.isText)it.text else if(it.kind=="table")it.cells.joinToString("\n"){row->row.joinToString(" | ")}else it.caption,15,modifier=Modifier.padding(vertical=10.dp))}
        Spacer(Modifier.height(12.dp));BasicTextField(tags,{tags=it},textStyle=TextStyle(color=c.text,fontSize=17.sp),cursorBrush=SolidColor(c.accent),modifier=Modifier.fillMaxWidth().background(c.fill,RoundedCornerShape(14.dp)).padding(14.dp),decorationBox={inner->if(tags.isEmpty())Label("Tags, separated by commas",17,color=c.tertiary);inner()})
        SheetRow("Save tags","done"){store.editing?.let{store.update(it.copy(tags=tags.split(',')))};onDismiss()}
        Label("Earlier versions",20,androidx.compose.ui.text.font.FontWeight.SemiBold,modifier=Modifier.padding(top=12.dp))
        history.forEach{r->Label(DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(r.createdAt)),13,color=c.secondary,modifier=Modifier.padding(top=14.dp));Label(r.note.text.take(160),14,lines=3);SheetRow("Restore this version","undo"){store.resolve(r);onDismiss()}}
    }
}

@Composable fun PdfPages(file:File) {
    var pages by remember(file){mutableStateOf<List<android.graphics.Bitmap>>(emptyList())};var problem by remember{mutableStateOf<String?>(null)}
    LaunchedEffect(file){try{pages=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){android.os.ParcelFileDescriptor.open(file,android.os.ParcelFileDescriptor.MODE_READ_ONLY).use{fd->android.graphics.pdf.PdfRenderer(fd).use{pdf->(0 until minOf(pdf.pageCount,20)).map{i->pdf.openPage(i).use{page->val width=700;val height=(width.toFloat()*page.height/page.width).toInt().coerceIn(1,1400);android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888).also{it.eraseColor(android.graphics.Color.WHITE);page.render(it,null,null,android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)}}}}}}}catch(e:Exception){problem=e.message}}
    pages.forEach{bitmap->androidx.compose.foundation.Image(bitmap.asImageBitmap(),"PDF page",Modifier.fillMaxWidth().padding(vertical=8.dp))};problem?.let{Label(it,14)}
    Label("Preview shows up to 20 pages. Save the original for all pages.",12,color=LocalLeafColors.current.secondary)
}
