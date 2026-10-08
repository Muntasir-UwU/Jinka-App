package com.jinki.app.player

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.jinki.app.JinkiApplication
import com.jinki.app.MainActivity
import com.jinki.app.R
import com.jinki.app.data.ListenDay
import com.jinki.app.data.ListenTracker
import com.jinki.app.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CoroutineExceptionHandler

class PlaybackService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private var session: MediaSession? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val listenTracker = ListenTracker { key, total -> flushListen(key, total) }
    private var persistJob: Job? = null

    private val app: JinkiApplication get() = application as JinkiApplication

    
    private val exceptionHandler = CoroutineExceptionHandler { _, exception ->
        Log.e("PlaybackService", "Background coroutine failed", exception)
    }

    override fun onCreate() {
        super.onCreate()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .build()
                .apply { setSmallIcon(R.drawable.ic_notification) }
        )

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(buildMediaSourceFactory(this))
            .setLoadControl(buildLoadControl())
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(ServiceListener())
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
            .build()

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .setCallback(SessionCallback())
            .build()

        app.container.connection.sleepTimer.onElapsed = {
            serviceScope.launch { runCatching { player.pause() } }
            app.container.connection.notifyTimerElapsed()
        }
        serviceScope.launch { applySavedRepeatMode() }
        persistJob = serviceScope.launch(exceptionHandler) {
            while (true) {
                delay(PERSIST_INTERVAL_MS)
                if (player.isPlaying) runCatching { persistResume() }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0 ||
            player.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        persistJob?.cancel()
        runCatching { app.container.connection.sleepTimer.onElapsed = {} }
        try {
            // The scope is torn down below, so the final persist must finish
            // first instead of racing serviceScope.cancel(). Bounded by
            // FLUSH_TIMEOUT_MS: onDestroy runs on the main thread, and both
            // calls switch off it internally (Room / withContext(IO)), so
            // this cannot deadlock — it only caps how long destroy blocks.
            runBlocking {
                withTimeoutOrNull(FLUSH_TIMEOUT_MS) {
                    runCatching { listenTracker.flush() }
                    runCatching { persistResume() }
                }
            }
        } catch (_: Exception) {
            // Best effort: the process may be dying anyway.
        } finally {
            runCatching { session?.release() }
            session = null
            runCatching { player.release() }
            serviceScope.cancel()
        }
        super.onDestroy()
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>
        ): ListenableFuture<List<MediaItem>> = Futures.immediateFuture(mediaItems)
    }

    private inner class ServiceListener : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                listenTracker.start()
            } else {
                listenTracker.stop()
                serviceScope.launch { runCatching { listenTracker.flush() } }
                serviceScope.launch { runCatching { persistResume() } }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            serviceScope.launch { runCatching { listenTracker.flush() } }
            serviceScope.launch { runCatching { persistResume() } }
        }

        override fun onPlayerError(error: PlaybackException) {
            
            listenTracker.stop()
            recoverFrom(error)
        }
    }

    private fun recoverFrom(error: PlaybackException) {
        val item = player.currentMediaItem ?: run {
            report(error, null)
            return
        }
        
        
        
        val shouldPlay = player.playWhenReady

        if (player.hasNextMediaItem()) {
            val skipped = item.mediaMetadata.title?.toString().orEmpty()
            runCatching {
                player.seekToNextMediaItem()
                player.prepare()
                if (shouldPlay) player.play()
            }
            report(error, skipped)
            return
        }

        report(error, null)
    }

    private fun report(error: PlaybackException, skippedTitle: String?) {
        
        
        Log.d("Playback", "describe errorCode=${error.errorCode} msg=${error.message}")
        app.container.connection.notifyPlaybackError(
            message = describe(error),
            skippedTitle = skippedTitle
        )
    }

    private fun describe(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
            getString(R.string.playback_error_link_expired)
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
            getString(R.string.playback_error_file_missing)
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
        PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED ->
            getString(R.string.playback_error_unplayable)
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_TIMEOUT ->
            getString(R.string.playback_error_connection_lost)
        else -> error.message?.takeIf { it.isNotBlank() }
            ?: getString(R.string.playback_error_generic)
    }

    private suspend fun persistResume() {
        val item = player.currentMediaItem ?: return
        try {
            app.container.tracks.saveResume(
                item.toTrack(),
                player.currentPosition,
                player.duration.coerceAtLeast(0)
            )
        } catch (e: Exception) {
            Log.e("PlaybackService", "Failed to persist resume point", e)
        }
    }

    private suspend fun flushListen(key: String, total: Int) {
        val dao = app.container.database.listenDao()
        val existing = try {
            dao.getDay(key)?.seconds ?: 0
        } catch (e: Exception) {
            Log.e("PlaybackService", "Failed to read listen history", e)
            0
        }
        try {
            dao.upsert(ListenDay(key, existing + total))
        } catch (e: Exception) {
            
            Log.e("PlaybackService", "Failed to write listen history", e)
        }
    }

    private suspend fun applySavedRepeatMode() {
        val mode = try {
            app.container.prefs.repeatMode.first()
        } catch (e: Exception) {
            Log.e("PlaybackService", "Failed to read repeat mode", e)
            RepeatMode.OFF
        }
        player.repeatMode = mode.toExo()
    }

    companion object {
        const val PERSIST_INTERVAL_MS = 30_000L
        const val FLUSH_TIMEOUT_MS = 1_500L
    }
}
