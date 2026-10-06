package com.jinki.app.data.lyrics


data class LyricLine(
    val timeSeconds: Double,
    val text: String
)


object LrcParser {

    private const val STAMP_BODY = """\d{1,3}:\d{2}(?:[.:]\d{1,3})?"""

    private val STAMP = Regex("""\[($STAMP_BODY)]""")
    private val LEADING_STAMPS = Regex("""^\s*(?:\[$STAMP_BODY]\s*)+""")
    private val WORD_TAG = Regex("""<$STAMP_BODY>""")
    private val OFFSET = Regex("""^\s*\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)

    
    fun parse(lrc: String?): List<LyricLine> {
        if (lrc.isNullOrEmpty()) return emptyList()

        var offsetSeconds = 0.0
        val out = ArrayList<LyricLine>()

        for (raw in lrc.split('\n')) {
            val line = raw.trimEnd('\r')

            val offsetMatch = OFFSET.find(line)
            if (offsetMatch != null) {
                offsetSeconds = (offsetMatch.groupValues[1].toLongOrNull() ?: 0L) / 1000.0
                continue
            }

            val prefix = LEADING_STAMPS.find(line) ?: continue
            val text = WORD_TAG.replace(line.substring(prefix.range.last + 1), "").trim()

            for (m in STAMP.findAll(prefix.value)) {
                val seconds = parseStamp(m.groupValues[1]) ?: continue
                out += LyricLine((seconds - offsetSeconds).coerceAtLeast(0.0), text)
            }
        }
        return out.sortedBy { it.timeSeconds }
    }

    
    fun parseStamp(stamp: String): Double? {
        val colon = stamp.indexOf(':')
        if (colon <= 0) return null
        val minutes = stamp.substring(0, colon).toIntOrNull() ?: return null
        val seconds = stamp.substring(colon + 1).replace(':', '.').toDoubleOrNull() ?: return null
        if (seconds.isNaN() || seconds.isInfinite()) return null
        return minutes * 60.0 + seconds
    }

    
    fun stripTimestamps(text: String): String =
        WORD_TAG.replace(STAMP.replace(text, ""), "").trim()

    
    fun activeIndex(lyrics: List<LyricLine>, timeSeconds: Double): Int {
        var lo = 0
        var hi = lyrics.lastIndex
        var answer = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lyrics[mid].timeSeconds <= timeSeconds) {
                answer = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return answer
    }

    
    fun plainLines(text: String?): List<String> {
        if (text.isNullOrEmpty()) return emptyList()
        return text.split('\n').map { line ->
            if (line.trim().isEmpty()) "\u00A0" else line
        }
    }
}
