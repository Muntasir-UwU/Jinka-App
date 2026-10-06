package com.jinki.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.jinki.app.R
import com.jinki.app.data.lyrics.LrcParser
import com.jinki.app.data.lyrics.Lyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext



private val DefaultBackground = Color(0xFF121212)


private val EaseInOutCurve = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

private const val SCROLL_MS = 300
private const val SIZE_MS = 300

private val ActiveColor = Color.White
private val InactiveColor = Color.White.copy(alpha = 0.35f)


@Composable
fun rememberPositionSeconds(player: Player?): State<Double> =
    produceState(initialValue = 0.0, player) {
        while (true) {
            value = (player?.currentPosition ?: 0L) / 1000.0
            delay(40)
        }
    }


@Composable
fun rememberAlbumBackground(imageUrl: String?, fallback: Color = DefaultBackground): Color {
    val context = LocalContext.current
    var target by remember(imageUrl) { mutableStateOf(fallback) }
    LaunchedEffect(imageUrl) {
        if (imageUrl.isNullOrBlank()) return@LaunchedEffect
        val result = context.imageLoader.execute(
            ImageRequest.Builder(context).data(imageUrl).allowHardware(false).build()
        )
        val bitmap = (result as? SuccessResult)?.drawable?.toBitmap() ?: return@LaunchedEffect
        target = withContext(Dispatchers.Default) {
            val dominant = Palette.from(bitmap).generate().getDominantColor(fallback.toArgb())
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(dominant, hsl)
            hsl[1] = (hsl[1] * 0.7f).coerceIn(0f, 1f) 
            hsl[2] = 0.07f                             
            Color(ColorUtils.HSLToColor(hsl))
        }
    }
    val animated by animateColorAsState(target, tween(500), label = "albumBackground")
    return animated
}


@Composable
fun LyricsSheetContent(
    title: String,
    lyrics: Lyrics,
    positionSeconds: Double? = null,
    backgroundColor: Color = DefaultBackground,
    isFullScreen: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(horizontal = 20.dp)
    ) {
        Text(
            text = title.ifBlank { stringResource(R.string.lyrics_title) },
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
        Spacer(Modifier.height(12.dp))
        when (lyrics) {
            is Lyrics.Loading -> LyricsMessage(stringResource(R.string.lyrics_fetching))
            is Lyrics.Empty -> LyricsMessage(stringResource(R.string.lyrics_unavailable))
            is Lyrics.Plain -> Text(
                text = lyrics.text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = if (isFullScreen) 20.sp else 14.sp
                ),
                color = Color.White,
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
            )
            is Lyrics.Synced -> SyncedLines(
                lyrics = lyrics,
                positionSeconds = positionSeconds,
                isFullScreen = isFullScreen,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SyncedLines(
    lyrics: Lyrics.Synced,
    positionSeconds: Double?,
    isFullScreen: Boolean,
    modifier: Modifier = Modifier
) {
    val active = positionSeconds?.let { LrcParser.activeIndex(lyrics.lines, it) } ?: -1
    val activeNow by rememberUpdatedState(active)
    val listState = rememberLazyListState()
    val fontSize by animateFloatAsState(
        targetValue = if (isFullScreen) 30f else 18f,
        animationSpec = tween(SIZE_MS),
        label = "lyricFontSize"
    )

    BoxWithConstraints(modifier) {
        
        val edgeSpace = maxHeight / 2

        
        suspend fun recenter(line: Int, animate: Boolean) {
            if (line < 0) return
            val index = line + 1 
            fun visible() = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }

            if (visible() == null) {
                listState.scrollToItem(index) 
                withFrameNanos { }
            }
            val item = visible() ?: return
            val info = listState.layoutInfo
            val centre = (info.viewportStartOffset + info.viewportEndOffset) / 2f
            val delta = item.offset + item.size / 2f - centre
            listState.animateScrollBy(
                delta,
                if (animate) tween(SCROLL_MS, easing = EaseInOutCurve) else snap()
            )
        }

        
        LaunchedEffect(active) { recenter(active, animate = true) }
        
        
        LaunchedEffect(isFullScreen) {
            delay(SIZE_MS + 40L)
            recenter(activeNow, animate = true)
        }

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "top") { Spacer(Modifier.height(edgeSpace)) }
            itemsIndexed(lyrics.lines, key = { i, l -> "$i-${l.timeSeconds}" }) { i, line ->
                val isActive = i == active
                val color by animateColorAsState(
                    targetValue = if (isActive) ActiveColor else InactiveColor,
                    animationSpec = tween(SIZE_MS),
                    label = "lyricColor"
                )
                val scale by animateFloatAsState(
                    targetValue = if (isActive) 1.05f else 1.0f,
                    animationSpec = tween(SIZE_MS),
                    label = "lyricScale"
                )
                
                Text(
                    text = line.text.ifEmpty { "♪" },
                    color = color,
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * 1.3f).sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                )
            }
            item(key = "bottom") { Spacer(Modifier.height(edgeSpace)) }
        }
    }
}

@Composable
private fun LyricsMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.6f)
    )
}
