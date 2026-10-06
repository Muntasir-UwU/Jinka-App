package com.jinki.app.ui.nowplaying

import com.jinki.app.player.RepeatMode
import androidx.activity.compose.BackHandler
import com.jinki.app.ui.nowplaying.NowPlayingState
import com.jinki.app.ui.nowplaying.NowPlayingActions
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin


import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import coil.compose.AsyncImage
import com.jinki.app.R
import com.jinki.app.ui.LocalPlayerContentPadding
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

enum class SheetKind { Queue, Lyrics }
enum class RepeatMode { Off, All, One }

data class QueueItem(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String? = null, 
)

private val RepeatAmber = Color(0xFFB5761F)
private val NowPlayingBg = Color(0xFF190F1B)
private val CreamWhite = Color(0xFFFFF8E7)


@Composable
private fun nowPlayingColors() = MaterialTheme.colorScheme.copy(
    surface = NowPlayingBg,
    onSurface = CreamWhite,
    onSurfaceVariant = CreamWhite.copy(alpha = 0.65f),
    surfaceVariant = CreamWhite.copy(alpha = 0.12f),
    outline = CreamWhite.copy(alpha = 0.4f),
    outlineVariant = CreamWhite.copy(alpha = 0.22f),
)

@Composable
fun NowPlayingScreen(
    state: NowPlayingState,
    actions: NowPlayingActions,
    artwork: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
    queueContent: (@Composable () -> Unit)? = null,
    lyricsContent: (@Composable () -> Unit)? = null,
    ambientColors: List<Color> = emptyList(),
    snackbarHost: @Composable () -> Unit = {}, 
) {
    var sheet by rememberSaveable { mutableStateOf<SheetKind?>(null) }
    BackHandler(enabled = sheet != null) { sheet = null }

    
    
    val contentPadding = LocalPlayerContentPadding.current

    
    var scrub by remember { mutableStateOf<Float?>(null) }
    val liveProgress =
        if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
    val shownProgress = scrub ?: liveProgress
    val shownPositionMs = scrub?.let { (it * state.durationMs).toLong() } ?: state.positionMs

    MaterialTheme(colorScheme = nowPlayingColors()) {
    
    
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        val fullPx = with(LocalDensity.current) { maxHeight.toPx() }

        AmbientBlobBackground(
            seed = "${state.title}|${state.artist}",
            colors = ambientColors,
            modifier = Modifier.fillMaxSize()
        )

        Column(modifier.fillMaxSize().padding(contentPadding)) {
            TopBar(
                onOpenQueue = { sheet = SheetKind.Queue },
                onOpenLyrics = { sheet = SheetKind.Lyrics },
            )

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                artwork(
                    Modifier
                        .aspectRatio(1f, matchHeightConstraintsFirst = true)
                        .clip(RoundedCornerShape(20.dp))
                )
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.size(48.dp)) 
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = state.title,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = state.artist,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    
                    IconButton(onClick = actions.onToggleLike) {
                        Icon(
                            imageVector = if (state.isLiked) Icons.Rounded.Favorite
                            else Icons.Rounded.FavoriteBorder,
                            contentDescription = if (state.isLiked) "Unlike" else "Like",
                            tint = if (state.isLiked) RepeatAmber
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                
                state.error?.let {
                    Text(
                        text = it,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                Waveform(
                    progress = shownProgress,
                    onSeek = actions.onSeek,
                    waveform = state.waveform, 
                    onScrub = { scrub = it },  
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = formatTime(shownPositionMs),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatTime(state.durationMs),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                ActionsRow(
                    onPlayPause = actions.onPlayPause,
                    onPrevious = actions.onPrevious,
                    onNext = actions.onNext,
                    onToggleRepeat = actions.onCycleRepeat,
                    onToggleShuffle = actions.onToggleShuffle,
                    isPlaying = state.isPlaying,
                    repeatMode = state.repeatMode,
                    isLiked = state.isLiked,
                    isBuffering = state.isBuffering, 
                    shuffle = state.shuffle,         
                    modifier = Modifier.padding(top = 10.dp, bottom = 20.dp),
                )
            }

            bottomBar()
        }

        DragSheet(
            kind = sheet,
            fullPx = fullPx,
            onDismiss = { sheet = null },
            topInset = contentPadding.calculateTopPadding(),
            bottomInset = contentPadding.calculateBottomPadding(),
        ) { kind ->
            when (kind) {
                SheetKind.Queue ->
                    if (queueContent != null) queueContent()
                    else QueueContent(
                        state.queue,
                        state.currentIndex,
                        actions.onQueueItemClick,
                        actions.onQueueRemove,
                        actions.onQueueMove,
                    )
                SheetKind.Lyrics ->
                    if (lyricsContent != null) lyricsContent()
                    else LyricsContent(state.lyrics, state.activeLyricIndex, actions.onLyricClick)
            }
        }

        
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = contentPadding.calculateBottomPadding())
        ) { snackbarHost() }
    }
    } 
}

