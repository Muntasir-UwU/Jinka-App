package com.jinki.app.ui.nowplaying

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.abs



private val BaseTop = Color(0xFF212121)
private val BaseBottom = Color(0xFF000000)

@Composable
fun AmbientBlobBackground(
    seed: String,
    modifier: Modifier = Modifier,
    colors: List<Color> = emptyList()
) {
    val dark = isSystemInDarkTheme()
    val palette = remember(seed, dark, colors) {
        if (colors.size >= 2) {
            colors.take(2).map { if (dark) it.copy(alpha = 0.6f) else it }
        } else {
            blobPalette(seed, dark)
        }
    }

    val c1 by animateColorAsState(palette[0], tween(900), label = "blob1")
    val c2 by animateColorAsState(palette[1], tween(900), label = "blob2")

    val transition = rememberInfiniteTransition(label = "blobDrift")
    val p1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift1"
    )
    val p2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(19_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift2"
    )

    Box(modifier.background(Brush.verticalGradient(listOf(BaseTop, BaseBottom)))) {
        Canvas(
            Modifier
                .fillMaxSize()
                .blur(100.dp, BlurredEdgeTreatment.Unbounded)
        ) {
            val w = size.width
            val h = size.height
            val r = size.maxDimension * 0.65f

            
            drawBlob(c1, Offset(w * (0.10f + 0.15f * p1), h * (0.08f + 0.10f * p2)), r)

            
            drawBlob(c2, Offset(w * (0.90f - 0.15f * p2), h * (0.92f - 0.10f * p1)), r)
        }
        
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.25f))
                    )
                )
        )
    }
}

private fun DrawScope.drawBlob(color: Color, center: Offset, radius: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color, color.copy(alpha = 0f)),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}


private fun blobPalette(seed: String, dark: Boolean): List<Color> {
    val h = abs(seed.hashCode())
    val baseHue = (h % 360).toFloat()
    val spread = 55f + (h / 360 % 40)         
    val saturation = 0.45f + (h / 720_000 % 20) / 100f
    val value = if (dark) 0.55f else 0.95f
    val alpha = if (dark) 0.60f else 0.70f
    fun blob(hue: Float) = Color.hsv(((hue % 360f) + 360f) % 360f, saturation, value, alpha)
    return listOf(blob(baseHue), blob(baseHue + spread))
}
