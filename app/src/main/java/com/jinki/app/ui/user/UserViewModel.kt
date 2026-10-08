package com.jinki.app.ui.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.jinki.app.AppContainer
import com.jinki.app.R
import com.jinki.app.data.AuthRepository
import com.jinki.app.data.ListenDao
import com.jinki.app.data.ListenStats
import com.jinki.app.data.SyncRepository
import com.jinki.app.data.UserPrefs
import com.jinki.app.di.FirebaseModule
import com.jinki.app.player.PlayerConnection
import java.time.LocalDate
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch


class UserViewModel(
    private val prefs: UserPrefs,
    listenDao: ListenDao,
    private val connection: PlayerConnection,
    private val auth: AuthRepository,
    private val sync: SyncRepository,
    private val database: FirebaseDatabase
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
        observeActiveUsers()
    }

    // ---- Sync (matches what UserScreen expects) ----

    private val _activeUsers = MutableStateFlow<List<ActiveUser>>(emptyList())

    /** Everyone listed under /users with a /presence marker, minus yourself. */
    val activeUsers: StateFlow<List<ActiveUser>> = _activeUsers.asStateFlow()

    private val _syncedWith = MutableStateFlow<ActiveUser?>(null)

    /** Who you are currently synced with, or null. */
    val syncedWith: StateFlow<ActiveUser?> = _syncedWith.asStateFlow()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)

    /** Password-check progress for the sync sheet. */
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private fun observeActiveUsers() {
        viewModelScope.launch {
            auth.currentUser
                .map { it?.uid }
                .distinctUntilChanged()
                .flatMapLatest { uid ->
                    if (uid == null) flowOf(emptyList<ActiveUser>()) else liveActiveUsers(uid)
                }
                .collect { _activeUsers.value = it }
        }
    }

    /**
     * Live /users + /presence lists for everyone but [selfUid]. Listeners
     * are removed when the flow closes (the ViewModel's scope is cancelled
     * on clear). Never touches /playback or /private.
     */
    private fun liveActiveUsers(selfUid: String): Flow<List<ActiveUser>> = callbackFlow {
        val usersRef = database.getReference("users")
        val presenceRef = database.getReference("presence")
        var usersSnap: DataSnapshot? = null
        var present: Set<String> = emptySet()
        fun publish() {
            val snap = usersSnap ?: return
            trySend(usersSnapshotToActiveUsers(snap, selfUid, present))
        }
        val usersListener = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                usersSnap = s
                publish()
            }

            override fun onCancelled(e: DatabaseError) { /* keep last list */ }
        }
        val presenceListener = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                present = s.children.mapNotNull { it.key }.toSet()
                publish()
            }

            override fun onCancelled(e: DatabaseError) { /* keep last list */ }
        }
        usersRef.addValueEventListener(usersListener)
        presenceRef.addValueEventListener(presenceListener)
        awaitClose {
            usersRef.removeEventListener(usersListener)
            presenceRef.removeEventListener(presenceListener)
        }
    }

    /**
     * Check [password] for [userId] on the server. The password lives only
     * in this call's parameter — it is never logged, never put in state,
     * and gone when the call returns.
     */
    fun syncWithUser(userId: String, password: String) {
        if (_syncState.value is SyncState.Checking) return
        _syncState.value = SyncState.Checking
        viewModelScope.launch {
            val result = sync.verifySyncPassword(userId, password)
            result.fold(
                onSuccess = {
                    _syncedWith.value = _activeUsers.value.firstOrNull { it.id == userId }
                        ?: ActiveUser(id = userId, name = userId)
                    _syncState.value = SyncState.Success
                },
                onFailure = { e ->
                    _syncState.value =
                        if (e.message == WRONG_PASSWORD_MESSAGE) SyncState.WrongPassword
                        else SyncState.Error(e.message ?: "Something went wrong. Try again.")
                }
            )
        }
    }

    /**
     * Stop syncing. Local only: the server grant simply expires on its own,
     * so this never writes to syncGrants (clients can't anyway).
     */
    fun stopSync() {
        _syncedWith.value = null
        setPartnerSeconds(null)
        _syncState.value = SyncState.Idle
    }

    fun resetSyncState() {
        _syncState.value = SyncState.Idle
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
                container.connection,
                AuthRepository(FirebaseModule.auth),
                SyncRepository(FirebaseModule.functions),
                FirebaseModule.database
            ) as T
    }
}


// Matches SyncRepository's PERMISSION_DENIED message: this exact text means
// "wrong password" as opposed to any other failure.
private const val WRONG_PASSWORD_MESSAGE = "Incorrect password."


private fun usersSnapshotToActiveUsers(
    snapshot: DataSnapshot,
    selfUid: String,
    present: Set<String>
): List<ActiveUser> {
    @Suppress("UNCHECKED_CAST")
    val raw = snapshot.value as? Map<String, Any?> ?: emptyMap()
    return raw.mapNotNull { (id, value) ->
        if (id == selfUid) return@mapNotNull null
        val fields = value as? Map<String, Any?> ?: emptyMap()
        val name = (fields["name"] as? String).orEmpty().ifBlank { id }
        ActiveUser(
            id = id,
            name = name,
            nowPlaying = fields["nowPlaying"] as? String,
            isActive = id in present,
            lastSeen = fields["lastSeen"] as? String,
            photoUri = fields["photoUri"] as? String
        )
    }.sortedWith(compareByDescending<ActiveUser> { it.isActive }.thenBy { it.name })
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
