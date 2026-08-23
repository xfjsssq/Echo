package com.echo.recorder.ui.lock

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
 * 通用密码输入框 (修复"输入法唤不醒"的核心组件).
 *
 * 相比直接使用 OutlinedTextField 的关键差异:
 * 1. 不使用 KeyboardType.NumberPassword / Password —— 这两种类型会触发部分国产 ROM 的
 *    "安全键盘", 该输入法在 Compose 下经常弹不出来或弹出一片空白, 一旦收起就再也点不出来.
 *    这里改为普通 Number/Text 键盘 + Compose 侧的 PasswordVisualTransformation 打码.
 *    (已核实 Compose ui 1.6.8 的 EditorInfo 编码: KeyboardType.Number 输出纯
 *    TYPE_CLASS_NUMBER, 不带 password variation, 不会触发华为安全键盘.)
 * 2. 以 IME inset 高度 (键盘真实可见) 为准, 在**输入框持有焦点期间维持"键盘必须可见"
 *    的不变式**: 键盘不在就自动补弹, 包括初始弹出与任何意外收起 (典型: 用户点击输入框
 *    外的空白, 部分系统/输入法会据此收起键盘 —— 此后自动弹回, 点击等于没有任何效果).
 *    旧实现只在组合后的一次性窗口内补弹, 窗口结束后再被收起就再也唤不醒.
 * 3. showSoftInput 的 served view 用当前持有焦点的 view (findFocus 回退 decor):
 *    Dialog 刚打开时焦点转移未完成, 直接传 decorView 会被系统静默拒绝.
 * 4. 密码输入页没有需要键盘让位的滚动内容, 键盘常驻是安全页的合理行为.
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

    // 键盘是否"真实可见": 以 IME inset 高度为准 (show() 返回值只代表请求已送达,
    // 部分国产输入法会静默吞掉 show 请求, 只有 inset 才是事实).
    // IME inset 变化会触发重组, 由 SideEffect 同步进 state 供补弹协程读取.
    var imeVisible by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val imeVisibleNow = WindowInsets.ime.getBottom(density) > 0
    SideEffect { imeVisible = imeVisibleNow }

    var hasFocus by remember { mutableStateOf(false) }

    fun forceShow() {
        keyboard?.show()
        imm?.showSoftInput(view.findFocus() ?: view, InputMethodManager.SHOW_FORCED)
    }

    LaunchedEffect(Unit) {
        // 等窗口/对话框稳定 (覆盖 Dialog 焦点转移 + 上一步输入法收起动画) 再请求焦点,
        // 否则部分机型焦点被抢占导致输入法不弹出.
        delay(300)
        focusRequester.requestFocus()
        // 焦点到手后由下方的常驻循环接管补弹, 这里不再单独 show
        // (requestFocus 触发的 restartInput 会作废紧随其后的 show 请求).
    }

    // 常驻不变式: 焦点持有期间键盘必须可见.
    // hasFocus 变化时本协程自动重启; 协程内每 250ms 轮询一次键盘可见性,
    // 不可见就补弹 —— 覆盖初始弹出慢、show 被吞、用户点空白被系统收起后自动恢复等所有情形.
    LaunchedEffect(hasFocus) {
        if (!hasFocus) return@LaunchedEffect
        delay(150) // 跳过焦点切换引发的 restartInput 窗口
        while (true) {
            if (!imeVisible) forceShow()
            delay(250)
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
                hasFocus = it.isFocused
                if (it.isFocused && !imeVisible) forceShow()
            }
            // 点击兜底: 焦点可能已经在这个输入框上 (键盘却没弹出来),
            // 此时 onFocusChanged 不会触发, 必须靠点击事件再强弹一次.
            // 不消费事件, 不影响文本输入框自身的点击/光标处理.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    if (!imeVisible) forceShow()
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
            if (value.length == 6) {
                onComplete(value)
                // 回调后清空: 验证失败 (密码错误) 时输入框留在本页, 残留的 6 位会被
                // take(6) 过滤掉后续所有按键, 用户必须长按退格才能重输
                // —— 表现为"键盘弹了但按数字没反应", 曾被误判为输入法唤不醒.
                value = ""
            }
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
