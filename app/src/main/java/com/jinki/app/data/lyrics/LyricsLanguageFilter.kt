package com.jinki.app.data.lyrics


object LyricsLanguageFilter {

    
    private val LATIN = Regex("[a-zA-Z\u00C0-\u024F]")

    
    private val BANGLA = Regex("[\u0980-\u09FF]")

    
    private val REJECTED = Regex(
        "[\\u0900-\\u097F\\u0A00-\\u0A7F\\u0A80-\\u0AFF\\u0B00-\\u0B7F" +
            "\\u0B80-\\u0BFF\\u0C00-\\u0C7F\\u0C80-\\u0CFF\\u0D00-\\u0D7F" +
            "\\u0600-\\u06FF\\u0750-\\u077F\\u4E00-\\u9FFF\\u3040-\\u309F" +
            "\\u30A0-\\u30FF\\uAC00-\\uD7AF\\u0400-\\u04FF\\u0500-\\u052F" +
            "\\u0E00-\\u0E7F\\u1000-\\u109F]"
    )

    private val STAMP = Regex("""\[\d{2}:\d{2}([.:]\d{1,3})?]""")

    
    fun isAcceptableLettered(text: String?): Boolean {
        if (text.isNullOrEmpty()) return false
        val cleaned = STAMP.replace(text, "").trim()
        if (cleaned.isEmpty()) return true

        val accepted = LATIN.findAll(cleaned).count() + BANGLA.findAll(cleaned).count()
        val rejected = REJECTED.findAll(cleaned).count()

        if (rejected > 0) return false

        val totalAlpha = accepted + rejected
        if (totalAlpha == 0) return true

        return accepted.toDouble() / totalAlpha > 0.7
    }

    
    fun isLyricsAcceptable(lyricsText: String?): Boolean {
        if (lyricsText.isNullOrEmpty()) return false
        val lines = lyricsText.split('\n')
            .filter { STAMP.replace(it, "").trim().isNotEmpty() }
        if (lines.isEmpty()) return true
        val good = lines.count { isAcceptableLettered(it) }
        return good.toDouble() / lines.size >= 0.8
    }
}
