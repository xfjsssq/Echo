package com.echo.recorder.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.echo.recorder.R
import com.echo.recorder.settings.PasswordCrypto

/**
 * 密码验证对话框.
 *
 * @param storedHash 存储的密码哈希 (saltHex:hashHex)
 * @param recoveryHash 存储的恢复密钥哈希, 用于"忘记密码"重置
 * @param isPattern 是否为图案密码
 * @param onVerify 验证成功后回调
 * @param onDismiss 取消回调
 */
@Composable
fun PasswordPromptDialog(
    storedHash: String?,
    recoveryHash: String?,
    isPattern: Boolean,
    onVerify: () -> Unit,
    onDismiss: () -> Unit,
) {
    // null 表示未设置密码 -> 直接放行.
    if (storedHash == null) {
        onVerify()
        return
    }
    var error by remember { mutableStateOf<String?>(null) }
    var showRecovery by remember { mutableStateOf(false) }
    var recoveryInput by remember { mutableStateOf("") }

    if (showRecovery) {
        // 恢复密钥验证.
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.reset_password_with_recovery)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.recovery_key_label, ""))
                    OutlinedTextField(
                        value = recoveryInput,
                        onValueChange = { recoveryInput = it.filter { c -> c.isDigit() }.take(6); error = null },
                        label = { Text(stringResource(R.string.recovery_key_title)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (recoveryHash != null && PasswordCrypto.verify(recoveryInput, recoveryHash)) {
                        onVerify()
                    } else {
                        error = "recovery_key_wrong"
                    }
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            },
        )
        return
    }

    if (isPattern) {
        var pattern by remember { mutableStateOf<List<Int>>(emptyList()) }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.input_password)) },
            text = {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    PatternLockView(
                        onPatternComplete = { p ->
                            val stored = storedHash
                            if (stored != null && PasswordCrypto.verify(p.joinToString(","), stored)) {
                                onVerify()
                            } else {
                                error = "password_wrong"
                                pattern = emptyList()
                            }
                        },
                    )
                    error?.let {
                        Text(
                            stringResource(R.string.password_wrong),
                            color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            },
        )
    } else {
        var input by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.input_password)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it.filter { c -> c.isDigit() }.take(6); error = null },
                        label = { Text(stringResource(R.string.pin_input_hint)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    error?.let {
                        Text(
                            stringResource(R.string.password_wrong),
                            color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                        )
                    }
                    TextButton(onClick = { showRecovery = true }) {
                        Text(stringResource(R.string.forgot_password))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (PasswordCrypto.verify(input, storedHash)) onVerify()
                    else error = "password_wrong"
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
