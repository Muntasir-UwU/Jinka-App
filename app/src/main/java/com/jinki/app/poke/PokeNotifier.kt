package com.jinki.app.poke

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.jinki.app.MainActivity
import com.jinki.app.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Free-tier poke notifications: Realtime Database + local notifications only.
 * Single listenerelen (one ChildEventListener at a time) shared by the
 * Application (foreground) and the PlaybackService (while music plays).
 * The periodic worker covers the dead-process case via [drainOnce].
 */
object PokeNotifier {

    const val CHANNEL_ID = "pokes"
    private const val TAG = "JinkiPush"
    private const val PREFS = "poke_prefs"
    private const val KEY_UNREAD = "unread"
    private const val MAX_AGE_MS = 10 * 60 * 1000L

    private val owners = mutableSetOf<String>()
    private var currentUid: String? = null
    private var listener: ChildEventListener? = null
    private var queryRef: DatabaseReference? = null
    private var appCtx: Context? = null

    private val _unread = MutableStateFlow(0)
    val unread: StateFlow<Int> = _unread.asStateFlow()

    private val _received = MutableSharedFlow<Unit>(extraBufferCapacity = 64)
    val received: SharedFlow<Unit> = _received.asSharedFlow()

    fun ensureChannel(context: Context) {
        appCtx = context.applicationContext
        loadPersisted()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.poke_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "create poke channel failed", e)
        }
    }

    @Synchronized
    fun attach(context: Context, uid: String, owner: String) {
        appCtx = context.applicationContext
        loadPersisted()
        owners.add(owner)
        if (currentUid != uid) {
            removeListenerLocked()
            currentUid = uid
        }
        if (listener != null) return
        try {
            val ref = FirebaseDatabase.getInstance().getReference("pokes").child(uid)
            val l = object : ChildEventListener {
                override fun onChildAdded(snap: DataSnapshot, prev: String?) {
                    handleChild(appCtx ?: context.applicationContext, uid, snap)
                }
                override fun onChildChanged(s: DataSnapshot, p: String?) = Unit
                override fun onChildRemoved(s: DataSnapshot) = Unit
                override fun onChildMoved(s: DataSnapshot, p: String?) = Unit
                override fun onCancelled(e: DatabaseError) {
                    Log.e(TAG, "poke listener cancelled: ${e.message}")
                }
            }
            ref.addChildEventListener(l)
            queryRef = ref
            listener = l
        } catch (e: Exception) {
            Log.e(TAG, "poke attach failed", e)
        }
    }

    @Synchronized
    fun detach(owner: String) {
        owners.remove(owner)
        if (owners.isEmpty()) {
            removeListenerLocked()
            currentUid = null
        }
    }

    /** Badge reset when the You screen is opened. */
    fun reset() {
        _unread.value = 0
        try {
            appCtx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                ?.edit()?.putInt(KEY_UNREAD, 0)?.apply()
        } catch (e: Exception) {
            Log.e(TAG, "poke reset failed", e)
        }
    }

    /** Worker path: read pokes/{myUid} once, notify, delete. Never throws. */
    suspend fun drainOnce(context: Context) {
        val ctx = context.applicationContext
        appCtx = ctx
        try {
            val uid = try {
                FirebaseAuth.getInstance().currentUser?.uid
            } catch (e: Exception) {
                Log.e(TAG, "poke drain: no auth", e)
                return
            } ?: return
            val ref = try {
                FirebaseDatabase.getInstance().getReference("pokes").child(uid)
            } catch (e: Exception) {
                Log.e(TAG, "poke drain: no database", e)
                return
            }
            val snap = try {
                ref.get().await()
            } catch (e: Exception) {
                Log.e(TAG, "poke drain get failed", e)
                return
            }
            for (child in snap.children) {
                val pushId = child.key ?: continue
                val from = child.child("from").getValue(String::class.java)
                val fromName = child.child("fromName").getValue(String::class.java)
                val at = child.child("at").getValue(Long::class.java)
                    ?: (child.child("at").getValue(Double::class.java)?.toLong())
                if (isStaleOrSelf(uid, from, at)) {
                    try {
                        ref.child(pushId).removeValue().await()
                    } catch (e: Exception) {
                        Log.e(TAG, "poke drain delete stale failed", e)
                    }
                    continue
                }
                onValidPoke(ctx, pushId, fromName)
                try {
                    ref.child(pushId).removeValue().await()
                } catch (e: Exception) {
                    Log.e(TAG, "poke drain delete failed", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "poke drain failed", e)
        }
    }

    private fun handleChild(ctx: Context, myUid: String, snap: DataSnapshot) {
        val pushId = snap.key ?: return
        try {
            val from = snap.child("from").getValue(String::class.java)
            val fromName = snap.child("fromName").getValue(String::class.java)
            val at = snap.child("at").getValue(Long::class.java)
                ?: (snap.child("at").getValue(Double::class.java)?.toLong())
            if (isStaleOrSelf(myUid, from, at)) {
                deleteQuietly(myUid, pushId)
                return
            }
            onValidPoke(ctx, pushId, fromName)
            deleteQuietly(myUid, pushId)
        } catch (e: Exception) {
            Log.e(TAG, "poke handle failed", e)
            deleteQuietly(myUid, pushId)
        }
    }

    private fun isStaleOrSelf(myUid: String, from: String?, at: Long?): Boolean {
        if (from == null) return true
        if (from == myUid) return true
        if (at == null) return true
        return System.currentTimeMillis() - at > MAX_AGE_MS
    }

    private fun onValidPoke(ctx: Context, pushId: String, fromName: String?) {
        val name = fromName?.takeIf { it.isNotBlank() } ?: "Someone"
        _unread.value = _unread.value + 1
        _received.tryEmit(Unit)
        persist()
        if (isForeground()) return
        try {
            ensureChannel(ctx)
            val intent = Intent(ctx, MainActivity::class.java).apply {
                action = MainActivity.ACTION_OPEN_YOU
                putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_USER)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val pi = PendingIntent.getActivity(
                ctx,
                pushId.hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("$name poked you")
                .setContentText("Hello cuttie ")
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            val manager =
                ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(pushId.hashCode(), notif)
        } catch (e: Exception) {
            Log.e(TAG, "poke notify failed", e)
        }
    }

    private fun deleteQuietly(myUid: String, pushId: String) {
        try {
            FirebaseDatabase.getInstance().getReference("pokes")
                .child(myUid).child(pushId).removeValue()
                .addOnFailureListener { e -> Log.e(TAG, "poke delete failed", e) }
        } catch (e: Exception) {
            Log.e(TAG, "poke delete failed", e)
        }
    }

    private fun isForeground(): Boolean = try {
        ProcessLifecycleOwner.get().lifecycle.currentState
            .isAtLeast(Lifecycle.State.STARTED)
    } catch (_: Exception) {
        false
    }

    private fun persist() {
        try {
            appCtx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                ?.edit()?.putInt(KEY_UNREAD, _unread.value)?.apply()
        } catch (e: Exception) {
            Log.e(TAG, "poke persist failed", e)
        }
    }

    private fun loadPersisted() {
        try {
            val ctx = appCtx ?: return
            val saved = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_UNREAD, 0)
            if (saved > _unread.value) _unread.value = saved
        } catch (e: Exception) {
            Log.e(TAG, "poke load failed", e)
        }
    }

    @Synchronized
    private fun removeListenerLocked() {
        val ref = queryRef
        val l = listener
        queryRef = null
        listener = null
        if (ref != null && l != null) {
            try {
                ref.removeEventListener(l)
            } catch (e: Exception) {
                Log.e(TAG, "poke detach failed", e)
            }
        }
    }
}
