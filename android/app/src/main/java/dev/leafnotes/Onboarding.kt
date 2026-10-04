package dev.leafnotes

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.*
import androidx.activity.compose.BackHandler

@Composable fun PebbleOnboarding(store:Store,onFinish:()->Unit,onConnect:()->Unit) {
    val c=LocalLeafColors.current
    var page by rememberSaveable {mutableStateOf(0)}
    var name by rememberSaveable {mutableStateOf(store.preferences.getString("profileName","") ?: "")}
    val keyboard=androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    fun finish(){keyboard?.hide();store.preferences.edit().putString("profileName",name.trim().take(40)).putBoolean("onboardingComplete",true).apply();onFinish()}
    BackHandler {if(page>0)page-- else finish()}
    Box(Modifier.fillMaxSize().background(c.page)) {
        Box(Modifier.fillMaxWidth().height(440.dp).background(Brush.verticalGradient(listOf(Color(0xFF6595FA).copy(alpha=if(c.dark).18f else .12f),Color(0xFF6595FA).copy(alpha=if(c.dark).10f else .06f),Color.Transparent))))
        Column(Modifier.fillMaxSize().padding(horizontal=28.dp)) {
            Row(Modifier.fillMaxWidth().height(60.dp),verticalAlignment=Alignment.CenterVertically) {
                if(page>0)Pressable(Modifier.size(44.dp),"Previous onboarding step",onClick={keyboard?.hide();page--}){Glyph("back",size=22)} else Label("PEBBLE NOTES",12,FontWeight.SemiBold,color=c.secondary)
                Spacer(Modifier.weight(1f));Pressable(Modifier.height(44.dp).padding(horizontal=12.dp),"Skip onboarding",onClick={finish()}){Label("Skip",14,color=c.secondary)}
            }
            Column(Modifier.weight(1f).fillMaxWidth().widthIn(max=520.dp).align(Alignment.CenterHorizontally).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                if(page<2)OnboardingArtwork(page,Modifier.fillMaxWidth().height(270.dp))
                else if(page==2) {Spacer(Modifier.height(32.dp));Box(Modifier.size(88.dp).background(c.accent.copy(alpha=.15f),RoundedCornerShape(30.dp)),contentAlignment=Alignment.Center){Label(if(name.trim().isEmpty())"P" else name.trim().take(1).uppercase(),38,FontWeight.SemiBold)}}
                else {Spacer(Modifier.height(32.dp));Box(Modifier.size(88.dp).background(c.accent.copy(alpha=.15f),RoundedCornerShape(30.dp)),contentAlignment=Alignment.Center){Glyph("check",size=36,tint=c.accent)}}
                Label(listOf("Little thoughts.\nA little more room.","Ideas worth keeping.\nPlans worth making.","Make it feel\nlike your space.","Your thoughts.\nYour pace.")[page],34,FontWeight.Bold)
                Label(listOf("A calm home for notes, images, and everything you want to come back to.","Collect inspiration, write with rich blocks, and turn your next steps into checklists and tasks.","What should we call you? Your name stays on this device, and you can change it later.","Pebble saves here first, even offline. Connect Google Drive when you want your notes on both devices.")[page],16,color=c.secondary)
                if(page==2) {
                    androidx.compose.material3.OutlinedTextField(value=name,onValueChange={name=it.take(40)},label={androidx.compose.material3.Text("Your name · optional")},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),colors=androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedTextColor=c.text,unfocusedTextColor=c.text,focusedBorderColor=c.accent,unfocusedBorderColor=c.separator,cursorColor=c.accent))
                    Label("Choose your accent",13,color=c.secondary)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){accentChoices.forEach{accent->Pressable(Modifier.weight(1f).height(48.dp),"$accent accent",onClick={store.preferences.edit().putString("accentColor",accent).apply()}){Box(Modifier.size(28.dp).background(accentColor(accent,c.dark),CircleShape),contentAlignment=Alignment.Center){if(store.preferences.getString("accentColor","Yellow")==accent)Glyph("done",size=16,tint=if(accent in listOf("Yellow","Green","Orange"))Color.Black else Color.White)}}}}
                }
                if(page==3) {SheetRow("Connect Google Drive","settings"){finish();onConnect()};Label("Optional. No account is needed to start writing.",13,color=c.secondary)}
                Spacer(Modifier.height(12.dp))
            }
            Column(Modifier.fillMaxWidth().widthIn(max=520.dp).align(Alignment.CenterHorizontally).padding(top=12.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){repeat(4){index->Box(Modifier.width(if(page==index)22.dp else 6.dp).height(6.dp).background(if(page==index)c.text else c.text.copy(alpha=.2f),CircleShape))}}
                Pressable(Modifier.fillMaxWidth().height(54.dp).background(c.text,CircleShape),if(page==3)"Start using Pebble" else "Continue",onClick={keyboard?.hide();if(page<3)page++ else finish()}){Label(if(page==3)"Start using Pebble" else "Continue",16,FontWeight.SemiBold,color=c.page)}
            }
        }
    }
}
@Composable private fun OnboardingArtwork(page:Int,modifier:Modifier) {
    val c=LocalLeafColors.current
    Box(modifier.clearAndSetSemantics{}) {
        Column(Modifier.align(Alignment.Center).offset(x=(-32).dp,y=(-12).dp).rotate(-9f).width(210.dp).shadow(12.dp,RoundedCornerShape(24.dp)).background(c.paper,RoundedCornerShape(24.dp)).border(.5.dp,c.text.copy(alpha=.09f),RoundedCornerShape(24.dp)).padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Glyph(if(page==0)"compose" else "calendar",size=22,tint=Color(0xFF6B98F4));Label(if(page==0)"A little perspective" else "A lighter tomorrow",19,FontWeight.SemiBold)
            if(page==0){Label("Keep the good things.\nMake room for new ones.",13,color=c.secondary);repeat(2){Box(Modifier.fillMaxWidth(if(it==0).8f else .6f).height(4.dp).background(c.text.copy(alpha=.1f),CircleShape))}}
            else listOf("Make something small","Collect a new idea","Take a little pause").forEachIndexed{i,text->Row(verticalAlignment=Alignment.CenterVertically){Glyph(if(i==0)"done" else "circle",size=17,tint=if(i==0)c.accent else c.secondary);Spacer(Modifier.width(8.dp));Label(text,12,color=c.secondary)}}
        }
        Column(Modifier.align(Alignment.BottomEnd).offset(x=(-6).dp,y=(-5).dp).rotate(8f).width(142.dp).height(157.dp).shadow(10.dp,RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Color(0xFF83ADF6),Color(0xFFB6B1EE),Color(0xFFE0C4AB))),RoundedCornerShape(24.dp)).padding(17.dp),verticalArrangement=Arrangement.SpaceBetween) {
            Glyph(if(page==0)"images" else "check",size=24,tint=Color.White)
            Label(if(page==0)"A spark of\ninspiration" else "One step\nat a time",18,FontWeight.SemiBold,color=Color.White)
        }
    }
}
