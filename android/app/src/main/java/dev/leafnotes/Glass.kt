package dev.leafnotes

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.layer.*
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize

class Backdrop(val layer: GraphicsLayer) { var origin by mutableStateOf(Offset.Zero) }
val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }
@Composable fun FrostedHost(content: @Composable () -> Unit) { val layer=rememberGraphicsLayer(); val source=remember(layer){Backdrop(layer)}; CompositionLocalProvider(LocalBackdrop provides source,content=content) }
fun Modifier.backdropSource(): Modifier = composed {
    val source=LocalBackdrop.current
    if(source==null)this else onGloballyPositioned {source.origin=it.positionInRoot()}.drawWithContent {source.layer.record {this@drawWithContent.drawContent()};drawLayer(source.layer)}
}
fun Modifier.frosted(shape:Shape,light:Boolean=false,surface:Color?=null):Modifier = composed {
    val source=LocalBackdrop.current;val glass=rememberGraphicsLayer();var origin by remember{mutableStateOf(Offset.Zero)};val c=LocalLeafColors.current
    val radius=with(androidx.compose.ui.platform.LocalDensity.current){(if(light)8.dp else 18.dp).toPx()}
    this.clip(shape).onGloballyPositioned{origin=it.positionInRoot()}.drawWithContent {
        if(source!=null && Build.VERSION.SDK_INT>=31) {glass.renderEffect=BlurEffect(radius,radius,TileMode.Clamp);glass.record {translate(source.origin.x-origin.x,source.origin.y-origin.y){drawLayer(source.layer)}};drawLayer(glass)}
        drawRect((surface ?: if(light)c.page else c.paper).copy(alpha=if(Build.VERSION.SDK_INT>=31)if(light).86f else .80f else .95f));drawContent()
    }
}

// Background-only fade: labels and touch targets stay fully opaque.
@Composable fun ScrollHeader(modifier:Modifier=Modifier,homeGlow:Boolean=false,surface:Color?=null,content:@Composable ColumnScope.()->Unit) {
    val c=LocalLeafColors.current
    val glowHeight=with(androidx.compose.ui.platform.LocalDensity.current){540.dp.toPx()}
    Box(modifier.fillMaxWidth()) {
        Box(Modifier.matchParentSize().graphicsLayer{compositingStrategy=androidx.compose.ui.graphics.CompositingStrategy.Offscreen}.drawWithContent {
            drawContent()
            drawRect(Brush.verticalGradient(0f to Color.White,.82f to Color.White,1f to Color.Transparent),blendMode=BlendMode.DstIn)
        }.frosted(RectangleShape,light=true,surface=surface).then(if(homeGlow)Modifier.background(homeGlowBrush(c.dark,glowHeight))else Modifier))
        Column(Modifier.padding(bottom=16.dp),content=content)
    }
}

fun homeGlowColor(dark:Boolean)=Color(0xFF9451E8).copy(alpha=if(dark).64f else .22f)
fun homeGlowBrush(dark:Boolean,endY:Float=Float.POSITIVE_INFINITY)=Brush.verticalGradient(0f to homeGlowColor(dark),.40f to homeGlowColor(dark).copy(alpha=if(dark).30f else .10f),1f to Color.Transparent,endY=endY)
