package com.echo.recorder.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.echo.recorder.ui.theme.EchoMotion
import kotlinx.coroutines.delay

/**
 * 一次性入场动画 — 淡入 + 上浮 + 微放大 (强调缓动, 内容"稳稳落位").
 *
 * 用途: 对话框卡片首次出现 / 应用外壳进入 / 任何凭空出现的容器.
 * Dialog 内容在独立窗口, AnimatedContent 无法过渡其出现, 入场动画是唯一解.
 */
@Composable
fun EntranceAnimation(
    delayMillis: Int = 0,
    rise: Dp = 16.dp,
    scaleFrom: Float = 0.98f,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (delayMillis > 0) delay(delayMillis.toLong())
        progress.animateTo(1f, tween(400, easing = EchoMotion.EmphasizedDecelerate))
    }
    val p = progress.value
    Box(
        Modifier.graphicsLayer {
            alpha = p.coerceIn(0f, 1f)
            translationY = (1f - p) * rise.toPx()
            val s = scaleFrom + (1f - scaleFrom) * p
            scaleX = s
            scaleY = s
        },
    ) {
        content()
    }
}
