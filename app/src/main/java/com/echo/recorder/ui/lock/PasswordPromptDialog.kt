package com.echo.recorder.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.echo.recorder.R
import com.echo.recorder.auth.LockoutManager
import com.echo.recorder.auth.PasswordVerifier
import com.echo.recorder.settings.SettingsRepository
import com.echo.recorder.ui.common.rememberEchoHaptics
import com.echo.recorder.ui.common.rememberShakeState
import com.echo.recorder.ui.common.shake
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * 密码验证对话框.
 *
 * @param storedHash 存储的密码哈希 (saltHex:hashHex)
 * @param recoveryHash 存储的恢复密钥哈希, 用于"忘记密码"重置
 * @param onVerify 验证成功后回调
 * @param onDismiss 取消回调
 * 密码类型 (pin/mixed) 由本组件从 DataStore 自行读取, 调用方无需关心.
 * 图案密码已彻底移除.
 */
@Composable
fun PasswordPromptDialog(
    storedHash: String?,
    recoveryHash: String?,
    onVerify: () -> Unit,
    onDismiss: () -> Unit,
) {
    // null 表示未设置密码 -> 直接放行.
    if (storedHash == null) {
        LaunchedEffect(Unit) { onVerify() }
        return
    }

    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    val passwordType by produceState<String?>(initialValue = null) {
        value = repo.passwordType.first()
    }

    // 退出 (验证通过/取消) 前必须主动隐藏键盘并清焦点:
    // 输入框组件持有"焦点期间键盘必须可见"的常驻补弹不变式 (国产 ROM 唤不醒的修复),
    // 且初始弹出走 SHOW_FORCED —— 不主动 hide, 对话框关闭后输入法会一直赖在后续界面上,
    // 表现为"点过一次导入按钮, 输入法就一直跟着弹".
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    fun exit(action: () -> Unit) {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        action()
    }

    var error by remember { mutableStateOf(false) }
    var showRecovery by remember { mutableStateOf(false) }
    var recoveryInput by remember { mutableStateOf("") }
    // 锁定倒计时 (秒).
    var lockSeconds by remember { mutableIntStateOf(LockoutManager.remainingSeconds().toInt()) }

    // 错误反馈: 横向抖动 + Reject 触感
    val shake = rememberShakeState()
    val haptics = rememberEchoHaptics()
    LaunchedEffect(error) {
        if (error) {
            haptics.reject()
            shake.shake()
        }
    }

    // 启动时检查是否已锁定.
    LaunchedEffect(Unit) {
        lockSeconds = LockoutManager.remainingSeconds().toInt()
    }
    // 倒计时协程.
    LaunchedEffect(lockSeconds) {
        if (lockSeconds > 0) {
            while (lockSeconds > 0) {
                delay(1000)
                lockSeconds--
            }
        }
    }

    if (showRecovery) {
        // 恢复密钥验证.
        if (lockSeconds > 0) {
            LockoutDialog(
                lockSeconds = lockSeconds,
                onDismiss = onDismiss,
            )
            return
        }
        AlertDialog(
            onDismissRequest = { exit(onDismiss) },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(stringResource(R.string.reset_password_with_recovery)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.shake(shake),
                ) {
                    RecoveryKeyInput(
                        value = recoveryInput,
                        onValueChange = { recoveryInput = it; error = false },
                        label = stringResource(R.string.recovery_key_title),
                    )
                    if (error) {
                        Text(
                            stringResource(R.string.recovery_key_wrong),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (PasswordVerifier.verifyRecovery(recoveryInput, recoveryHash) == PasswordVerifier.Result.Success) {
                        exit(onVerify)
                    } else {
                        error = true
                        lockSeconds = LockoutManager.remainingSeconds().toInt()
                    }
                }) { Text(stringResource(R.string.confirm)) }
            },
                dismissButton = {
                    TextButton(onClick = { exit(onDismiss) }) { Text(stringResource(R.string.cancel)) }
                },
            )
        return
    }

    // 锁定中显示倒计时.
    if (lockSeconds > 0) {
        LockoutDialog(
            lockSeconds = lockSeconds,
            onDismiss = { exit(onDismiss) },
        )
        return
    }

    var input by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { exit(onDismiss) },
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(stringResource(R.string.input_password)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.shake(shake),
            ) {
                if (passwordType == "mixed") {
                    // 扩展密码: 输入 + 确认按钮.
                    MixedPasswordInput(
                        value = input,
                        onValueChange = { input = it; error = false },
                        label = stringResource(R.string.mixed_input_hint),
                        onImeAction = {
                            when (PasswordVerifier.verifyPassword(input, storedHash)) {
                                PasswordVerifier.Result.Success -> { haptics.confirm(); exit(onVerify) }
                                PasswordVerifier.Result.Locked -> { lockSeconds = LockoutManager.remainingSeconds().toInt() }
                                PasswordVerifier.Result.Wrong -> {
                                    error = true
                                    lockSeconds = LockoutManager.remainingSeconds().toInt()
                                }
                                else -> { /* sealed 已穷尽 */ }
                            }
                        },
                    )
                    Spacer(Modifier.height(4.dp))
                    Button(onClick = {
                        when (PasswordVerifier.verifyPassword(input, storedHash)) {
                            PasswordVerifier.Result.Success -> { haptics.confirm(); exit(onVerify) }
                            PasswordVerifier.Result.Locked -> { lockSeconds = LockoutManager.remainingSeconds().toInt() }
                            PasswordVerifier.Result.Wrong -> {
                                error = true
                                lockSeconds = LockoutManager.remainingSeconds().toInt()
                            }
                            else -> { /* sealed 已穷尽 */ }
                        }
                    }) { Text(stringResource(R.string.confirm)) }
                } else {
                    // PIN: 6 位数字, 输满自动验证.
                    PinInput(
                        onComplete = { pin ->
                            when (PasswordVerifier.verifyPassword(pin, storedHash)) {
                                PasswordVerifier.Result.Success -> { haptics.confirm(); exit(onVerify) }
                                PasswordVerifier.Result.Locked -> { lockSeconds = LockoutManager.remainingSeconds().toInt() }
                                PasswordVerifier.Result.Wrong -> {
                                    error = true
                                    lockSeconds = LockoutManager.remainingSeconds().toInt()
                                }
                                else -> { /* sealed 已穷尽 */ }
                            }
                        },
                    )
                }
                if (error) {
                    Text(
                        stringResource(R.string.password_wrong),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                TextButton(onClick = { showRecovery = true }) {
                    Text(stringResource(R.string.forgot_password))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { exit(onDismiss) }) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** 锁定倒计时对话框. */
@Composable
internal fun LockoutDialog(
    lockSeconds: Int,
    onDismiss: () -> Unit,
) {
    var seconds by remember { mutableIntStateOf(lockSeconds) }
    LaunchedEffect(Unit) {
        while (seconds > 0) {
            delay(1000)
            seconds--
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.password_wrong)) },
        text = {
            if (seconds > 0) {
                Text(stringResource(R.string.lockout_countdown, seconds))
            } else {
                Text(stringResource(R.string.lockout_countdown, 0))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.confirm)) }
        },
    )
}
