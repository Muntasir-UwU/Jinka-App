package com.jinki.app.data

import java.time.LocalDate


object ListenStats {

    
    fun todayKey(date: LocalDate = LocalDate.now()): String =
        "%04d-%02d-%02d".format(date.year, date.monthValue, date.dayOfMonth)

    
    fun lastFourDays(today: LocalDate = LocalDate.now()): List<String> =
        (3 downTo 0).map { todayKey(today.minusDays(it.toLong())) }

    
    fun averageDailyMinutes(history: Map<String, Int>, today: LocalDate = LocalDate.now()): Int {
        val total = lastFourDays(today).sumOf { history[it] ?: 0 }
        return Math.round(total / 4f / 60f)
    }

    fun todaySeconds(history: Map<String, Int>, today: LocalDate = LocalDate.now()): Int =
        history[todayKey(today)] ?: 0

    
    fun formatDuration(totalSeconds: Int): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }
}


class ListenTracker(
    private val onFlush: suspend (dateKey: String, totalSeconds: Int) -> Unit
) {
    private var segmentStartMs: Long? = null
    private var accumulatedSeconds: Int = 0

    @Synchronized
    fun start(nowMs: Long = System.currentTimeMillis()) {
        if (segmentStartMs != null) return
        segmentStartMs = nowMs
    }

    
    @Synchronized
    fun stop(nowMs: Long = System.currentTimeMillis()) {
        val start = segmentStartMs ?: return
        segmentStartMs = null
        val delta = ((nowMs - start) / 1000.0).toInt()
        if (delta > 0) accumulatedSeconds += delta
    }

    
    suspend fun flush(): Int {
        stop()
        val total = synchronized(this) { accumulatedSeconds }
        if (total <= 0) return 0
        onFlush(ListenStats.todayKey(), total)
        synchronized(this) { accumulatedSeconds = 0 }
        return total
    }
}
