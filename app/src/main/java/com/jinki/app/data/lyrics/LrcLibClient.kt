package com.jinki.app.data.lyrics

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject


data class LrcLibTrack(
    val trackName: String,
    val artistName: String,
    val albumName: String?,
    val durationSeconds: Double?,
    val plainLyrics: String?,
    val syncedLyrics: String?
)


class LrcLibClient(
    private val httpClient: OkHttpClient,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val baseUrl: HttpUrl = DEFAULT_BASE_URL
) {

    
    suspend fun get(
        trackName: String,
        artistName: String,
        albumName: String? = null,
        durationSeconds: Double? = null
    ): SourceReply<LrcLibTrack> = withContext(io) {
        val url = baseUrl.newBuilder()
            .addPathSegments("api/get")
            .addQueryParameter("track_name", trackName)
            .addQueryParameter("artist_name", artistName)
            .apply {
                if (!albumName.isNullOrBlank()) addQueryParameter("album_name", albumName)
                if (durationSeconds != null && durationSeconds > 0) {
                    addQueryParameter("duration", durationSeconds.toInt().toString())
                }
            }
            .build()
        httpClient.fetchText(url).parse<LrcLibTrack> { body ->
            val obj = runCatching { JSONObject(body) }.getOrNull()
                ?: return@parse SourceReply.Failure
            parseTrack(obj)?.let { SourceReply.Hit(it) } ?: SourceReply.Miss
        }
    }

    
    suspend fun searchByNames(trackName: String, artistName: String): SourceReply<List<LrcLibTrack>> =
        search {
            addQueryParameter("track_name", trackName)
            addQueryParameter("artist_name", artistName)
        }

    
    suspend fun searchByQuery(query: String): SourceReply<List<LrcLibTrack>> =
        search { addQueryParameter("q", query) }

    private suspend fun search(block: HttpUrl.Builder.() -> Unit): SourceReply<List<LrcLibTrack>> =
        withContext(io) {
            val url = baseUrl.newBuilder()
                .addPathSegments("api/search")
                .apply(block)
                .build()
            when (val reply = httpClient.fetchText(url)) {
                
                SourceReply.Miss -> SourceReply.Hit(emptyList<LrcLibTrack>())
                SourceReply.Failure -> SourceReply.Failure
                is SourceReply.Hit -> {
                    val array = runCatching { JSONArray(reply.value) }.getOrNull()
                    if (array == null) {
                        SourceReply.Failure
                    } else {
                        SourceReply.Hit(
                            (0 until array.length()).mapNotNull { index ->
                                array.optJSONObject(index)?.let(::parseTrack)
                            }
                        )
                    }
                }
            }
        }

    private fun parseTrack(json: JSONObject): LrcLibTrack? {
        val trackName = json.optString("trackName").takeIf { it.isNotBlank() } ?: return null
        return LrcLibTrack(
            trackName = trackName,
            artistName = json.optString("artistName").orEmpty(),
            albumName = json.optStringOrNull("albumName"),
            durationSeconds = json.optDoubleOrNull("duration"),
            plainLyrics = json.optStringOrNull("plainLyrics"),
            syncedLyrics = json.optStringOrNull("syncedLyrics")
        )
    }

    companion object {
        val DEFAULT_BASE_URL: HttpUrl = "https://lrclib.net/api/".toHttpUrl()

        private const val TIMEOUT_SECONDS = 10L

        
        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }
}
