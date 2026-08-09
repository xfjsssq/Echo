package com.echo.recorder.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.echo.recorder.R
import com.echo.recorder.common.PublicDirManager
import com.echo.recorder.settings.PasswordCrypto
import com.echo.recorder.settings.SettingsRepository
import com.echo.recorder.ui.common.rememberPublicDirGrant
import com.echo.recorder.ui.fmtTime
import com.echo.recorder.ui.lock.PasswordInputField
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 公共目录 (备份文件夹) 文件管理界面.
 *
 * 直接扫描 SAF 授权的备份文件夹, 列出所有 .m4a 文件.
 * 此界面中的文件仅可查看和删除 (删除备份文件夹中的原始文件, 永久删除不可恢复),
 * 不可播放、不可导入 (导入入口在列表页).
 * 删除需双重验证 (若已开密码): 应用密码 + 恢复密钥; 未开密码时仅一次确认.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicDirManagementScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()

    var files by remember { mutableStateOf<List<PublicDirManager.PublicFileInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    suspend fun load() {
        loading = true
        files = PublicDirManager.scanPublic(context)
        loading = false
    }
    LaunchedEffect(Unit) { load() }

    val grant = rememberPublicDirGrant(onGranted = { scope.launch { load() } })

    val passwordEnabled by produceState(initialValue = false) {
        value = settings.passwordEnabled.first()
    }
    val storedHash by produceState<String?>(initialValue = null) {
        value = settings.passwordHash.first()
    }
    val recoveryHash by produceState<String?>(initialValue = null) {
        value = settings.recoveryHash.first()
    }

    var pendingDelete by remember { mutableStateOf<PublicDirManager.PublicFileInfo?>(null) }
    // 验证步骤: 0=确认删除 1=输密码 2=输恢复密钥
    var verifyStep by remember { mutableStateOf(0) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun deleteNow(target: PublicDirManager.PublicFileInfo) {
        scope.launch {
            PublicDirManager.deletePublic(context, target.fileName)
            pendingDelete = null
            verifyStep = 0
            input = ""
            error = null
            load()
        }
    }

    // 删除确认 / 双重验证对话框.
    pendingDelete?.let { target ->
        when (verifyStep) {
            0 -> AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text(stringResource(R.string.delete)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(target.displayName)
                        Text(
                            stringResource(R.string.public_dir_delete_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (passwordEnabled) {
                            verifyStep = 1; input = ""; error = null
                        } else {
                            deleteNow(target)
                        }
                    }) { Text(stringResource(R.string.confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) }
                },
            )
            1 -> AlertDialog(
                onDismissRequest = { pendingDelete = null; verifyStep = 0 },
                title = { Text(stringResource(R.string.double_verify_needed)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PasswordInputField(
                            value = input,
                            onValueChange = { input = it.filter { c -> c.isDigit() }.take(6); error = null },
                            label = stringResource(R.string.input_password),
                            keyboardType = KeyboardType.Number,
                        )
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val hash = storedHash
                        if (hash != null && PasswordCrypto.verify(input, hash)) {
                            verifyStep = 2; input = ""; error = null
                        } else {
                            error = "password_wrong"
                        }
                    }) { Text(stringResource(R.string.confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null; verifyStep = 0 }) { Text(stringResource(R.string.cancel)) }
                },
            )
            2 -> AlertDialog(
                onDismissRequest = { pendingDelete = null; verifyStep = 0 },
                title = { Text(stringResource(R.string.double_verify_step2)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PasswordInputField(
                            value = input,
                            onValueChange = { input = it.filter { c -> c.isDigit() }.take(6); error = null },
                            label = stringResource(R.string.recovery_key_title),
                            keyboardType = KeyboardType.Number,
                        )
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val hash = recoveryHash
                        if (hash != null && PasswordCrypto.verify(input, hash)) {
                            deleteNow(target)
                        } else {
                            error = "recovery_key_wrong"
                        }
                    }) { Text(stringResource(R.string.confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null; verifyStep = 0 }) { Text(stringResource(R.string.cancel)) }
                },
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.public_dir_management_title)) },
                navigationIcon = {
                    IconButton(onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "back") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!grant.granted) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.public_dir_choose_folder_note),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = grant.request,
                            modifier = Modifier.padding(top = 20.dp),
                        ) { Text(stringResource(R.string.public_dir_choose_folder)) }
                    }
                }
            } else {
                Text(
                    stringResource(R.string.public_dir_management_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
                if (loading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("...")
                    }
                } else if (files.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.no_recordings))
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(files, key = { it.fileName }) { info ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(info.displayName, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        text = fmtTime(info.lastModified),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { pendingDelete = info; verifyStep = 0 }) {
                                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                                }
                            }
                            Divider()
                        }
                    }
                }
            }
        }
    }
}
