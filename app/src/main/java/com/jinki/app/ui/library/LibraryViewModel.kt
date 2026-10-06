package com.jinki.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jinki.app.AppContainer
import com.jinki.app.data.PlaylistEntry
import com.jinki.app.data.PlaylistRepository
import com.jinki.app.data.PlaylistWithCount
import com.jinki.app.data.QueueStore
import com.jinki.app.data.Track
import com.jinki.app.data.TrackRepository
import com.jinki.app.data.source.SourceException
import com.jinki.app.data.source.SourceRef
import com.jinki.app.data.toTrack
import com.jinki.app.player.PlayerConnection
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch


@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val tracks: TrackRepository,
    private val connection: PlayerConnection,
    private val playlists: PlaylistRepository,
    private val queueStore: QueueStore
) : ViewModel() {

    val rows: StateFlow<List<Track>> = tracks.observeOffline()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    
    val playingKey: StateFlow<String?> = connection.state
        .map { player -> if (player.isPlaying) player.track?.storageKey else null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    
    private val _resolvingKey = MutableStateFlow<String?>(null)
    val resolvingKey: StateFlow<String?> = _resolvingKey.asStateFlow()

    

    
    val queueList: StateFlow<List<Track>> = queueStore.queueList

    
    val queueCurrentIndex: StateFlow<Int?> =
        combine(queueList, playingKey) { list, key ->
            if (key == null) null
            else list.indexOfFirst { it.storageKey == key }.takeIf { it >= 0 }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    
    fun addToQueue(track: Track) = queueStore.addToQueue(track)

    
    fun removeFromQueue(index: Int) = queueStore.removeFromQueue(index)

    private val _error = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val error: SharedFlow<String> = _error.asSharedFlow()

    

    val playlistSummaries: StateFlow<List<PlaylistWithCount>> = playlists.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedPlaylistId = MutableStateFlow<Long?>(null)
    val selectedPlaylistId: StateFlow<Long?> = _selectedPlaylistId.asStateFlow()

    val entries: StateFlow<List<PlaylistEntry>> = selectedPlaylistId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else playlists.observeEntries(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    
    private val _playlistTarget = MutableStateFlow<Track?>(null)
    val playlistTarget: StateFlow<Track?> = _playlistTarget.asStateFlow()

    
    val memberIds: StateFlow<Set<Long>> = playlistTarget
        .flatMapLatest { target ->
            if (target == null) flowOf(emptySet())
            else playlists.observeContainers(target.source, target.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun selectPlaylist(id: Long?) {
        _selectedPlaylistId.value = id
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            val id = playlists.create(name)
            
            
            if (id > 0) {
                _playlistTarget.value?.let { playlists.addTrack(id, it) }
            }
        }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch {
            playlists.delete(id)
            if (_selectedPlaylistId.value == id) _selectedPlaylistId.value = null
        }
    }

    fun openPlaylistSheet(track: Track) {
        _playlistTarget.value = track
    }

    fun closePlaylistSheet() {
        _playlistTarget.value = null
    }

    fun togglePlaylistMembership(playlistId: Long) {
        val target = _playlistTarget.value ?: return
        viewModelScope.launch {
            val added = playlists.addTrack(playlistId, target)
            if (!added) playlists.removeTrack(playlistId, target.source, target.id)
        }
    }

    fun removeEntry(entryId: Long) {
        viewModelScope.launch { playlists.removeEntry(entryId) }
    }

    
    fun playEntry(entry: PlaylistEntry, all: List<PlaylistEntry>) {
        viewModelScope.launch {
            val track = entry.toTrack()
            if (track.localPath != null) {
                val downloaded = all.map { it.toTrack() }.filter { it.localPath != null }
                val index = downloaded.indexOfFirst { it.storageKey == track.storageKey }
                    .coerceAtLeast(0)
                connection.playTracks(downloaded, index, true)
            } else {
                _resolvingKey.value = track.storageKey
                try {
                    val local = connection.ensureLocal(
                        SourceRef(entry.source, entry.trackId),
                        track
                    )
                    connection.enqueueAndPlay(local, true)
                } catch (e: Exception) {
                    _error.emit(e.message ?: "Playback failed.")
                } finally {
                    if (_resolvingKey.value == track.storageKey) _resolvingKey.value = null
                }
            }
        }
    }

    

    private val tapStamps = mutableMapOf<Int, MutableList<Long>>()

    fun onTap(index: Int) {
        val row = rows.value.getOrNull(index) ?: return
        val now = System.currentTimeMillis()
        val stamps = (tapStamps[index] ?: mutableListOf())
            .filter { now - it < TRIPLE_CLICK_WINDOW_MS }
            .toMutableList()
        stamps.add(now)

        if (stamps.size >= TRIPLE_CLICKS) {
            tapStamps.remove(index)
            remove(row)
            return
        }
        tapStamps[index] = stamps
        togglePlay(row)
    }

    private fun togglePlay(row: Track) {
        val current = connection.state.value.track
        if (current?.storageKey == row.storageKey) {
            connection.toggle()
            return
        }
        
        queueStore.addToQueue(row)
        connection.enqueueAndPlay(row, true)
    }

    private fun remove(row: Track) {
        viewModelScope.launch {
            try {
                
                
                
                if (connection.state.value.track?.storageKey == row.storageKey) {
                    connection.pause()
                }
                tracks.remove(row.source, row.id)
            } catch (_: Exception) {
                
                
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LibraryViewModel(container.tracks, container.connection, container.playlists, container.queueStore) as T
    }

    companion object {
        const val TRIPLE_CLICK_WINDOW_MS = 600L
        const val TRIPLE_CLICKS = 3
    }
}