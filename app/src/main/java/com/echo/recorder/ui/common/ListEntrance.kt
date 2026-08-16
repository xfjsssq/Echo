package com.echo.recorder.ui.common

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/**
 * 列表项入场动效 (iOS 风格 reveal):
 *
 * 新行出现时 → 轻微上浮 + 淡入 + **模糊→清晰** (API 31+ 启用硬件模糊,
 * 低版本自动降级为纯淡入上浮). 按 [index] 交错延迟 (上限 216ms),
 * 滚动时持续为下方新出现的行提供"延迟浮现"的高级感; 动画只跑一次,
 * 量化模糊步进避免每帧新建 RenderEffect 造成 GC 抖动.
 *
 * @param index 行索引, 用于交错延迟 (越靠下越晚出现)
 * @param withBlur 是否启用模糊→清晰 (表头等轻元素可关闭, 只做淡入上浮)
 */
@Composable
fun Modifier.animatedListEntrance(index: Int = 0, withBlur: Boolean = true): Modifier {
    val progress = remember { Animatable(0f) }
    val delayMs = (index.coerceAtMost(12) * 18).toLong()
    val blurEnabled = withBlur && Build.VERSION.SDK_INT >= 31
    val lastBlurStep = remember { IntArray(1) { -1 } }

    LaunchedEffect(Unit) {
        if (delayMs > 0) delay(delayMs)
        progress.animateTo(1f, tween(durationMillis = 420, easing = FastOutSlowInEasing))
    }

    return this.then(
        Modifier.graphicsLayer {
            val p = progress.value
            alpha = p
            translationY = (1f - p) * 20f
            if (blurEnabled) {
                // 量化模糊: 0..7 步, 每步约 3.2px, 只在步进变化时重建 RenderEffect
                val step = ((1f - p) * 7f).toInt()
                if (step != lastBlurStep[0]) {
                    lastBlurStep[0] = step
                    renderEffect = if (step > 0) {
                        val radius = step * 3.2f
                        BlurEffect(radius, radius, TileMode.Clamp)
                    } else {
                        null
                    }
                }
            }
        }
    )
}
