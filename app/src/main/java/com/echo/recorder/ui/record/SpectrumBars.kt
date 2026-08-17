package com.echo.recorder.ui.record

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import kotlin.math.floor
import kotlin.math.pow

/**
 * 频谱条可视化 (替代 FluidWaveform 的另一种音频能量呈现).
 *
 * 设计要点:
 * 1. **中心高、两侧低**的钟形包络 — 像真实频谱分析仪, 中间频段能量更足.
 * 2. **快攻慢放** — 每根条独立 attack 0.55 / decay 0.18, 声音一来瞬间冲高,
 *    一停缓缓回落, 经典 VU/频谱仪手感.
 * 3. **独立舞动** — 每根条用各自相位的值噪声调制, 永不齐上齐下, 有机不机械.
 * 4. **静音呼吸** — 无声时也有 ~7% 高度的轻微起伏, 避免完全死板.
 * 5. **黄蓝渐变** — 底部暖黄 (primaryContainer) → 顶部冷蓝 (secondaryContainer),
 *    与引导卡片 / 原 FluidWaveform 同源; 矮条只显暖色, 高条才触及冷蓝.
 * 6. **镜面倒影** — 基线下方镜像淡出, 保持与 V15 一致的纵深质感.
 * 7. **reveal** — 出现进度 0→1, 前 25% 保持纯白衔接白球消散, 后 75% 渐变到主题色.
 *
 * @param amplitude 实时感知振幅 0.0-1.0
 * @param waveColor 兼容保留 (颜色已由黄蓝渐变取代, 不再使用)
 * @param reveal 出现进度 0→1
 * @param backgroundColor 所在区域背景色, 用于混合柔化, 消除割裂感
 */
