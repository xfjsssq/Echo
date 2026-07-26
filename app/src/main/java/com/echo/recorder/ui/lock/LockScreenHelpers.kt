package com.echo.recorder.ui.lock

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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

/** 文本按钮 (简化版). */
@Composable
fun TextButtonClickable(onClick: () -> Unit, content: @Composable () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.padding(top = 8.dp)) {
        content()
    }
}

/** PIN 输入框 (6 位数字密码). */
@Composable
fun PinInput(onComplete: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    OutlinedTextFieldPin(
        value = value,
        onValueChange = {
            value = it
            if (it.length == 6) onComplete(it)
        },
        label = stringResource(R.string.pin_input_hint),
    )
}

/** 通用密码输入框. */
@Composable
fun OutlinedTextFieldPin(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onValueChange(v.filter { c -> c.isDigit() }.take(6)) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
