package com.echo.recorder.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * 错误抖动 —— 密码错误/校验失败时的横向 shake, 代替"错误文字突然出现"的生硬反馈.
 *
 * 用法:
 * ```
 * val shake = rememberShakeState()
 * LaunchedEffect(error) { if (error != null) shake.shake() }
 * Box(Modifier.shake(shake)) { ... }
 * ```
 */
class ShakeState {
    internal val offset = Animatable(0f)

    /** 一次 420ms 抖动: 快速左冲 → 阻尼回摆衰减 (幅度递减, 不机械等幅). */
    suspend fun shake() {
        offset.snapTo(0f)
        offset.animateTo(
            targetValue = 0f,
            animationSpec = keyframes {
                durationMillis = 420
                0f at 0
                1f at 60
                -0.7f at 150
                0.45f at 240
                -0.25f at 320
                0.1f at 380
                0f at 420
            },
        )
    }
}

@Composable
fun rememberShakeState(): ShakeState = remember { ShakeState() }

/** 抖动位移最大 9dp, 随 offset 值缩放. */
fun Modifier.shake(state: ShakeState): Modifier = composed {
    graphicsLayer {
        translationX = state.offset.value * 9.dp.toPx()
    }
}
