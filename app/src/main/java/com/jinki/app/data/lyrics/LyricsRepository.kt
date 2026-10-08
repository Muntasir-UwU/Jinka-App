package com.jinki.app.data.lyrics

import android.util.Log
import com.jinki.app.data.LyricsCacheEntry
import com.jinki.app.data.LyricsDao
import com.jinki.app.data.Track
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class LyricsRepository(
    private val dao: LyricsDao,
    private val client: LrcLibClient,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    simpHttp: OkHttpClient? = null,
    private val simpMusic: SimpMusicClient = SimpMusicClient(simpHttp ?: LrcLibClient.defaultHttpClient()),
    private val clock: () -> Long = System::currentTimeMillis
) {

    private val _cache = MutableStateFlow<Map<String, Lyrics>>(emptyMap())
    val cache: StateFlow<Map<String, Lyrics>> = _cache.asStateFlow()

    private val inFlight = ConcurrentHashMap<String, Deferred<Lyrics>>()

    private val transientFailures = ConcurrentHashMap<String, Long>()

    fun storeKeyFor(track: Track): String =
        normalize(track.artist) + "|" + normalize(track.title)

    private fun normalize(value: String?): String =
        // Cache keys must be locale-independent: default-locale lowercasing
        // (Turkish dotted-I and friends) would split one track into two keys.
        value.orEmpty().trim().lowercase(Locale.ROOT).replace(WHITESPACE, " ")

    fun peek(track: Track): Lyrics? = _cache.value[storeKeyFor(track)]

    fun peekByKey(key: String): Lyrics? = _cache.value[key]

    fun isLoading(key: String): Boolean = inFlight.containsKey(key)

    suspend fun lyricsFor(track: Track, audioDurationSeconds: Double? = null): Lyrics {
        val key = storeKeyFor(track)
        settled(key)?.let { return it }
        return jobFor(key, track, audioDurationSeconds).await()
    }

    fun request(track: Track, audioDurationSeconds: Double? = null) {
        val key = storeKeyFor(track)
        if (settled(key) != null) return
        jobFor(key, track, audioDurationSeconds)
    }

    private fun settled(key: String): Lyrics? {
        val cached = _cache.value[key] ?: return null
        if (cached is Lyrics.Loading) return null
        val failedAt = transientFailures[key] ?: return cached
        return if (clock() - failedAt < RETRY_COOLDOWN_MS) cached else null
    }

    private fun jobFor(
        key: String,
        track: Track,
        audioDurationSeconds: Double?
    ): Deferred<Lyrics> {
        inFlight[key]?.let { return it }

        val created = scope.async(io, start = CoroutineStart.LAZY) {
            resolve(key, track, audioDurationSeconds)
        }
        val raced = inFlight.putIfAbsent(key, created)
        if (raced != null) {
            created.cancel()
            return raced
        }

        created.invokeOnCompletion { cause ->
            inFlight.remove(key, created)
            
            if (cause != null) {
                _cache.update { if (it[key] is Lyrics.Loading) it - key else it }
            }
        }
        publish(key, Lyrics.Loading)
        created.start()
        return created
    }

    private suspend fun resolve(
        key: String,
        track: Track,
        audioDurationSeconds: Double?
    ): Lyrics {
        var fromDisk = false
        val fetched = try {
            readThrough(key)?.also { fromDisk = true } ?: fetch(track, audioDurationSeconds)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Fetched.failed()
        }

        if (!fetched.persistable && !fetched.definitive) {
            transientFailures[key] = clock()
        } else {
            transientFailures.remove(key)
        }

        publish(key, fetched.lyrics)
        if (!fromDisk && fetched.persistable) persist(key, fetched)
        return fetched.lyrics
    }

    private inline fun <T> quietly(block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e("LyricsRepository", "Database operation failed", e)
        null
    }

    private suspend fun readThrough(key: String): Fetched? {
        val entry = withContext(io) { quietly { dao.find(key) } } ?: return null
        return entry.toFetched()
    }

    private suspend fun persist(key: String, fetched: Fetched) {
        withContext(io) {
            quietly { dao.upsert(fetched.toEntry(key)) }
            quietly { dao.prune(cutoff = clock() - MAX_AGE_MS) }
        }
    }

    private suspend fun fetch(track: Track, audioDurationSeconds: Double?): Fetched {
        val tally = Tally()

        
        val exact = when (
            val reply = client.get(
                trackName = track.title,
                artistName = track.artist,
                albumName = track.album.takeIf { it.isNotBlank() },
                durationSeconds = audioDurationSeconds
            )
        ) {
            is SourceReply.Hit -> reply.value
            SourceReply.Miss -> null
            SourceReply.Failure -> { tally.failed = true; null }
        }
        var best: Pick? = if (exact != null && withinTolerance(exact, audioDurationSeconds)) {
            score(exact, audioDurationSeconds)
        } else {
            null
        }

        
        
        val namedPick = pickBest(
            client.searchByNames(track.title, track.artist).itemsOrEmpty(tally),
            audioDurationSeconds
        )
        if (namedPick != null && (best == null || namedPick.score < best.score)) {
            best = namedPick
        }

        
        if (best == null) {
            best = pickBest(
                client.searchByQuery(broadQuery(track)).itemsOrEmpty(tally),
                audioDurationSeconds
            )
        }

        
        
        
        if (best == null || !best.hasSynced) {
            val alt = fromSimpMusic(
                track,
                audioDurationSeconds,
                tally = if (best == null) tally else null
            )
            if (alt != null && (best == null || alt.hasSynced)) {
                best = alt
            }
        }

        
        if (best == null) {
            best = pickBest(
                client.searchByQuery(broadQuery(track) + " romanized").itemsOrEmpty(tally),
                audioDurationSeconds
            )
        }

        return best?.toFetched()
            ?: if (tally.failed) Fetched.failed() else Fetched.empty()
    }

    private suspend fun fromSimpMusic(
        track: Track,
        audioDurationSeconds: Double?,
        tally: Tally?
    ): Pick? {
        val byQuery = simpMusic.search(broadQuery(track)).itemsOrEmpty(tally)
        pickMatching(byQuery, track, audioDurationSeconds)?.let { return it }

        val byTitle = simpMusic.searchByTitle(track.title).itemsOrEmpty(tally)
        return pickMatching(byTitle, track, audioDurationSeconds)
    }

    private fun pickMatching(
        results: List<LrcLibTrack>,
        track: Track,
        audioDurationSeconds: Double?
    ): Pick? = pickBest(
        results.filter {
            titleMatches(it.trackName, track.title) &&
                artistMatches(it.artistName, track.artist) &&
                withinTolerance(it, audioDurationSeconds)
        },
        audioDurationSeconds
    )

    private fun looseKey(value: String): String =
        value.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    private fun titleMatches(candidate: String, wanted: String): Boolean {
        val c = looseKey(candidate)
        val w = looseKey(wanted)
        if (c.isEmpty() || w.isEmpty()) return false
        return c.contains(w) || w.contains(c)
    }

    private fun artistMatches(candidate: String, wanted: String): Boolean {
        val c = looseKey(candidate)
        if (c.isEmpty() || wanted.isBlank()) return true
        return wanted.split(',', '&', ';', '/', '×')
            .map { looseKey(it) }
            .filter { it.isNotEmpty() }
            .any { c.contains(it) || it.contains(c) }
    }

    private fun broadQuery(track: Track): String =
        (if (track.artist.isNotBlank()) track.artist + " " else "") + track.title

    private fun withinTolerance(result: LrcLibTrack, audioDurationSeconds: Double?): Boolean {
        val actual = audioDurationSeconds ?: return true
        val candidate = result.durationSeconds ?: return true
        return abs(candidate - actual) <= DURATION_TOLERANCE
    }

    private fun score(result: LrcLibTrack, audioDurationSeconds: Double?): Pick? {
        val hasSynced = result.syncedLyrics?.let { LyricsLanguageFilter.isLyricsAcceptable(it) } == true
        val hasPlain = result.plainLyrics?.let { LyricsLanguageFilter.isLyricsAcceptable(it) } == true
        if (!hasSynced && !hasPlain) return null

        var score = 0.0
        val duration = result.durationSeconds
        if (audioDurationSeconds != null && audioDurationSeconds > 0 && duration != null) {
            score += abs(duration - audioDurationSeconds)
        }
        if (hasSynced) score -= SYNCED_BONUS

        return Pick(result, score, hasSynced, hasPlain)
    }

    private fun pickBest(results: List<LrcLibTrack>, audioDurationSeconds: Double?): Pick? =
        results.mapNotNull { score(it, audioDurationSeconds) }.minByOrNull { it.score }

    private fun publish(key: String, lyrics: Lyrics) {
        _cache.update { it + (key to lyrics) }
    }

    companion object {
        const val DURATION_TOLERANCE = 15.0
        const val SYNCED_BONUS = 5.0
        const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
        const val RETRY_COOLDOWN_MS = 30_000L
        val WHITESPACE = Regex("""\s+""")
    }
}


