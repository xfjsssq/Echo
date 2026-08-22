package com.echo.recorder.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.echo.recorder.ui.theme.EchoMotion

/**
 * 统一按压缩放反馈 —— 可叠加在任意已有的 clickable/combinedClickable 之上.
 *
 * 观察式实现 (PointerEventPass.Initial 只旁观不拦截), 不占用点击手势:
 * - 按下: 快速缩到 pressedScale (fastEffects, ~百毫秒内回应)
 * - 松开: 轻微过冲的果冻回弹 (fastSpatial, 与 FeatheredPillButton 手感一致但更有弹性)
 *
 * 列表滚动取消按压时自动复位 (waitForUpOrCancellation 返回 null).
 */
fun Modifier.echoPressScale(
    pressedScale: Float = 0.97f,
): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = if (pressed) EchoMotion.fastEffects() else EchoMotion.fastSpatial(),
        label = "echo_press",
    )

    this
        .pointerInputObserver {
            pressed = it
        }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
}

/** 观察按压状态而不干扰子级/同级的手势处理. */
private fun Modifier.pointerInputObserver(
    onPress: (Boolean) -> Unit,
): Modifier = this.then(
    Modifier.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            onPress(true)
            waitForUpOrCancellation(pass = PointerEventPass.Initial)
            onPress(false)
        }
    },
)
