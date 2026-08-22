package com.echo.recorder.ui.common

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * 触感标点 —— 每个关键状态变化给一次物理回应 (整个 App 此前完全没有触感).
 *
 * 语义划分:
 * - confirm: 动作成功生效 (开始录音/保存/密码正确)
 * - reject:  拒绝/删除/错误 (密码错误/删除确认)
 * - tick:    细粒度档位 (Tab 切换/滑杆落位/选日)
 * - longPress: 进入长按模式 (列表选择)
 *
 * API 分级: CONFIRM/REJECT/GESTURE_START 为 API 30+, 低版本降级到通用常量 (minSdk 26 安全).
 */
class EchoHaptics(private val view: View) {

    fun confirm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    fun reject() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            view.performHapticFeedback(HapticConstantsCompat.LongPress)
        }
    }

    fun tick() {
        view.performHapticFeedback(HapticConstantsCompat.ClockTick)
    }

    fun longPress() {
        view.performHapticFeedback(HapticConstantsCompat.LongPress)
    }

    fun gestureStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
        } else {
            view.performHapticFeedback(HapticConstantsCompat.ClockTick)
        }
    }
}

/** HapticFeedbackConstants 部分成员在低版本 SDK 上编译不可见, 用常量值桥接. */
private object HapticConstantsCompat {
    // HapticFeedbackConstants.LONG_PRESS = 0, CLOCK_TICK = 4 (自 API 3 起稳定不变)
    const val LongPress = 0
    const val ClockTick = 4
}

@Composable
fun rememberEchoHaptics(): EchoHaptics {
    val view = LocalView.current
    return remember(view) { EchoHaptics(view) }
}
