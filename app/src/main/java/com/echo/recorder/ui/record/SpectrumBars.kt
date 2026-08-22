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
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow

/**
 * 频谱条可视化 v2 (Gemini Live 风格, 贴底锚定).
 *
 * v2 变化:
 * 1. **贴底锚定** — 基线在容器 0.88h, 条自屏幕底边向上生长, 下方 12% 只放倒影,
 *    不再"悬浮在空中".
 * 2. **绽放入场** — reveal 时条从中线向两侧依次破土而出 (中心先起, 带轻微过冲),
 *    衔接 logo 下坠消散的"能量落地"感.
 * 3. **零每帧分配** — 渐变 shader 按内容哈希缓存, 稳态 (reveal=1) 完全复用,
 *    高刷/长录音不再产生 GC 压力.
 * 4. 保留: 40 根胶囊条、钟形包络、快攻慢放、独立值噪声舞动、静音呼吸、
 *    黄蓝渐变、能量辉光、镜面倒影、帧率无关时间步.
 *
 * @param amplitude 实时感知振幅 0.0-1.0
 * @param waveColor 兼容保留 (颜色已由黄蓝渐变取代, 不再使用)
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

    // shader 缓存: 稳态下 key 不变 → 零分配 (v1 每帧 new LinearGradient, GC 压力源)
    val shaderCache = remember { ShaderCache() }

    val barPaint = remember { Paint() }
    val glowPaint = remember { Paint() }
    val reflPaint = remember { Paint() }

    val currentAmplitude by rememberUpdatedState(amplitude)
    val currentReveal by rememberUpdatedState(reveal)
    val currentBg by rememberUpdatedState(backgroundColor)

    val warmTheme = MaterialTheme.colorScheme.primaryContainer   // 暖黄
    val coolTheme = MaterialTheme.colorScheme.secondaryContainer // 冷蓝

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
                val barAtk = alpha(0.55f)
                val barRel = alpha(0.18f)

                val target = currentAmplitude.coerceIn(0f, 1f)
                val perceived = target.pow(0.6f) // 小音量放大, 大音量柔和
                val sa = smoothAmp.floatValue
                smoothAmp.floatValue = if (perceived > sa) {
                    sa + (perceived - sa) * envAtk   // 快攻
                } else {
                    sa + (perceived - sa) * envRel   // 快放
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
                        cur + (tgt - cur) * barAtk   // attack: 瞬间冲高
                    } else {
                        cur + (tgt - cur) * barRel   // decay: 缓慢回落
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

        // 白 → 黄蓝渐变 (前 25% 纯白衔接白球消散)
        val cr = ((r - 0.25f) / 0.75f).coerceIn(0f, 1f)
        val warmBase = lerp(Color.White, warmTheme, cr)
        val coolBase = lerp(Color.White, coolTheme, cr)
        val mix = 0.85f
        val warm = lerp(currentBg, warmBase, mix)   // 底部暖黄
        val cool = lerp(currentBg, coolBase, mix)   // 顶部冷蓝

        // ── 贴底布局: 基线 0.88h, 条向上生长, 底部 12% 只放倒影 ──
        val baselineY = h * 0.88f
        val maxBarH = h * 0.64f
        val reflSpace = h - baselineY
        val slot = w / N
        val barWidth = slot * 0.55f
        val gap = slot - barWidth
        val radius = barWidth * 0.5f // 顶端全圆 → 胶囊感

        // 渐变 shader: 贯穿可能最高条的区域, 矮条只取底部暖段
        val gradTop = baselineY - maxBarH * 1.08f
        val env = smoothAmp.floatValue // draw 内读取 → 每帧失效重绘

        // ── 绽放入场: 中线先起向两侧扩散, easeOutBack 轻微过冲 ──
        val half = (N - 1) / 2f
        fun bloom(i: Int): Float {
            if (r >= 1f) return 1f
            val dist = abs(i - half) / half
            val delay = dist * 0.38f
            val local = ((r - delay) / (1f - delay)).coerceIn(0f, 1f)
            val c1 = 1.70158f
            val x = local - 1f
            return (1f + (c1 + 1f) * x * x * x + c1 * x * x).coerceAtLeast(0f)
        }

        // ── 镜面倒影: 基线下方镜像, 收敛到底边渐隐 ──
        val reflBottom = baselineY + reflSpace * 0.92f
        reflPaint.shader = shaderCache.reflection(
            key = ShaderKey(warm.toArgb(), cool.toArgb(), reflBottom.toInt(), (r * 64).toInt(), 0),
            build = {
                android.graphics.LinearGradient(
                    0f, baselineY, 0f, reflBottom,
                    intArrayOf(
                        lerp(warm, cool, 0.5f).copy(alpha = 0.18f * r).toArgb(),
                        lerp(warm, cool, 0.5f).copy(alpha = 0f).toArgb(),
                    ),
                    floatArrayOf(0f, 1f),
                    android.graphics.Shader.TileMode.CLAMP,
                )
            },
        )
        reflPaint.style = PaintingStyle.Fill
        reflPaint.strokeCap = StrokeCap.Round
        reflPaint.asFrameworkPaint().maskFilter =
            android.graphics.BlurMaskFilter(w * 0.016f, android.graphics.BlurMaskFilter.Blur.NORMAL)

        for (i in 0 until N) {
            val hh = maxBarH * heights[i].coerceIn(0f, 1f) * bloom(i)
            if (hh <= 0.5f) continue
            val x = i * slot + gap * 0.5f
            val reflH = (hh * 0.30f).coerceAtMost(reflSpace)
            drawContext.canvas.drawRoundRect(
                x, baselineY, x + barWidth, baselineY + reflH,
                radius, radius, reflPaint,
            )
        }

        // ── 能量辉光: 声音越大光越盛 ──
        glowPaint.shader = shaderCache.glow(
            key = ShaderKey(cool.toArgb(), warm.toArgb(), baselineY.toInt(), (r * 64).toInt(), 1),
            build = {
                android.graphics.LinearGradient(
                    0f, gradTop, 0f, baselineY,
                    intArrayOf(
                        cool.copy(alpha = 0.28f * r).toArgb(),
                        warm.copy(alpha = 0.34f * r).toArgb(),
                    ),
                    floatArrayOf(0f, 1f),
                    android.graphics.Shader.TileMode.CLAMP,
                )
            },
        )
        glowPaint.style = PaintingStyle.Fill
        glowPaint.alpha = (0.22f + 0.30f * env).coerceIn(0f, 1f)
        glowPaint.asFrameworkPaint().maskFilter =
            android.graphics.BlurMaskFilter(w * 0.020f, android.graphics.BlurMaskFilter.Blur.NORMAL)

        for (i in 0 until N) {
            val hh = maxBarH * heights[i].coerceIn(0f, 1f) * bloom(i)
            if (hh <= 0.5f) continue
            val x = i * slot + gap * 0.5f
            drawContext.canvas.drawRoundRect(
                x, baselineY - hh, x + barWidth, baselineY,
                radius, radius, glowPaint,
            )
        }

        // ── 主条: 渐变填充 + 轻微发光 ──
        barPaint.shader = shaderCache.bars(
            key = ShaderKey(cool.toArgb(), warm.toArgb(), baselineY.toInt(), (r * 64).toInt(), 2),
            build = {
                android.graphics.LinearGradient(
                    0f, gradTop, 0f, baselineY,
                    intArrayOf(
                        cool.copy(alpha = 0.92f * r).toArgb(),
                        warm.copy(alpha = 0.96f * r).toArgb(),
                    ),
                    floatArrayOf(0f, 1f),
                    android.graphics.Shader.TileMode.CLAMP,
                )
            },
        )
        barPaint.style = PaintingStyle.Fill
        barPaint.strokeCap = StrokeCap.Round
        barPaint.asFrameworkPaint().maskFilter =
            android.graphics.BlurMaskFilter(w * 0.006f, android.graphics.BlurMaskFilter.Blur.NORMAL)

        for (i in 0 until N) {
            val hh = maxBarH * heights[i].coerceIn(0f, 1f) * bloom(i)
            if (hh <= 0.5f) continue
            val x = i * slot + gap * 0.5f
            drawContext.canvas.drawRoundRect(
                x, baselineY - hh, x + barWidth, baselineY,
                radius, radius, barPaint,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════
// shader 缓存: key 相同则复用, 稳态零分配
// ═══════════════════════════════════════════════════════════

private class ShaderKey(val a: Int, val b: Int, val y: Int, val r: Int, val slot: Int) {
    override fun equals(other: Any?): Boolean =
        other is ShaderKey && other.a == a && other.b == b && other.y == y && other.r == r && other.slot == slot

    override fun hashCode(): Int = a * 31 + b * 7 + y + r * 13 + slot * 17
}

private class ShaderCache {
    private var barKey: ShaderKey? = null
    private var barShader: android.graphics.Shader? = null
    private var glowKey: ShaderKey? = null
    private var glowShader: android.graphics.Shader? = null
    private var reflKey: ShaderKey? = null
    private var reflShader: android.graphics.Shader? = null

    fun bars(key: ShaderKey, build: () -> android.graphics.Shader): android.graphics.Shader? {
        if (barKey != key || barShader == null) {
            barShader = build()
            barKey = key
        }
        return barShader
    }

    fun glow(key: ShaderKey, build: () -> android.graphics.Shader): android.graphics.Shader? {
        if (glowKey != key || glowShader == null) {
            glowShader = build()
            glowKey = key
        }
        return glowShader
    }

    fun reflection(key: ShaderKey, build: () -> android.graphics.Shader): android.graphics.Shader? {
        if (reflKey != key || reflShader == null) {
            reflShader = build()
            reflKey = key
        }
        return reflShader
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
