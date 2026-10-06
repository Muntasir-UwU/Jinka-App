package com.jinki.app.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)


@Entity(tableName = "playlist_entries")
data class PlaylistEntry(
    @PrimaryKey(autoGenerate = true) val entryId: Long = 0,
    val playlistId: Long,
    val source: String,
    val trackId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String = "",
    
    val localPath: String? = null,
    val position: Int = 0
) {
    
    val storageKey: String get() = "$source|$trackId"
}


data class PlaylistWithCount(
    @Embedded val playlist: Playlist,
    val entryCount: Int
)


fun PlaylistEntry.toTrack(): Track = Track(
    id = trackId,
    source = source,
    title = title,
    artist = artist,
    thumbnailUrl = thumbnailUrl,
    streamUrl = if (localPath != null) "file://$localPath" else "",
    localPath = localPath
)
