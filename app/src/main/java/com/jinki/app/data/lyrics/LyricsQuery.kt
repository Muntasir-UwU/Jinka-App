package com.jinki.app.data.lyrics

/**
 * Query cleaning for lyrics lookups.
 *
 * YouTube titles/artists arrive dirty ("Official Video", "feat.", "VEVO",
 * "Artist - Topic" channels). lrclib matches on exact metadata, so the dirt
 * has to go — but nothing here touches the script a title is written in.
 * Non-Latin text passes through byte-identical. (OkHttp's
 * addQueryParameter URL-encodes everything, so no manual encoding either.)
 */
private val BRACKET_NOISE = Regex(
    """\s*[\(\[][^()\[\]]*\b(official|video|audio|lyrics?|lyrical|visualizer|hd|hq|feat\.?|ft\.?|featuring|with|remix|version|music|explicit|clean|live|acoustic)\b[^()\[\]]*[\)\]]""",
    RegexOption.IGNORE_CASE
)

private val TRAILING_FEAT = Regex(
    """\s+(?:feat\.?|ft\.?|featuring|with)\s+.*$""",
    RegexOption.IGNORE_CASE
)

private val TRAILING_DASH_NOISE = Regex(
    """\s*[-–—|]\s*(?:official|lyrics?|video|audio).*$""",
    RegexOption.IGNORE_CASE
)

private val TRAILING_PIPE = Regex("""\s*\|.*$""")

private val TOPIC_SUFFIX = Regex("""\s*-\s*topic$""", RegexOption.IGNORE_CASE)
private val VEVO_WORD = Regex("""\bvevo\b""", RegexOption.IGNORE_CASE)
private val OFFICIAL_SUFFIX = Regex("""\s+official$""", RegexOption.IGNORE_CASE)
private val EXTRA_SPACES = Regex("""\s+""")

private val CHANNEL_HINT = Regex(
    """vevo|\btopic\b|official|music|records|entertainment|studio|channel""",
    RegexOption.IGNORE_CASE
)

/** "Song (Official Video) [feat. X]" -> "Song". Falls back to the raw title. */
fun cleanQueryTitle(raw: String): String {
    val cleaned = raw
        .replace(BRACKET_NOISE, "")
        .replace(TRAILING_DASH_NOISE, "")
        .replace(TRAILING_FEAT, "")
        .replace(TRAILING_PIPE, "")
        .replace(EXTRA_SPACES, " ")
        .trim(' ', '-', '–', '—', '|')
    return cleaned.ifBlank { raw.trim() }
}

/** "Artist - Topic" / "ArtistVEVO" / "Artist official" -> "Artist". */
fun cleanQueryArtist(raw: String): String {
    val cleaned = raw
        .replace(TOPIC_SUFFIX, "")
        .replace(VEVO_WORD, "")
        .replace(OFFICIAL_SUFFIX, "")
        .replace(EXTRA_SPACES, " ")
        .trim(' ', '-', '–', '—')
    return cleaned.ifBlank { raw.trim() }
}

/**
 * "Artist - Title" where the artist looks like a channel ("XVEVO",
 * "X - Topic", "X Official") -> (artist, title). Null otherwise, in which
 * case the caller keeps the fields as they are.
 */
fun splitArtistTitle(title: String, artist: String): Pair<String, String>? {
    if (!CHANNEL_HINT.containsMatchIn(artist)) return null
    val cut = title.indexOf(" - ")
    if (cut <= 0) return null
    val a = title.substring(0, cut).trim()
    val t = title.substring(cut + 3).trim()
    if (a.isEmpty() || t.isEmpty()) return null
    return a to t
}
