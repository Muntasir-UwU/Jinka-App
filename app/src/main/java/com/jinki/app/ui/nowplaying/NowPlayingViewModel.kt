package com.jinki.app.ui.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jinki.app.AppContainer
import com.jinki.app.data.QueueStore
import com.jinki.app.data.Track
import com.jinki.app.data.TrackRepository
import com.jinki.app.data.artwork.trackThumbnailCandidates
import com.jinki.app.data.lyrics.Lyrics
import com.jinki.app.data.lyrics.LyricsRepository
import com.jinki.app.data.source.SearchHit
import com.jinki.app.player.PlayerConnection
import com.jinki.app.player.PlayerUiState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch


data class RemovedTrack(val index: Int, val track: Track)

class NowPlayingViewModel(
    val connection: PlayerConnection,
    private val lyricsRepo: LyricsRepository,
    val lastSearchResults: StateFlow<List<SearchHit>>,
    private val tracks: TrackRepository,
    private val queueStore: QueueStore
) : ViewModel() {

    val player: StateFlow<PlayerUiState> = connection.state
    val lyrics: StateFlow<Map<String, Lyrics>> = lyricsRepo.cache

    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    fun toggleFavorite(key: String) {
        _favorites.value =
            if (key in _favorites.value) _favorites.value - key
            else _favorites.value + key
    }

    fun requestLyrics(track: Track) {
        lyricsRepo.request(track)
    }

    fun lyricsKey(track: Track): String = lyricsRepo.storeKeyFor(track)

    fun waveformSeed(track: Track): String = "${track.id}|${track.title}|${track.artist}"

    fun artworkCandidates(track: Track, results: List<SearchHit>): List<String> =
        trackThumbnailCandidates(track, results)

    
    
    
    val queue: StateFlow<List<QueueItem>> = queueStore.queueList
        .map { list ->
            list.map {
                QueueItem(
                    id = it.storageKey,
                    title = it.title,
                    artist = it.artist,
                    artworkUrl = trackThumbnailCandidates(it, lastSearchResults.value)
                        .firstOrNull(),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var persistJob: Job? = null

    
    
    @OptIn(FlowPreview::class)
    private fun startPersisting() {
        if (persistJob != null) return
        persistJob = viewModelScope.launch {
            queueStore.queueList.debounce(300).collect { list ->
                queueStore.saveOrder(list.map { it.storageKey })
            }
        }
    }

    init {
        viewModelScope.launch {
            val savedOrder = queueStore.loadOrder()

            
            
            
            
            var restored = false
            tracks.observeOffline().collect { library ->
                if (restored) return@collect
                restored = true
                val byKey = library.associateBy { it.storageKey }
                val current = queueStore.queueList.value
                val currentKeys = current.mapTo(HashSet()) { it.storageKey }
                queueStore.replaceAll(
                    current + savedOrder.mapNotNull { byKey[it] }
                        .filter { it.storageKey !in currentKeys }
                )
                startPersisting()
            }
        }
    }

    fun playQueueItem(index: Int) {
        val list = queueStore.queueList.value
        val row = list.getOrNull(index) ?: return
        if (connection.state.value.track?.storageKey == row.storageKey) {
            connection.toggle()
        } else {
            connection.playTracks(list, index, true)
        }
    }

    
    fun removeFromQueue(index: Int): RemovedTrack? {
        val track = queueStore.removeFromQueue(index) ?: return null
        val inSync = playerMatches(queueStore.queueList.value)
        if (inSync) connection.removeAt(index) 
        return RemovedTrack(index, track)
    }

    
    private fun playerMatches(list: List<Track>): Boolean =
        connection.queue.value.map { it.storageKey } == list.map { it.storageKey }

    fun restoreToQueue(removed: RemovedTrack) {
        val inSync = playerMatches(queueStore.queueList.value)
        val at = removed.index.coerceIn(0, queueStore.queueList.value.size)
        queueStore.insertAt(at, removed.track)
        if (inSync) connection.insertAt(at, removed.track)
    }

    fun moveInQueue(from: Int, to: Int) {
        val inSync = playerMatches(queueStore.queueList.value)
        val moved = queueStore.moveInQueue(from, to)
        
        if (moved && inSync) connection.moveItem(from, to)
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            NowPlayingViewModel(
                container.connection,
                container.lyrics,
                container.lastSearchResults,
                container.tracks,
                container.queueStore
            ) as T
    }
}
