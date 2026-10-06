package com.jinki.app.player

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.jinki.app.data.Track
import java.io.File


fun Track.toMediaItem(): MediaItem {
    val path = requireNotNull(localPath) { "Download before playing: $storageKey" }
    val artwork = thumbnailUrl.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
    return MediaItem.Builder()
        .setMediaId(storageKey)
        .setUri(Uri.fromFile(File(path)))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setArtworkUri(artwork)
                .setExtras(toExtras())
                .build()
        )
        .build()
}


fun MediaItem.toTrack(): Track {
    readExtras()?.let { return it }
    
    
    
    val parts = mediaId.split('|', limit = 2)
    val uri = localConfiguration?.uri
    return Track(
        id = parts.getOrNull(1).orEmpty(),
        source = parts.getOrNull(0).orEmpty(),
        title = mediaMetadata.title?.toString().orEmpty(),
        artist = mediaMetadata.artist?.toString().orEmpty(),
        thumbnailUrl = mediaMetadata.artworkUri?.toString().orEmpty(),
        streamUrl = uri?.toString().orEmpty(),
        localPath = uri?.takeIf { it.scheme == "file" }?.path
    )
}


private fun MediaItem.readExtras(): Track? {
    val bundle = mediaMetadata.extras ?: return null
    val source = bundle.getString(EXTRA_SOURCE) ?: return null
    val id = bundle.getString(EXTRA_ID) ?: return null
    val playbackUri = localConfiguration?.uri?.toString().orEmpty()
    return Track(
        id = id,
        source = source,
        title = bundle.getString(EXTRA_TITLE).orEmpty(),
        artist = bundle.getString(EXTRA_ARTIST).orEmpty(),
        album = bundle.getString(EXTRA_ALBUM).orEmpty(),
        thumbnailUrl = bundle.getString(EXTRA_THUMBNAIL).orEmpty(),
        streamUrl = bundle.getString(EXTRA_STREAM_URL) ?: playbackUri,
        durationSeconds = bundle.getInt(EXTRA_DURATION, 0),
        
        
        
        
        localPath = playbackUri.takeIf { it.startsWith(FILE_SCHEME) }?.removePrefix(FILE_SCHEME),
        pageUrl = bundle.getString(EXTRA_PAGE_URL)
    )
}

private fun Track.toExtras(): Bundle = Bundle().apply {
    putString(EXTRA_ID, id)
    putString(EXTRA_SOURCE, source)
    putString(EXTRA_TITLE, title)
    putString(EXTRA_ARTIST, artist)
    putString(EXTRA_ALBUM, album)
    putString(EXTRA_THUMBNAIL, thumbnailUrl)
    putString(EXTRA_STREAM_URL, streamUrl)
    putInt(EXTRA_DURATION, durationSeconds)
    putString(EXTRA_LOCAL_PATH, localPath)
    putString(EXTRA_PAGE_URL, pageUrl)
}


private const val EXTRA_ID = "jinki.id"
private const val EXTRA_SOURCE = "jinki.source"
private const val EXTRA_TITLE = "jinki.title"
private const val EXTRA_ARTIST = "jinki.artist"
private const val EXTRA_ALBUM = "jinki.album"
private const val EXTRA_THUMBNAIL = "jinki.thumbnail"
private const val EXTRA_STREAM_URL = "jinki.streamUrl"
private const val EXTRA_DURATION = "jinki.duration"
private const val EXTRA_LOCAL_PATH = "jinki.localPath"
private const val EXTRA_PAGE_URL = "jinki.pageUrl"

private const val FILE_SCHEME = "file://"
