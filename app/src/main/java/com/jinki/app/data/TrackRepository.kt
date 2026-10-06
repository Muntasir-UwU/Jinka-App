package com.jinki.app.data

import android.util.Log
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class TrackRepository(
    private val library: LibraryDao,
    private val playback: PlaybackDao,
    private val io: CoroutineDispatcher = Dispatchers.IO
) {

    fun observeOffline(): Flow<List<Track>> =
        library.observeOffline().map { rows -> rows.map { it.toTrack() } }

    suspend fun isOffline(source: String, id: String): Boolean = withContext(io) {
        bestEffort { library.find(source, id) } != null
    }

    suspend fun findTrack(source: String, id: String): Track? = withContext(io) {
        val row = bestEffort { library.find(source, id) } ?: return@withContext null
        val track = row.toTrack()
        val path = track.localPath.orEmpty()
        if (path.isBlank() || !File(path).exists()) return@withContext null
        return@withContext track
    }

    suspend fun save(track: Track, localPath: String) {
        withContext(io) { library.upsert(track.toOffline(localPath)) }
    }

    suspend fun remove(source: String, id: String): Boolean = withContext(io) {
        val row = bestEffort { library.find(source, id) } ?: return@withContext false
        bestEffort { library.delete(source, id) }
        val file = File(row.localPath)
        if (file.exists()) bestEffort { file.delete() }
        true
    }

    suspend fun allLocalPaths(): List<String> = withContext(io) {
        bestEffort { library.allLocalPaths() } ?: emptyList()
    }

    fun observeResume(): Flow<ResumePoint?> =
        playback.observe().map { state -> state?.toResumePoint() }

    suspend fun saveResume(track: Track, positionMs: Long, durationMs: Long) {
        withContext(io) {
            bestEffort {
                playback.save(
                    PlaybackState(
                        source = track.source,
                        trackId = track.id,
                        title = track.title,
                        artist = track.artist,
                        thumbnailUrl = track.thumbnailUrl,
                        streamUrl = track.streamUrl,
                        localPath = track.localPath,
                        positionMs = positionMs,
                        durationMs = durationMs
                    )
                )
            }
        }
    }
}

data class ResumePoint(
    val track: Track,
    val positionMs: Long
)

fun OfflineTrack.toTrack(): Track = Track(
    id = id,
    source = source,
    title = title,
    artist = artist,
    album = album,
    thumbnailUrl = thumbnailUrl,
    streamUrl = "file://$localPath",
    durationSeconds = 0,
    localPath = localPath
)

fun Track.toOffline(localPath: String): OfflineTrack = OfflineTrack(
    storageKey = storageKey,
    source = source,
    id = id,
    title = title,
    artist = artist,
    album = album,
    thumbnailUrl = thumbnailUrl,
    localPath = localPath
)

private fun PlaybackState.toResumePoint(): ResumePoint = ResumePoint(
    track = Track(
        id = trackId,
        source = source,
        title = title,
        artist = artist,
        thumbnailUrl = thumbnailUrl,
        streamUrl = streamUrl,
        localPath = localPath
    ),
    positionMs = positionMs
)

private suspend fun <T> bestEffort(block: suspend () -> T): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Log.e("TrackRepository", "Database operation failed", e)
    null
}
