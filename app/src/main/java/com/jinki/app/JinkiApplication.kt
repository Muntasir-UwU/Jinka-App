package com.jinki.app

import android.app.Application
import androidx.profileinstaller.ProfileInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren

class JinkiApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        ProfileInstaller.writeProfile(this)
    }

    val container: AppContainer by lazy { AppContainer(this) }

    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onTerminate() {
        scope.coroutineContext.cancelChildren()
        super.onTerminate()
    }
}
