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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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

    private val _debugTrace = MutableStateFlow("")
    /** Human-readable trace of the last lookup (inputs, steps, decisions). */
    val debugTrace: StateFlow<String> = _debugTrace.asStateFlow()

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

    /**
     * Lookup chain, stopping at the first step that yields usable lyrics:
     * exact lrclib lookups (with album, then without), named searches,
     * a broad text search, then SimpMusic. Steps run longest-first so the
     * cheapest exact hit wins over a fuzzy one.
     */
    private suspend fun fetch(track: Track, audioDurationSeconds: Double?): Fetched {
        val tally = Tally()
        val trace = Trace { _debugTrace.value = it }
        fun done(f: Fetched): Fetched {
            _debugTrace.value = trace.build()
            return f
        }
        val duration = audioDurationSeconds?.takeIf { it > 0 }
        val album = track.album.trim().takeIf { it.isNotEmpty() }
        trace.emit("input title=\"${track.title}\" artist=\"${track.artist}\" album=\"${track.album}\" duration=${duration?.let { "${it.toInt()}s" } ?: "unknown"}")

        val cleanedTitle = cleanQueryTitle(track.title)
        val cleanedArtist = cleanQueryArtist(track.artist)
        val rawTitle = track.title.trim()
        val rawArtist = track.artist.trim()
        val matchTitles = listOf(cleanedTitle, rawTitle)
            .map { it.trim() }.filter { it.isNotEmpty() }.distinct()

        // (title, artist) pairs to try: cleaned first, then the originals,
        // then an "Artist - Title" split when the artist looks like a channel.
        val pairs = ArrayList<Pair<String, String>>(4)
        fun addPair(t: String, a: String) {
            val tt = t.trim()
            val aa = a.trim()
            if (tt.isNotEmpty() && aa.isNotEmpty() &&
                pairs.none { it.first == tt && it.second == aa }
            ) {
                pairs += tt to aa
            }
        }
        addPair(cleanedTitle, cleanedArtist)
        addPair(rawTitle, rawArtist)
        splitArtistTitle(track.title, track.artist)?.let { (a, t) -> addPair(t, a) }

        trace.emit("cleaned title=\"$cleanedTitle\" artist=\"$cleanedArtist\"")

        // a) Exact lookup: with album first, then without on 404.
        // (Unchanged behaviour: a hit counts with synced or plain lyrics.)
        for ((t, a) in pairs) {
            val withAlbum = if (album != null) {
                attempt(tally, "get+album") {
                    client.get(t, a, album, duration) { url, code -> trace.http("get+album", url, code) }
                }
            } else {
                SourceReply.Miss
            }
            acceptHit(withAlbum)?.let {
                note("get", withAlbum, it)
                trace.emit("get: accepted (${kindOf(it)})")
                return done(it.toFetched())
            }
            if (withAlbum is SourceReply.Hit) {
                trace.emit("get: rejected, no usable lyrics")
            } else if (withAlbum is SourceReply.Failure) {
                trace.emit("get: request failed")
            }
            if (withAlbum is SourceReply.Miss) {
                val noAlbum = attempt(tally, "get-album") {
                    client.get(t, a, null, duration) { url, code -> trace.http("get-album", url, code) }
                }
                acceptHit(noAlbum)?.let {
                    note("get", noAlbum, it)
                    trace.emit("get: accepted (${kindOf(it)})")
                    return done(it.toFetched())
                }
                if (noAlbum is SourceReply.Hit) {
                    trace.emit("get: rejected, no usable lyrics")
                } else if (noAlbum is SourceReply.Failure) {
                    trace.emit("get: request failed")
                }
            }
        }

        // b) Named search on lrclib's own fields (no duration sent).
        if (cleanedTitle.isNotBlank() && cleanedArtist.isNotBlank()) {
            val reply = attempt(tally, "search-names") {
                client.searchByNames(cleanedTitle, cleanedArtist) { url, code ->
                    trace.http("search-names", url, code)
                }
            }
            selectSynced(reply, matchTitles, cleanedArtist, rawArtist, duration, tally, trace, "search-names")?.let {
                return done(it.toFetched())
            }
        }

        // c) Title-only search: the artist field can't be trusted (channels/labels).
        if (cleanedTitle.isNotBlank()) {
            val reply = attempt(tally, "search-title") {
                client.searchByTitleName(cleanedTitle) { url, code -> trace.http("search-title", url, code) }
            }
            selectSynced(reply, matchTitles, cleanedArtist, rawArtist, duration, tally, trace, "search-title")?.let {
                return done(it.toFetched())
            }
        }

        // d) Broad text search on the cleaned title alone, as a last lrclib fallback.
        if (cleanedTitle.isNotBlank()) {
            val reply = attempt(tally, "search-q") {
                client.searchByQuery(cleanedTitle) { url, code -> trace.http("search-q", url, code) }
            }
            selectSynced(reply, matchTitles, cleanedArtist, rawArtist, duration, tally, trace, "search-q")?.let {
                return done(it.toFetched())
            }
        }

        // e) Second source as the final fallback.
        val simpQuery = listOf(cleanedArtist.ifBlank { rawArtist }, cleanedTitle.ifBlank { rawTitle })
            .filter { it.isNotBlank() }.joinToString(" ").trim()
            .ifEmpty { "$rawArtist $rawTitle".trim() }
        if (simpQuery.isNotEmpty()) {
            val alt = fromSimpMusic(track, simpQuery, matchTitles, cleanedArtist, rawArtist, duration, tally, trace)
            if (alt != null) return done(alt.toFetched())
        }

        val outcome = if (tally.failed) Fetched.failed() else Fetched.empty()
        trace.emit("fetch done: ${if (tally.failed) "failed" else "empty"}")
        return done(outcome)
    }

    /** One network step with a hard per-request timeout. Timeouts are
     * transient (retry later); real cancellation still propagates. */
    private suspend fun <T> attempt(
        tally: Tally,
        step: String,
        block: suspend () -> SourceReply<T>
    ): SourceReply<T> {
        return try {
            withTimeout(STEP_TIMEOUT_MS) { block() }
        } catch (e: TimeoutCancellationException) {
            tally.failed = true
            Log.d("JinkiLyrics", "step=$step status=timeout kept=0")
            SourceReply.Failure
        }
    }

    private fun <T> takeItems(reply: SourceReply<List<T>>, tally: Tally): List<T> = when (reply) {
        is SourceReply.Hit -> reply.value
        SourceReply.Miss -> emptyList()
        SourceReply.Failure -> {
            tally.failed = true
            emptyList()
        }
    }

    /** An exact-lookup hit counts if it carries any usable lyrics. */
    private fun acceptHit(reply: SourceReply<LrcLibTrack>): LrcLibTrack? {
        val hit = (reply as? SourceReply.Hit)?.value ?: return null
        return hit.takeIf { accept(it) }
    }

    /** Any script, any language: usable means synced or plain text exists. */
    private fun accept(result: LrcLibTrack): Boolean =
        !result.syncedLyrics.isNullOrBlank() || !result.plainLyrics.isNullOrBlank()

    /**
     * Synced-only selection for the search steps: the title must match
     * (exact, or one contains the other) but the artist is only a bonus —
     * YouTube metadata is often a channel or label, not the performer.
     * Ranks by closest duration (unknown track duration → most lyric
     * lines), exact title first, artist match next. Records under 30 s are
     * dropped only when a longer one exists. Language is never a criterion.
     */
    private fun selectSynced(
        reply: SourceReply<List<LrcLibTrack>>,
        titles: List<String>,
        cleanedArtist: String,
        rawArtist: String,
        trackDuration: Double?,
        tally: Tally,
        trace: Trace,
        step: String
    ): LrcLibTrack? {
        val results = takeItems(reply, tally)
        val longExists = results.any { (it.durationSeconds ?: 0.0) >= SHORT_TRACK_CUTOFF_S }
        val pool = ArrayList<LrcLibTrack>()
        for ((i, r) in results.withIndex()) {
            if (i >= MAX_DEBUG_CANDIDATES) {
                trace.emit("$step ... +${results.size - i} more")
                break
            }
            val dur = r.durationSeconds
            val label = "[$i] ${r.artistName} – ${r.trackName} (${dur?.let { "${it.toInt()}s" } ?: "?"})"
            if (r.syncedLyrics.isNullOrBlank()) {
                trace.emit("$step $label: rejected, no synced lyrics")
                continue
            }
            if (titles.none { titleOk(r.trackName, it) }) {
                trace.emit("$step $label: rejected, title mismatch")
                continue
            }
            if (dur != null && dur < SHORT_TRACK_CUTOFF_S && longExists) {
                trace.emit("$step $label: rejected, too short (<30s), longer exists")
                continue
            }
            pool += r
            trace.emit("$step $label: candidate")
        }
        val picked = if (trackDuration != null && trackDuration > 0) {
            pool.minWithOrNull(
                compareBy(
                    { abs((it.durationSeconds ?: Double.MAX_VALUE) - trackDuration) },
                    { if (titles.any { t -> isExactTitle(it.trackName, t) }) 0 else 1 },
                    { if (artistBonus(it.artistName, cleanedArtist, rawArtist)) 0 else 1 }
                )
            )
        } else {
            pool.sortedWith(
                compareByDescending<LrcLibTrack> {
                    it.syncedLyrics.orEmpty().lines().count { l -> l.isNotBlank() }
                }
                    .thenBy { if (titles.any { t -> isExactTitle(it.trackName, t) }) 0 else 1 }
                    .thenBy { if (artistBonus(it.artistName, cleanedArtist, rawArtist)) 0 else 1 }
            ).firstOrNull()
        }
        if (picked != null) {
            val detail = if (trackDuration != null && trackDuration > 0) {
                "Δ${abs((picked.durationSeconds ?: trackDuration) - trackDuration).toInt()}s"
            } else {
                "${picked.syncedLyrics.orEmpty().lines().count { it.isNotBlank() }} lines"
            }
            trace.emit("$step picked: ${picked.artistName} – ${picked.trackName} ($detail)")
        }
        return picked
    }

    private fun normTitle(s: String): String =
        s.lowercase(Locale.ROOT).trim().replace(WHITESPACE, " ")

    private fun titleOk(candidate: String, want: String): Boolean {
        val c = normTitle(candidate)
        val w = normTitle(want)
        if (c.isEmpty() || w.isEmpty()) return false
        return c == w || c.contains(w) || w.contains(c)
    }

    private fun isExactTitle(candidate: String, want: String): Boolean {
        val c = normTitle(candidate)
        val w = normTitle(want)
        return c.isNotEmpty() && c == w
    }

    private fun artistBonus(candidate: String, vararg wanted: String): Boolean {
        val c = normTitle(candidate)
        if (c.isEmpty()) return false
        return wanted.map(::normTitle).filter { it.isNotEmpty() }
            .any { w -> w == c || c.contains(w) || w.contains(c) }
    }

    private fun statusOf(reply: SourceReply<*>): String = when (reply) {
        is SourceReply.Hit -> "Hit"
        SourceReply.Miss -> "Miss"
        SourceReply.Failure -> "Failure"
    }

    private fun note(step: String, reply: SourceReply<*>, result: LrcLibTrack) {
        Log.d("JinkiLyrics", "step=$step status=${statusOf(reply)} kept=1 kind=${kindOf(result)}")
    }

    private fun kindOf(result: LrcLibTrack): String =
        if (!result.syncedLyrics.isNullOrBlank()) "synced" else "plain"

    private suspend fun fromSimpMusic(
        track: Track,
        query: String,
        titles: List<String>,
        cleanedArtist: String,
        rawArtist: String,
        duration: Double?,
        tally: Tally,
        trace: Trace
    ): LrcLibTrack? {
        val byQuery = attempt(tally, "simp-q") {
            simpMusic.search(query) { url, code -> trace.http("simp-q", url, code) }
        }
        selectSynced(byQuery, titles, cleanedArtist, rawArtist, duration, tally, trace, "simp-q")?.let { return it }
        val byTitle = attempt(tally, "simp-title") {
            simpMusic.searchByTitle(titles.firstOrNull().orEmpty().ifBlank { track.title }) { url, code ->
                trace.http("simp-title", url, code)
            }
        }
        return selectSynced(byTitle, titles, cleanedArtist, rawArtist, duration, tally, trace, "simp-title")
    }

    private fun publish(key: String, lyrics: Lyrics) {
        _cache.update { it + (key to lyrics) }
    }

    companion object {
        const val STEP_TIMEOUT_MS = 8_000L
        const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
        const val RETRY_COOLDOWN_MS = 30_000L
        const val SHORT_TRACK_CUTOFF_S = 30.0
        const val MAX_DEBUG_CANDIDATES = 10
        val WHITESPACE = Regex("""\s+""")
    }
}


private class Tally {
    var failed = false
}


/** Per-lookup debug record, mirrored to logcat (tag JinkiLyrics). */
private class Trace(private val onUpdate: (String) -> Unit) {
    private val lines = ArrayList<String>()

    fun emit(s: String) {
        lines.add(s)
        Log.d("JinkiLyrics", s)
        // Live-publish too, so even an unexpected throw leaves the
        // partial trace behind instead of a stale one from last time.
        onUpdate(lines.joinToString("\n"))
    }

    fun http(step: String, url: String, code: Int?) {
        emit("$step url=$url status=${code?.toString() ?: "no-response"}")
    }

    fun build(): String = lines.joinToString("\n")
}


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

private fun LrcLibTrack.toFetched(): Fetched {
    val parsed = if (!syncedLyrics.isNullOrBlank()) LrcParser.parse(syncedLyrics) else emptyList()
    return when {
        parsed.isNotEmpty() -> Fetched(Lyrics.Synced(parsed), Fetched.TYPE_LRC, syncedLyrics!!)
        !plainLyrics.isNullOrBlank() -> Fetched(Lyrics.Plain(plainLyrics), Fetched.TYPE_PLAIN, plainLyrics)
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
