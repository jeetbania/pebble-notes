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

@Composable fun DocumentBlocks(store:Store,note:Note,noteId:String?,onEditor:(EditText)->Unit,onEditing:(Boolean)->Unit,onPreview:(Media)->Unit) {
    val c=LocalLeafColors.current; val metrics=LocalTypeSizes.current.copy(scale=note.textScale)
    var filePreview by remember { mutableStateOf<Media?>(null) }
    val saveFile=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream")){uri->if(uri!=null){val media=filePreview;if(media!=null)try{store.context.contentResolver.openOutputStream(uri)?.use{it.write(File(store.media,media.id).readBytes())}}catch(e:Exception){store.error=e.message}}}
    val reorder=remember{ReorderGroup()};var draggingBlock by remember{mutableStateOf<String?>(null)}
    var blockMenu by remember { mutableStateOf<String?>(null) };var focusNext by remember { mutableStateOf<String?>(null) }
    var slashBlock by remember { mutableStateOf<String?>(null) };var slashQuery by remember { mutableStateOf("") };var slashRange by remember { mutableStateOf(0..0) }
    note.document.forEachIndexed { index,block -> if(note.blockVisible(block,draggingBlock))key(noteId,block.id) {
        var wraps by remember{mutableStateOf(false)}
        Column(Modifier.fillMaxWidth().reorderTarget(block.id,reorder).padding(top=if(block.spans.any{it.kind in listOf("headline","title","subtitle")})18.dp else if(block.kind in listOf("check","bullet","number"))2.dp else 8.dp)) {
            if(block.isText) Row(Modifier.fillMaxWidth().padding(start=((block.indent+note.depth(block))*18).dp),verticalAlignment=if(wraps)Alignment.Top else Alignment.CenterVertically) {
                Box(Modifier.width(24.dp)) { if(store.activeBlock==block.id)ReorderGrip(block.id,reorder,note.document.filter{it.parentId==block.parentId}.map{it.id},onDragging={draggingBlock=if(it)block.id else null}){ids->store.editing?.let{n->val peers=n.document.filter{it.parentId==block.parentId}.map{it.id};val old=peers.indexOf(block.id);val next=ids.indexOf(block.id);if(old!=next)store.moveBlock(block.id,next-old)}} }
                if(block.kind=="toggle")Pressable(Modifier.size(36.dp),"Expand or collapse toggle",onClick={store.changeBlock(block.id){it.copy(collapsed=it.collapsed!=true)}}){Label(if(block.collapsed==true || draggingBlock==block.id)"▸" else "▾",20)}
                if(block.kind=="check") Pressable(Modifier.size(36.dp),if(block.checked)"Uncheck item" else "Check item",onClick={store.changeBlock(block.id){it.copy(checked=!it.checked)}}){Box(Modifier.size(22.dp).then(if(block.checked)Modifier.background(c.accent,CircleShape)else Modifier.border(1.3.dp,c.tertiary,CircleShape)),contentAlignment=Alignment.Center){if(block.checked)Glyph("done",size=15,tint=Color.White)}}
                else if(block.kind in listOf("bullet","number")) Label(if(block.kind=="bullet")"•" else "${note.document.take(index+1).count{it.kind=="number"}}.",18,color=c.secondary,modifier=Modifier.width(30.dp).padding(top=4.dp))
                AndroidView(factory={context->EditText(context).apply {
                    textSize=metrics.size("body").toFloat();editorMetrics[this]=metrics;gravity=Gravity.TOP;setBackgroundColor(android.graphics.Color.TRANSPARENT);setPadding(0,0,0,0);minLines=1;setLineSpacing(3*resources.displayMetrics.density,1f);typeface=Typeface.create("sans-serif",Typeface.NORMAL)
                    inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                    var insertedBreak=-1
                    hint="Press / for commands…";tag=false;setText(attributed(Note(text=block.text,spans=block.spans),metrics))
                    setOnFocusChangeListener{_,hasFocus->if(hasFocus){activeTextEditor=this;store.activeBlock=block.id;onEditor(this)};onEditing(hasFocus)}
                    setOnKeyListener{_,key,event->if(key==KeyEvent.KEYCODE_ENTER && event.action==KeyEvent.ACTION_DOWN && store.editing?.document?.firstOrNull{it.id==block.id}?.kind in listOf("bullet","number","check","toggle")){if(store.editing?.document?.firstOrNull{it.id==block.id}?.kind=="toggle"){store.addChild(block.id);focusNext=store.activeBlock}else focusNext=store.splitList(block.id,selectionStart.coerceAtLeast(0));true}else false}
                    addTextChangedListener(object:TextWatcher {
                        override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
                        override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){if(tag!=true){applyTypingStyle(this@apply,start,count);insertedBreak=if(count==1 && s?.get(start)=='\n')start else -1}}
                        override fun afterTextChanged(s:Editable?){if(tag!=true && s!=null){val split=insertedBreak;insertedBreak=-1;if(split>=0 && store.editing?.document?.firstOrNull{it.id==block.id}?.kind in listOf("bullet","number","check","toggle")){tag=true;s.delete(split,split+1);tag=false;store.editBlock(block.id,s.toString(),spansFrom(s));if(store.editing?.document?.firstOrNull{it.id==block.id}?.kind=="toggle"){store.addChild(block.id);focusNext=store.activeBlock}else focusNext=store.splitList(block.id,split)}else {store.editBlock(block.id,s.toString(),spansFrom(s));val caret=selectionStart.coerceIn(0,s.length);val start=s.substring(0,caret).lastIndexOf('\n')+1;val prefix=s.substring(start,caret);if(prefix.startsWith("/")&&prefix.length<64){slashBlock=block.id;slashQuery=prefix.drop(1);slashRange=start until caret}else if(slashBlock==block.id)slashBlock=null}}}
                    })
                }},update={view->
                    view.post { wraps=view.lineCount>1 };val metricsChanged=editorMetrics[view]!=metrics;editorMetrics[view]=metrics;view.textSize=metrics.size("body").toFloat();view.setTextColor(c.text.toArgb());view.textCursorDrawable?.setTint(c.accent.toArgb());view.setHintTextColor(c.tertiary.toArgb());view.alpha=if(block.checked).55f else 1f
                    view.tag=true;view.text.getSpans(0,view.text.length,CompletionStrike::class.java).forEach{view.text.removeSpan(it)};if(block.kind=="check" && block.checked && view.text.isNotEmpty())view.text.setSpan(CompletionStrike(),0,view.text.length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);view.tag=false
                    if(metricsChanged || view.text.toString()!=block.text || spansFrom(view.text)!=canonicalSpans(block.spans)){val pos=view.selectionStart.coerceIn(0,block.text.length);view.tag=true;view.setText(attributed(Note(text=block.text,spans=block.spans),metrics));view.setSelection(pos);view.tag=false}
                    view.tag=true;view.text.getSpans(0,view.text.length,CompletionStrike::class.java).forEach{view.text.removeSpan(it)};if(block.kind=="check" && block.checked && view.text.isNotEmpty())view.text.setSpan(CompletionStrike(),0,view.text.length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);view.tag=false
                    if(view.hasFocus()){store.activeBlock=block.id;onEditor(view)}
                    if(focusNext==block.id){view.requestFocus();view.setSelection(0);focusNext=null}
                },modifier=Modifier.weight(1f).heightIn(min=32.dp))
            }
            else if(block.kind=="divider")Box(Modifier.fillMaxWidth().height(.5.dp).background(c.separator))
            else if(block.kind=="table") {
                Column(Modifier.horizontalScroll(rememberScrollState()).clip(RoundedCornerShape(12.dp)).background(c.fill)) {
                    block.cells.forEachIndexed{row,cells->Row {cells.forEachIndexed{col,value->BasicTextField(value,{new->store.editing?.let{n->store.update(n.editBlock(block.id){b->b.copy(cells=b.cells.mapIndexed{r,line->line.mapIndexed{k,v->if(r==row&&k==col)new else v}})},"cell:${block.id}:$row:$col")}},textStyle=TextStyle(color=c.text,fontSize=16.sp),cursorBrush=SolidColor(c.accent),modifier=Modifier.width(145.dp).heightIn(min=46.dp).border(.5.dp,c.separator).padding(12.dp))}}}
                }
                Row(Modifier.padding(top=6.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)){Pressable(Modifier.padding(6.dp),"Add row",onClick={store.changeBlock(block.id){if(it.cells.size<100)it.copy(cells=it.cells+listOf(List(it.cells[0].size){""}))else it}}){Label("+ Row",13,color=c.secondary)};Pressable(Modifier.padding(6.dp),"Add column",onClick={store.changeBlock(block.id){if(it.cells[0].size<12)it.copy(cells=it.cells.map{r->r+""})else it}}){Label("+ Column",13,color=c.secondary)}}
            } else note.attachments.firstOrNull{it.id==block.mediaId}?.let{a->
                if(block.kind=="image") {
                    val ratio=remember(a.id){val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(File(store.media,a.id).path,bounds);if(bounds.outHeight>0)bounds.outWidth.toFloat()/bounds.outHeight else 1f}
                    val width=minOf((LocalConfiguration.current.screenWidthDp-56).toFloat(),if(block.presentation=="large")540f*ratio else 220f).coerceAtLeast(1f)
                    Pressable(Modifier.fillMaxWidth(),"View ${a.name}",onClick={onPreview(a)}){MediaImage(File(store.media,a.id),Modifier.width(width.dp).aspectRatio(ratio),fit=true,radius=10)}
                } else Pressable(Modifier.fillMaxWidth(),"Open "+a.name,onClick={filePreview=a}){Label(a.name,17,modifier=Modifier.fillMaxWidth().background(c.fill,RoundedCornerShape(16.dp)).padding(18.dp))}
                BasicTextField(block.caption,{value->store.editing?.let{store.update(it.editBlock(block.id){b->b.copy(caption=value)},"caption:"+block.id)}},textStyle=TextStyle(color=c.secondary,fontSize=13.sp),cursorBrush=SolidColor(c.accent),modifier=Modifier.fillMaxWidth().padding(top=8.dp),decorationBox={inner->if(block.caption.isEmpty())Label("Add a caption…",13,color=c.tertiary);inner()})
            }
            if(block.kind=="toggle" && block.collapsed!=true && draggingBlock!=block.id)SheetRow("Add inside toggle","plus"){store.addChild(block.id);focusNext=store.activeBlock}
            if(slashBlock==block.id){val commands=listOf("text" to "Text","title" to "Heading 1","subtitle" to "Heading 2","headline" to "Heading 3","bullet" to "Bulleted list","number" to "Numbered list","check" to "Checklist","toggle" to "Toggle list","table" to "Table","divider" to "Divider").filter{val q=slashQuery.lowercase().filter{it.isLetterOrDigit()};q.isEmpty()||(it.first+" "+it.second+if(it.first=="check")" todo checkbox"else "").lowercase().split(" ").any{word->word.startsWith(q)}||it.second.lowercase().filter{ch->ch.isLetterOrDigit()}.startsWith(q)}
                Column(Modifier.fillMaxWidth().heightIn(max=320.dp).verticalScroll(rememberScrollState()).background(c.paper,RoundedCornerShape(14.dp)).border(.5.dp,c.separator,RoundedCornerShape(14.dp))){commands.forEach{(command,title)->SheetRow(title,"list"){val live=store.editing?.document?.firstOrNull{it.id==block.id}?:block;val start=slashRange.first.coerceIn(0,live.text.length);val end=(slashRange.last+1).coerceIn(start,live.text.length);val text=live.text.removeRange(start,end);store.changeBlock(block.id){it.copy(text=text,spans=if(command in listOf("title","subtitle","headline"))listOf(Span(0,text.length,command))else emptyList(),kind=if(command in listOf("title","subtitle","headline"))"text"else command,cells=if(command=="table")listOf(listOf("",""),listOf("",""))else it.cells,collapsed=if(command=="toggle")false else it.collapsed)};if(command in listOf("title","subtitle","headline"))activeTextEditor?.let{typingStyles[it]=mutableSetOf(command)};slashBlock=null}};SheetRow("Close commands","close"){slashBlock=null}}
            }
            if(!block.isText || store.activeBlock==block.id)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) { Pressable(Modifier.size(30.dp),"Block options",onClick={store.activeBlock=block.id;blockMenu=block.id}){Glyph("more",size=15,tint=c.tertiary)} }
        }
    }}
    Row(Modifier.fillMaxWidth().padding(top=8.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)){Pressable(Modifier.padding(8.dp),"Add text",onClick={store.addBlock("text")}){Label("+ Text",14,color=c.secondary)};Pressable(Modifier.padding(8.dp),"Insert table",onClick={store.addBlock("toggle")} ){Label("+ Toggle",14,color=c.secondary)};Pressable(Modifier.padding(8.dp),"Insert table",onClick={store.addBlock("table")}){Label("+ Table",14,color=c.secondary)}}
    filePreview?.let{a->IosSheet(a.name,onDismiss={filePreview=null}){
        if(a.mime=="application/pdf")PdfPages(File(store.media,a.id)) else Label("Original file saved on this device",15,color=c.secondary)
        SheetRow("Save original file","share"){saveFile.launch(a.name)}
    }}
    blockMenu?.let{id->note.document.firstOrNull{it.id==id}?.let{b->IosSheet("Block options",onDismiss={blockMenu=null}) {
        SheetRow("Move up","back"){store.moveBlock(id,-1);blockMenu=null};SheetRow("Move down","back"){store.moveBlock(id,1);blockMenu=null};SheetRow("Duplicate","compose"){store.duplicateBlock(id);blockMenu=null}
        if(b.isText){SheetRow("Indent","list"){store.changeBlock(id){it.copy(indent=(it.indent+1).coerceAtMost(8))};blockMenu=null};SheetRow("Outdent","list"){store.changeBlock(id){it.copy(indent=(it.indent-1).coerceAtLeast(0))};blockMenu=null};SheetRow("Toggle list","list"){store.changeBlock(id){it.copy(kind="toggle",collapsed=false)};blockMenu=null};SheetRow("Checklist","check"){store.changeBlock(id){it.copy(kind=if(it.kind=="check")"text" else "check")};blockMenu=null}}
        if(b.kind=="image")SheetRow(if(b.presentation=="large")"Small image" else "Large image","images"){store.changeBlock(id){it.copy(presentation=if(it.presentation=="large")"small" else "large")};blockMenu=null}
        if(b.kind=="table"){SheetRow("Remove last row","trash"){store.changeBlock(id){if(it.cells.size>1)it.copy(cells=it.cells.dropLast(1))else it};blockMenu=null};SheetRow("Remove last column","trash"){store.changeBlock(id){if(it.cells[0].size>1)it.copy(cells=it.cells.map{r->r.dropLast(1)})else it};blockMenu=null}}
        SheetRow("Remove block","trash",tint=c.danger){store.removeBlock(id);blockMenu=null}
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
