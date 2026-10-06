package com.jinki.app.ui.nowplaying

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min


@Composable
fun rememberArtworkColors(candidates: List<String>): List<Color> {
    val context = LocalContext.current
    var colors by remember { mutableStateOf<List<Color>>(emptyList()) }

    LaunchedEffect(candidates) {
        var result: List<Color> = emptyList()
        for (url in candidates) {
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(64)
                .allowHardware(false) 
                .build()
            val drawable = (context.imageLoader.execute(request) as? SuccessResult)?.drawable
            val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: continue
            result = withContext(Dispatchers.Default) { dominantBlobColors(bitmap) }
            if (result.isNotEmpty()) break
        }
        colors = result
    }
    return colors
}


private fun dominantBlobColors(bitmap: Bitmap): List<Color> {
    val w = bitmap.width
    val h = bitmap.height
    if (w == 0 || h == 0) return emptyList()
    val pixels = IntArray(w * h)
    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

    val buckets = 12
    val weight = FloatArray(buckets)
    val rSum = FloatArray(buckets)
    val gSum = FloatArray(buckets)
    val bSum = FloatArray(buckets)
    var avgR = 0f
    var avgG = 0f
    var avgB = 0f
    var counted = 0
    val hsv = FloatArray(3)

    for (p in pixels) {
        if (android.graphics.Color.alpha(p) < 200) continue
        val r = android.graphics.Color.red(p)
        val g = android.graphics.Color.green(p)
        val b = android.graphics.Color.blue(p)
        avgR += r
        avgG += g
        avgB += b
        counted++

        android.graphics.Color.colorToHSV(p, hsv)
        val s = hsv[1]
        val v = hsv[2]
        if (s < 0.18f || v < 0.18f) continue
        val bucket = ((hsv[0] / 360f) * buckets).toInt().coerceIn(0, buckets - 1)
        val wgt = s * v
        weight[bucket] += wgt
        rSum[bucket] += r * wgt
        gSum[bucket] += g * wgt
        bSum[bucket] += b * wgt
    }
    if (counted == 0) return emptyList()

    
    val ranked = (0 until buckets).filter { weight[it] > 0f }.sortedByDescending { weight[it] }
    val picked = mutableListOf<Int>()
    for (bucket in ranked) {
        val tooClose = picked.any {
            val d = abs(it - bucket)
            min(d, buckets - d) < 2
        }
        if (!tooClose) picked += bucket
        if (picked.size == 3) break
    }

    
    if (picked.isEmpty()) {
        val v = ((avgR + avgG + avgB) / (3f * counted * 255f)).coerceIn(0.55f, 0.9f)
        return listOf(
            Color.hsv(220f, 0.10f, v, 0.8f),
            Color.hsv(30f, 0.10f, v, 0.8f),
            Color.hsv(0f, 0f, (v - 0.1f).coerceAtLeast(0.5f), 0.8f)
        )
    }

    fun colorOf(bucket: Int): FloatArray {
        val wgt = weight[bucket]
        val argb = android.graphics.Color.rgb(
            (rSum[bucket] / wgt).toInt().coerceIn(0, 255),
            (gSum[bucket] / wgt).toInt().coerceIn(0, 255),
            (bSum[bucket] / wgt).toInt().coerceIn(0, 255)
        )
        val out = FloatArray(3)
        android.graphics.Color.colorToHSV(argb, out)
        return out
    }

    
    fun lifted(hue: Float, sat: Float, value: Float) = Color.hsv(
        hue = ((hue % 360f) + 360f) % 360f,
        saturation = sat.coerceIn(0.35f, 0.85f),
        value = value.coerceIn(0.65f, 0.95f),
        alpha = 0.8f
    )

    val colors = picked.map { bucket ->
        val c = colorOf(bucket)
        lifted(c[0], c[1], c[2])
    }.toMutableList()

    
    val base = colorOf(picked[0])
    val shifts = listOf(35f, -35f)
    var i = 0
    while (colors.size < 3) {
        colors += lifted(base[0] + shifts[i % shifts.size], base[1], base[2])
        i++
    }
    return colors
}
