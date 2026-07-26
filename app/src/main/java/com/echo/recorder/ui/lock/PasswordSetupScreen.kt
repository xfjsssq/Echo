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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.echo.recorder.R
import com.echo.recorder.auth.PasswordVerifier
import com.echo.recorder.settings.PasswordCrypto
import com.echo.recorder.settings.SettingsRepository
import kotlinx.coroutines.launch

/**
 * 密码设置流程.
 *
 * 数字密码: 选类型 → 输入 6 位 → 确认按钮 → 再次输入 → 一致则生成恢复密钥.
 * 图案密码: 选类型 → 绘制图案 → 图案保留 + 确认按钮 → 再次绘制 → 一致则生成恢复密钥.
 * 不一致则提示并回到第一次输入.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordSetupScreen(
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()

    // 0=选类型 1=第一次输入 2=第二次输入 3=恢复密钥展示
    var step by remember { mutableIntStateOf(0) }
    var isPattern by remember { mutableStateOf(false) }
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var recoveryKey by remember { mutableStateOf<String?>(null) }
    // 图案重置 key, 用于清空 PatternLockView.
    var patternResetKey by remember { mutableIntStateOf(0) }

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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (step) {
                0 -> TypeSelect(
                    onPickPin = { isPattern = false; step = 1; error = false },
                    onPickPattern = { isPattern = true; step = 1; error = false },
                )
                1 -> FirstEnter(
                    isPattern = isPattern,
                    label = if (isPattern) R.string.pattern_input_hint else R.string.pin_input_hint,
                    resetKey = patternResetKey,
                    onDrawn = { first = it; error = false; step = 2 },
                )
                2 -> SecondEnter(
                    isPattern = isPattern,
                    first = first,
                    label = if (isPattern) R.string.pattern_confirm_hint else R.string.pin_confirm_hint,
                    resetKey = patternResetKey,
                    error = error,
                    onConfirm = { second = it
                        if (second == first) {
                            // 生成恢复密钥并存储.
                            val key = PasswordCrypto.generateRecoveryKey()
                            recoveryKey = key
                            val salt = PasswordCrypto.newSalt()
                            scope.launch {
                                repo.setPasswordEnabled(true)
                                repo.setPasswordType(if (isPattern) "pattern" else "pin")
                                repo.setPassword(PasswordCrypto.encode(first, salt))
                                val rSalt = PasswordCrypto.newSalt()
                                repo.setRecoveryHash(PasswordCrypto.encode(key, rSalt))
                            }
                            step = 3
                        } else {
                            // 不一致: 回到第一次输入.
                            error = true
                            first = ""; second = ""
                            patternResetKey++
                            step = 1
                        }
                    },
                )
                3 -> RecoveryKeyShow(
                    key = recoveryKey ?: "",
                    onFinish = onDone,
                )
            }
        }
    }
}

@Composable
private fun TypeSelect(onPickPin: () -> Unit, onPickPattern: () -> Unit) {
    Text(stringResource(R.string.password_type_title), style = MaterialTheme.typography.titleMedium)
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedButton(onClick = onPickPin, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.password_type_pin))
        }
        OutlinedButton(onClick = onPickPattern, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.password_type_pattern))
        }
    }
}

@Composable
private fun FirstEnter(
    isPattern: Boolean,
    label: Int,
    resetKey: Int,
    onDrawn: (String) -> Unit,
) {
    if (isPattern) {
        Text(stringResource(label))
        Spacer(Modifier.height(16.dp))
        var drawn by remember { mutableStateOf<List<Int>>(emptyList()) }
        PatternLockView(
            resetKey = resetKey,
            onPatternComplete = { drawn = it },
        )
        // 绘制完成后显示确认按钮 (图案保留在屏幕上).
        if (drawn.size >= 4) {
            Text(
                stringResource(R.string.pattern_first_drawn),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onDrawn(drawn.joinToString(",")) },
            ) { Text(stringResource(R.string.confirm)) }
        }
    } else {
        PinInput(
            onComplete = { onDrawn(it) },
        )
    }
}

@Composable
private fun SecondEnter(
    isPattern: Boolean,
    first: String,
    label: Int,
    resetKey: Int,
    error: Boolean,
    onConfirm: (String) -> Unit,
) {
    if (isPattern) {
        Text(stringResource(label))
        Spacer(Modifier.height(16.dp))
        var drawn by remember { mutableStateOf<List<Int>>(emptyList()) }
        PatternLockView(
            resetKey = resetKey,
            onPatternComplete = { drawn = it },
        )
        if (error) {
            Text(
                stringResource(R.string.pattern_not_match),
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (drawn.size >= 4) {
            Button(
                onClick = { onConfirm(drawn.joinToString(",")) },
            ) { Text(stringResource(R.string.confirm)) }
        }
    } else {
        var value by remember { mutableStateOf("") }
        OutlinedTextFieldPin(
            value = value,
            onValueChange = {
                value = it
                if (it.length == 6) onConfirm(it)
            },
            label = stringResource(label),
        )
        if (error) {
            Text(
                stringResource(R.string.pin_not_match),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun RecoveryKeyShow(key: String, onFinish: () -> Unit) {
    Text(stringResource(R.string.recovery_key_title), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(16.dp))
    Text(
        stringResource(R.string.recovery_key_warning),
        color = MaterialTheme.colorScheme.error,
    )
    Spacer(Modifier.height(16.dp))
    Text(
        stringResource(R.string.recovery_key_label, key),
        style = MaterialTheme.typography.headlineSmall,
    )
    Spacer(Modifier.height(24.dp))
    Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.confirm))
    }
}
