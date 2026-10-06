package com.jinki.app.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.roundToLong


class SleepTimer(
    private val scope: CoroutineScope,
    
    var onElapsed: () -> Unit
) {

    private val _remainingSeconds = MutableStateFlow(0L)
    val remainingSeconds: StateFlow<Long> = _remainingSeconds.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private var job: Job? = null

    
    fun start(minutes: Double): Boolean {
        
        
        if (minutes.isNaN() || minutes.isInfinite()) return false
        
        
        val durationMs = (minutes * MS_PER_MINUTE).roundToLong()
        if (durationMs <= 0) return false

        job?.cancel()
        val endAt = System.currentTimeMillis() + durationMs
        _isActive.value = true

        job = scope.launch {
            while (true) {
                val leftMs = endAt - System.currentTimeMillis()
                if (leftMs <= 0) break
                
                
                _remainingSeconds.value = ceil(leftMs / 1000.0).toLong()
                delay(minOf(leftMs, TICK_MS))
            }
            cancel()
            onElapsed()
        }
        return true
    }

    
    fun cancel(): Boolean {
        val had = _isActive.value
        job?.cancel()
        job = null
        _isActive.value = false
        _remainingSeconds.value = 0
        return had
    }

    companion object {
        const val MS_PER_MINUTE = 60_000.0
        const val TICK_MS = 1_000L
    }
}
