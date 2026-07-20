package com.echo.recorder.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 缓冲时长选项 (秒). */
enum class BufferDuration(val seconds: Int, val label: String, val estimatedMb: String) {
    S30(30, "30 秒", "约 0.3 MB"),
    M1(60, "1 分钟", "约 0.6 MB"),
    M3(180, "3 分钟", "约 2.1 MB"),
    M5(300, "5 分钟", "约 3.5 MB"),
    M10(600, "10 分钟", "约 7.0 MB"),
}

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "echo_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val BUFFER_SECONDS = intPreferencesKey("buffer_seconds")
        val PASSWORD_ENABLED = booleanPreferencesKey("password_enabled")
        val PASSWORD_HASH = stringPreferencesKey("password_hash")
    }

    val bufferSeconds: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.BUFFER_SECONDS] ?: BufferDuration.M3.seconds
    }

    val passwordEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.PASSWORD_ENABLED] ?: false
    }

    suspend fun setBufferSeconds(seconds: Int) {
        context.settingsDataStore.edit { it[Keys.BUFFER_SECONDS] = seconds }
    }

    suspend fun setPasswordEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.PASSWORD_ENABLED] = enabled }
    }
}
