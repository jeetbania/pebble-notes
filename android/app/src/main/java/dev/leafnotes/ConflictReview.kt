package dev.leafnotes

import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

@Composable fun ConflictReview(store:Store,onClose:()->Unit) {
    val c=LocalPebbleColors.current
    val versions=store.heads.filter{it.noteId==store.selected}.sortedByDescending{it.createdAt}
    IosSheet("One note, two directions",onDismiss=onClose,footer={GhostAction("Decide later","close",onClick=onClose)}) {
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.background(c.accent.copy(alpha=.12f),RoundedCornerShape(16.dp)).padding(14.dp)){Glyph("sync",size=24,tint=c.accent)}
            Label("Changes were made on more than one device. Choose what to continue with; earlier versions stay in History.",15,color=c.secondary,modifier=Modifier.weight(1f))
        }
        Spacer(Modifier.height(20.dp))
        versions.forEachIndexed {index,r->key(r.id) {
            val local=r.json.optString("deviceId")==store.deviceId
            Column(Modifier.fillMaxWidth().background(c.fill,RoundedCornerShape(20.dp)).border(.7.dp,c.separator,RoundedCornerShape(20.dp)).padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Label(if(local)"This phone" else "Another device",13,FontWeight.Medium);Label("Version ${index+1}",13,color=c.secondary)}
                Label(r.note.displayTitle,20,FontWeight.SemiBold,lines=2)
                Label(DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(r.createdAt)),12,color=c.secondary)
                Box(Modifier.fillMaxWidth().height(.7.dp).background(c.separator))
                if(r.note.deleted){Label("Moved to Trash",16,FontWeight.Medium);Label("Choosing this version keeps the note in Trash.",14,color=c.secondary)}
                else Label(r.note.text.take(400).ifBlank{"No body text in this version."},15,color=c.secondary,lines=6)
                GhostAction(if(r.note.deleted)"Use deleted version" else "Use this version","next"){store.resolve(r);onClose()}
            }
            Spacer(Modifier.height(14.dp))
        }}
        Label("Every saved version is preserved",13,color=c.secondary)
    }
}
