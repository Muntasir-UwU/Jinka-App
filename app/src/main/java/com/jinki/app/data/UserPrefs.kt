package com.jinki.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
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

    
    // Raw stored value, blank when never set. Callers decide what blank
    // means — this flow never invents a name (no fallback).
    val userName: Flow<String> =
        context.userPrefsStore.data.map { prefs -> prefs[USER_NAME].orEmpty() }

    suspend fun setUserName(name: String) {
        context.userPrefsStore.edit { prefs -> prefs[USER_NAME] = name }
    }

    /** Absolute path of the profile photo copy in app-private storage, or blank. */
    val avatarPath: Flow<String> =
        context.userPrefsStore.data.map { prefs -> prefs[AVATAR_PATH].orEmpty() }

    suspend fun setAvatarPath(path: String) {
        context.userPrefsStore.edit { prefs -> prefs[AVATAR_PATH] = path }
    }

    /** True once a submitted name has been acknowledged by the server. */
    val nameConfirmed: Flow<Boolean> =
        context.userPrefsStore.data.map { prefs -> prefs[NAME_CONFIRMED] ?: false }

    suspend fun setNameConfirmed(confirmed: Boolean) {
        context.userPrefsStore.edit { prefs -> prefs[NAME_CONFIRMED] = confirmed }
    }

    /**
     * True after the overlay submits a name the server hasn't confirmed
     * yet. Tells a fresh submit apart from an old install that simply
     * never went through the overlay.
     */
    val namePending: Flow<Boolean> =
        context.userPrefsStore.data.map { prefs -> prefs[NAME_PENDING] ?: false }

    suspend fun setNamePending(pending: Boolean) {
        context.userPrefsStore.edit { prefs -> prefs[NAME_PENDING] = pending }
    }

    
    val wifiOnly: Flow<Boolean> =
        context.userPrefsStore.data.map { prefs -> prefs[WIFI_ONLY] ?: false }

    suspend fun setWifiOnly(enabled: Boolean) {
        context.userPrefsStore.edit { prefs -> prefs[WIFI_ONLY] = enabled }
    }

    /** Manual lyric sync nudge applied on top of the auto lookahead. */
    val lyricsOffsetMs: Flow<Long> =
        context.userPrefsStore.data.map { prefs -> prefs[LYRICS_OFFSET_MS] ?: 0L }

    suspend fun setLyricsOffsetMs(valueMs: Long) {
        context.userPrefsStore.edit { prefs ->
            prefs[LYRICS_OFFSET_MS] = valueMs.coerceIn(LYRICS_OFFSET_MIN_MS, LYRICS_OFFSET_MAX_MS)
        }
    }

    companion object {
        val REPEAT_MODE = stringPreferencesKey("repeat_mode")
        val USER_NAME = stringPreferencesKey("user_name")
        val AVATAR_PATH = stringPreferencesKey("avatar_path")
        val NAME_CONFIRMED = booleanPreferencesKey("name_confirmed")
        val NAME_PENDING = booleanPreferencesKey("name_pending")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val LYRICS_OFFSET_MS = longPreferencesKey("lyrics_offset_ms")
        const val LYRICS_OFFSET_MIN_MS = -2000L
        const val LYRICS_OFFSET_MAX_MS = 2000L
    }
}
