package com.jinki.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey


data class Track(
    val id: String,
    val source: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val thumbnailUrl: String = "",
    val streamUrl: String = "",
    val durationSeconds: Int = 0,
    val localPath: String? = null,
    val pageUrl: String? = null
) {
    
    val storageKey: String get() = "$source|$id"

    val isDownloaded: Boolean get() = localPath != null

    
    val playbackUri: String get() = if (localPath != null) "file://$localPath" else streamUrl
}


@Entity(tableName = "offline_tracks")
data class OfflineTrack(
    @PrimaryKey val storageKey: String,
    val source: String,
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val thumbnailUrl: String = "",
    val localPath: String,
    val savedAt: Long = System.currentTimeMillis()
)


@Entity(tableName = "playback_state")
data class PlaybackState(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val source: String,
    val trackId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val streamUrl: String,
    val localPath: String?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}


@Entity(tableName = "listen_history")
data class ListenDay(
    @PrimaryKey val date: String,
    val seconds: Int
)


@Entity(tableName = "lyrics_cache")
data class LyricsCacheEntry(
    @PrimaryKey val storeKey: String,
    val type: String,
    val content: String,
    val savedAt: Long = System.currentTimeMillis()
)
