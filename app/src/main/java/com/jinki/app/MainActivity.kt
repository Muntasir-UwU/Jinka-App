package com.jinki.app

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
import com.jinki.app.ui.JinkaApp
import com.jinki.app.ui.theme.JinkaTheme
import com.jinki.app.util.PermissionUtil

class MainActivity : ComponentActivity() {

    
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            
        }

    
    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (PermissionUtil.hasNotificationPermission(this)) return
        val seen = getPreferences(MODE_PRIVATE)
        if (seen.getBoolean(KEY_NOTIFICATION_ASKED, false)) return
        seen.edit().putBoolean(KEY_NOTIFICATION_ASKED, true).apply()
        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        
        
        
        
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        maybeRequestNotificationPermission()

        
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        
        
        
        

        val container = (application as JinkiApplication).container
        setContent {
            JinkaTheme {
                
                val view = LocalView.current
                CompositionLocalProvider(LocalView provides view) {
                    JinkaApp(container)
                }
            }
        }
    }

    companion object {
        private const val KEY_NOTIFICATION_ASKED = "notification_permission_asked"
    }
}
