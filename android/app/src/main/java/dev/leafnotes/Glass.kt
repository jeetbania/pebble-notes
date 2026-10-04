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
fun Modifier.frosted(shape:Shape,light:Boolean=false):Modifier = composed {
    val source=LocalBackdrop.current;val glass=rememberGraphicsLayer();var origin by remember{mutableStateOf(Offset.Zero)};val c=LocalLeafColors.current
    val radius=with(androidx.compose.ui.platform.LocalDensity.current){(if(light)8.dp else 18.dp).toPx()}
    this.clip(shape).onGloballyPositioned{origin=it.positionInRoot()}.drawWithContent {
        if(source!=null && Build.VERSION.SDK_INT>=31) {glass.renderEffect=BlurEffect(radius,radius,TileMode.Clamp);glass.record {translate(source.origin.x-origin.x,source.origin.y-origin.y){drawLayer(source.layer)}};drawLayer(glass)}
        drawRect(c.paper.copy(alpha=if(Build.VERSION.SDK_INT>=31)if(light).22f else .72f else .95f));drawContent()
    }
}
