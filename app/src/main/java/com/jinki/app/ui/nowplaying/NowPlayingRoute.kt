package com.jinki.app.ui.nowplaying

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jinki.app.R
import com.jinki.app.data.lyrics.Lyrics
import com.jinki.app.ui.components.LyricsSheetContent
import com.jinki.app.ui.waveform.generateWaveformHeights

@Composable
fun NowPlayingRoute(viewModel: NowPlayingViewModel, onCollapse: () -> Unit) {
    val player by viewModel.player.collectAsStateWithLifecycle()
    val track = player.track
    val lyricsCache by viewModel.lyrics.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val lastResults by viewModel.lastSearchResults.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()

    LaunchedEffect(track?.storageKey) {
        track?.let { viewModel.requestLyrics(it) }
    }

    val bars = remember(track) {
        track?.let { t ->
            generateWaveformHeights(viewModel.waveformSeed(t), WAVEFORM_BAR_COUNT)
                .map { it / WAVEFORM_MAX_HEIGHT }
        } ?: emptyList()
    }

    val candidates = remember(track, lastResults) {
        track?.let { viewModel.artworkCandidates(it, lastResults) } ?: emptyList()
    }
    val ambientColors = rememberArtworkColors(candidates)

    NowPlayingScreen(
        state = NowPlayingState(
            title = track?.title.orEmpty().ifBlank { stringResource(R.string.unknown_title) },
            artist = track?.artist.orEmpty(),
            positionMs = player.positionMs,
            durationMs = player.durationMs,
            isPlaying = player.isPlaying,
            isLiked = track?.storageKey in favorites,
            repeatMode = player.repeatMode,
            shuffle = player.shuffleEnabled,
            isBuffering = player.isBuffering,
            error = player.error,
            waveform = bars,
            queue = queue,
            currentIndex = queue.indexOfFirst { it.id == track?.storageKey },
        ),
        actions = NowPlayingActions(
            onCollapse = onCollapse,
            onToggleLike = { track?.let { viewModel.toggleFavorite(it.storageKey) } },
            onPlayPause = { viewModel.connection.toggle() },
            onPrevious = { viewModel.connection.prev() },
            onNext = { viewModel.connection.next() },
            onCycleRepeat = { viewModel.connection.cycleRepeat() },
            onToggleShuffle = { viewModel.connection.toggleShuffle() },
            onSeek = { fraction ->
                viewModel.connection.seekTo((fraction * player.durationMs).toLong())
            },
            onQueueItemClick = { viewModel.playQueueItem(it) },
            onQueueRemove = { viewModel.removeFromQueue(it) },
            onQueueMove = { from, to -> viewModel.moveInQueue(from, to) },
        ),
        lyricsContent = {
            if (track != null) {
                LyricsSheetContent(
                    title = track.title,
                    lyrics = lyricsCache[viewModel.lyricsKey(track)] ?: Lyrics.Loading,
                    positionSeconds = player.positionMs / 1000.0
                )
            }
        },
        ambientColors = ambientColors,
        artwork = {
            if (track == null) return@NowPlayingScreen
            var artIndex by remember(candidates) { mutableIntStateOf(0) }
            val model = candidates.getOrNull(artIndex)
            if (model == null) {
                ArtworkFallback()
            } else {
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    onError = {
                        if (artIndex < candidates.lastIndex) artIndex++
                    }
                )
            }
        }
    )
}

private const val WAVEFORM_BAR_COUNT = 64

private const val WAVEFORM_MAX_HEIGHT = 34f

@Composable
private fun ArtworkFallback() {
    Icon(
        imageVector = Icons.Rounded.MusicNote,
        contentDescription = null,
        tint = Color(0xFFBFBFC4),
        modifier = Modifier.fillMaxSize().padding(96.dp),
    )
}
