package com.echo.recorder.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.echo.recorder.settings.PasswordCrypto
import com.echo.recorder.settings.SettingsRepository
import kotlinx.coroutines.launch

/**
 * 密码设置流程.
 *
 * 步骤: 选择类型 (数字/图案) -> 输入 -> 确认 -> 生成恢复密钥 (强制展示警告).
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

    // 0=选类型 1=输入 2=确认 3=恢复密钥
    var step by remember { mutableStateOf(0) }
    var isPattern by remember { mutableStateOf(false) }
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var recoveryKey by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.password_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "back") }
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
                    onPickPin = { isPattern = false; step = 1 },
                    onPickPattern = { isPattern = true; step = 1 },
                )
                1 -> EnterPassword(
                    isPattern = isPattern,
                    label = if (isPattern) R.string.pattern_input_hint else R.string.pin_input_hint,
                    onEnter = { first = it; second = ""; error = null; step = 2 },
                )
                2 -> ConfirmPassword(
                    isPattern = isPattern,
                    first = first,
                    label = if (isPattern) R.string.pattern_confirm_hint else R.string.pin_confirm_hint,
                    error = error,
                    onConfirm = { second = it; error = null
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
                            error = if (isPattern) "pattern_mismatch" else "pin_mismatch"
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
private fun EnterPassword(isPattern: Boolean, label: Int, onEnter: (String) -> Unit) {
    if (isPattern) {
        Text(stringResource(label), modifier = Modifier.padding(bottom = 16.dp))
        var drawn by remember { mutableStateOf<List<Int>>(emptyList()) }
        PatternLockView(onPatternComplete = { drawn = it })
        Button(
            onClick = { if (drawn.size >= 4) onEnter(drawn.joinToString(",")) },
            modifier = Modifier.padding(top = 16.dp),
        ) { Text(stringResource(R.string.confirm)) }
    } else {
        var value by remember { mutableStateOf("") }
        OutlinedTextField(
            value = value,
            onValueChange = { value = it.filter { c -> c.isDigit() }.take(6) },
            label = { Text(stringResource(label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { if (value.length == 6) onEnter(value) },
            modifier = Modifier.padding(top = 16.dp),
        ) { Text(stringResource(R.string.confirm)) }
    }
}

@Composable
private fun ConfirmPassword(
    isPattern: Boolean,
    first: String,
    label: Int,
    error: String?,
    onConfirm: (String) -> Unit,
) {
    if (isPattern) {
        Text(stringResource(label), modifier = Modifier.padding(bottom = 16.dp))
        var drawn by remember { mutableStateOf<List<Int>>(emptyList()) }
        PatternLockView(onPatternComplete = { drawn = it })
        error?.let { Text(stringResource(R.string.pattern_mismatch), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        Button(
            onClick = { if (drawn.size >= 4) onConfirm(drawn.joinToString(",")) },
            modifier = Modifier.padding(top = 16.dp),
        ) { Text(stringResource(R.string.confirm)) }
    } else {
        var value by remember { mutableStateOf("") }
        OutlinedTextField(
            value = value,
            onValueChange = { value = it.filter { c -> c.isDigit() }.take(6) },
            label = { Text(stringResource(label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(stringResource(R.string.pin_mismatch), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        Button(
            onClick = { if (value.length == 6) onConfirm(value) },
            modifier = Modifier.padding(top = 16.dp),
        ) { Text(stringResource(R.string.confirm)) }
    }
}

@Composable
private fun RecoveryKeyShow(key: String, onFinish: () -> Unit) {
    Text(stringResource(R.string.recovery_key_title), style = MaterialTheme.typography.titleMedium)
    Text(
        stringResource(R.string.recovery_key_warning),
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(vertical = 16.dp),
    )
    Text(
        stringResource(R.string.recovery_key_label, key),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(bottom = 24.dp),
    )
    Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.confirm))
    }
}
