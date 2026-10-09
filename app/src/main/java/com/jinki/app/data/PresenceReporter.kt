package com.jinki.app.data

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue

/**
 * Marks this device online/offline from the app's foreground state.
 *
 * Plain language: Android tells us when the app comes to the screen
 * (ON_START) and when it leaves it (ON_STOP). On start we stamp
 * presence/{uid} as online; on stop we stamp it offline with the current
 * time. The onDisconnect handler below is the safety net: if the app is
 * killed, crashes, or loses network, the server applies the offline stamp
 * by itself. Registered once in JinkiApplication — never in an Activity,
 * whose onDestroy is not reliable for this.
 */
class PresenceReporter(
    private val auth: AuthRepository,
    private val database: FirebaseDatabase
) : DefaultLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) {
        setOnline(true)
    }

    override fun onStop(owner: LifecycleOwner) {
        setOnline(false)
    }

    private fun setOnline(online: Boolean) {
        val uid = auth.currentUid() ?: return
        val ref = database.getReference("presence").child(uid)
        if (online) {
            ref.updateChildren(mapOf("online" to true))
        } else {
            ref.updateChildren(offlineUpdate())
        }
        // Crash, force-close, or dead network: the server writes this for us.
        ref.onDisconnect().updateChildren(offlineUpdate())
    }

    companion object {
        fun offlineUpdate(): Map<String, Any> = mapOf(
            "online" to false,
            "lastSeen" to ServerValue.TIMESTAMP
        )
    }
}
