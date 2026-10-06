package com.jinki.app.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext


class PlaylistRepository(
    private val dao: PlaylistDao,
    private val io: CoroutineDispatcher = Dispatchers.IO
) {

    fun observePlaylists(): Flow<List<PlaylistWithCount>> = dao.observePlaylists()

    fun observeEntries(playlistId: Long): Flow<List<PlaylistEntry>> =
        dao.observeEntries(playlistId)

    
    fun observeContainers(source: String, trackId: String): Flow<Set<Long>> =
        dao.observeContainers(source, trackId).map { it.toSet() }

    
    suspend fun create(name: String): Long {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return -1
        return withContext(io) {
            try { dao.insertPlaylist(Playlist(name = trimmed)) } catch (_: Exception) { -1 }
        }
    }

    suspend fun rename(id: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        withContext(io) {
            try { dao.renamePlaylist(id, trimmed) } catch (_: Exception) { }
        }
    }

    
    suspend fun delete(id: Long) {
        withContext(io) {
            try {
                dao.deleteEntries(id)
                dao.deletePlaylist(id)
            } catch (_: Exception) { }
        }
    }

    
    suspend fun addTrack(playlistId: Long, track: Track): Boolean = withContext(io) {
        try {
            if (dao.findEntry(playlistId, track.source, track.id) != null) return@withContext false
            val position = (dao.maxPosition(playlistId) ?: -1) + 1
            dao.insertEntry(
                PlaylistEntry(
                    playlistId = playlistId,
                    source = track.source,
                    trackId = track.id,
                    title = track.title,
                    artist = track.artist,
                    thumbnailUrl = track.thumbnailUrl,
                    localPath = track.localPath,
                    position = position
                )
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    
    suspend fun removeTrack(playlistId: Long, source: String, trackId: String): Boolean =
        withContext(io) {
            try {
                val entry = dao.findEntry(playlistId, source, trackId) ?: return@withContext false
                dao.deleteEntry(entry.entryId)
                true
            } catch (_: Exception) {
                false
            }
        }

    suspend fun removeEntry(entryId: Long) {
        withContext(io) {
            try { dao.deleteEntry(entryId) } catch (_: Exception) { }
        }
    }
}
