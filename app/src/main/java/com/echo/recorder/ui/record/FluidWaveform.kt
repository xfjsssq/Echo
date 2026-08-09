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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.random.Random

/**
 * Gemini Live 风格音频能量幕 (V13 — 三峰独立随机游走 + 对比度响应).
 *
 * 从第一性原理重构 (不再调参):
 * 1. **形态层 / 能量层彻底解耦** — 形态由 3 个高斯峰的幅度/宽度/位置
 *    独立随机游走驱动 (互不相关、永不重复, 严格 ≤3 波峰); 音频只通过
 *    "对比度"通道影响形态, 不直接推顶点 → 架构上杜绝齐上齐下.
 * 2. **动态中间值对称拉伸** — 每帧算当前高度中位数, 围绕它对称拉伸:
 *    峰更高、谷更低, 中间值本身不动 → 高者上冲低者下压, 差距夸张.
 * 3. **快攻快放 + 灵敏度压缩** — 包络 attack 0.75 / release 0.50,
 *    振幅 ^0.6 压缩 → 小音量灵敏、大音量不炸, 响应即时不慵懒.
 * 4. **静止灵动** — env=0 时形态本身持续演化, 微呼吸不呆板.
 * 5. 保留: 光源泛光、顶部无分界线、左右填满、噪点、reveal、颜色混比.
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
    // 22 个顶点 = 三峰平滑轮廓的采样点 (曲线穿过全部顶点, 全宽铺满)
    val N = 22

    // 每个顶点的当前相对高度 (跨帧缓动 → 液体拖尾)
    val heights = remember { FloatArray(N) { 0f } }
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

    // 泛光用画刷 (每帧只换 shader, 复用对象避免 GC 抖动)
    val glowPaint = remember { Paint() }
    val corePaint = remember { Paint() }

    // 始终读取最新参数
    val currentAmplitude by rememberUpdatedState(amplitude)
    val currentReveal by rememberUpdatedState(reveal)
    val currentBg by rememberUpdatedState(backgroundColor)

    // 明亮主题: 混色少 → 颜色突出; 暗黑主题: 混色多 → 柔和
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // ── 逐帧更新: 三峰形状 + 动态中间值差分音频响应 ──
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { }
            time += 0.028f

            val target = currentAmplitude.coerceIn(0f, 1f)
            // 灵敏度压缩: 小音量放大 (^0.6), 大音量柔和 → 反馈明显但不炸
            val perceived = target.pow(0.6f)
            val sa = smoothAmp.floatValue
            smoothAmp.floatValue = if (perceived > sa) {
                sa + (perceived - sa) * 0.75f   // 快攻: 声音一来立刻响应
            } else {
                sa + (perceived - sa) * 0.50f   // 快放: 一停很快回落
            }
            val env = smoothAmp.floatValue

            // 从左向右缓慢滚动: 完整一轮约 40 秒 (time*0.024), 慢而持续
            val scroll = time * 0.024f

            // 三个高斯峰: 幅度/宽度/位置各自独立随机游走 (互不相关、永不重复),
            // 中心缓慢右移 → 随机感 + 滚动感 + 严格 ≤3 波峰
            val shape = FloatArray(N)
            for (i in 0 until N) {
                val xa = i / (N - 1f) // 0..1, 全宽铺满
                var s = 0f
                for (k in 0 until 3) {
                    val seed = 31 + k * 97
                    val ampK = 0.45f + 0.40f * valueNoise(seed.toFloat(), time * 0.30f)
                    val wK = 0.085f + 0.040f * valueNoise(seed + 11f, time * 0.26f)
                    val cK = scroll + k * 0.335f + 0.07f * valueNoise(seed + 23f, time * 0.20f)
                    s += ampK * gauss(xa, cK, wK)
                }
                shape[i] = s
            }

            // 动态中间值: 当前帧高度的中位数 (随音高自适应, 非定值)
            val sorted = shape.copyOf().apply { sort() }
            val mid = (sorted[N / 2 - 1] + sorted[N / 2]) * 0.5f

            // 对比度响应: 围绕当前中间值对称拉伸 → 峰更高、谷更低;
            // 中间值本身不动 → 绝不整体平移、绝不齐上齐下
            val contrast = 1f + 2.8f * env

            for (i in 0 until N) {
                val stretched = mid + (shape[i] - mid) * contrast
                // 软膝: 顶部渐近无硬线; 底部保底不贴死
                val kneed = if (stretched > 0.85f) 0.85f + (stretched - 0.85f) * 0.28f else stretched
                val targetH = kneed.coerceIn(0.02f, 1.25f)

                val cur = heights[i]
                heights[i] = cur + (targetH - cur) * 0.45f
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

        // 渐变上界在可能最高峰之上 → 顶部永远先淡出, 不会露出硬边
        val maxH = h * 0.56f
        val gradTop = h - maxH * 1.25f
        val baseY = h // 底边贴容器底部 → 填满底部, 无空隙
        val step = w / (N - 1f)   // 全宽铺满: 首顶点 x=0, 末顶点 x=w

        // 顶点: 覆盖整个宽度, 顶 y 由缓动高度决定
        val pts = List(N) { i ->
            val hh = maxH * heights[i].coerceIn(0f, 1.25f) * r
            Offset(step * i, baseY - hh)
        }

        // 连续轮廓: 左缘 → 平滑曲线穿过全部顶点 → 右缘 → 底边闭合
        // Catmull-Rom → 三次贝塞尔: 顶点间 C1 连续, 曲线圆滑无折角
        val path = Path()
        path.moveTo(0f, baseY)
        path.lineTo(pts[0].x, pts[0].y)
        for (i in 0 until N - 1) {
            val p0 = pts[(i - 1).coerceAtLeast(0)]
            val p1 = pts[i]
            val p2 = pts[i + 1]
            val p3 = pts[(i + 2).coerceAtMost(N - 1)]
            val c1 = Offset(p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f)
            val c2 = Offset(p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f)
            path.cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
        }
        path.lineTo(w, baseY)
        path.close()

        // ── 光源式泛光: 波形像发光体, 上方也有光亮 ──

        // 1) 大范围柔光 (强模糊) — 主泛光, 盖掉顶部任何边界
        glowPaint.shader = android.graphics.LinearGradient(
            0f, gradTop, 0f, baseY,
            intArrayOf(
                Color.Transparent.toArgb(),
                solid.copy(alpha = 0.30f * r).toArgb(),
                solid.copy(alpha = 0.45f * r).toArgb(),
            ),
            floatArrayOf(0f, 0.5f, 1f),
            android.graphics.Shader.TileMode.CLAMP,
        )
        glowPaint.asFrameworkPaint().maskFilter =
            android.graphics.BlurMaskFilter(w * 0.050f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        drawContext.canvas.drawPath(path, glowPaint)

        // 2) 紧凑光芯 (小模糊) — 波形本体有发光感
        corePaint.shader = android.graphics.LinearGradient(
            0f, gradTop, 0f, baseY,
            intArrayOf(
                Color.Transparent.toArgb(),
                solid.copy(alpha = 0.22f * r).toArgb(),
                solid.copy(alpha = 0.50f * r).toArgb(),
            ),
            floatArrayOf(0f, 0.6f, 1f),
            android.graphics.Shader.TileMode.CLAMP,
        )
        corePaint.asFrameworkPaint().maskFilter =
            android.graphics.BlurMaskFilter(w * 0.015f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        drawContext.canvas.drawPath(path, corePaint)

        // 3) 主体: 清晰渐变填充 — 顶部在最高峰之上淡出, 底部实色
        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to Color.Transparent,
                    0.28f to solid.copy(alpha = 0.22f * r),
                    0.70f to solid.copy(alpha = 0.55f * r),
                    1.0f to solid.copy(alpha = (if (isDark) 0.55f else 0.88f) * r),
                ),
                startY = gradTop,
                endY = baseY,
            ),
        )

        // 4) 极淡噪点: 增质感, 让渐变过渡更平滑 (保留极光颗粒感)
        drawContext.canvas.drawRect(0f, 0f, w, h, noisePaint)
    }
}

/**
 * 环面高斯峰: center 可越过左右边界循环滚动; 距离按环面最短路径,
 * 波峰从右边消失会从左边重新出现, 形成持续的左→右滚动.
 */
private fun gauss(xa: Float, center: Float, width: Float): Float {
    var c = center % 1f
    if (c < 0f) c += 1f
    var d = abs(xa - c)
    if (d > 0.5f) d = 1f - d
    return exp(-(d * d) / (2f * width * width))
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

// ═══════════════════════════════════════════════════════════
// 值噪声 (Value Noise): 顶点维度 + 时间维度 → 有机、不重复、不对称
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
    // 双线性插值 (Float 版 lerp 手写, 避免 graphics 包没有 Float 重载)
    val a = hashNoise(xi, yi) + (hashNoise(xi + 1, yi) - hashNoise(xi, yi)) * u
    val b = hashNoise(xi, yi + 1) + (hashNoise(xi + 1, yi + 1) - hashNoise(xi, yi + 1)) * u
    return a + (b - a) * v
}
