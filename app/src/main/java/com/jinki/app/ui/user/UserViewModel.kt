package com.jinki.app.ui.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jinki.app.AppContainer
import com.jinki.app.R
import com.jinki.app.data.ListenDao
import com.jinki.app.data.ListenStats
import com.jinki.app.data.UserPrefs
import com.jinki.app.player.PlayerConnection
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch


class UserViewModel(
    private val prefs: UserPrefs,
    listenDao: ListenDao,
    private val connection: PlayerConnection
) : ViewModel() {

    val name: StateFlow<String> = prefs.userName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "Muntasir")

    private val history: StateFlow<Map<String, Int>> = listenDao
        .observeSince(ListenStats.todayKey(LocalDate.now().minusDays(3)))
        .combine(ticker()) { days, _ -> days.associate { it.date to it.seconds } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    
    val todaySeconds: StateFlow<Int> = history
        .map { ListenStats.todaySeconds(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val greeting: StateFlow<Greeting> = combine(name, history) { userName, days ->
        greetingQuote(ListenStats.todaySeconds(days), ListenStats.averageDailyMinutes(days), userName)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), greetingQuote(0, 0, "Muntasir"))

    val timerRemaining: StateFlow<Long> = connection.sleepTimer.remainingSeconds
    val timerActive: StateFlow<Boolean> = connection.sleepTimer.isActive

    val wifiOnly: StateFlow<Boolean> = prefs.wifiOnly
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _photoUri = MutableStateFlow<String?>(null)
    val photoUri: StateFlow<String?> = _photoUri.asStateFlow()

    
    private val _partnerSeconds = MutableStateFlow<Int?>(null)
    val partnerSeconds: StateFlow<Int?> = _partnerSeconds.asStateFlow()

    fun setPartnerSeconds(seconds: Int?) {
        _partnerSeconds.value = seconds
    }

    private val _toast = MutableSharedFlow<Toast>(extraBufferCapacity = 1)
    val toast: SharedFlow<Toast> = _toast.asSharedFlow()

    private val _pokeCount = MutableStateFlow(0)
    val pokeCount: StateFlow<Int> = _pokeCount.asStateFlow()

    private val _messages = MutableStateFlow<List<String>>(emptyList())
    val messages: StateFlow<List<String>> = _messages.asStateFlow()

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()

    init {
        viewModelScope.launch {
            connection.timerElapsed.collect { _toast.emit(Toast(R.string.timer_elapsed_toast)) }
        }
    }

    fun setPhotoUri(uri: String?) {
        _photoUri.value = uri
    }

    
    fun startTimer(selectedMinutes: Int?, customMinutes: String) {
        val minutes = customMinutes.toDoubleOrNull()?.takeIf { it > 0 }
            ?: selectedMinutes?.toDouble()?.takeIf { it > 0 }
        if (minutes == null) {
            viewModelScope.launch { _toast.emit(Toast(R.string.timer_pick_duration)) }
            return
        }
        if (connection.sleepTimer.start(minutes)) {
            viewModelScope.launch {
                _toast.emit(Toast(R.string.timer_set_confirmation, listOf(minutes.toInt())))
            }
        }
    }

    fun cancelTimer() {
        connection.sleepTimer.cancel()
    }

    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            try { prefs.setWifiOnly(enabled) } catch (_: Exception) { }
        }
    }

    fun poke() {
        _pokeCount.value += 1
    }

    fun onDraftChange(text: String) {
        _draft.value = text
    }

    fun sendMessage() {
        val text = _draft.value.trim()
        if (text.isEmpty()) return
        _messages.value = _messages.value + text
        _draft.value = ""
    }

    
    private fun ticker() = kotlinx.coroutines.flow.flow {
        while (true) {
            emit(Unit)
            delay(1_000L)
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            UserViewModel(
                container.prefs,
                container.database.listenDao(),
                container.connection
            ) as T
    }
}


data class Toast(
    val resId: Int,
    val args: List<Any> = emptyList()
)


data class Greeting(
    val name: String,
    val quote: Quote
)

sealed interface Quote {
    
    data class Congrats(val listened: String) : Quote
    data object Missed : Quote
    data object Good : Quote
    data object Regular : Quote
}


fun greetingQuote(todaySeconds: Int, avgDailyMinutes: Int, name: String): Greeting {
    val quote = when {
        todaySeconds / 3600.0 >= 4.0 -> Quote.Congrats(ListenStats.formatDuration(todaySeconds))
        avgDailyMinutes < 10 -> Quote.Missed
        avgDailyMinutes < 30 -> Quote.Good
        else -> Quote.Regular
    }
    return Greeting(name, quote)
}


fun formatClock(totalSeconds: Int): String {
    val s = maxOf(0, totalSeconds)
    return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}
