package com.jinki.app.data.lyrics

import kotlin.math.roundToLong


data class LyricLine(
    val timeSeconds: Double,
    val text: String
)


/** A lyric line with millisecond precision, ready for smooth display. */
data class TimedLyricLine(
    val timeMs: Long,
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


    private const val GAP_MS = 6000L
    private const val GAP_MARK_AT_MS = 3000L
    private const val GAP_MARK_TEXT = "♪"


    /**
     * Full LRC pipeline to millisecond lines: 1- to 3-digit fractions,
     * several timestamps on one line, and the [offset:+/-ms] tag (positive
     * delays the display, per the LRC convention). Empty lines are dropped;
     * when silence between two lines exceeds [GAP_MS], a "♪" marker is
     * inserted [GAP_MARK_AT_MS] into the gap. Result is sorted by time.
     */
    fun parseMs(lrc: String?): List<TimedLyricLine> {
        if (lrc.isNullOrEmpty()) return emptyList()

        var offsetMs = 0L
        val stamped = ArrayList<Pair<Long, String>>()

        for (raw in lrc.split('\n')) {
            val line = raw.trimEnd('\r')

            val offsetMatch = OFFSET.find(line)
            if (offsetMatch != null) {
                offsetMs = offsetMatch.groupValues[1].toLongOrNull() ?: 0L
                continue
            }

            val prefix = LEADING_STAMPS.find(line) ?: continue
            val text = WORD_TAG.replace(line.substring(prefix.range.last + 1), "").trim()
            if (text.isEmpty()) continue

            for (m in STAMP.findAll(prefix.value)) {
                val ms = parseStampMs(m.groupValues[1]) ?: continue
                stamped += (ms + offsetMs).coerceAtLeast(0L) to text
            }
        }
        return withGaps(stamped.sortedBy { it.first })
    }


    private fun parseStampMs(stamp: String): Long? {
        val colon = stamp.indexOf(':')
        if (colon <= 0) return null
        val minutes = stamp.substring(0, colon).toLongOrNull() ?: return null
        val rest = stamp.substring(colon + 1).replace(':', '.')
        val dot = rest.indexOf('.')
        val seconds: Long
        val millis: Long
        if (dot < 0) {
            seconds = rest.toLongOrNull() ?: return null
            millis = 0L
        } else {
            seconds = rest.substring(0, dot).toLongOrNull() ?: return null
            millis = rest.substring(dot + 1).take(3).padEnd(3, '0').toLongOrNull()
                ?: return null
        }
        if (minutes < 0 || seconds < 0) return null
        return minutes * 60_000L + seconds * 1000L + millis
    }


    /** Converts already-parsed (seconds) lines to display (ms + gaps). */
    fun retime(lines: List<LyricLine>): List<TimedLyricLine> {
        val stamped = lines
            .filter { it.text.trim().isNotEmpty() }
            .map { (it.timeSeconds * 1000.0).roundToLong().coerceAtLeast(0L) to it.text.trim() }
            .sortedBy { it.first }
        return withGaps(stamped)
    }


    private fun withGaps(stamped: List<Pair<Long, String>>): List<TimedLyricLine> {
        if (stamped.isEmpty()) return emptyList()
        val out = ArrayList<TimedLyricLine>(stamped.size + 2)
        var prev = 0L
        var first = true
        for ((time, text) in stamped) {
            if (!first && time - prev > GAP_MS) {
                out += TimedLyricLine(prev + GAP_MARK_AT_MS, GAP_MARK_TEXT)
            } else if (first && time > GAP_MS) {
                out += TimedLyricLine(GAP_MARK_AT_MS, GAP_MARK_TEXT)
            }
            out += TimedLyricLine(time, text)
            prev = time
            first = false
        }
        return out
    }


    /** Last line at or before [timeMs], or -1. Binary search, O(log n). */
    fun activeIndexMs(lines: List<TimedLyricLine>, timeMs: Long): Int {
        var lo = 0
        var hi = lines.lastIndex
        var answer = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= timeMs) {
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
