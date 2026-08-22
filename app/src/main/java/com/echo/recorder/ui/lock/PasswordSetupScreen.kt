package com.echo.recorder.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.echo.recorder.R
import com.echo.recorder.settings.PasswordCrypto
import com.echo.recorder.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.echo.recorder.ui.common.AnimatedStep
import com.echo.recorder.ui.common.StepDirection
import com.echo.recorder.ui.common.rememberEchoHaptics
import com.echo.recorder.ui.common.rememberShakeState
import com.echo.recorder.ui.common.shake

/**
 * 密码设置流程.
 *
 * 类型: PIN (6 位数字) 或扩展密码 (6-32 位混合字符).
 * 流程: 选类型 → 第一次输入 → 再次输入 → 一致则生成恢复密钥 → 确认后写入.
 * 不一致则提示并回到第一次输入.
 * 图案密码已彻底移除.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordSetupScreen(
    isChangePassword: Boolean = false,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()
    // 仅进入非输入步骤 (恢复密钥展示) 前收起键盘;
    // 输入步骤之间 (第一步→第二步, 不一致返回重输) 保持输入法打开,
    // 新输入框拿到焦点时 IME 自然延续, 避免"收起→立刻弹出"动画竞态导致键盘唤不醒.
    val keyboard = LocalSoftwareKeyboardController.current

    // 0=选类型 1=第一次输入 2=第二次输入 3=恢复密钥展示
    var step by remember { mutableIntStateOf(if (isChangePassword) 1 else 0) }
    var passwordType by remember { mutableStateOf("pin") }
    // 修改密码: 读取原密码类型; 首次设置: 默认 "pin", 由用户在类型选择页决定.
    LaunchedEffect(Unit) {
        if (isChangePassword) {
            repo.passwordType.first()?.let { passwordType = it }
        }
    }
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<Int?>(null) }
    var recoveryKey by remember { mutableStateOf<String?>(null) }
    // 内存中暂存的密码和恢复密钥, 待用户确认恢复密钥后才写入 DataStore.
    var pendingPassword by remember { mutableStateOf<Pair<String, String>?>(null) }

    // 向导方向 + 错误反馈 (抖动 + Reject 触感) + 步骤触感
    var prevStep by remember { mutableIntStateOf(if (isChangePassword) 1 else 0) }
    val stepDirection = if (step > prevStep) StepDirection.Forward else StepDirection.Backward
    LaunchedEffect(step) { prevStep = step }
    val shake = rememberShakeState()
    val haptics = rememberEchoHaptics()
    LaunchedEffect(error) {
        if (error) {
            haptics.reject()
            shake.shake()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.password_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                // 可滚动: 键盘弹出压缩视口/步骤过渡高度变化时不再挤压裁切内容
                .verticalScroll(rememberScrollState())
                .shake(shake),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // 向导步骤: 方向感知滑动过渡 (0选类型→1输入→2再输入→3恢复密钥), 不再硬切
            AnimatedStep(targetState = step, direction = stepDirection) { s ->
                when (s) {
                    0 -> TypeSelect(
                        onPickPin = { haptics.tick(); passwordType = "pin"; step = 1; error = false; errorMsg = null },
                        onPickMixed = { haptics.tick(); passwordType = "mixed"; step = 1; error = false; errorMsg = null },
                    )
                1 -> FirstEnter(
                    passwordType = passwordType,
                    error = error,
                    errorMsg = errorMsg,
                    onDone = { value ->
                        if (value.length >= 6) {
                            first = value
                            error = false
                            errorMsg = null
                            step = 2
                        } else {
                            error = true
                            errorMsg = R.string.mixed_too_short
                        }
                    },
                )
                2 -> SecondEnter(
                    passwordType = passwordType,
                    error = error,
                    errorMsg = errorMsg,
                    onConfirm = { value ->
                        second = value
                        if (second == first) {
                            if (isChangePassword) {
                                // 修改密码: 只更新密码哈希, 恢复密钥保持不变 (ChangePasswordDialog 语义).
                                // 不生成新密钥, 不展示恢复密钥.
                                val salt = PasswordCrypto.newSalt()
                                val encodedPassword = PasswordCrypto.encode(first, salt)
                                scope.launch {
                                    repo.setPassword(encodedPassword)
                                    repo.setPasswordEnabled(true)
                                }
                                onDone()
                            } else {
                                // 首次设置: 先在内存中完成所有计算, 再展示恢复密钥.
                            val key = PasswordCrypto.generateRecoveryKey()
                            recoveryKey = key
                            val salt = PasswordCrypto.newSalt()
                            val encodedPassword = PasswordCrypto.encode(first, salt)
                            val rSalt = PasswordCrypto.newSalt()
                            val encodedRecovery = PasswordCrypto.encode(key, rSalt)
                            // 密码存储推迟到用户确认恢复密钥后, 避免 Activity 被销毁时恢复密钥未展示.
                            pendingPassword = encodedPassword to encodedRecovery
                            keyboard?.hide()
                            step = 3
                            }
                            } else {
                            // 不一致: 回到第一次输入.
                            error = true
                            errorMsg = if (passwordType == "mixed") R.string.mixed_not_match else R.string.pin_not_match
                            first = ""
                            second = ""
                            step = 1
                        }
                    },
                )
                3 -> RecoveryKeyShow(
                    key = recoveryKey ?: "",
                    onFinish = {
                        haptics.confirm()
                        // 用户确认已记录恢复密钥, 现在写入 DataStore.
                        // 顺序: 先写类型/哈希/恢复密钥, 最后再启用密码,
                        // 避免中途被杀导致"已启用但无密码哈希"的锁死空白页状态.
                        val (encPwd, encRec) = pendingPassword ?: ("" to "")
                        scope.launch {
                            repo.setPasswordType(passwordType)
                            repo.setPassword(encPwd)
                            repo.setRecoveryHash(encRec)
                            repo.setPasswordEnabled(true)
                        }
                        onDone()
                    },
                )
                }
            }
        }
    }
}

@Composable
private fun TypeSelect(onPickPin: () -> Unit, onPickMixed: () -> Unit) {
    Text(stringResource(R.string.password_type_title), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.password_type_subtitle),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedButton(onClick = onPickPin, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.password_type_pin))
        }
        OutlinedButton(onClick = onPickMixed, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.password_type_mixed))
        }
    }
}

@Composable
private fun FirstEnter(
    passwordType: String,
    error: Boolean,
    errorMsg: Int?,
    onDone: (String) -> Unit,
) {
    if (passwordType == "mixed") {
        // 扩展密码: 输入 + 确认按钮.
        var value by remember { mutableStateOf("") }
        MixedPasswordInput(
            value = value,
            onValueChange = { value = it },
            label = stringResource(R.string.mixed_input_hint),
            onImeAction = { onDone(value) },
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onDone(value) }) { Text(stringResource(R.string.confirm)) }
        if (error) {
            Text(
                stringResource(errorMsg ?: R.string.mixed_too_short),
                color = MaterialTheme.colorScheme.error,
            )
        }
    } else {
        PinInput(onComplete = onDone)
    }
}

@Composable
private fun SecondEnter(
    passwordType: String,
    error: Boolean,
    errorMsg: Int?,
    onConfirm: (String) -> Unit,
) {
    if (passwordType == "mixed") {
        // 扩展密码: 输入 + 确认按钮.
        var value by remember { mutableStateOf("") }
        MixedPasswordInput(
            value = value,
            onValueChange = { value = it },
            label = stringResource(R.string.mixed_confirm_hint),
            onImeAction = { onConfirm(value) },
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onConfirm(value) }) { Text(stringResource(R.string.confirm)) }
        if (error) {
            Text(
                stringResource(errorMsg ?: R.string.mixed_not_match),
                color = MaterialTheme.colorScheme.error,
            )
        }
    } else {
        // PIN: 输满 6 位自动进入下一步.
        var value by remember { mutableStateOf("") }
        PasswordInputField(
            value = value,
            onValueChange = {
                value = it.filter { c -> c.isDigit() }.take(6)
                if (value.length == 6) onConfirm(value)
            },
            label = stringResource(R.string.pin_confirm_hint),
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
        )
        if (error) {
            Text(
                stringResource(errorMsg ?: R.string.pin_not_match),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun RecoveryKeyShow(key: String, onFinish: () -> Unit) {
    Text(stringResource(R.string.recovery_key_title), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(12.dp))
    Text(
        stringResource(R.string.recovery_key_warning),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(16.dp))
    // 密钥卡片: 4 位分组 + 等宽数字 + 可复制 (修复原 24sp 格式串一行放不下被裁的问题)
    RecoveryKeyCard(key = key)
    Spacer(Modifier.height(24.dp))
    Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.confirm))
    }
}