@Composable
private fun TopBar(
    onOpenQueue: () -> Unit,
    onOpenLyrics: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onOpenQueue) {
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Open queue",
                tint = CreamWhite,
            )
        }
        Text(
            text = "Now playing",
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = onOpenLyrics) {
            Icon(
                Icons.Rounded.MoreHoriz,
                contentDescription = "Open lyrics",
                tint = CreamWhite,
            )
        }
    }
}

@Composable
private fun ActionsRow(
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    isPlaying: Boolean,
    repeatMode: RepeatMode,
    isLiked: Boolean,
    modifier: Modifier = Modifier,
    isBuffering: Boolean = false, 
    shuffle: Boolean = false,     
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggleRepeat) {
            Icon(
                imageVector = if (repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne
                else Icons.Rounded.Repeat,
                contentDescription = "Repeat",
                tint = if (repeatMode == RepeatMode.OFF) muted else RepeatAmber,
            )
        }
        IconButton(onClick = onPrevious, modifier = Modifier.size(52.dp)) {
            Icon(
                Icons.Rounded.SkipPrevious, "Previous",
                Modifier.size(34.dp), tint = onSurface
            )
        }
        Surface(
            shape = CircleShape,
            color = onSurface,
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .clickable(onClick = onPlayPause),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                
                if (isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(56.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        strokeWidth = 2.dp,
                    )
                }
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(34.dp),
                    tint = MaterialTheme.colorScheme.surface,
                )
            }
        }
        IconButton(onClick = onNext, modifier = Modifier.size(52.dp)) {
            Icon(Icons.Rounded.SkipNext, "Next", Modifier.size(34.dp), tint = onSurface)
        }
        IconButton(onClick = onToggleShuffle) {
            Icon(
                Icons.Rounded.Shuffle, "Shuffle",
                
                tint = if (shuffle) RepeatAmber else muted.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun Waveform(
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    bars: Int = 52,
    waveform: List<Float> = emptyList(),   
    onScrub: (Float?) -> Unit = {},        
) {
    val amps = if (waveform.isNotEmpty()) waveform else remember(bars) {
        List(bars) { i -> 0.25f + 0.75f * abs(sin(i * 0.7f) * cos(i * 0.23f)) }
    }
    val played = MaterialTheme.colorScheme.onSurface
    val rest = MaterialTheme.colorScheme.outlineVariant
    val onScrubNow by rememberUpdatedState(onScrub)
    val onSeekNow by rememberUpdatedState(onSeek)

    Canvas(
        modifier
            .height(36.dp)
            .pointerInput(Unit) {
                detectTapGestures { onSeekNow((it.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                var last = 0f
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        last = (o.x / size.width).coerceIn(0f, 1f)
                        onScrubNow(last)
                    },
                    onDragEnd = {
                        onSeekNow(last) 
                        onScrubNow(null)
                    },
                    onDragCancel = { onScrubNow(null) },
                    onHorizontalDrag = { change, _ ->
                        last = (change.position.x / size.width).coerceIn(0f, 1f)
                        onScrubNow(last)
                    },
                )
            }
    ) {
        val n = amps.size
        val gap = 2.dp.toPx()
        val barW = (size.width - gap * (n - 1)) / n
        amps.forEachIndexed { i, a ->
            val h = size.height * a.coerceIn(0.12f, 1f)
            drawRoundRect(
                color = if (i < progress * n) played else rest,
                topLeft = Offset(i * (barW + gap), (size.height - h) / 2f),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f),
            )
        }
    }
}

@Composable
private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

@Composable
private fun BoxScope.DragSheet(
    kind: SheetKind?,
    fullPx: Float,
    onDismiss: () -> Unit,
    topInset: Dp = 0.dp,     
    bottomInset: Dp = 0.dp,  
    content: @Composable (SheetKind) -> Unit,
) {
    val density = LocalDensity.current
    val halfPx = fullPx * 0.62f
    val height = remember { Animatable(0f) }
    var shown by remember { mutableStateOf<SheetKind?>(null) }

    LaunchedEffect(kind) {
        if (kind != null) {
            shown = kind
            if (height.value < halfPx) {
                height.animateTo(halfPx, tween(durationMillis = 320, easing = FastOutSlowInEasing))
            }
        } else {
            height.animateTo(0f, tween(durationMillis = 240, easing = FastOutLinearInEasing))
            shown = null
        }
    }

    val current = shown
    if (current == null || height.value < 1f) return

    val expanded = ((height.value - halfPx) / (fullPx - halfPx)).coerceIn(0f, 1f)
    val appear = (height.value / halfPx).coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f * appear * (1f - expanded)))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
    )

    val corner = lerp(16.dp, 0.dp, expanded)
    Surface(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(with(density) { height.value.toDp() }),
        shape = RoundedCornerShape(topStart = corner, topEnd = corner),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.fillMaxSize()) {
            val scope = rememberCoroutineScope()
            val draggable = rememberDraggableState { delta ->
                scope.launch { height.snapTo((height.value - delta).coerceIn(0f, fullPx)) }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .draggable(
                        state = draggable,
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity ->
                            val h = height.value
                            when {
                                velocity < -1500f || h > (halfPx + fullPx) / 2f ->
                                    height.settleTo(fullPx, velocity)
                                velocity > 1500f && h <= halfPx -> onDismiss()
                                velocity > 1500f -> height.settleTo(halfPx, velocity)
                                h > halfPx * 0.55f -> height.settleTo(halfPx, velocity)
                                else -> onDismiss()
                            }
                        },
                    )
                    .padding(top = 10.dp + lerp(0.dp, topInset, expanded), bottom = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(width = 40.dp, height = 5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.outline)
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Crossfade(
                    targetState = current,
                    modifier = Modifier.weight(1f),
                    animationSpec = tween(200),
                    label = "sheetTitle",
                ) { kind ->
                    Text(
                        text = if (kind == SheetKind.Queue) "Queue" else "Lyrics",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }

            Box(
                Modifier
                    .weight(1f)
                    .padding(bottom = bottomInset)
                    .graphicsLayer { alpha = ((appear - 0.4f) / 0.6f).coerceIn(0f, 1f) }
            ) {
                Crossfade(
                    targetState = current,
                    animationSpec = tween(220),
                    label = "sheetContent",
                ) { kind -> content(kind) }
            }
        }
    }
}

