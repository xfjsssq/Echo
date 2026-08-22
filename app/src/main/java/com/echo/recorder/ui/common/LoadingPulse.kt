package com.echo.recorder.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import com.echo.recorder.ui.theme.EchoMotion

/**
 * 三色呼吸加载点 —— 替代生硬的 Text("...") / 默认转圈.
 *
 * 品牌三色 (暖橙/声波蓝/珊瑚) 依次起伏, 像"正在听"的节拍:
 * 每点相位错开 140ms, 420ms 一次呼吸.
 */
@Composable
fun LoadingPulse(
    modifier: Modifier = Modifier,
    dotSize: Dp = 8.dp,
) {
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
    )
    val infinite = rememberInfiniteTransition(label = "loading_pulse")

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in colors.indices) {
            val breath by infinite.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(420, easing = EchoMotion.Emphasized),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(i * 140),
                ),
                label = "dot_$i",
            )
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .scale(0.7f + 0.35f * breath)
                    .alpha(0.45f + 0.55f * breath)
                    .clip(CircleShape)
                    .background(lerp(colors[i], Color.White, 0.05f * breath)),
            )
        }
    }
}