private class Tally {
    var failed = false
}

private fun <T> SourceReply<List<T>>.itemsOrEmpty(tally: Tally?): List<T> = when (this) {
    is SourceReply.Hit -> value
    SourceReply.Miss -> emptyList()
    SourceReply.Failure -> {
        tally?.failed = true
        emptyList()
    }
}

private data class Pick(
    val result: LrcLibTrack,
    val score: Double,
    val hasSynced: Boolean,
    val hasPlain: Boolean
)

private data class Fetched(
    val lyrics: Lyrics,
    val cacheType: String,
    val cacheContent: String,
    val definitive: Boolean = true
) {
    val persistable: Boolean get() = cacheType == TYPE_LRC || cacheType == TYPE_PLAIN

    companion object {
        const val TYPE_LRC = "lrc"
        const val TYPE_PLAIN = "plain"
        const val TYPE_NONE = "none"

        fun empty() = Fetched(Lyrics.Empty, TYPE_NONE, "")
        fun failed() = Fetched(Lyrics.Empty, TYPE_NONE, "", definitive = false)
    }
}

private fun Pick.toFetched(): Fetched {
    val synced = result.syncedLyrics
    val plain = result.plainLyrics

    
    
    val parsed = if (hasSynced && !synced.isNullOrBlank()) LrcParser.parse(synced) else emptyList()

    return when {
        parsed.isNotEmpty() -> Fetched(Lyrics.Synced(parsed), Fetched.TYPE_LRC, synced!!)
        hasPlain && !plain.isNullOrBlank() -> Fetched(Lyrics.Plain(plain), Fetched.TYPE_PLAIN, plain)
        else -> Fetched.empty()
    }
}

private fun LyricsCacheEntry.toFetched(): Fetched? = when (type) {
    Fetched.TYPE_LRC -> {
        val parsed = LrcParser.parse(content)
        if (parsed.isEmpty()) null else Fetched(Lyrics.Synced(parsed), Fetched.TYPE_LRC, content)
    }
    Fetched.TYPE_PLAIN ->
        if (content.isBlank()) null else Fetched(Lyrics.Plain(content), Fetched.TYPE_PLAIN, content)
    else -> null
}

private fun Fetched.toEntry(key: String) = LyricsCacheEntry(
    storeKey = key,
    type = cacheType,
    content = cacheContent
)
