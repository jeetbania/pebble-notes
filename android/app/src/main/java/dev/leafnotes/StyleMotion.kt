package dev.leafnotes

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

@Composable fun animatedStyleBrush(style:NoteStyle,c:LeafColors):Brush {
    val calm=LocalCalmMotion.current
    val start by animateColorAsState(hexColour(style.backdrop) ?: style.paper(c),tween(if(calm)0 else 220),label="backdrop start")
    val end by animateColorAsState(hexColour(style.backdropEnd ?: style.backdrop) ?: style.paper(c),tween(if(calm)0 else 220),label="backdrop end")
    return Brush.verticalGradient(if(style.gradientDirection=="up")listOf(end,start)else listOf(start,end))
}

@Composable fun CardFade(store:Store,identity:String,index:Int,content:@Composable ()->Unit) {
    val calm=LocalCalmMotion.current
    val immediate=calm || index>=16 || identity in store.revealedCards
    val opacity=remember(identity){Animatable(if(immediate)1f else 0f)}
    LaunchedEffect(identity) {
        if(!immediate){delay(index.coerceAtMost(3)*18L);opacity.animateTo(1f,tween(160))}
        store.revealedCards.add(identity)
    }
    Box(Modifier.graphicsLayer{alpha=opacity.value},content={content()})
}
