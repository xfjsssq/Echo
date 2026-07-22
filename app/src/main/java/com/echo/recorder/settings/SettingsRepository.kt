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

/** 主题模式. */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "echo_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val BUFFER_SECONDS = intPreferencesKey("buffer_seconds")
        val PASSWORD_ENABLED = booleanPreferencesKey("password_enabled")
        val PASSWORD_TYPE = stringPreferencesKey("password_type") // "pin" / "pattern"
        val PASSWORD_HASH = stringPreferencesKey("password_hash") // saltHex:hashHex
        val RECOVERY_HASH = stringPreferencesKey("recovery_hash") // saltHex:hashHex
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language") // "zh" / "en"
        val PUBLIC_DIR_ENABLED = booleanPreferencesKey("public_dir_enabled")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }

    val bufferSeconds: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.BUFFER_SECONDS] ?: BufferDuration.M3.seconds
    }

    val passwordEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.PASSWORD_ENABLED] ?: false
    }

    val passwordType: Flow<String?> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.PASSWORD_TYPE]
    }

    /** 存储的密码哈希 (saltHex:hashHex), 未设置时为 null. */
    val passwordHash: Flow<String?> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.PASSWORD_HASH]
    }

    /** 存储的恢复密钥哈希 (saltHex:hashHex), 未设置时为 null. */
    val recoveryHash: Flow<String?> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.RECOVERY_HASH]
    }

    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { prefs ->
        when (prefs[Keys.THEME_MODE]) {
            "LIGHT" -> ThemeMode.LIGHT
            "DARK" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    /** 语言代码 ("zh"/"en"), null 表示跟随系统. */
    val language: Flow<String?> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.LANGUAGE]
    }

    val publicDirEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.PUBLIC_DIR_ENABLED] ?: false
    }

    val onboardingDone: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.ONBOARDING_DONE] ?: false
    }

    suspend fun setBufferSeconds(seconds: Int) {
        context.settingsDataStore.edit { it[Keys.BUFFER_SECONDS] = seconds }
    }

    suspend fun setPasswordEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.PASSWORD_ENABLED] = enabled }
    }

    suspend fun setPasswordType(type: String?) {
        context.settingsDataStore.edit {
            if (type == null) it.remove(Keys.PASSWORD_TYPE) else it[Keys.PASSWORD_TYPE] = type
        }
    }

    suspend fun setPassword(hash: String?) {
        context.settingsDataStore.edit {
            if (hash == null) {
                it.remove(Keys.PASSWORD_HASH)
                it.remove(Keys.RECOVERY_HASH)
            } else {
                it[Keys.PASSWORD_HASH] = hash
            }
        }
    }

    suspend fun setRecoveryHash(hash: String?) {
        context.settingsDataStore.edit {
            if (hash == null) it.remove(Keys.RECOVERY_HASH) else it[Keys.RECOVERY_HASH] = hash
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setLanguage(code: String?) {
        context.settingsDataStore.edit {
            if (code == null) it.remove(Keys.LANGUAGE) else it[Keys.LANGUAGE] = code
        }
    }

    suspend fun setPublicDirEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.PUBLIC_DIR_ENABLED] = enabled }
    }

    suspend fun setOnboardingDone(done: Boolean) {
        context.settingsDataStore.edit { it[Keys.ONBOARDING_DONE] = done }
    }
}
