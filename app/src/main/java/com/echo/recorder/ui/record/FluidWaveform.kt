package com.echo.recorder.ui.record

import android.graphics.Bitmap
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.luminance
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Gemini Live 风格音频能量幕 (V8 — 涟漪正弦柱状图).
 *
 * 依据头脑风暴建议重写 (相比 V7 极光柔幕):
 * 1. **合成涟漪波为主, 音频只调制能量** — 用 (1+sin(x))/2 合成单自然波峰,
 *    永远有起伏: 安静时也有波峰波谷灵动流动, 说话时只是整体更饱满 + 中心脉冲,
 *    不再是"全低都下去、全高都上来"的规矩跟随. 双波对向传播形成干涉涟漪,
 *    打破匀速规律感.
 * 2. **20~30 根圆角柱, 左右对称** — 22 根, 相位左半随机右半镜像, 中心高两侧低
 *    的山丘包络, 柱顶透明渐变到底部实色发光, 带柔和光晕.
 * 3. **颜色突出** — 明亮主题与背景混色只取 15%, 底部 alpha 0.88, 橙色鲜明;
 *    暗黑主题混 45% 保持柔和.
 * 4. **快攻慢放缓动** — 每根柱子独立向目标逼近, 液体拖尾感.
 * 5. **噪点质感** — 波形区域叠加固定 seed 白色颗粒, 边缘过渡平滑不塑料.
 * 6. **白 → 主题色过渡** — reveal 0→1 时高度从 0 升起, 颜色先白后主题色.
 *
 * @param amplitude 实时感知振幅 0.0-1.0
 * @param waveColor 波形主色 (推荐主题 primary)
 * @param reveal 出现进度 0→1
 * @param backgroundColor 所在区域背景色, 用于混合柔化, 消除割裂感
 */