private suspend fun Animatable<Float, AnimationVector1D>.settleTo(target: Float, velocityY: Float) {
    animateTo(
        targetValue = target,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        initialVelocity = -velocityY,
    )
}

private val QueueRowHeight = 64.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueContent(
    queue: List<QueueItem>,
    currentIndex: Int,
    onClick: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
) {
    if (queue.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.queue_empty),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = currentIndex.coerceIn(0, queue.lastIndex)
    )
    val haptics = LocalHapticFeedback.current 

    val rowPx = with(LocalDensity.current) { QueueRowHeight.toPx() }
    val lastIndexNow by rememberUpdatedState(queue.lastIndex)
    val onMoveNow by rememberUpdatedState(onMove)
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    
    LaunchedEffect(currentIndex) {
        if (draggingId == null && currentIndex in queue.indices) {
            listState.animateScrollToItem(currentIndex)
        }
    }

    
    fun settle(startIndex: Int) {
        var idx = startIndex
        while (dragOffset > rowPx / 2 && idx < lastIndexNow) {
            onMoveNow(idx, idx + 1)
            idx++
            dragOffset -= rowPx
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        while (dragOffset < -rowPx / 2 && idx > 0) {
            onMoveNow(idx, idx - 1)
            idx--
            dragOffset += rowPx
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    
    LaunchedEffect(draggingId) {
        val id = draggingId ?: return@LaunchedEffect
        while (isActive) {
            val info = listState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.key == id }
            if (item != null) {
                val top = item.offset + dragOffset
                val bottom = top + item.size
                val edge = rowPx * 0.5f
                val step = when {
                    top < info.viewportStartOffset + edge -> -14f
                    bottom > info.viewportEndOffset - edge -> 14f
                    else -> 0f
                }
                if (step != 0f) {
                    val consumed = listState.scrollBy(step)
                    dragOffset += consumed
                    settle(item.index)
                }
            }
            delay(16)
        }
    }

    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        itemsIndexed(queue, key = { _, item -> item.id }) { index, item ->
            QueueRow(
                modifier = if (draggingId == item.id) Modifier else Modifier.animateItemPlacement(),
                item = item,
                index = index,
                isCurrent = index == currentIndex,
                isDragging = draggingId == item.id,
                dragOffset = if (draggingId == item.id) dragOffset else 0f,
                onClick = { onClick(index) },
                onRemove = onRemove,
                onMoveBy = { i, delta -> onMoveNow(i, i + delta) }, 
                onDragStart = {
                    draggingId = item.id
                    dragOffset = 0f
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress) 
                },
                onDragBy = { i, dy ->
                    dragOffset += dy
                    if (dragOffset > rowPx / 2 && i < lastIndexNow) {
                        onMoveNow(i, i + 1)
                        dragOffset -= rowPx
                    } else if (dragOffset < -rowPx / 2 && i > 0) {
                        onMoveNow(i, i - 1)
                        dragOffset += rowPx
                    }
                },
                onDragEnd = {
                    draggingId = null
                    dragOffset = 0f
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress) 
                },
            )
        }
    }
}

