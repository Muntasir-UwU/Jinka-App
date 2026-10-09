package com.jinki.app

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.profileinstaller.ProfileInstaller
import com.jinki.app.data.PresenceReporter
import com.jinki.app.di.FirebaseModule
import com.jinki.app.poke.PokeNotifier
import com.jinki.app.poke.PokeWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class JinkiApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        ProfileInstaller.writeProfile(this)
        PokeNotifier.ensureChannel(this)
        PokeWorker.schedule(this)
        // Silent sign-in on app start: anonymous account if there is none
        // yet. Retries quietly in the background; database writes elsewhere
        // await it, so nothing ever writes unauthenticated.
        scope.launch { container.auth.ensureSignedIn() }
        // Poke listener for the foreground process lifetime. The playback
        // service attaches with its own owner; PokeNotifier keeps a single
        // ChildEventListener while either owner is present.
        scope.launch {
            container.auth.currentUser
                .map { it?.uid }
                .distinctUntilChanged()
                .collect { uid ->
                    if (uid == null) PokeNotifier.detach(OWNER_APP)
                    else PokeNotifier.attach(this@JinkiApplication, uid, OWNER_APP)
                }
        }
        // One foreground/background observer for the whole process lifetime.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            PresenceReporter(container.auth, FirebaseModule.database)
        )
    }

    val container: AppContainer by lazy { AppContainer(this) }

    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onTerminate() {
        scope.coroutineContext.cancelChildren()
        super.onTerminate()
    }

    companion object {
        const val OWNER_APP = "app"
    }
}
