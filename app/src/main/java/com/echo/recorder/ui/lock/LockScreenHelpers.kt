package com.echo.recorder.ui.lock

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.echo.recorder.R
import kotlinx.coroutines.delay

/** 文本按钮 (简化版). */
@Composable
fun TextButtonClickable(onClick: () -> Unit, content: @Composable () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.padding(top = 8.dp)) {
        content()
    }
}

/**
 * 通用密码输入框 (修复"安全键盘 / 输入法弹不出来"的核心组件).
 *
 * 相比直接使用 OutlinedTextField 的关键差异:
 * 1. 不使用 KeyboardType.NumberPassword / Password —— 这两种类型会触发部分国产 ROM 的
 *    "安全键盘", 该输入法在 Compose 下经常弹不出来或弹出一片空白, 一旦收起就再也点不出来.
 *    这里改为普通 Number/Text 键盘 + Compose 侧的 PasswordVisualTransformation 打码,
 *    输入法行为与普通输入框一致, 稳定可靠.
 * 2. 组合进入后延迟片刻主动请求焦点并调用 show(), 确保输入框每次出现时输入法都会弹出
 *    (修复设置密码第二步、锁屏、弹窗中输入法消失的问题).
 * 3. 获得焦点时再次 show(), 兜底"键盘被收起后点击输入框弹不出来"的机型问题.
 * 4. Compose 的 SoftwareKeyboardController.show() 在部分国产 ROM 上不可靠,
 *    因此同时调用系统 InputMethodManager.showSoftInput() 双保险, 并重试多次.
 * 5. 用 SHOW_FORCED 强弹 (部分 ROM 会忽略 SHOW_IMPLICIT), 且重试窗口拉长到 ~2 秒,
 *    覆盖"上一步刚收起键盘、新输入框立刻要弹"的动画竞态.
 * 6. 点击输入框时即使焦点未变化也再次强弹, 解决"焦点已持有但键盘没弹"的僵局.
 */
@Composable
fun PasswordInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    val imm = remember {
        view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    }

    LaunchedEffect(Unit) {
        // 等窗口/对话框稳定后再请求焦点, 否则部分机型焦点被抢占导致输入法不弹出.
        delay(200)
        focusRequester.requestFocus()
        // 焦点拿到后再等一拍, 让 IME 完成窗口附着.
        delay(200)
        keyboard?.show()
        imm?.showSoftInput(view, InputMethodManager.SHOW_FORCED)
        // 部分国产 ROM 的 IME 弹出很慢、首次请求被吞, 或正处在"上一步收起键盘"的动画中.
        // 长窗口 + 强弹双保险: 每 250ms 补一枪, 直到稳定弹出 (~2 秒).
        repeat(8) {
            delay(250)
            keyboard?.show()
            imm?.showSoftInput(view, InputMethodManager.SHOW_FORCED)
        }
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction,
            autoCorrect = false,
            capitalization = KeyboardCapitalization.None,
        ),
        keyboardActions = KeyboardActions(onDone = { onImeAction?.invoke() }),
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged {
                if (it.isFocused) {
                    keyboard?.show()
                    imm?.showSoftInput(view, InputMethodManager.SHOW_FORCED)
                }
            }
            // 点击兜底: 焦点可能已经在这个输入框上 (键盘却没弹出来),
            // 此时 onFocusChanged 不会触发, 必须靠点击事件再强弹一次.
            // 不消费事件, 不影响文本输入框自身的点击/光标处理.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    keyboard?.show()
                    imm?.showSoftInput(view, InputMethodManager.SHOW_FORCED)
                    waitForUpOrCancellation()
                }
            },
    )
}

/** PIN 输入框 (6 位数字, 输满自动回调). */
@Composable
fun PinInput(onComplete: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    PasswordInputField(
        value = value,
        onValueChange = {
            value = it.filter { c -> c.isDigit() }.take(6)
            if (value.length == 6) onComplete(value)
        },
        label = stringResource(R.string.pin_input_hint),
        keyboardType = KeyboardType.Number,
        onImeAction = { if (value.length == 6) onComplete(value) },
    )
}

/** 恢复密钥输入框 (6 位数字). */
@Composable
fun RecoveryKeyInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    PasswordInputField(
        value = value,
        onValueChange = { v -> onValueChange(v.filter { c -> c.isDigit() }.take(6)) },
        label = label,
        keyboardType = KeyboardType.Number,
    )
}

/** 扩展密码输入框 (字母/数字/符号, 6-32 位, 不含空格). */
@Composable
fun MixedPasswordInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    onImeAction: (() -> Unit)? = null,
) {
    PasswordInputField(
        value = value,
        onValueChange = { v -> onValueChange(v.filterNot { it.isWhitespace() }.take(32)) },
        label = label,
        keyboardType = KeyboardType.Text,
        onImeAction = onImeAction,
    )
}
