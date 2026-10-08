package com.jinki.app

import android.content.Context
import com.jinki.app.data.JinkaDatabase
import com.jinki.app.data.PlaylistRepository
import com.jinki.app.data.TrackRepository
import com.jinki.app.data.UserPrefs
import com.jinki.app.data.download.DownloadManager
import com.jinki.app.data.lyrics.LrcLibClient
import com.jinki.app.data.lyrics.LyricsRepository
import com.jinki.app.data.source.SearchHit
import com.jinki.app.player.PlayerConnection
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import com.jinki.app.data.QueueStore


class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val database: JinkaDatabase = JinkaDatabase.get(appContext)
    val prefs = UserPrefs(appContext)

    val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
        .cache(okhttp3.Cache(appContext.cacheDir, 50 * 1024 * 1024)) 
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    val tracks = TrackRepository(
        library = database.libraryDao(),
        playback = database.playbackDao()
    )
    val playlists = PlaylistRepository(database.playlistDao())
    val queueStore = QueueStore(database.queueDao())
	val lyrics = LyricsRepository(
        dao = database.lyricsDao(),
        client = LrcLibClient(http),
        scope = appScope,
        // Share the cached app client instead of the bare fallback client.
        simpHttp = http
    )
    val connection = PlayerConnection(appContext, appScope, prefs, tracks, http)
    val downloads = DownloadManager(appContext, appScope, prefs)

    
    private val _lastSearchResults = MutableStateFlow<List<SearchHit>>(emptyList())
    val lastSearchResults: StateFlow<List<SearchHit>> = _lastSearchResults.asStateFlow()

    fun rememberSearchResults(hits: List<SearchHit>) {
        _lastSearchResults.value = hits
    }
}
