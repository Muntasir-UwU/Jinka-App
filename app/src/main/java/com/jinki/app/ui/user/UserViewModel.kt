package com.jinki.app.ui.user

import android.content.Context
import android.net.Uri
import android.text.format.DateUtils
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
import com.jinki.app.data.PresenceReporter
import com.jinki.app.data.UserPrefs
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.google.firebase.database.ServerValue
import com.jinki.app.data.encodeAvatarThumb
import com.jinki.app.data.storeAvatar
import com.jinki.app.di.FirebaseModule
import com.jinki.app.player.PlayerConnection
import com.jinki.app.poke.PokeNotifier
import com.jinki.app.util.ConnectivityObserver
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.coroutines.tasks.await


class UserViewModel(
    private val prefs: UserPrefs,
    listenDao: ListenDao,
    private val connection: PlayerConnection,
    private val auth: AuthRepository,
    private val database: FirebaseDatabase,
    private val connectivity: ConnectivityObserver
) : ViewModel() {

    val name: StateFlow<String> = prefs.userName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val history: StateFlow<Map<String, Int>> = listenDao
        .observeSince(ListenStats.todayKey(LocalDate.now().minusDays(3)))
        .combine(ticker()) { days, _ -> days.associate { it.date to it.seconds } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    
    val todaySeconds: StateFlow<Int> = history
        .map { ListenStats.todaySeconds(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val greeting: StateFlow<Greeting> = combine(name, history) { userName, days ->
        greetingQuote(ListenStats.todaySeconds(days), ListenStats.averageDailyMinutes(days), userName)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), greetingQuote(0, 0, ""))

    val timerRemaining: StateFlow<Long> = connection.sleepTimer.remainingSeconds
    val timerActive: StateFlow<Boolean> = connection.sleepTimer.isActive

    val wifiOnly: StateFlow<Boolean> = prefs.wifiOnly
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Profile photo path from storage: survives restarts, read by every screen showing the avatar. */
    val photoUri: StateFlow<String?> = prefs.avatarPath
        .map { it.ifBlank { null } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _avatarSaving = MutableStateFlow(false)
    val avatarSaving: StateFlow<Boolean> = _avatarSaving.asStateFlow()

    

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
        observeDisplayName()
        observePresence()
        observePokes()
    }

    // ---- People (matches what UserScreen expects) ----

    private val _activeUsers = MutableStateFlow<List<ActiveUser>>(emptyList())

    /** Everyone listed under /users, minus yourself. Online if in /presence. */
    val activeUsers: StateFlow<List<ActiveUser>> = _activeUsers.asStateFlow()

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
     * Live /users + /presence lists for everyone but [selfUid]. Presence
     * nodes look like { nowPlaying: "Title - Artist" } (older clients wrote
     * plain `true`, which simply means online). Listeners are removed when
     * the flow closes (the ViewModel's scope is cancelled on clear).
     * Never touches /playback or /private.
     */
    private fun liveActiveUsers(selfUid: String): Flow<List<ActiveUser>> = callbackFlow {
        val usersRef = database.getReference("users")
        val presenceRef = database.getReference("presence")
        var usersSnap: DataSnapshot? = null
        var presenceSnap: DataSnapshot? = null
        fun publish() {
            val u = usersSnap ?: return
            val p = presenceSnap ?: return
            trySend(usersSnapshotToActiveUsers(u, selfUid, presenceStatusOf(p)))
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
                presenceSnap = s
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

    // ---- Display name ----

    private val _nameLoaded = MutableStateFlow(false)

    /**
     * True only after the stored name has loaded AND (it is blank, or a
     * submitted name is still waiting for its first server confirmation),
     * so the overlay never flashes on launch — it appears solely when a
     * name is genuinely needed, and never again after confirmation.
     */
    val needsName: StateFlow<Boolean> = combine(
        _nameLoaded, name, prefs.namePending, prefs.nameConfirmed
    ) { loaded, n, pending, confirmed ->
        loaded && (n.isBlank() || (pending && !confirmed))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _saveState = MutableStateFlow<NameSaveState>(NameSaveState.Idle)

    val saveState: StateFlow<NameSaveState> = _saveState.asStateFlow()

    private fun observeDisplayName() {
        viewModelScope.launch {
            try {
                prefs.userName.first()
            } catch (_: Exception) { /* store unreadable: overlay stays hidden */ }
            _nameLoaded.value = true
        }
        observeNamePush()
    }

    /**
     * Overlay submit: validate, store on the device, mark the name pending,
     * then push until the server acknowledges. The overlay stays up the
     * whole time — it only closes when confirmation lands.
     */
    fun saveUserName(raw: String) {
        val clean = validateDisplayName(raw) ?: run {
            _saveState.value = NameSaveState.Error("Name must be 1 to 20 characters.")
            return
        }
        _saveState.value = NameSaveState.Saving
        viewModelScope.launch {
            try {
                prefs.setUserName(clean)
                prefs.setNamePending(true)
            } catch (_: Exception) {
                _saveState.value = NameSaveState.Error("Couldn't save the name. Try again.")
                return@launch
            }
            startPushJob(clean, onAck = {
                prefs.setNamePending(false)
                _saveState.value = NameSaveState.Idle
            })
        }
    }

    /**
     * Rename from settings: same validation and the same server push, but
     * it never touches the overlay state and never marks anything pending,
     * so the overlay can never appear because of a rename.
     */
    fun renameUserName(raw: String) {
        val clean = validateDisplayName(raw) ?: run {
            _saveState.value = NameSaveState.Error("Name must be 1 to 20 characters.")
            return
        }
        viewModelScope.launch {
            try {
                prefs.setUserName(clean)
            } catch (_: Exception) {
                _saveState.value = NameSaveState.Error("Couldn't save the name. Try again.")
                return@launch
            }
            startPushJob(clean, onAck = { })
        }
    }

    private fun validateDisplayName(raw: String): String? {
        val clean = raw.trim().filter { it != '\n' && it != '\r' }
        return clean.takeIf { it.length in 1..20 }
    }

    /** Cancel any in-flight push and start pushing [displayName] instead. */
    private fun startPushJob(displayName: String, onAck: suspend () -> Unit) {
        pushJob?.cancel()
        pushJob = viewModelScope.launch {
            pushUntilAck(displayName)
            onAck()
        }
    }

    private var pushJob: Job? = null

    /**
     * Push until the server acknowledges, retrying every few seconds.
     * Cancellation (a newer submit, ViewModel cleared) stops the loop.
     */
    private suspend fun pushUntilAck(displayName: String) {
        while (true) {
            try {
                val uid = auth.ensureSignedIn().uid
                database.getReference("users").child(uid)
                    .updateChildren(mapOf("name" to displayName)).await()
                prefs.setNameConfirmed(true)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                delay(NAME_PUSH_RETRY_MS)
            }
        }
    }

    /**
     * Whenever signed in with a local name on hand (app start, sign-in,
     * rename), push it if the server copy differs. Failures stay silent:
     * the next rename or sign-in tries again.
     */
    private fun observeNamePush() {
        viewModelScope.launch {
            auth.currentUser
                .map { it?.uid }
                .distinctUntilChanged()
                .collect { uid ->
                    if (uid == null) return@collect
                    val local = try {
                        prefs.userName.first()
                    } catch (_: Exception) {
                        return@collect
                    }
                    if (local.isBlank()) return@collect
                    pushNameIfDifferent(uid, local)
                }
        }
    }

    private suspend fun pushNameIfDifferent(uid: String, local: String) {
        try {
            val server = database.getReference("users").child(uid).child("name")
                .get().await().getValue(String::class.java)
            if (server != local) {
                database.getReference("users").child(uid)
                    .updateChildren(mapOf("name" to local)).await()
            }
            // The server holds this name now (either it already did, or the
            // write above just landed) — old installs confirm silently here.
            prefs.setNameConfirmed(true)
        } catch (_: Exception) { /* next rename or sign-in retries */ }
    }

    /**
     * Small line under the overlay field while saving: what we're waiting
     * for. Null whenever the overlay isn't in its saving state.
     */
    val statusText: StateFlow<String?> = combine(
        saveState,
        connectivity.isOnline,
        auth.currentUser.map { it?.uid }
    ) { state, online, uid ->
        if (state !is NameSaveState.Saving) null
        else if (!online || uid == null) "Waiting for connection…"
        else "Connecting…"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ---- Presence ----

    /**
     * Advertise what is playing while signed in. Writes only when the
     * value changes. The node is never deleted anymore: going offline
     * (sign-out, app backgrounded, crash, lost network) stamps
     * online=false + lastSeen instead, so others see "Last seen …".
     * PresenceReporter owns the foreground/background transitions;
     * this flow owns the nowPlaying text and marks online as a fallback
     * for sign-ins that complete after the first ON_START.
     */
    private fun observePresence() {
        viewModelScope.launch {
            var lastUid: String? = null
            var armedFor: String? = null
            combine(
                auth.currentUser.map { it?.uid }.distinctUntilChanged(),
                connection.state.map { s ->
                    s.track?.let { t -> "${t.title} - ${t.artist}".take(PRESENCE_TEXT_MAX) }
                }.distinctUntilChanged()
            ) { uid, label -> uid to label }
                .distinctUntilChanged()
                .collect { (uid, label) ->
                    val prev = lastUid
                    if (prev != null && prev != uid) {
                        runCatching {
                            database.getReference("presence").child(prev)
                                .updateChildren(PresenceReporter.offlineUpdate()).await()
                        }
                        armedFor = null
                    }
                    lastUid = uid
                    if (uid == null) return@collect
                    val ref = database.getReference("presence").child(uid)
                    if (armedFor != uid) {
                        runCatching {
                            ref.onDisconnect().updateChildren(PresenceReporter.offlineUpdate()).await()
                        }
                        armedFor = uid
                    }
                    runCatching {
                        if (label == null) ref.child("nowPlaying").removeValue().await()
                        else ref.updateChildren(mapOf("nowPlaying" to label, "online" to true)).await()
                    }
                }
        }
    }

    /**
     * Copy the picked image into app storage and remember it, showing it
     * right away. Keeps the old picture when the copy fails.
     */
    fun saveAvatar(context: Context, source: Uri) {
        viewModelScope.launch {
            _avatarSaving.value = true
            try {
                val appContext = context.applicationContext
                val path = storeAvatar(appContext, source, auth.currentUid() ?: "local")
                prefs.setAvatarPath(path)
                val uid = auth.currentUid()
                if (uid != null) {
                    try {
                        val payload = encodeAvatarThumb(appContext, path)
                        database.getReference("users").child(uid).updateChildren(
                            mapOf("avatar" to payload, "avatarUpdatedAt" to ServerValue.TIMESTAMP)
                        ).await()
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        _toast.emit(Toast(R.string.avatar_share_failed, listOf(e.javaClass.simpleName + ": " + e.message)))
                    }
                }
            } catch (e: Exception) {
                // Don't toast when the save was cancelled (screen gone).
                // TEMP DEBUG AID: name the exception class until the bug is fixed.
                if (e is kotlinx.coroutines.CancellationException) throw e
                _toast.emit(Toast(R.string.avatar_save_failed_detail, listOf(e.javaClass.simpleName + ": " + e.message)))
            } finally {
                _avatarSaving.value = false
            }
        }
    }

    /** File version so the photo reloads the moment a new one is saved. */
    val avatarVersion: StateFlow<Long> = prefs.avatarPath
        .map { path -> if (path.isBlank()) 0L else File(path).lastModified() }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    private val avatarDecoded = mutableMapOf<String, Pair<Long, Bitmap>>()

    /** Other users' photos, decoded off the main thread and cached by uid + timestamp. */
    val otherAvatars: StateFlow<Map<String, Bitmap>> = activeUsers
        .mapLatest { users -> decodeAvatarMap(users) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private suspend fun decodeAvatarMap(users: List<ActiveUser>): Map<String, Bitmap> =
        withContext(Dispatchers.Default) {
            val out = LinkedHashMap<String, Bitmap>()
            for (user in users) {
                val b64 = user.avatarB64
                val ts = user.avatarUpdatedAt
                if (b64.isNullOrBlank() || ts == null) continue
                val hit = avatarDecoded[user.id]
                if (hit != null && hit.first == ts) {
                    out[user.id] = hit.second
                    continue
                }
                try {
                    val bytes = Base64.decode(b64, Base64.NO_WRAP)
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
                    avatarDecoded[user.id] = ts to bmp
                    out[user.id] = bmp
                } catch (_: Exception) {
                    // Broken data: stay on the letter circle.
                } catch (_: OutOfMemoryError) {
                    // One hostile image must not take down the list.
                }
            }
            avatarDecoded.keys.retainAll(out.keys)
            out
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

    private val lastPokeAt = mutableMapOf<String, Long>()

    /** Incoming pokes (listener + worker) land here via PokeNotifier. */
    private fun observePokes() {
        _pokeCount.value = PokeNotifier.unread.value
        viewModelScope.launch {
            PokeNotifier.received.collect { _pokeCount.value += 1 }
        }
    }

    /** Badge reset when the You screen is opened. */
    fun clearPokes() {
        _pokeCount.value = 0
        try {
            PokeNotifier.reset()
        } catch (e: Exception) {
            Log.e("JinkiPush", "poke reset failed", e)
        }
    }

    /**
     * Send a poke: writes pokes/{toUid}/{pushId} = {from, fromName, at}.
     * Existing poke() counter is untouched here — it only grows on receipt.
     * One send per 30 s per target uid.
     */
    fun pokeUser(toUid: String, toName: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val last = lastPokeAt[toUid] ?: 0L
            if (now - last < POKE_COOLDOWN_MS) {
                _toast.emit(Toast(R.string.poke_cooldown))
                return@launch
            }
            lastPokeAt[toUid] = now
            try {
                val myUid = auth.ensureSignedIn().uid
                if (toUid == myUid) return@launch
                val fromName = try {
                    prefs.userName.first().trim().ifBlank { "Someone" }
                } catch (_: Exception) {
                    "Someone"
                }
                database.getReference("pokes").child(toUid).push()
                    .setValue(
                        mapOf(
                            "from" to myUid,
                            "fromName" to fromName.take(40),
                            "at" to ServerValue.TIMESTAMP
                        )
                    ).await()
                _toast.emit(Toast(R.string.poke_sent, listOf(toName)))
            } catch (e: Exception) {
                lastPokeAt.remove(toUid)
                Log.e("JinkiPush", "poke send failed", e)
                _toast.emit(Toast(R.string.poke_failed, listOf(e.message ?: e.javaClass.simpleName)))
            }
        }
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
                container.auth,
                FirebaseModule.database,
                container.connectivity
            ) as T
    }
}


/** Save progress for the username overlay. */
sealed interface NameSaveState {
    data object Idle : NameSaveState
    data object Saving : NameSaveState
    data class Error(val message: String) : NameSaveState
}


private const val NAME_PUSH_RETRY_MS = 5_000L

private const val POKE_COOLDOWN_MS = 30_000L

// Server rule: presence nowPlaying is a string of at most 120 characters.
private const val PRESENCE_TEXT_MAX = 120


private fun usersSnapshotToActiveUsers(
    snapshot: DataSnapshot,
    selfUid: String,
    presence: Map<String, PresenceStatus>
): List<ActiveUser> {
    @Suppress("UNCHECKED_CAST")
    val raw = snapshot.value as? Map<String, Any?> ?: emptyMap()
    return raw.mapNotNull { (id, value) ->
        if (id == selfUid) return@mapNotNull null
        val fields = value as? Map<String, Any?> ?: emptyMap()
        val name = (fields["name"] as? String).orEmpty().ifBlank { id }
        val status = presence[id]
        ActiveUser(
            id = id,
            name = name,
            nowPlaying = status?.nowPlaying,
            isActive = status?.online == true,
            lastSeen = status?.lastSeen?.let(::formatLastSeen),
            photoUri = fields["photoUri"] as? String,
            avatarB64 = fields["avatar"] as? String,
            avatarUpdatedAt = (fields["avatarUpdatedAt"] as? Number)?.toLong()
        )
    }.sortedWith(compareByDescending<ActiveUser> { it.isActive }.thenBy { it.name })
}


private data class PresenceStatus(
    val nowPlaying: String?,
    val online: Boolean,
    val lastSeen: Long?
)


/** uid -> presence detail; a missing/legacy node simply yields no entry. */
private fun presenceStatusOf(snapshot: DataSnapshot): Map<String, PresenceStatus> =
    snapshot.children.mapNotNull { child ->
        val key = child.key ?: return@mapNotNull null
        val map = child.value as? Map<*, *>
        key to PresenceStatus(
            nowPlaying = map?.get("nowPlaying") as? String,
            online = (map?.get("online") as? Boolean) ?: false,
            lastSeen = (map?.get("lastSeen") as? Number)?.toLong()
        )
    }.toMap()


private fun formatLastSeen(millis: Long): String =
    DateUtils.getRelativeTimeSpanString(
        millis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS
    ).toString()


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
