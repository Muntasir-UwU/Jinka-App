package com.jinki.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jinki.app.AppContainer
import com.jinki.app.data.PlaylistRepository
import com.jinki.app.data.Track
import com.jinki.app.data.TrackRepository
import com.jinki.app.data.download.DownloadManager
import com.jinki.app.data.download.DownloadStatus
import com.jinki.app.data.source.SearchHit
import com.jinki.app.data.source.SourceException
import com.jinki.app.data.source.SourceRegistry
import com.jinki.app.data.source.toSourceRef
import com.jinki.app.player.PlayerConnection
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch


class SearchViewModel(
    private val connection: PlayerConnection,
    private val tracks: TrackRepository,
    private val downloads: DownloadManager,
    private val playlists: PlaylistRepository,
    private val rememberResults: (List<SearchHit>) -> Unit
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _hits = MutableStateFlow<List<SearchHit>>(emptyList())
    private val _searching = MutableStateFlow(false)

    
    val playingKey: StateFlow<String> = connection.state
        .map { player -> if (player.isPlaying) player.track?.storageKey.orEmpty() else "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val _error = MutableStateFlow<SearchError?>(null)
    val error: StateFlow<SearchError?> = _error.asStateFlow()

    
    @OptIn(FlowPreview::class)
    val rows: StateFlow<List<SearchRow>> =
        combine(_hits, downloads.states) { hits, states ->
            hits.map { hit ->
                val download = states[hit.storageKey]
                val status = when (download?.status) {
                    DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING -> RowStatus.SAVING
                    DownloadStatus.FAILED -> RowStatus.FAILED
                    DownloadStatus.DONE, DownloadStatus.CANCELLED, null ->
                        if (savedKeys.value.contains(hit.storageKey)) RowStatus.SAVED
                        else RowStatus.IDLE
                }
                SearchRow(hit, status)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val savedKeys = MutableStateFlow<Set<String>>(emptySet())

    val isSearching: StateFlow<Boolean> = _searching.asStateFlow()

    private var tapJob: Job? = null
    private var pendingHit: String? = null

    init {
        observeDownloadFailures()
        observeQueries()
    }

    fun onQueryChange(query: String) {
        _query.value = query
    }

    
    fun clearQuery() {
        onQueryChange("")
    }

    
    fun onTap(hit: SearchHit) {
        if (tapJob?.isActive == true && pendingHit == hit.storageKey) {
            tapJob?.cancel()
            tapJob = null
            pendingHit = null
            downloads.download(hit)
            return
        }
        tapJob?.cancel()
        pendingHit = hit.storageKey
        tapJob = viewModelScope.launch {
            delay(TAP_WINDOW_MS)
            pendingHit = null
        }
    }

    fun clearError() {
        _error.value = null
    }

    
    private val _playlistTarget = MutableStateFlow<SearchHit?>(null)
    val playlistTarget: StateFlow<SearchHit?> = _playlistTarget.asStateFlow()

    
    val playlistSummaries = playlists.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    
    @OptIn(ExperimentalCoroutinesApi::class)
    val memberIds: StateFlow<Set<Long>> = playlistTarget
        .flatMapLatest { target ->
            if (target == null) flowOf(emptySet())
            else playlists.observeContainers(target.source, target.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun openPlaylistSheet(hit: SearchHit) {
        
        tapJob?.cancel()
        tapJob = null
        pendingHit = null
        _playlistTarget.value = hit
    }

    fun closePlaylistSheet() {
        _playlistTarget.value = null
    }

    fun togglePlaylistMembership(playlistId: Long) {
        val hit = _playlistTarget.value ?: return
        viewModelScope.launch {
            val skeleton = Track(
                id = hit.id,
                source = hit.source,
                title = hit.title,
                artist = hit.artist,
                thumbnailUrl = hit.thumbnailUrl
            )
            val added = playlists.addTrack(playlistId, skeleton)
            if (!added) playlists.removeTrack(playlistId, hit.source, hit.id)
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            val id = playlists.create(name)
            if (id > 0) {
                _playlistTarget.value?.let { hit ->
                    playlists.addTrack(
                        id,
                        Track(
                            id = hit.id,
                            source = hit.source,
                            title = hit.title,
                            artist = hit.artist,
                            thumbnailUrl = hit.thumbnailUrl
                        )
                    )
                }
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeQueries() {
        viewModelScope.launch {
            
            
            _query
                .debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
                .collectLatest { query -> runSearch(query) }
        }
    }

    private suspend fun runSearch(query: String) {
        if (query.isBlank()) {
            _hits.value = emptyList()
            _searching.value = false
            return
        }
        _searching.value = true
        try {
            val hits = SourceRegistry.search(query)
            _hits.value = hits
            rememberResults(hits)
            val saved = hits.filter { tracks.isOffline(it.source, it.id) }
                .map { it.storageKey }
                .toSet()
            savedKeys.value = saved
        } catch (e: SourceException) {
            _error.value = SearchError(message = e.message.orEmpty())
            _hits.value = emptyList()
        } finally {
            _searching.value = false
        }
    }

    

    
    private fun observeDownloadFailures() {
        val seen = mutableSetOf<String>()
        viewModelScope.launch {
            downloads.states.collect { states ->
                states.forEach { (key, state) ->
                    if (state.status == DownloadStatus.FAILED && seen.add(key)) {
                        val title = _hits.value
                            .firstOrNull { it.storageKey == key }
                            ?.title
                            .orEmpty()
                        _error.value = SearchError(
                            message = state.error ?: "Download failed.",
                            trackTitle = title.ifBlank { null }
                        )
                    }
                    if (state.status == DownloadStatus.DONE) {
                        savedKeys.value = savedKeys.value + key
                    }
                }
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SearchViewModel(
                container.connection,
                container.tracks,
                container.downloads,
                container.playlists,
                container::rememberSearchResults
            ) as T
    }

    companion object {
        const val SEARCH_DEBOUNCE_MS = 450L
        const val TAP_WINDOW_MS = 280L
    }
}


data class SearchRow(
    val hit: SearchHit,
    val status: RowStatus = RowStatus.IDLE
)


data class SearchError(
    val message: String,
    val trackTitle: String? = null
)

enum class RowStatus {
    IDLE,
    SAVING,
    FAILED,
    SAVED
}
