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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import kotlin.math.floor
import kotlin.math.pow

/**
 * 音频能量光带 v3 (Gemini Live 风格, 底部连续流动渐变).
 *
 * 第一性原理重构 —— v2 (40 根离散频谱条 × 3 层模糊 = 每帧 120 个 blurred rect)
 * 是录音页掉帧主源之一. v3 用一条连续平滑曲线还原 Gemini Live 的"流动光带":
 *
 * 1. **单路径渲染**: 40 个内部采样点 → Catmull 式平滑 Path, 全帧只 3 次绘制
 *    (填充 + 顶线 + 一层柔光), 模糊从 120 次 → 1 次.
 * 2. **贴底**: 曲面直接闭合到容器底边 (由调用方清掉导航栏 inset 后即贴屏幕底).
 * 3. **流动渐变**: 暖橙→亮蓝品牌渐变沿横向缓慢流动 (shader 矩阵平移, 零分配).
 * 4. **入场**: reveal 0→1 时曲面自底部涌起 (能量落地感), 前 25% 纯白衔接白球消散.
 * 5. 保留: 钟形包络、快攻慢放、独立值噪声舞动、静音呼吸、帧率无关时间步.
 *
 * @param amplitude 实时感知振幅 0.0-1.0
 * @param waveColor 兼容保留 (颜色由品牌渐变取代, 不再使用)
 * @param reveal 出现进度 0→1
 * @param backgroundColor 所在区域背景色, 用于混合柔化
 */
