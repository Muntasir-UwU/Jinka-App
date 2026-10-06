package com.jinki.app.ui.nowplaying

import androidx.compose.runtime.Immutable
import com.jinki.app.player.RepeatMode

@Immutable
data class NowPlayingState(
    val title: String,
    val artist: String,
    val positionMs: Long,
    val durationMs: Long,
    val isPlaying: Boolean,
    val isLiked: Boolean,
    val repeatMode: RepeatMode,
    val shuffle: Boolean,
    val isBuffering: Boolean,
    val error: String?,
    val waveform: List<Float>,
    val queue: List<QueueItem> = emptyList(),
    val currentIndex: Int = -1,
    val lyrics: List<String> = emptyList(),
    val activeLyricIndex: Int = -1
)

@Immutable
data class NowPlayingActions(
    val onCollapse: () -> Unit = {},
    val onMore: () -> Unit = {},
    val onToggleLike: () -> Unit = {},
    val onPlayPause: () -> Unit = {},
    val onPrevious: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onCycleRepeat: () -> Unit = {},
    val onToggleShuffle: () -> Unit = {},
    val onSeek: (Float) -> Unit = {},
    val onLyrics: () -> Unit = {},
    val onQueue: () -> Unit = {},
    val onQueueItemClick: (Int) -> Unit = {},
    val onQueueRemove: (Int) -> Unit = {},
    val onQueueMove: (from: Int, to: Int) -> Unit = { _, _ -> },
    val onLyricClick: (Int) -> Unit = {}
)