@Composable
private fun QueueRow(
    modifier: Modifier = Modifier,
    item: QueueItem,
    index: Int,
    isCurrent: Boolean,
    isDragging: Boolean,
    dragOffset: Float,
    onClick: () -> Unit,
    onRemove: (Int) -> Unit,
    onDragStart: () -> Unit,
    onDragBy: (index: Int, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
    onMoveBy: (index: Int, delta: Int) -> Unit = { _, _ -> }, 
) {
    val scope = rememberCoroutineScope()
    val swipe = remember(item.id) { Animatable(0f) }
    val threshold = with(LocalDensity.current) { 120.dp.toPx() }
    val indexNow by rememberUpdatedState(index)
    val onRemoveNow by rememberUpdatedState(onRemove)
    val onDragByNow by rememberUpdatedState(onDragBy)
    val onMoveByNow by rememberUpdatedState(onMoveBy) 

    val lift by animateFloatAsState(if (isDragging) 1f else 0f, label = "dragLift")

    Box(
        modifier
            .fillMaxWidth()
            .height(QueueRowHeight)
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                translationY = dragOffset
                scaleX = 1f + 0.03f * lift
                scaleY = 1f + 0.03f * lift
                shadowElevation = 16f * lift
            }
            
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction("Move up") {
                        if (indexNow > 0) { onMoveByNow(indexNow, -1); true } else false
                    },
                    CustomAccessibilityAction("Move down") {
                        onMoveByNow(indexNow, 1); true
                    },
                    CustomAccessibilityAction("Remove from queue") {
                        onRemoveNow(indexNow); true
                    },
                )
            }
    ) {
        if (swipe.value != 0f) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = if (swipe.value > 0f) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "Remove from queue",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        Row(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = swipe.value }
                .background(MaterialTheme.colorScheme.surface)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { d ->
                        scope.launch { swipe.snapTo(swipe.value + d) }
                    },
                    onDragStopped = {
                        val x = swipe.value
                        if (abs(x) > threshold) {
                            swipe.animateTo(if (x > 0f) 1500f else -1500f, tween(180))
                            onRemoveNow(indexNow)
                        } else {
                            swipe.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                        }
                    },
                )
                .clickable(onClick = onClick)
                .padding(start = 20.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                
                if (item.artworkUrl != null) {
                    AsyncImage(
                        model = item.artworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    item.title,
                    fontSize = 15.sp,
                    fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
                    color = if (isCurrent) RepeatAmber else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.artist,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isCurrent) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Now playing", tint = RepeatAmber)
            }
            Icon(
                imageVector = Icons.Rounded.DragHandle,
                contentDescription = "Hold and drag to reorder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(12.dp)
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onDragStart() },
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragEnd,
                            onDrag = { change, amount ->
                                change.consume()
                                onDragByNow(indexNow, amount.y)
                            },
                        )
                    },
            )
        }
    }
}

@Composable
private fun LyricsContent(lyrics: List<String>, activeLyricIndex: Int, onClick: (Int) -> Unit) {
    if (lyrics.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No lyrics",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val listState = rememberLazyListState()
        val edgePad = (maxHeight / 2 - 24.dp).coerceAtLeast(0.dp)

        LaunchedEffect(activeLyricIndex) {
            if (activeLyricIndex in lyrics.indices) listState.animateScrollToItem(activeLyricIndex)
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(top = edgePad, bottom = edgePad),
        ) {
            itemsIndexed(lyrics) { index, line ->
                val distance = if (activeLyricIndex < 0) 1 else abs(index - activeLyricIndex)
                val target = when (distance) {
                    0 -> 1f
                    1 -> 0.55f
                    2 -> 0.35f
                    else -> 0.22f
                }
                val alpha by animateFloatAsState(target, label = "lyricAlpha")
                val scale by animateFloatAsState(if (distance == 0) 1f else 0.92f, label = "lyricScale")
                Text(
                    text = line,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp,
                    lineHeight = 30.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .clickable { onClick(index) }
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                )
            }
        }
    }
}

private data class NavDest(val label: String, val icon: ImageVector)

private val navDestinations = listOf(
    NavDest("Home", Icons.Rounded.Public),
    NavDest("Search", Icons.Rounded.Search),
    NavDest("Library", Icons.Rounded.LibraryMusic),
    NavDest("You", Icons.Rounded.Person),
)

@Composable
fun JinkaBottomBar(selected: Int, onSelect: (Int) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        navDestinations.forEachIndexed { i, dest ->
            NavigationBarItem(
                selected = i == selected,
                onClick = { onSelect(i) },
                icon = { Icon(dest.icon, contentDescription = dest.label) },
                label = { Text(dest.label) },
            )
        }
    }
}
