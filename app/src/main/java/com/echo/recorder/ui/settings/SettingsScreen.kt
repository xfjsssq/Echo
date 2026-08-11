package com.echo.recorder.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.echo.recorder.ui.theme.ThemeManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.echo.recorder.R
import com.echo.recorder.i18n.LocaleManager
import com.echo.recorder.settings.BufferDuration
import com.echo.recorder.settings.SettingsRepository
import com.echo.recorder.ui.theme.ThemeMode
import com.echo.recorder.ui.lock.PasswordPromptDialog
import com.echo.recorder.ui.lock.ChangePasswordDialog
import com.echo.recorder.ui.lock.ResetRecoveryKeyDialog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 设置页 (分组 Preference 样式).
 *
 * 分组: 录音设置 / 安全设置 / 存储设置 / 外观与语言 / 帮助与关于.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onRestartService: (savePending: Boolean) -> Unit = {},
    onOpenPasswordSetup: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onOpenPublicDir: () -> Unit = {},
    onChangePassword: () -> Unit = {},
    onOpenOnboarding: () -> Unit = {},
    themeMode: ThemeMode = ThemeMode.LIGHT,
    onThemeChange: (ThemeMode) -> Unit = {},
) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()

    var selected by remember { mutableStateOf(BufferDuration.M3) }
    var passwordOn by remember { mutableStateOf(false) }
    var language by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        selected = BufferDuration.values().firstOrNull { it.seconds == repo.bufferSeconds.first() } ?: BufferDuration.M3
        passwordOn = repo.passwordEnabled.first()
        language = repo.language.first()
    }

    var showRestart by remember { mutableStateOf(false) }
    var showSavePending by remember { mutableStateOf(false) }
    var pendingSeconds by remember { mutableStateOf<Int?>(null) }
    var pendingSelected by remember { mutableStateOf<BufferDuration?>(null) }
    val displaySelected = pendingSelected ?: selected

    // 主题/语言选择弹窗.
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    // 修改密码 / 查看恢复密钥 / 重置恢复密钥 / 关闭密码验证.
    var showChangePassword by remember { mutableStateOf(false) }
    var showResetRecoveryKey by remember { mutableStateOf(false) }
    var verifyDisablePassword by remember { mutableStateOf(false) }
    // 隐私政策.
    var showPrivacyPolicy by remember { mutableStateOf(false) }

    val storedHash by produceState<String?>(initialValue = null) {
        value = repo.passwordHash.first()
    }
    val recoveryHash by produceState<String?>(initialValue = null) {
        value = repo.recoveryHash.first()
    }

    // 第一确认: 修改缓冲时长需重启.
    if (showRestart) {
        AlertDialog(
            onDismissRequest = { showRestart = false; pendingSelected = null },
            title = { Text(stringResource(R.string.restart_title)) },
            text = { Text(stringResource(R.string.restart_text)) },
            confirmButton = {
                TextButton(onClick = { showRestart = false; showSavePending = true }) {
                    Text(stringResource(R.string.restart_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestart = false; pendingSeconds = null; pendingSelected = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // 第二层确认: 是否保存当前正在录制的音频.
    if (showSavePending) {
        val secs = pendingSeconds
        AlertDialog(
            onDismissRequest = { showSavePending = false; pendingSeconds = null; pendingSelected = null },
            title = { Text(stringResource(R.string.save_pending_title)) },
            text = { Text(stringResource(R.string.save_pending_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showSavePending = false
                    if (pendingSelected != null) selected = pendingSelected!!
                    pendingSelected = null
                    val s = secs
                    pendingSeconds = null
                    if (s != null) scope.launch { repo.setBufferSeconds(s) }
                    onRestartService(true)
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSavePending = false
                    val s = secs
                    pendingSeconds = null
                    if (s != null) scope.launch { repo.setBufferSeconds(s) }
                    onRestartService(false)
                }) { Text(stringResource(R.string.dont_save)) }
            },
        )
    }

    // 主题选择.
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(stringResource(R.string.theme)) },
            text = {
                Column(Modifier.selectableGroup()) {
                    ThemeMode.values().forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = themeMode == mode,
                                    onClick = {
                                        showThemeDialog = false
                                        // 主题切换: 仅更新 Compose 状态 + 持久化, 不重建 Activity.
                                        onThemeChange(mode)
                                    },
                                    role = Role.RadioButton,
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = themeMode == mode, onClick = null)
                            Text(
                                text = stringResource(
                                    when (mode) {
                                        ThemeMode.LIGHT -> R.string.theme_light
                                        ThemeMode.DARK -> R.string.theme_dark
                                    }
                                ),
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text(stringResource(R.string.close)) }
            },
        )
    }

    // 语言选择.
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.language)) },
            text = {
                Column(Modifier.selectableGroup()) {
                    listOf("zh" to R.string.language_zh, "en" to R.string.language_en).forEach { (code, labelRes) ->
                        val current = LocaleManager.current(language)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = current == code,
                                    onClick = {
                                        showLanguageDialog = false
                                        // 先等待 DataStore 写入完成, 再重建 Activity, 确保新 Activity 读到新语言.
                                        scope.launch {
                                            repo.setLanguage(code)
                                            (context as? android.app.Activity)?.let { LocaleManager.recreate(it) }
                                        }
                                    },
                                    role = Role.RadioButton,
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = current == code, onClick = null)
                            Text(stringResource(labelRes), modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLanguageDialog = false }) { Text(stringResource(R.string.close)) }
            },
        )
    }

    // 修改密码.
    if (showChangePassword) {
        ChangePasswordDialog(
            onDismiss = { showChangePassword = false },
            onVerified = {
                showChangePassword = false
                onChangePassword()
            },
        )
    }

    // 重置恢复密钥.
    if (showResetRecoveryKey) {
        ResetRecoveryKeyDialog(
            onDismiss = { showResetRecoveryKey = false },
        )
    }

    // 关闭密码验证: 需验证密码, 但不删除哈希以便重新开启时无需重设密码.
    if (verifyDisablePassword) {
        PasswordPromptDialog(
            storedHash = storedHash,
            recoveryHash = recoveryHash,
            onVerify = {
                verifyDisablePassword = false
                passwordOn = false
                scope.launch { repo.setPasswordEnabled(false) }
            },
            onDismiss = { verifyDisablePassword = false },
        )
    }

    // 隐私政策.
    if (showPrivacyPolicy) {
        AlertDialog(
            onDismissRequest = { showPrivacyPolicy = false },
            title = { Text(stringResource(R.string.privacy_policy)) },
            text = {
                androidx.compose.foundation.rememberScrollState().let { scrollState ->
                    Text(
                        text = stringResource(R.string.privacy_text),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.verticalScroll(scrollState),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyPolicy = false }) { Text(stringResource(R.string.close)) }
            },
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // ---- 录音设置 ----
            GroupTitle(stringResource(R.string.group_recording))
            SettingsCard {
                BufferDuration.values().forEach { dur ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = displaySelected == dur,
                                onClick = {
                                    pendingSelected = dur
                                    pendingSeconds = dur.seconds
                                    showRestart = true
                                },
                            )
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = displaySelected == dur, onClick = null)
                        Text(
                            stringResource(
                                R.string.buffer_duration_label,
                                dur.label(context),
                                dur.estimatedMb(context),
                            ),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                Preference(
                    title = stringResource(R.string.privacy_mode),
                    enabled = false,
                ) {}
            }

            // ---- 安全设置 ----
            GroupTitle(stringResource(R.string.group_security))
            SettingsCard {
                SwitchPreference(
                    title = stringResource(R.string.password_protection),
                    subtitle = stringResource(R.string.settings_password_subtitle),
                    checked = passwordOn,
                    onToggle = { on ->
                        if (on) {
                            if (storedHash != null) {
                                passwordOn = true
                                scope.launch { repo.setPasswordEnabled(true) }
                            } else {
                                onOpenPasswordSetup()
                            }
                        } else {
                            verifyDisablePassword = true
                        }
                    },
                )
                Divider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                Preference(
                    title = stringResource(R.string.change_password),
                    subtitle = stringResource(R.string.change_password_by_password),
                ) { showChangePassword = true }
                Divider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                Preference(
                    title = stringResource(R.string.reset_recovery_key_title),
                    subtitle = stringResource(R.string.reset_recovery_key_subtitle),
                ) { showResetRecoveryKey = true }
            }

            // ---- 存储设置 ----
            GroupTitle(stringResource(R.string.group_storage))
            SettingsCard {
                // 公共目录备份永远默认开启, 不再提供开关, 仅保留"公共目录文件"入口.
                Preference(
                    title = stringResource(R.string.public_dir_files),
                    subtitle = stringResource(R.string.public_dir_files_subtitle),
                ) { onOpenPublicDir() }
            }

            // ---- 外观与语言 ----
            GroupTitle(stringResource(R.string.group_appearance))
            SettingsCard {
                Preference(
                    title = stringResource(R.string.theme),
                    subtitle = stringResource(
                        when (themeMode) {
                            ThemeMode.LIGHT -> R.string.theme_light
                            ThemeMode.DARK -> R.string.theme_dark
                        }
                    ),
                ) { showThemeDialog = true }
                Divider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                Preference(
                    title = stringResource(R.string.language),
                    subtitle = stringResource(if (LocaleManager.current(language) == "en") R.string.language_en else R.string.language_zh),
                ) { showLanguageDialog = true }
            }

            // ---- 帮助与关于 ----
            GroupTitle(stringResource(R.string.group_help))
            SettingsCard {
                Preference(title = stringResource(R.string.how_to_use)) { onOpenOnboarding() }
                Divider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                Preference(title = stringResource(R.string.privacy_policy)) { showPrivacyPolicy = true }
                Divider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                Preference(title = stringResource(R.string.about)) { onOpenAbout() }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 10.dp),
    )
}

/** 圆角卡片容器, 承载一组设置项. */
@Composable
private fun SettingsCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp),
            content = content,
        )
    }
}

@Composable
private fun Preference(
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
            }
        }
        if (enabled) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SwitchPreference(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}