@Composable
fun SpectrumBars(
    amplitude: Float,
    waveColor: Color,
    reveal: Float = 1f,
    backgroundColor: Color = Color.White,
    modifier: Modifier = Modifier,
) {
    val N = 40 // 频谱条数

    // 每根条当前高度 (跨帧缓动 → 快攻慢放拖尾)
    val heights = remember { FloatArray(N) { 0f } }
    // 平滑包络 (快攻慢放)
    val smoothAmp = remember { mutableFloatStateOf(0f) }
    var time by remember { mutableFloatStateOf(0f) }

    // 静态钟形包络: 中心 0.5 处最高, 两侧衰减 (预计算, 不随帧变)
    val bell = remember {
        FloatArray(N) { i ->
            val x = i / (N - 1f) - 0.5f          // -0.5 .. 0.5
            kotlin.math.exp(-(x * 3.4f) * (x * 3.4f)) // 高斯, 中心 1.0
        }
    }

    val barPaint = remember { Paint() }
    val reflPaint = remember { Paint() }

    val currentAmplitude by rememberUpdatedState(amplitude)
    val currentReveal by rememberUpdatedState(reveal)
    val currentBg by rememberUpdatedState(backgroundColor)

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val warmTheme = MaterialTheme.colorScheme.primaryContainer   // 暖黄
    val coolTheme = MaterialTheme.colorScheme.secondaryContainer // 冷蓝

    // ── 逐帧更新: 振幅平滑 + 每根条独立目标 + 快攻慢放 ──
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { }
            time += 0.030f

            val target = currentAmplitude.coerceIn(0f, 1f)
            val perceived = target.pow(0.6f) // 小音量放大, 大音量柔和
            val sa = smoothAmp.floatValue
            smoothAmp.floatValue = if (perceived > sa) {
                sa + (perceived - sa) * 0.75f   // 快攻
            } else {
                sa + (perceived - sa) * 0.45f   // 快放
            }
            val env = smoothAmp.floatValue

            for (i in 0 until N) {
                // 每根条独立调制: 慢速值噪声, 让相邻条不同步
                val mod = 0.55f + 0.45f * valueNoise(i.toFloat() * 0.7f, time * 0.5f)
                val audio = env.pow(0.7f) * bell[i] * mod
                // 静音呼吸: 无声时也有轻微起伏, 避免死板
                val idle = 0.07f * bell[i] * (0.4f + 0.6f * valueNoise(i.toFloat() * 0.7f + 50f, time * 0.8f))
                val tgt = (audio + idle).coerceIn(0.02f, 1f)

                val cur = heights[i]
                heights[i] = if (tgt > cur) {
                    cur + (tgt - cur) * 0.55f   // attack: 瞬间冲高
                } else {
                    cur + (tgt - cur) * 0.18f   // decay: 缓慢回落
                }
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val r = currentReveal.coerceIn(0f, 1f)

        // 白 → 黄蓝渐变 (前 25% 纯白衔接白球消散)
        val cr = ((r - 0.25f) / 0.75f).coerceIn(0f, 1f)
        val warmBase = lerp(Color.White, warmTheme, cr)
        val coolBase = lerp(Color.White, coolTheme, cr)
        val mix = if (isDark) 0.55f else 0.85f
        val warm = lerp(currentBg, warmBase, mix)   // 底部暖黄
        val cool = lerp(currentBg, coolBase, mix)   // 顶部冷蓝

        // 布局: 基线在 0.60h, 条向上生长, 下方留倒影区
        val baselineY = h * 0.60f
        val maxBarH = h * 0.42f
        val slot = w / N
        val barWidth = slot * 0.55f
        val gap = slot - barWidth
        val radius = barWidth * 0.5f // 顶端全圆 → 胶囊感

        // 渐变 shader: 贯穿可能最高条的区域, 矮条只取底部暖段
        val gradTop = baselineY - maxBarH * 1.05f

        // ── 镜面倒影: 下方镜像 + 纵向渐变淡出 ──
        reflPaint.shader = android.graphics.LinearGradient(
            0f, baselineY, 0f, baselineY + maxBarH * 0.5f,
            intArrayOf(
                lerp(warm, cool, 0.5f).copy(alpha = (if (isDark) 0.12f else 0.18f) * r).toArgb(),
                lerp(warm, cool, 0.5f).copy(alpha = 0f).toArgb(),
            ),
            floatArrayOf(0f, 1f),
            android.graphics.Shader.TileMode.CLAMP,
        )
        reflPaint.style = PaintingStyle.Fill
        reflPaint.strokeCap = StrokeCap.Round
        reflPaint.asFrameworkPaint().maskFilter =
            android.graphics.BlurMaskFilter(w * 0.018f, android.graphics.BlurMaskFilter.Blur.NORMAL)

        for (i in 0 until N) {
            val hh = maxBarH * heights[i].coerceIn(0f, 1f) * r
            val x = i * slot + gap * 0.5f
            // 倒影: 关于基线镜像, 收敛 0.5 (短于本体), 圆角底
            val reflH = hh * 0.5f
            drawContext.canvas.drawRoundRect(
                x, baselineY, x + barWidth, baselineY + reflH,
                radius, radius, reflPaint,
            )
        }

        // ── 主条: 渐变填充 + 轻微发光 ──
        barPaint.shader = android.graphics.LinearGradient(
            0f, gradTop, 0f, baselineY,
            intArrayOf(
                cool.copy(alpha = 0.92f * r).toArgb(),
                warm.copy(alpha = 0.96f * r).toArgb(),
            ),
            floatArrayOf(0f, 1f),
            android.graphics.Shader.TileMode.CLAMP,
        )
        barPaint.style = PaintingStyle.Fill
        barPaint.strokeCap = StrokeCap.Round
        barPaint.asFrameworkPaint().maskFilter =
            android.graphics.BlurMaskFilter(w * 0.006f, android.graphics.BlurMaskFilter.Blur.NORMAL)

        for (i in 0 until N) {
            val hh = maxBarH * heights[i].coerceIn(0f, 1f) * r
            val x = i * slot + gap * 0.5f
            val top = baselineY - hh
            drawContext.canvas.drawRoundRect(
                x, top, x + barWidth, baselineY,
                radius, radius, barPaint,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════
// 值噪声 (Value Noise): 让每根条独立舞动, 有机不重复
// ═══════════════════════════════════════════════════════════

private fun hashNoise(x: Int, y: Int): Float {
    var h = x * 374761393 + y * 668265263
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h and 0x7fffffff) / 2147483647f
}

private fun valueNoise(x: Float, y: Float): Float {
    val xi = floor(x).toInt()
    val yi = floor(y).toInt()
    val xf = x - xi
    val yf = y - yi
    val u = xf * xf * (3f - 2f * xf)
    val v = yf * yf * (3f - 2f * yf)
    val a = hashNoise(xi, yi) + (hashNoise(xi + 1, yi) - hashNoise(xi, yi)) * u
    val b = hashNoise(xi, yi + 1) + (hashNoise(xi + 1, yi + 1) - hashNoise(xi, yi + 1)) * u
    return a + (b - a) * v
}
