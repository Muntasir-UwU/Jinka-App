package com.jinki.app.ui.search

private val NOISE_WORDS =
    "official|video|audio|lyrics?|lyrical|visuali[sz]er|hd|hq|4k|remaster(?:ed)?|" +
        "feat|ft|featuring|prod|music video|full song|from"


private val BRACKET_NOISE = Regex(
    """\s*[\(\[][^\)\]]*\b(?:$NOISE_WORDS)\b[^\)\]]*[\)\]]""",
    RegexOption.IGNORE_CASE
)


private val TRAILING_FEAT = Regex(
    """\s+(?:feat|ft|featuring)\b\.?\s+.*$""",
    RegexOption.IGNORE_CASE
)


private val TRAILING_DASH_NOISE = Regex(
    """\s*[-–—|]\s*(?:official|lyrics?|lyrical|video|audio|visuali[sz]er|full song|music video)\b.*$""",
    RegexOption.IGNORE_CASE
)

private val TOPIC_SUFFIX = Regex("""\s*-\s*Topic$""", RegexOption.IGNORE_CASE)
private val OFFICIAL_SUFFIX = Regex("""\s+official$""", RegexOption.IGNORE_CASE)
private val EXTRA_SPACES = Regex("""\s{2,}""")


fun cleanTitle(raw: String): String {
    val cleaned = raw
        .replace(BRACKET_NOISE, "")
        .replace(TRAILING_DASH_NOISE, "")
        .replace(TRAILING_FEAT, "")
        .replace(EXTRA_SPACES, " ")
        .trim(' ', '-', '–', '—', '|')
    return cleaned.ifBlank { raw.trim() }
}


fun cleanArtist(raw: String): String {
    val cleaned = raw
        .replace(TOPIC_SUFFIX, "")
        .replace(OFFICIAL_SUFFIX, "")
        .replace(EXTRA_SPACES, " ")
        .trim()
    return cleaned.ifBlank { raw.trim() }
}