@Composable
fun SpectrumBars(
    amplitude: Float,
    waveColor: Color,
    reveal: Float = 1f,
    backgroundColor: Color = Color.White,
    modifier: Modifier = Modifier,
) {
    val N = 40 // 内部采样数 (驱动曲线形态)

    val heights = remember { FloatArray(N) { 0f } }
    val smoothAmp = remember { mutableFloatStateOf(0f) }
    var time by remember { mutableFloatStateOf(0f) }

    // 静态钟形包络: 中心 0.5 处最高, 两侧衰减
    val bell = remember {
        FloatArray(N) { i ->
            val x = i / (N - 1f) - 0.5f
            // 2.2 (原 3.4): 钟形更平缓 — 两侧保有 ~30% 基础高度, 边缘也会随噪声舞动
            kotlin.math.exp(-(x * 2.2f) * (x * 2.2f))
        }
    }

    val fillPaint = remember { Paint() }
    val glowPaint = remember { Paint() }
    val path = remember { Path() }

    val currentAmplitude by rememberUpdatedState(amplitude)
    val currentReveal by rememberUpdatedState(reveal)
    val currentBg by rememberUpdatedState(backgroundColor)

    // 极光配色 (更淡档, 用户两轮反馈要求): 高明度低饱和的蓝绿雾感
    val auroraBlue = Color(0xFFCDE0FF)                     // 淡蓝
    val auroraGreen = Color(0xFFD2F3E4)                    // 淡极光绿

    // ── 逐帧更新: 帧率无关时间步 + 指数平滑按帧时长归一 (60fps 调参基准) ──
    LaunchedEffect(Unit) {
        var lastFrameNanos = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (lastFrameNanos == 0L) 1f / 60f
                else ((now - lastFrameNanos) / 1_000_000_000f).coerceIn(0.004f, 0.05f)
                lastFrameNanos = now

                // 0.030f/帧(30ms 假设)在 60fps 实测 ≈ 1.8/s, 保持该基准速率
                time += dt * 1.8f

                fun alpha(f: Float) = 1f - (1f - f).pow(60f * dt)
                val envAtk = alpha(0.75f)
                val envRel = alpha(0.45f)
                val ptAtk = alpha(0.55f)
                val ptRel = alpha(0.18f)

                val target = currentAmplitude.coerceIn(0f, 1f)
                val perceived = target.pow(0.6f)
                val sa = smoothAmp.floatValue
                smoothAmp.floatValue = if (perceived > sa) {
                    sa + (perceived - sa) * envAtk
                } else {
                    sa + (perceived - sa) * envRel
                }
                val env = smoothAmp.floatValue

                for (i in 0 until N) {
                    val mod = 0.55f + 0.45f * valueNoise(i.toFloat() * 0.7f, time * 0.5f)
                    val audio = env.pow(0.7f) * bell[i] * mod
                    val idle = 0.07f * bell[i] * (0.4f + 0.6f * valueNoise(i.toFloat() * 0.7f + 50f, time * 0.8f))
                    val tgt = (audio + idle).coerceIn(0.02f, 1f)
                    val cur = heights[i]
                    heights[i] = if (tgt > cur) {
                        cur + (tgt - cur) * ptAtk
                    } else {
                        cur + (tgt - cur) * ptRel
                    }
                }
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val r = currentReveal.coerceIn(0f, 1f)
        if (r <= 0.005f) return@Canvas

        // 入场曲线: 涌起 (easeOutBack 轻微过冲) + 淡入
        val rise = if (r >= 1f) 1f else {
            val x = r - 1f
            (1f + 2.70158f * x * x * x + 1.70158f * x * x).coerceAtLeast(0f)
        }

        // 白 → 极光色 (前 25% 纯白衔接白球消散), 再与背景混合柔化
        val cr = ((r - 0.25f) / 0.75f).coerceIn(0f, 1f)
        val auroraA = lerp(currentBg, lerp(Color.White, auroraBlue, cr), 0.85f)
        val auroraB = lerp(currentBg, lerp(Color.White, auroraGreen, cr), 0.85f)

        // ── 贴底连续曲面: 采样点 → 平滑 Path → 闭合到容器底边 ──
        val bottom = h + 2f // 略微超出, 保证无缝贴边
        val maxBand = h * 0.82f
        // draw 内读取包络 → 每帧失效重绘 (帧循环写入驱动)
        val env = smoothAmp.floatValue

        path.reset()
        val xs = FloatArray(N) { i -> i / (N - 1f) * w }
        val ys = FloatArray(N) { i -> bottom - maxBand * heights[i].coerceIn(0f, 1.15f) * rise }
        path.moveTo(xs[0], ys[0])
        for (i in 1 until N) {
            // Catmull 折中: 以相邻点中点为控制点的三次贝塞尔, C1 连续
            val mx = (xs[i - 1] + xs[i]) * 0.5f
            path.cubicTo(mx, ys[i - 1], mx, ys[i], xs[i], ys[i])
        }
        path.lineTo(w, bottom)
        path.lineTo(0f, bottom)
        path.close()

        // 流动渐变: [蓝→绿→蓝] 极光循环 + 矩阵平移 (零分配); REPEATED 保证接缝连续
        val gradWidth = w * 1.6f
        val shader = android.graphics.LinearGradient(
            0f, 0f, gradWidth, 0f,
            intArrayOf(
                auroraA.copy(alpha = 0.90f * r).toArgb(),
                auroraB.copy(alpha = 0.92f * r).toArgb(),
                auroraA.copy(alpha = 0.90f * r).toArgb(),
            ),
            floatArrayOf(0f, 0.5f, 1f),
            android.graphics.Shader.TileMode.REPEAT,
        )
        val phase = (time * 0.12f) % 1f
        val matrix = android.graphics.Matrix()
        matrix.setTranslate(-phase * gradWidth, 0f)
        shader.setLocalMatrix(matrix)

        // 1. 辉光层: 沿曲面的外圈光晕 (Stroke 描边 + 大模糊) — 用户钦点的边缘发光, 恢复
        glowPaint.shader = shader
        glowPaint.style = PaintingStyle.Stroke
        glowPaint.strokeWidth = w * 0.05f
        glowPaint.asFrameworkPaint().maskFilter =
            android.graphics.BlurMaskFilter(w * 0.030f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        drawContext.canvas.drawPath(path, glowPaint)

        // 2. 填充层: 流动渐变面 + 轻微模糊 —
        //    分界的真身是本层无模糊的"硬边": 锐利的填充边 vs 模糊的外圈辉光 = 浓度突变线.
        //    填充自身柔化后, 主体边缘渐入辉光, 光从内部长到边缘, 分界消失.
        fillPaint.shader = shader
        fillPaint.style = PaintingStyle.Fill
        fillPaint.asFrameworkPaint().maskFilter =
            android.graphics.BlurMaskFilter(w * 0.012f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        drawContext.canvas.drawPath(path, fillPaint)
    }
}

// ═══════════════════════════════════════════════════════════
// 值噪声 (Value Noise): 让曲面有机流动, 不机械
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
