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
import com.echo.recorder.R
import com.echo.recorder.ui.theme.ThemeMode

/** 缓冲时长选项 (秒). */
enum class BufferDuration(val seconds: Int) {
    S30(30),
    M1(60),
    M3(180),
    M5(300),
    M10(600);

    /** 本地化标签, 如 "30 秒" / "30s". */
    fun label(context: Context): String = when (this) {
        S30 -> "${seconds}${context.getString(R.string.buffer_seconds_unit)}"
        else -> "${seconds / 60}${context.getString(R.string.buffer_minutes_unit)}"
    }

    /** 本地化预估大小, 如 "约 0.3 MB" / "~0.3 MB". */
    fun estimatedMb(context: Context): String {
        val mb = seconds * 30.0 / 1024.0
        return "${context.getString(R.string.approx_prefix)}%.1f MB".format(mb)
    }
}

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "echo_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val BUFFER_SECONDS = intPreferencesKey("buffer_seconds")
        val PASSWORD_ENABLED = booleanPreferencesKey("password_enabled")
        val PASSWORD_TYPE = stringPreferencesKey("password_type") // "pin" / "mixed"
        val PASSWORD_HASH = stringPreferencesKey("password_hash") // saltHex:hashHex
        val RECOVERY_HASH = stringPreferencesKey("recovery_hash") // saltHex:hashHex
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language") // "zh" / "en"
        val PUBLIC_TREE_URI = stringPreferencesKey("public_tree_uri") // SAF 备份文件夹授权
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
            // 旧版本"跟随系统"已删除, 统一回退为明亮.
            else -> ThemeMode.LIGHT
        }
    }

    /** 语言代码 ("zh"/"en"), null 表示跟随系统. */
    val language: Flow<String?> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.LANGUAGE]
    }

    /** 公共目录备份文件夹的 SAF tree URI, null 表示尚未授权. */
    val publicTreeUri: Flow<String?> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.PUBLIC_TREE_URI]
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

    suspend fun setPublicTreeUri(uri: String?) {
        context.settingsDataStore.edit {
            if (uri == null) it.remove(Keys.PUBLIC_TREE_URI) else it[Keys.PUBLIC_TREE_URI] = uri
        }
    }

    suspend fun setOnboardingDone(done: Boolean) {
        context.settingsDataStore.edit { it[Keys.ONBOARDING_DONE] = done }
    }
}
