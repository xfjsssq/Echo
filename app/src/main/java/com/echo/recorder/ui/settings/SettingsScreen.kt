package com.echo.recorder.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.echo.recorder.settings.BufferDuration
import com.echo.recorder.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 设置页.
 *
 * - 缓冲时长: 实时读写 DataStore; 用户改了弹出 "需重启小E" + 二次确认 (是否保存当前音频), 确认后回调宿主重启服务.
 * - 密码保护开关 UI 已画, 功能占位.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onRestartService: (savePending: Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()

    // 真实当前值 (来自 DataStore), 首次由 first() 同步兜底.
    var selected by remember { mutableStateOf(BufferDuration.M3) }
    var passwordOn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        selected = BufferDuration.values().firstOrNull { it.seconds == repo.bufferSeconds.first() } ?: BufferDuration.M3
        passwordOn = repo.passwordEnabled.first()
    }

    var showRestart by remember { mutableStateOf(false) }
    var showSavePending by remember { mutableStateOf(false) }
    var pendingSeconds by remember { mutableStateOf<Int?>(null) }

    // 第一层确认: 修改缓冲时长需重启.
    if (showRestart) {
        AlertDialog(
            onDismissRequest = { showRestart = false },
            title = { Text("修改回音时长需要完全重启小E，是否继续？") },
            text = { Text("重启后新的缓冲时长才会生效") },
            confirmButton = {
                TextButton(onClick = {
                    showRestart = false
                    // 直接弹出第二层确认.
                    showSavePending = true
                }) { Text("重启") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRestart = false
                    pendingSeconds = null
                }) { Text("取消") }
            },
        )
    }

    // 第二层确认: 是否保存当前正在录制的音频.
    if (showSavePending) {
        val secs = pendingSeconds
        AlertDialog(
            onDismissRequest = { showSavePending = false; pendingSeconds = null },
            title = { Text("是否需要保存当前正在录制的音频?") },
            text = { Text("选择保存会先把当前缓冲写入列表, 再重启小E") },
            confirmButton = {
                TextButton(onClick = {
                    showSavePending = false
                    val s = secs
                    pendingSeconds = null
                    if (s != null) scope.launch { repo.setBufferSeconds(s) }
                    onRestartService(true)
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSavePending = false
                    val s = secs
                    pendingSeconds = null
                    if (s != null) scope.launch { repo.setBufferSeconds(s) }
                    onRestartService(false)
                }) { Text("不保存") }
            },
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("设置") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("即时回放缓冲时长", style = MaterialTheme.typography.titleMedium)
            Column(Modifier.selectableGroup()) {
                BufferDuration.values().forEach { dur ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected == dur,
                                onClick = {
                                    selected = dur
                                    // 仅当相对当前持久值真的有改动才触发重启流程.
                                    pendingSeconds = dur.seconds
                                    showRestart = true
                                },
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == dur, onClick = null)
                        Text(
                            text = dur.label + "  (" + dur.estimatedMb + ")",
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }

            Divider()

            Text("密码保护", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("开启密码保护")
                    Text(
                        text = "开启后, 冷启动与删除录音需输入密码",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = passwordOn,
                    onCheckedChange = {
                        passwordOn = it
                        scope.launch { repo.setPasswordEnabled(it) }
                    },
                )
            }
            Text(
                text = "(功能占位, 后续填充)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
