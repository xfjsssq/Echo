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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.echo.recorder.R
import com.echo.recorder.ServiceLocator
import com.echo.recorder.common.PublicDirManager
import com.echo.recorder.domain.model.Recording
import com.echo.recorder.settings.PasswordCrypto
import com.echo.recorder.settings.SettingsRepository
import com.echo.recorder.ui.fmtTime
import kotlinx.coroutines.launch

/**
 * 公共目录文件管理界面.
 *
 * 列出所有虚引用文件, 仅可删除 (不可播放). 删除需双重验证 (若已开密码): 应用密码 + 恢复密钥;
 * 未开密码时仅一次确认.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicDirManagementScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { ServiceLocator.repository(context) }
    val vfs = remember { ServiceLocator.virtualRefStore(context) }
    val settings = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()

    val repoImpl = remember { repo as? com.echo.recorder.data.RecordingRepositoryImpl }
    val virtualRefs by produceState(initialValue = emptyList<Recording>()) {
        repoImpl?.virtualRefsFlow()?.collect { value = it }
    }
    val passwordEnabled by produceState(initialValue = false) {
        value = settings.passwordEnabled.first()
    }

    var pendingDelete by remember { mutableStateOf<Recording?>(null) }
    // 验证步骤: 0=确认删除 1=输密码 2=输恢复密钥
    var verifyStep by remember { mutableStateOf(0) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val storedHash by produceState<String?>(initialValue = null) {
        value = settings.passwordHash.first()
    }
    val recoveryHash by produceState<String?>(initialValue = null) {
        value = settings.recoveryHash.first()
    }
    // 本地副本用于智能转换.
    val hash = storedHash
    val recHash = recoveryHash

    // 对话框.
    pendingDelete?.let { target ->
        when (verifyStep) {
            0 -> AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text(stringResource(R.string.delete)) },
                text = { Text(target.displayName) },
                confirmButton = {
                    TextButton(onClick = {
                        if (passwordEnabled) {
                            verifyStep = 1; input = ""; error = null
                        } else {
                            // 未开密码: 直接删除.
                            scope.launch {
                                repoImpl?.removeVirtualRef(target.id)
                                PublicDirManager.deletePublic(context, target.id)
                            }
                            pendingDelete = null
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
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it.filter { c -> c.isDigit() }.take(6); error = null },
                            label = { Text(stringResource(R.string.input_password)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
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
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it.filter { c -> c.isDigit() }.take(6); error = null },
                            label = { Text(stringResource(R.string.recovery_key_title)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (recHash != null && PasswordCrypto.verify(input, recHash)) {
                            scope.launch {
                                repoImpl?.removeVirtualRef(target.id)
                                PublicDirManager.deletePublic(context, target.id)
                            }
                            pendingDelete = null; verifyStep = 0
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
            Text(
                stringResource(R.string.public_dir_management_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            if (virtualRefs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_recordings))
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(virtualRefs, key = { it.id }) { rec ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(rec.displayName, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = fmtTime(rec.createdAt),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { pendingDelete = rec; verifyStep = 0 }) {
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
