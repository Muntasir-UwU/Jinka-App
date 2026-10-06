package com.jinki.app.data.artwork

import com.jinki.app.data.Track
import com.jinki.app.data.source.SOURCE_SOUNDCLOUD
import com.jinki.app.data.source.SearchHit
import com.jinki.app.data.source.isVideoId
import kotlin.math.abs
import kotlin.math.max


fun normalizeArtworkText(value: String?): String =
    (value ?: "")
        .lowercase()
        .replace(PAREN_OR_BRACKET, " ")
        .replace(FILLER_WORDS, " ")
        .replace(NON_WORD, " ")
        .replace(WHITESPACE, " ")
        .trim()


fun artworkTokenScore(a: String?, b: String?): Double {
    val aa = normalizeArtworkText(a).split(' ').filter { it.isNotEmpty() }
    val bb = normalizeArtworkText(b).split(' ').filter { it.isNotEmpty() }.toSet()
    if (aa.isEmpty() || bb.isEmpty()) return 0.0
    return aa.count { it in bb }.toDouble() / max(aa.size, bb.size).toDouble()
}


fun soundCloudArtworkCandidates(url: String?): List<String> {
    val value = url?.trim().orEmpty()
    if (value.isEmpty()) return emptyList()
    val out = ArrayList<String>(3)
    fun add(candidate: String) {
        if (candidate.isNotEmpty() && candidate !in out) out.add(candidate)
    }
    if (SIZED_IMAGE_SUFFIX.containsMatchIn(value)) {
        add(SIZED_IMAGE_SUFFIX.replace(value, "-original$2$3"))
        add(SIZED_IMAGE_SUFFIX.replace(value, "-t500x500$2$3"))
    }
    add(value)
    return out
}


fun youtubeArtworkCandidates(id: String?): List<String> {
    val value = id?.trim().orEmpty()
    if (!isVideoId(value)) return emptyList()
    return listOf("maxresdefault", "sddefault", "hqdefault", "mqdefault")
        .map { "https://i.ytimg.com/vi/$value/$it.jpg" }
}



fun findMatchedSoundCloudArtwork(track: Track, searchResults: List<SearchHit>): List<String> {
    
    
    if (track.source == SOURCE_SOUNDCLOUD || searchResults.isEmpty()) return emptyList()

    val anchor = searchResults.firstOrNull { it.storageKey == track.storageKey }
        ?: searchResults.firstOrNull {
            it.source != SOURCE_SOUNDCLOUD &&
                normalizeArtworkText(it.title) == normalizeArtworkText(track.title) &&
                artworkTokenScore(it.artist, track.artist) >= ANCHOR_ARTIST_SCORE
        }
        ?: return emptyList()

    val anchorTitle = normalizeArtworkText(anchor.title)
    val anchorArtist = normalizeArtworkText(anchor.artist)
    val anchorDuration = anchor.durationSeconds

    
    
    var best: SearchHit? = null
    var bestScore = 0.0
    var bestTitle = 0.0
    var bestArtist = 0.0
    var bestDuration = 0.0
    for (candidate in searchResults) {
        if (candidate.source != SOURCE_SOUNDCLOUD || candidate.thumbnailUrl.isBlank()) continue
        val titleScore = artworkTokenScore(anchorTitle, candidate.title)
        val artistScore = artworkTokenScore(anchorArtist, candidate.artist)
        val candidateDuration = candidate.durationSeconds
        val durationScore =
            if (anchorDuration > 0 && candidateDuration > 0) {
                max(
                    0.0,
                    1.0 - abs(anchorDuration - candidateDuration).toDouble() /
                        max(anchorDuration, candidateDuration).toDouble()
                )
            } else {
                0.0
            }
        val score = titleScore * TITLE_WEIGHT + artistScore * ARTIST_WEIGHT +
            durationScore * DURATION_WEIGHT
        if (best == null || score > bestScore) {
            best = candidate
            bestScore = score
            bestTitle = titleScore
            bestArtist = artistScore
            bestDuration = durationScore
        }
    }

    val hit = best ?: return emptyList()
    val strongTextMatch = bestTitle >= MIN_TITLE_SCORE && bestArtist >= MIN_ARTIST_SCORE &&
        (bestDuration >= MIN_DURATION_SCORE || anchorDuration <= 0 || hit.durationSeconds <= 0)
    if (!strongTextMatch || bestScore < MIN_TOTAL_SCORE) return emptyList()

    return soundCloudArtworkCandidates(hit.thumbnailUrl)
}


fun trackThumbnailCandidates(track: Track, searchResults: List<SearchHit> = emptyList()): List<String> {
    val out = ArrayList<String>()
    findMatchedSoundCloudArtwork(track, searchResults).forEach { addUrl(out, it) }
    if (track.source == SOURCE_SOUNDCLOUD) {
        soundCloudArtworkCandidates(track.thumbnailUrl).forEach { addUrl(out, it) }
    } else {
        youtubeArtworkCandidates(track.id).forEach { addUrl(out, it) }
    }
    addUrl(out, track.thumbnailUrl)
    return out
}


fun trackThumbnailCandidates(hit: SearchHit, searchResults: List<SearchHit> = emptyList()): List<String> {
    val out = ArrayList<String>()
    findMatchedSoundCloudArtwork(hit.toTrackSkeleton(), searchResults).forEach { addUrl(out, it) }
    if (hit.source == SOURCE_SOUNDCLOUD) {
        soundCloudArtworkCandidates(hit.thumbnailUrl).forEach { addUrl(out, it) }
    } else {
        youtubeArtworkCandidates(hit.id).forEach { addUrl(out, it) }
    }
    addUrl(out, hit.thumbnailHighUrl)
    addUrl(out, hit.thumbnailFallbackUrl)
    addUrl(out, hit.thumbnailUrl)
    return out
}


private fun addUrl(out: MutableList<String>, value: String?) {
    val trimmed = value?.trim().orEmpty()
    if (trimmed.isNotEmpty() && trimmed !in out) out.add(trimmed)
}


private fun SearchHit.toTrackSkeleton(): Track = Track(
    id = id,
    source = source,
    title = title,
    artist = artist
)

private val PAREN_OR_BRACKET = Regex("""\([^)]*\)|\[[^\]]*]""")


private val FILLER_WORDS =
    Regex("""official|audio|video|music|lyrics|visualizer|remix|version|full|hd|hq""")

private val NON_WORD = Regex("""[^a-z0-9\u00C0-\u024F\u0980-\u09FF]+""")
private val WHITESPACE = Regex("""\s+""")

private val SIZED_IMAGE_SUFFIX =
    Regex("""-(large|t\d+x\d+|small|tiny)\.(jpg|jpeg|png|webp)(\?.*)?$""", RegexOption.IGNORE_CASE)

private const val ANCHOR_ARTIST_SCORE = 0.5
private const val TITLE_WEIGHT = 0.5
private const val ARTIST_WEIGHT = 0.35
private const val DURATION_WEIGHT = 0.15
private const val MIN_TITLE_SCORE = 0.65
private const val MIN_ARTIST_SCORE = 0.5
private const val MIN_DURATION_SCORE = 0.75
private const val MIN_TOTAL_SCORE = 0.68
