package com.jinki.app.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.jinki.app.R
import com.jinki.app.data.Track
import com.jinki.app.data.TrackRepository
import com.jinki.app.data.UserPrefs
import com.jinki.app.data.download.fetchTrackToFile
import com.jinki.app.data.source.SourceRef
import com.jinki.app.data.source.SourceRegistry
import com.jinki.app.data.source.applyStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File


class PlayerConnection(
    private val context: Context,
    val scope: CoroutineScope,
    private val prefs: UserPrefs,
    private val tracks: TrackRepository,
    private val http: OkHttpClient
) {

    val sleepTimer = SleepTimer(scope, onElapsed = {})

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    
    private val _timerElapsed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val timerElapsed: SharedFlow<Unit> = _timerElapsed.asSharedFlow()

    
    private val _playbackErrors = MutableSharedFlow<PlaybackError>(extraBufferCapacity = 4)
    val playbackErrors: SharedFlow<PlaybackError> = _playbackErrors.asSharedFlow()

    private var controller: MediaController? = null
    private var ticker: Job? = null

    
    private val pendingCommands = ArrayDeque<(MediaController) -> Unit>()

    
    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    init {
        val appContext = context.applicationContext
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener(
            { onControllerReady(runCatching { future.get() }.getOrNull()) },
            ContextCompat.getMainExecutor(appContext)
        )
        scope.launch {
            val saved = try { prefs.repeatMode.first() } catch (_: Exception) { RepeatMode.OFF }
            _state.value = _state.value.copy(repeatMode = saved)
        }
    }

    
    private fun onController(command: (MediaController) -> Unit) {
        val c = controller
        if (c != null) {
            runCommand(c, command)
            return
        }
        if (pendingCommands.size >= MAX_PENDING_COMMANDS) pendingCommands.removeFirst()
        pendingCommands.addLast(command)
    }

    
    private fun runCommand(c: MediaController, command: (MediaController) -> Unit) {
        try {
            command(c)
        } catch (e: SecurityException) {
            // Android 12+ throws ForegroundServiceStartNotAllowedException (a
            // SecurityException) when playback would pull the service to the
            // foreground from the background. Surface it, never crash on it.
            notifyPlaybackError(context.getString(R.string.playback_error_background_start))
        }
    }

    
    suspend fun ensureLocal(
        ref: SourceRef,
        base: Track,
        onProgress: suspend (bytesWritten: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Track = withContext(Dispatchers.IO) {
        tracks.findTrack(ref.source, ref.id)?.let { return@withContext it }
        val stream = SourceRegistry.resolve(ref)
        val full = base.applyStream(stream)
        val file = fetchTrackToFile(context, http, full, stream.suffix, onProgress)
        tracks.save(full, file.absolutePath)
        full.copy(localPath = file.absolutePath)
    }

    
    fun playTracks(tracks: List<Track>, startIndex: Int = 0, autoplay: Boolean = true) {
        if (tracks.isEmpty()) return
        val index = startIndex.coerceIn(tracks.indices)
        onController { c ->
            val current = c.currentMediaItem
            val currentQueue = _queue.value
            if (tracks.size == 1 && currentQueue.size == 1 &&
                current?.mediaId == tracks[0].storageKey &&
                currentQueue[0].storageKey == tracks[0].storageKey
            ) {
                setPlaying(c, autoplay)
                syncSessionState()
                return@onController
            }
            _queue.value = tracks
            c.setMediaItems(tracks.map { it.toMediaItem() }, index, 0L)
            c.prepare()
            if (autoplay) c.play()
            syncSessionState()
        }
    }

    
    fun enqueueAndPlay(track: Track, autoplay: Boolean = true) {
        onController { c ->
            val current = _queue.value
            val currentIndex = c.currentMediaItemIndex.coerceAtLeast(0)
            if (current.getOrNull(currentIndex)?.storageKey == track.storageKey) {
                setPlaying(c, autoplay)
                syncSessionState()
                return@onController
            }
            val existing = current.indexOfFirst { it.storageKey == track.storageKey }
            if (existing >= 0) {
                c.seekTo(existing, 0L)
                if (autoplay) startOrResume(c)
            } else {
                c.addMediaItem(track.toMediaItem())
                _queue.value = current + track
                c.seekTo(current.size, 0L)
                if (autoplay) startOrResume(c)
            }
            syncSessionState()
        }
    }

    fun toggle() {
        onController { c ->
            setPlaying(c, autoplay = !c.isPlaying)
            syncSessionState()
        }
    }

    fun play() {
        onController { c ->
            startOrResume(c)
            syncSessionState()
        }
    }

    fun pause() {
        onController { c ->
            c.pause()
            syncSessionState()
        }
    }

    fun next() {
        onController { c ->
            
            
            
            
            c.seekToNext()
            startOrResume(c)
            syncSessionState()
        }
    }

    
    fun prev() {
        onController { c ->
            if (c.currentPosition > RESTART_THRESHOLD_MS) c.seekTo(0L) else c.seekToPrevious()
        }
    }

    fun seekTo(positionMs: Long) {
        onController { c ->
            
            
            recover(c, play = false)
            c.seekTo(positionMs.coerceAtLeast(0L))
        }
    }

    
    fun jumpTo(index: Int) {
        onController { c ->
            if (index in _queue.value.indices) {
                startOrResume(c)
                c.seekTo(index, 0L)
                syncSessionState()
            }
        }
    }

    
    fun removeAt(index: Int) {
        onController { c ->
            val current = _queue.value
            if (index !in current.indices) return@onController
            c.removeMediaItem(index)
            _queue.value = current.filterIndexed { i, _ -> i != index }
            syncSessionState()
        }
    }

    
    fun moveItem(from: Int, to: Int) {
        onController { c ->
            val current = _queue.value
            if (from !in current.indices || to !in current.indices || from == to) return@onController
            c.moveMediaItem(from, to)
            _queue.value = current.toMutableList().apply { add(to, removeAt(from)) }
            syncSessionState()
        }
    }
	
	fun insertAt(index: Int, track: Track) {
    	onController { c ->
        	val current = _queue.value
        	val at = index.coerceIn(0, current.size)
        	c.addMediaItem(at, track.toMediaItem())
        	_queue.value = current.toMutableList().apply { add(at, track) }
        	syncSessionState()
    	}
	}

    
    fun notifyTimerElapsed() {
        scope.launch { _timerElapsed.emit(Unit) }
    }

    
    fun notifyPlaybackError(message: String, skippedTitle: String? = null) {
        val error = PlaybackError(message = message, skippedTitle = skippedTitle)
        _state.value = _state.value.copy(error = message)
        scope.launch { _playbackErrors.emit(error) }
    }

    
    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    
    fun cycleRepeat() {
        scope.launch {
            val next = try { prefs.repeatMode.first() } catch (_: Exception) { RepeatMode.OFF }
                .next()
            try { prefs.setRepeatMode(next) } catch (_: Exception) { }
            onController { it.repeatMode = next.toExo() }
            _state.value = _state.value.copy(repeatMode = next)
        }
    }

    
    fun toggleShuffle() {
        onController { c ->
            c.shuffleModeEnabled = !c.shuffleModeEnabled
            _state.value = _state.value.copy(shuffleEnabled = c.shuffleModeEnabled)
        }
    }

    private fun onControllerReady(controller: MediaController?) {
        if (controller == null) {
            _state.value = _state.value.copy(
                error = context.getString(R.string.player_unavailable)
            )
            return
        }
        this.controller = controller
        controller.addListener(ControllerListener())
        adoptControllerQueue(controller)
        
        
        val queued = pendingCommands.toList()
        pendingCommands.clear()
        queued.forEach { runCommand(controller, it) }
        syncSessionState()
        restartTicker()
        scope.launch { restoreLastTrack() }
    }

    
    private suspend fun restoreLastTrack() {
        val resume = try {
            tracks.observeResume().first()
        } catch (_: Exception) {
            null
        } ?: return
        val path = resume.track.localPath
        if (path.isNullOrBlank() || !File(path).exists()) return
        val c = controller ?: return
        if (c.mediaItemCount > 0) return
        _queue.value = listOf(resume.track)
        c.setMediaItem(resume.track.toMediaItem())
        c.prepare()
        c.seekTo(resume.positionMs.coerceAtLeast(0L))
        syncSessionState()
    }

    
    private fun adoptControllerQueue(c: MediaController) {
        if (_queue.value.isNotEmpty() || c.mediaItemCount == 0) return
        val restored = (0 until c.mediaItemCount).mapNotNull { index ->
            runCatching { c.getMediaItemAt(index).toTrack() }.getOrNull()
        }
        if (restored.isNotEmpty()) _queue.value = restored
    }

    
    private fun setPlaying(c: MediaController, autoplay: Boolean) {
        if (autoplay) {
            startOrResume(c)
        } else {
            c.pause()
        }
    }

    
    private fun startOrResume(c: MediaController) {
        recover(c, play = true)
    }

    
    private fun recover(c: MediaController, play: Boolean) {
        if (c.playbackState == Player.STATE_IDLE && c.currentMediaItem != null) c.prepare()
        if (play) c.play()
    }

    private inner class ControllerListener : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            restartTicker()
            syncSessionState()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            syncSessionState()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            
            
            adoptControllerQueue(controller ?: return)
            syncSessionState()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            adoptControllerQueue(controller ?: return)
            syncSessionState()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _state.value = _state.value.copy(repeatMode = RepeatMode.fromExo(repeatMode))
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _state.value = _state.value.copy(shuffleEnabled = shuffleModeEnabled)
        }

        override fun onPlayerError(error: PlaybackException) {
            
            
            
            
        }
    }

    private fun syncSessionState() {
        val c = controller ?: return
        val index = c.currentMediaItemIndex.coerceAtLeast(0)
        val currentQueue = _queue.value
        val playbackState = c.playbackState
        _state.value = PlayerUiState(
            track = currentQueue.getOrNull(index),
            currentIndex = index,
            isPlaying = c.isPlaying,
            positionMs = c.currentPosition.coerceAtLeast(0L),
            durationMs = c.duration.coerceAtLeast(0L),
            repeatMode = RepeatMode.fromExo(c.repeatMode),
            shuffleEnabled = c.shuffleModeEnabled,
            isConnected = true,
            
            
            
            canGoNext = currentQueue.size > 1 || c.repeatMode == Player.REPEAT_MODE_ALL,
            canGoPrevious = currentQueue.isNotEmpty(),
            playbackState = playbackState,
            isBuffering = playbackState == Player.STATE_BUFFERING,
            
            
            
            error = if (c.playerError != null || playbackState == Player.STATE_IDLE) {
                _state.value.error
            } else {
                null
            }
        )
    }

    
    private fun restartTicker() {
        ticker?.cancel()
        if (controller?.isPlaying != true) return
        ticker = scope.launch {
            while (true) {
                delay(TICK_MS)
                val c = controller ?: break
                if (!c.isPlaying) break
                _state.value = _state.value.copy(
                    isPlaying = true,
                    positionMs = c.currentPosition.coerceAtLeast(0L),
                    durationMs = c.duration.coerceAtLeast(0L)
                )
            }
        }
    }

    companion object {
        const val TICK_MS = 500L
        const val RESTART_THRESHOLD_MS = 3_000L
        const val MAX_PENDING_COMMANDS = 16
    }
}


data class PlayerUiState(
    val track: Track? = null,
    
    val currentIndex: Int = 0,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffleEnabled: Boolean = false,
    val isConnected: Boolean = false,
    val canGoNext: Boolean = false,
    val canGoPrevious: Boolean = false,
    
    val playbackState: Int = Player.STATE_IDLE,
    val isBuffering: Boolean = false,
    val error: String? = null
)


data class PlaybackError(
    val message: String,
    
    val skippedTitle: String? = null
)
