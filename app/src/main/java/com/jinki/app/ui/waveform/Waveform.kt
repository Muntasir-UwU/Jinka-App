package com.jinki.app.ui.waveform

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jinki.app.ui.theme.TextPrimary
import com.jinki.app.ui.theme.TextTertiary
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin


@Composable
fun Waveform(
    seed: String,
    progress: Float,
    modifier: Modifier = Modifier,
    barWidth: Dp = WaveBarWidth,
    barGap: Dp = WaveBarGap,
    barColor: Color = TextTertiary,
    playedColor: Color = TextPrimary
) {
    BoxWithConstraints(modifier) {
        val slot = barWidth + barGap
        
        val count = max(MinBars, ((maxWidth + barGap) / slot).toInt())
        val heights = remember(seed, count) { generateWaveformHeights(seed, count) }
        val playedCount = (heights.size * progress.coerceIn(0f, 1f)).roundToInt()

        Canvas(Modifier.fillMaxSize()) {
            val barPx = barWidth.toPx()
            val gapPx = barGap.toPx()
            val scale = size.height / MaxHeightPx
            val totalWidth = heights.size * barPx + (heights.size - 1) * gapPx
            var x = (size.width - totalWidth) / 2f
            val radius = CornerRadius(barPx / 2f, barPx / 2f)

            heights.forEachIndexed { index, units ->
                val h = units * scale
                drawRoundRect(
                    color = if (index < playedCount) playedColor else barColor,
                    topLeft = Offset(x, (size.height - h) / 2f),
                    size = Size(barPx, h),
                    cornerRadius = radius
                )
                x += barPx + gapPx
            }
        }
    }
}


fun generateWaveformHeights(seed: String, count: Int): List<Int> {
    if (count <= 0) return emptyList()
    val rand = Mulberry32(hashSeed(seed))
    val waves = List(3) {
        Wave(
            freq = 1.0 + rand.nextDouble() * 5.0,
            phase = rand.nextDouble() * PI * 2.0,
            amp = 0.4 + rand.nextDouble() * 0.6
        )
    }

    return List(count) { i ->
        val x = (i.toDouble() / count) * PI * 2.0
        var v = waves.sumOf { sin(x * it.freq + it.phase) * it.amp } / waves.size
        v = (v + 1.0) / 2.0
        v = v * 0.75 + rand.nextDouble() * 0.25
        v = v.coerceIn(0.0, 1.0)
        
        
        (MinHeightPx + v * (MaxHeightPx - MinHeightPx)).roundToInt()
    }
}

private data class Wave(val freq: Double, val phase: Double, val amp: Double)


internal fun hashSeed(str: String): Int {
    var h = FNV_OFFSET_BASIS
    for (c in str) {
        h = h xor c.code
        h *= FNV_PRIME
    }
    return h
}

private const val FNV_OFFSET_BASIS = -2128831035 
private const val FNV_PRIME = 16777619


internal class Mulberry32(seed: Int) {
    private var a = seed

    fun nextDouble(): Double {
        a += MULBERRY_INCREMENT
        var t = (a xor (a ushr 15)) * (1 or a)
        t = (t + (t xor (t ushr 7)) * (61 or t)) xor t
        return (t xor (t ushr 14)).toUInt().toDouble() / UINT_RANGE
    }

    companion object {
        const val MULBERRY_INCREMENT = 0x6D2B79F5
        const val UINT_RANGE = 4294967296.0
    }
}

val WaveBarWidth: Dp = 3.dp
val WaveBarGap: Dp = 2.dp
const val MinBars = 24

private const val MinHeightPx = 4
private const val MaxHeightPx = 34