@Composable
fun FluidWaveform(
    amplitude: Float,
    waveColor: Color,
    reveal: Float = 1f,
    backgroundColor: Color = Color.White,
    modifier: Modifier = Modifier,
) {
    // 柱子数量: 20~30 区间取 22, 左右对称
    val N = 22

    // 每根柱子的当前相对高度 (跨帧缓动 → 液体拖尾)
    val heights = remember { FloatArray(N) { 0f } }
    // 镜像相位: 左半随机, 右半对称 → 左右对称又不齐整的灵动涟漪
    val phases = remember {
        val a = FloatArray(N) { Random.nextFloat() * 2f * PI.toFloat() }
        for (i in 0 until N) {
            val mirror = N - 1 - i
            if (mirror > i) a[mirror] = a[i]
        }
        a
    }
    // 静态有机基形 (对称微扰): 让柱子高度不齐整, 有生命力
    val baseShape = remember {
        FloatArray(N) { i ->
            val xa = abs((i / (N - 1f)) * 2f - 1f)
            1f + 0.05f * sin(xa * 5.2f) + 0.04f * sin(xa * 9.7f + 1.2f)
        }
    }
    // 平滑包络 (快攻慢放)
    val smoothAmp = remember { mutableFloatStateOf(0f) }
    var time by remember { mutableFloatStateOf(0f) }

    // 噪点纹理 (固定 seed, 只生成一次; 白色随机颗粒用于边缘质感)
    val noiseBitmap = remember {
        val bmp = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val rnd = Random(0xE0C0A11)
        for (y in 0 until 128) {
            for (x in 0 until 128) {
                val a = (rnd.nextFloat() * 40).toInt() // 0..39
                bmp.setPixel(x, y, android.graphics.Color.argb(a, 255, 255, 255))
            }
        }
        bmp.asImageBitmap()
    }
    val noisePaint = remember {
        Paint().apply {
            shader = ImageShader(noiseBitmap, TileMode.Repeated, TileMode.Repeated)
            alpha = 0.30f
        }
    }

    // 始终读取最新参数
    val currentAmplitude by rememberUpdatedState(amplitude)
    val currentReveal by rememberUpdatedState(reveal)
    val currentBg by rememberUpdatedState(backgroundColor)

    // 明亮主题: 混色少 → 颜色突出; 暗黑主题: 混色多 → 柔和
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // ── 逐帧更新: 合成涟漪为主, 音频只调制能量 ──
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { }
            time += 0.028f

            val target = currentAmplitude.coerceIn(0f, 1f)
            val sa = smoothAmp.floatValue
            smoothAmp.floatValue = if (target > sa) {
                sa + (target - sa) * 0.50f
            } else {
                sa + (target - sa) * 0.10f
            }
            val amp = smoothAmp.floatValue

            for (i in 0 until N) {
                val x = (i / (N - 1f)) * 2f - 1f // -1..1
                val xa = abs(x)
                // 中心包络: 中间高两侧低 (山丘主脊)
                val center = (1f - 0.45f * xa * xa).coerceAtLeast(0.10f)
                // 合成涟漪 (1+sin)/2 → 单自然波峰; 双波对向传播 → 灵动干涉
                val wave1 = 0.5f + 0.5f * sin(xa * 3.4f + time * 1.5f + phases[i])
                val wave2 = 0.5f + 0.5f * sin(xa * 2.1f - time * 2.3f + phases[i] * 1.7f)
                val ripple = wave1 * 0.60f + wave2 * 0.40f
                // 音频只调制能量与中心脉冲: 全低时也有波峰, 全高时不整体升平
                val energy = 0.60f + 0.55f * amp
                val centerPulse = 1f + 0.30f * amp * (1f - xa).coerceAtLeast(0f)
                val targetH = ((0.30f + 0.70f * ripple) * center * baseShape[i] * energy * centerPulse)
                    .coerceIn(0.04f, 1.05f)
                val cur = heights[i]
                heights[i] = if (targetH > cur) {
                    cur + (targetH - cur) * 0.32f // 快攻: 活泼
                } else {
                    cur + (targetH - cur) * 0.12f // 慢放: 液体拖尾
                }
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val r = currentReveal.coerceIn(0f, 1f)

        // 白 → 主题色 (前 25% 保持纯白衔接白球消散)
        val cr = ((r - 0.25f) / 0.75f).coerceIn(0f, 1f)
        val base = lerp(Color.White, waveColor, cr)
        // 与背景混合: 明亮主题少混 → 颜色突出; 暗黑主题多混 → 柔和
        val mix = if (isDark) 0.55f else 0.85f
        val solid = lerp(currentBg, base, mix)
        val topStop = solid.copy(alpha = if (isDark) 0.08f else 0.12f)
        val midStop = solid.copy(alpha = if (isDark) 0.28f else 0.50f)
        val baseStop = solid.copy(alpha = if (isDark) 0.55f else 0.88f)
        val glowAlpha = if (isDark) 0.08f else 0.12f

        val maxH = h * 0.80f
        val baseY = h // 底边贴容器底部 → 填满底部, 无空隙
        val step = w / N
        val barW = step * 0.58f
        val corner = barW * 0.38f
        val glowW = barW * 1.8f

        for (i in 0 until N) {
            val hh = maxH * heights[i].coerceIn(0f, 1.05f) * r
            if (hh < 1f) continue
            val cx = step * (i + 0.5f)
            val top = baseY - hh

            // 光晕层: 宽 1.8x 淡色圆角 → 柱子像发光体
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color.Transparent,
                        1.0f to solid.copy(alpha = glowAlpha * r),
                    ),
                    startY = top,
                    endY = baseY,
                ),
                topLeft = Offset(cx - glowW / 2f, top),
                size = Size(glowW, hh),
                cornerRadius = CornerRadius(glowW / 2f, glowW / 2f),
            )

            // 主体: 顶透明 → 底实色 渐变圆角柱
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color.Transparent,
                        0.45f to topStop.copy(alpha = topStop.alpha * r),
                        0.75f to midStop.copy(alpha = midStop.alpha * r),
                        1.0f to baseStop.copy(alpha = baseStop.alpha * r),
                    ),
                    startY = top,
                    endY = baseY,
                ),
                topLeft = Offset(cx - barW / 2f, top),
                size = Size(barW, hh),
                cornerRadius = CornerRadius(corner, corner),
            )
        }

        // 极淡噪点: 增质感, 让渐变过渡更平滑 (保留极光颗粒感)
        drawContext.canvas.drawRect(0f, 0f, w, h, noisePaint)
    }
}

/**
 * 全屏噪点覆盖层 — 给整个页面加一层极淡的颗粒, 让大色块过渡更温和.
 *
 * 固定 seed 静态纹理, 每帧只平铺一次, 无动画开销.
 *
 * @param alpha 整体颗粒强度 (0.0-0.2 建议, 默认 0.08 温和可见)
 */
@Composable
fun GrainOverlay(modifier: Modifier = Modifier, alpha: Float = 0.08f) {
    val noiseBitmap = remember {
        val bmp = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val rnd = Random(0xA11CE)
        for (y in 0 until 128) {
            for (x in 0 until 128) {
                val a = (rnd.nextFloat() * 70).toInt() // 0..69
                bmp.setPixel(x, y, android.graphics.Color.argb(a, 255, 255, 255))
            }
        }
        bmp.asImageBitmap()
    }
    val paint = remember(alpha) {
        Paint().apply {
            shader = ImageShader(noiseBitmap, TileMode.Repeated, TileMode.Repeated)
            this.alpha = alpha
        }
    }
    Canvas(modifier = modifier.fillMaxSize()) {
        drawContext.canvas.drawRect(0f, 0f, size.width, size.height, paint)
    }
}
