package com.jinki.app.data.source

import com.jinki.app.data.Track


const val SOURCE_YOUTUBE = "youtube"
const val SOURCE_SOUNDCLOUD = "soundcloud"


internal const val EXTRACTION_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"


data class SearchHit(
    val id: String,
    val source: String,
    val title: String,
    val artist: String = "",
    val thumbnailUrl: String = "",
    val thumbnailHighUrl: String = "",
    val thumbnailFallbackUrl: String = "",
    val durationSeconds: Int = 0,
    
    val pageUrl: String? = null
) {
    
    val storageKey: String get() = "$source|$id"
}


data class ResolvedStream(
    val url: String,
    val title: String = "",
    val artist: String = "",
    val thumbnailUrl: String = "",
    val durationSeconds: Int = 0,
    val formatId: Int? = null,
    val averageBitrate: Int = 0,
    
    val suffix: String? = null
)


class SourceException(message: String, cause: Throwable? = null) : Exception(message, cause)


data class SourceRef(
    val source: String,
    val id: String,
    val pageUrl: String? = null
)

private const val VIDEO_ID_LENGTH = 11


internal fun isVideoId(id: String): Boolean =
    id.length == VIDEO_ID_LENGTH && id.all { it in BASE64_URL }

private val BASE64_URL = ('a'..'z') + ('A'..'Z') + ('0'..'9') + listOf('-', '_')


fun SearchHit.toSourceRef(): SourceRef = SourceRef(source = source, id = id, pageUrl = pageUrl)


fun Track.toSourceRef(): SourceRef = SourceRef(source = source, id = id, pageUrl = pageUrl)


fun SearchHit.toTrack(stream: ResolvedStream): Track = Track(
    id = id,
    source = source,
    title = stream.title.ifBlank { title },
    artist = stream.artist.ifBlank { artist },
    thumbnailUrl = stream.thumbnailUrl.ifBlank { thumbnailUrl },
    streamUrl = stream.url,
    durationSeconds = if (durationSeconds > 0) durationSeconds else stream.durationSeconds,
    pageUrl = pageUrl
)


fun Track.applyStream(stream: ResolvedStream): Track = copy(
    streamUrl = stream.url,
    title = stream.title.ifBlank { title },
    artist = stream.artist.ifBlank { artist },
    thumbnailUrl = stream.thumbnailUrl.ifBlank { thumbnailUrl },
    durationSeconds = if (durationSeconds > 0) durationSeconds else stream.durationSeconds
)
