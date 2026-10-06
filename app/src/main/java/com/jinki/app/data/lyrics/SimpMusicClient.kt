package com.jinki.app.data.lyrics

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.json.JSONObject


class SimpMusicClient(
    private val httpClient: OkHttpClient,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val baseUrl: HttpUrl = DEFAULT_BASE_URL,
    private val clock: () -> Long = System::currentTimeMillis
) {

    private val blockedUntil = AtomicLong(0L)

    
    suspend fun search(query: String, limit: Int = DEFAULT_LIMIT): SourceReply<List<LrcLibTrack>> =
        request("v1/search", "q", query, limit)

    
    suspend fun searchByTitle(title: String, limit: Int = DEFAULT_LIMIT): SourceReply<List<LrcLibTrack>> =
        request("v1/search/title", "title", title, limit)

    private suspend fun request(
        path: String,
        param: String,
        value: String,
        limit: Int
    ): SourceReply<List<LrcLibTrack>> = withContext(io) {
        if (clock() < blockedUntil.get()) return@withContext SourceReply.Failure

        val url = baseUrl.newBuilder()
            .addPathSegments(path)
            .addQueryParameter(param, value)
            .addQueryParameter("limit", limit.toString())
            .build()

        httpClient.fetchText(url) { retryAfterSeconds ->
            blockedUntil.set(clock() + retryAfterSeconds * 1000L)
        }.parse<List<LrcLibTrack>> { body -> parseEnvelope(body) }
    }

    private fun parseEnvelope(body: String): SourceReply<List<LrcLibTrack>> {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return SourceReply.Failure
        if (!root.optBoolean("success", true)) return SourceReply.Hit(emptyList())
        val data = root.optJSONArray("data") ?: return SourceReply.Hit(emptyList())
        val tracks = (0 until data.length()).mapNotNull { index ->
            data.optJSONObject(index)?.let(::parseItem)
        }
        return SourceReply.Hit(tracks)
    }

    private fun parseItem(json: JSONObject): LrcLibTrack? {
        val title = json.optStringOrNull("songTitle") ?: return null
        return LrcLibTrack(
            trackName = title,
            artistName = json.optString("artistName").orEmpty(),
            albumName = json.optStringOrNull("albumName"),
            
            
            durationSeconds = json.optDoubleOrNull("durationSeconds")
                ?: json.optDoubleOrNull("duration"),
            plainLyrics = json.optStringOrNull("plainLyric")
                ?: json.optStringOrNull("plainLyrics"),
            syncedLyrics = json.optStringOrNull("syncedLyrics")
        )
    }

    companion object {
        val DEFAULT_BASE_URL: HttpUrl = "https://api-lyrics.simpmusic.org/".toHttpUrl()
        const val DEFAULT_LIMIT = 10
    }
}
