package com.jinki.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jinki.app.player.RepeatMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userPrefsStore by preferencesDataStore("user_prefs")


class UserPrefs(private val context: Context) {

    
    val repeatMode: Flow<RepeatMode> =
        context.userPrefsStore.data.map { prefs ->
            RepeatMode.parse(prefs[REPEAT_MODE])
        }

    suspend fun setRepeatMode(mode: RepeatMode) {
        context.userPrefsStore.edit { prefs -> prefs[REPEAT_MODE] = mode.name }
    }

    
    val userName: Flow<String> =
        context.userPrefsStore.data.map { prefs ->
            prefs[USER_NAME]?.takeIf { it.isNotBlank() } ?: DEFAULT_USER_NAME
        }

    suspend fun setUserName(name: String) {
        context.userPrefsStore.edit { prefs -> prefs[USER_NAME] = name }
    }

    
    val wifiOnly: Flow<Boolean> =
        context.userPrefsStore.data.map { prefs -> prefs[WIFI_ONLY] ?: false }

    suspend fun setWifiOnly(enabled: Boolean) {
        context.userPrefsStore.edit { prefs -> prefs[WIFI_ONLY] = enabled }
    }

    companion object {
        val REPEAT_MODE = stringPreferencesKey("repeat_mode")
        val USER_NAME = stringPreferencesKey("user_name")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        const val DEFAULT_USER_NAME = "TANISHA"
    }
}
