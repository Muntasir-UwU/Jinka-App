package com.jinki.app

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.lifecycleScope
import com.jinki.app.ui.JinkaApp
import com.jinki.app.ui.theme.JinkaTheme
import com.jinki.app.util.PermissionUtil
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val _openYou = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openYou: SharedFlow<Unit> = _openYou.asSharedFlow()

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {

        }

    /**
     * One-time POST_NOTIFICATIONS prompt, never at cold start. Called when
     * the user first opens the You screen or sends their first poke.
     * If denied, the pref stays set: never ask again, never crash.
     */
    fun maybeRequestPokePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (PermissionUtil.hasNotificationPermission(this)) return
        val seen = getPreferences(MODE_PRIVATE)
        if (seen.getBoolean(KEY_NOTIFICATION_ASKED, false)) return
        seen.edit().putBoolean(KEY_NOTIFICATION_ASKED, true).apply()
        try {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } catch (_: Exception) {
            // Denied or unavailable: stay silent, never ask again.
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {




        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        if (isOpenYouIntent(intent)) {
            lifecycleScope.launch { _openYou.tryEmit(Unit) }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }




        val container = (application as JinkiApplication).container
        setContent {
            JinkaTheme {

                val view = LocalView.current
                CompositionLocalProvider(LocalView provides view) {
                    JinkaApp(container, openYou)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isOpenYouIntent(intent)) {
            lifecycleScope.launch { _openYou.emit(Unit) }
        }
    }

    private fun isOpenYouIntent(intent: Intent?): Boolean {
        if (intent == null) return false
        if (intent.action == ACTION_OPEN_YOU) return true
        return intent.getStringExtra(EXTRA_OPEN) == OPEN_USER
    }

    companion object {
        private const val KEY_NOTIFICATION_ASKED = "notification_permission_asked"
        const val EXTRA_OPEN = "open"
        const val OPEN_USER = "user"
        const val ACTION_OPEN_YOU = "com.jinki.app.OPEN_YOU"
    }
}
