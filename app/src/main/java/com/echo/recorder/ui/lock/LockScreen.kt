package com.echo.recorder.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.echo.recorder.R
import com.echo.recorder.auth.LockoutManager
import com.echo.recorder.auth.PasswordVerifier
import kotlinx.coroutines.delay

/**
 * 冷启动 + 主动锁定统一锁屏层.
 *
 * 全屏覆盖, 验证密码/图案通过后调用 [onUnlocked] 进入主界面.
 * 支持:
 * - PIN (6 位数字) 和图案两种验证
 * - 错误次数锁定倒计时
 * - 忘记密码→恢复密钥重置
 */
@Composable
fun LockScreen(
    storedHash: String?,
    recoveryHash: String?,
    isPattern: Boolean,
    onUnlocked: () -> Unit,
    onResetPassword: () -> Unit = {},
) {
    // null 表示未设置密码 -> 直接放行.
    if (storedHash == null) {
        LaunchedEffect(Unit) { onUnlocked() }
        return
    }

    var lockSeconds by remember { mutableIntStateOf(LockoutManager.remainingSeconds().toInt()) }
    var showRecovery by remember { mutableStateOf(false) }

    // 倒计时协程.
    LaunchedEffect(lockSeconds) {
        if (lockSeconds > 0) {
            while (lockSeconds > 0) {
                delay(1000)
                lockSeconds--
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // 顶部图标徽章 (呼应猫咪图标风格)
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(88.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(44.dp),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(R.string.lock_screen_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 28.dp),
        )

        when {
            lockSeconds > 0 -> {
                // 锁定倒计时.
                CircularProgressIndicator()
                Text(
                    stringResource(R.string.lockout_countdown, lockSeconds),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            showRecovery -> {
                // 恢复密钥重置.
                RecoveryKeyReset(
                    recoveryHash = recoveryHash,
                    onReset = {
                        // 清除密码并解锁, 用户可重新设置密码.
                        onResetPassword()
                        showRecovery = false
                        lockSeconds = 0
                    },
                    onBack = { showRecovery = false },
                )
            }
            else -> {
                // 密码/图案验证.
                PasswordPromptContent(
                    storedHash = storedHash,
                    isPattern = isPattern,
                    onVerify = {
                        LockoutManager.recordSuccess()
                        onUnlocked()
                    },
                    onWrong = {
                        lockSeconds = LockoutManager.remainingSeconds().toInt()
                    },
                    onForgot = { showRecovery = true },
                )
            }
        }
    }
}

/** 密码/图案验证内容. */
@Composable
private fun PasswordPromptContent(
    storedHash: String,
    isPattern: Boolean,
    onVerify: () -> Unit,
    onWrong: () -> Unit,
    onForgot: () -> Unit,
) {
    var error by remember { mutableStateOf(false) }

    if (isPattern) {
        var patternResetKey by remember { mutableIntStateOf(0) }
        PatternLockView(
            resetKey = patternResetKey,
            onPatternComplete = { p ->
                when (PasswordVerifier.verifyPassword(p.joinToString(","), storedHash)) {
                    PasswordVerifier.Result.Success -> onVerify()
                    PasswordVerifier.Result.Locked -> { /* 由倒计时处理 */ }
                    PasswordVerifier.Result.Wrong -> {
                        error = true
                        patternResetKey++
                        onWrong()
                    }
                    else -> { /* sealed 已穷尽, 此分支不会到达 */ }
                }
            },
        )
    } else {
        PinInput(
            onComplete = { pin ->
                when (PasswordVerifier.verifyPassword(pin, storedHash)) {
                    PasswordVerifier.Result.Success -> onVerify()
                    PasswordVerifier.Result.Locked -> { /* 由倒计时处理 */ }
                    PasswordVerifier.Result.Wrong -> {
                        error = true
                        onWrong()
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
            modifier = Modifier.padding(top = 12.dp),
        )
    }
    TextButtonClickable(onClick = onForgot) {
        Text(stringResource(R.string.forgot_password))
    }
}

/** 恢复密钥重置. */
@Composable
private fun RecoveryKeyReset(
    recoveryHash: String?,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    OutlinedTextFieldPin(
        value = input,
        onValueChange = { input = it; error = false },
        label = stringResource(R.string.recovery_key_title),
    )
    if (error) {
        Text(
            stringResource(R.string.recovery_key_wrong),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    TextButtonClickable(onClick = {
        if (PasswordVerifier.verifyRecovery(input, recoveryHash) == PasswordVerifier.Result.Success) {
            onReset()
        } else {
            error = true
            LockoutManager.recordFailure()
        }
    }) { Text(stringResource(R.string.confirm)) }
    TextButtonClickable(onClick = onBack) { Text(stringResource(R.string.cancel)) }
}
