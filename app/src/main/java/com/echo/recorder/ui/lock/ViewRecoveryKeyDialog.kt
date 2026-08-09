package com.echo.recorder.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.echo.recorder.R
import com.echo.recorder.auth.PasswordVerifier
import com.echo.recorder.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 查看恢复密钥对话框: 验证密码后展示恢复密钥.
 * 密码类型 (pin/mixed) 由本组件从 DataStore 自行读取.
 */
@Composable
fun ViewRecoveryKeyDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { SettingsRepository(context) }
    val passwordType by produceState<String?>(initialValue = null) {
        value = repo.passwordType.first()
    }

    // 0=验证密码 1=展示密钥
    var step by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var recoveryKey by remember { mutableStateOf<String?>(null) }

    // 验证当前密码, 通过后展示恢复密钥提示.
    val submit: () -> Unit = {
        scope.launch {
            val hash = repo.passwordHash.first()
            if (PasswordVerifier.verifyPassword(input, hash) == PasswordVerifier.Result.Success) {
                // 恢复密钥无法从哈希反推, 这里仅展示提示.
                // 实际恢复密钥只在设置时展示一次, 这里展示存储的哈希提示用户.
                recoveryKey = context.getString(R.string.recovery_key_view_only_once)
                step = 1
            } else {
                error = true
            }
        }
    }

    when (step) {
        0 -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.verify_current_password)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (passwordType == "mixed") {
                        MixedPasswordInput(
                            value = input,
                            onValueChange = { input = it; error = false },
                            label = stringResource(R.string.mixed_input_hint),
                        )
                        Spacer(Modifier.padding(top = 4.dp))
                        Button(onClick = submit) { Text(stringResource(R.string.confirm)) }
                    } else {
                        PasswordInputField(
                            value = input,
                            onValueChange = { input = it.filter { c -> c.isDigit() }.take(6); error = false },
                            label = stringResource(R.string.pin_input_hint),
                            keyboardType = KeyboardType.Number,
                        )
                        Spacer(Modifier.padding(top = 4.dp))
                        Button(onClick = submit) { Text(stringResource(R.string.confirm)) }
                    }
                    if (error) {
                        Text(
                            stringResource(R.string.password_wrong),
                            color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            },
        )
        1 -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.recovery_key_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.recovery_key_view_note),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        recoveryKey ?: "",
                        style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.confirm)) }
            },
        )
    }
}
