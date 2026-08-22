package com.echo.recorder.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.echo.recorder.auth.LockoutManager
import com.echo.recorder.settings.PasswordCrypto
import com.echo.recorder.settings.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 修改密码对话框: 选择通过密码或恢复密钥验证, 验证通过后进入 [PasswordSetupScreen] 设置新密码.
 * 修改密码不会变更恢复密钥.
 * 密码类型 (pin/mixed) 由本组件从 DataStore 自行读取.
 */
@Composable
fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onVerified: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { SettingsRepository(context) }
    val passwordType by produceState<String?>(initialValue = null) {
        value = repo.passwordType.first()
    }

    // 0=选方式 1=验证密码 2=验证密钥
    var step by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var lockSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { lockSeconds = LockoutManager.remainingSeconds().toInt() }
    LaunchedEffect(lockSeconds) {
        if (lockSeconds > 0) {
            while (lockSeconds > 0) { delay(1000); lockSeconds-- }
        }
    }

    if (lockSeconds > 0) {
        LockoutDialog(lockSeconds = lockSeconds, onDismiss = onDismiss)
        return
    }

    // 验证当前密码 (根据实际类型校验输入).
    val submitPassword: () -> Unit = {
        scope.launch {
            val stored = repo.passwordHash.first()
            if (stored != null && PasswordCrypto.verify(input, stored)) {
                LockoutManager.recordSuccess()
                onVerified()
            } else {
                error = context.getString(R.string.password_wrong)
                LockoutManager.recordFailure()
                lockSeconds = LockoutManager.remainingSeconds().toInt()
            }
        }
    }

    when (step) {
        0 -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.change_password_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(
                        onClick = { step = 1; input = ""; error = null },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.change_password_by_password)) }
                    TextButton(
                        onClick = { step = 2; input = ""; error = null },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.change_password_by_recovery)) }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            },
        )
        1 -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.verify_current_password)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (passwordType == "mixed") {
                        MixedPasswordInput(
                            value = input,
                            onValueChange = { input = it; error = null },
                            label = stringResource(R.string.mixed_input_hint),
                            onImeAction = submitPassword,
                        )
                        Spacer(Modifier.height(4.dp))
                        Button(onClick = submitPassword) { Text(stringResource(R.string.confirm)) }
                    } else {
                        PasswordInputField(
                            value = input,
                            onValueChange = { input = it.filter { c -> c.isDigit() }.take(6); error = null },
                            label = stringResource(R.string.pin_input_hint),
                            keyboardType = KeyboardType.Number,
                            onImeAction = submitPassword,
                        )
                        Spacer(Modifier.height(4.dp))
                        Button(onClick = submitPassword) { Text(stringResource(R.string.confirm)) }
                    }
                    error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            },
        )
        2 -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.verify_current_recovery)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RecoveryKeyInput(
                        value = input,
                        onValueChange = { input = it; error = null },
                        label = stringResource(R.string.recovery_key_title),
                    )
                    error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val stored = repo.recoveryHash.first()
                        if (stored != null && PasswordCrypto.verify(input, stored)) {
                            LockoutManager.recordSuccess()
                            onVerified()
                        } else {
                            error = context.getString(R.string.recovery_key_wrong)
                            LockoutManager.recordFailure()
                            lockSeconds = LockoutManager.remainingSeconds().toInt()
                        }
                    }
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/**
 * 重置恢复密钥对话框: 验证旧密钥后生成新 6 位密钥并展示.
 * 旧密钥立即失效, 无法通过密码重置密钥.
 */
@Composable
fun ResetRecoveryKeyDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { SettingsRepository(context) }

    // 0=验证旧密钥 1=展示新密钥
    var step by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var newKey by remember { mutableStateOf("") }
    var lockSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { lockSeconds = LockoutManager.remainingSeconds().toInt() }
    LaunchedEffect(lockSeconds) {
        if (lockSeconds > 0) {
            while (lockSeconds > 0) { delay(1000); lockSeconds-- }
        }
    }

    if (lockSeconds > 0 && step == 0) {
        LockoutDialog(lockSeconds = lockSeconds, onDismiss = onDismiss)
        return
    }

    when (step) {
        0 -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.reset_recovery_key_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.reset_recovery_key_subtitle))
                    RecoveryKeyInput(
                        value = input,
                        onValueChange = { input = it; error = null },
                        label = stringResource(R.string.recovery_key_title),
                    )
                    error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val stored = repo.recoveryHash.first()
                        if (stored != null && PasswordCrypto.verify(input, stored)) {
                            // 生成新密钥并存储.
                            val key = PasswordCrypto.generateRecoveryKey()
                            val rSalt = PasswordCrypto.newSalt()
                            repo.setRecoveryHash(PasswordCrypto.encode(key, rSalt))
                            LockoutManager.recordSuccess()
                            newKey = key
                            step = 1
                        } else {
                            error = context.getString(R.string.recovery_key_wrong)
                            LockoutManager.recordFailure()
                            lockSeconds = LockoutManager.remainingSeconds().toInt()
                        }
                    }
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            },
        )
        1 -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.new_recovery_key_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.recovery_key_warning),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    )
                    // 密钥卡片: 4 位分组 + 等宽数字 + 可复制 (修复原 24sp 一行放不下被裁)
                    RecoveryKeyCard(key = newKey)
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.confirm)) }
            },
        )
    }
}
