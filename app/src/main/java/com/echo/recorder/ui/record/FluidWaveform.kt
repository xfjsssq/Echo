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
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.random.Random

/**
 * Gemini Live 风格音频能量幕 (V15 — 漂浮光带 + 镜面倒影).
 *
 * 在 V13 架构 (形态层/能量层解耦 + 动态中间值对比度响应) 之上全面整改视觉语言:
 * 不再"水位线填满", 改为一条随能量呼吸的**漂浮光带**:
 * 1. **漂浮光带** — 波形以圆头渐变描边呈现 (底部暖黄 → 顶部冷蓝),
 *    线宽随能量 2.2dp→7.7dp 呼吸, 悬浮于容器中下部, 不贴底不顶天.
 * 2. **镜面倒影** — 光带下方镜像一条淡出的倒影 (纵向渐变 + 模糊),
 *    像立在镜面水面上, 增加纵深与质感.
 * 3. **光源泛光** — 光带外侧大半径柔光, 顶部无硬边.
 * 4. 保留: 三峰独立随机游走、动态中间值对称拉伸、快攻快放、噪点、reveal.
 *
 * @param amplitude 实时感知振幅 0.0-1.0
 * @param waveColor 兼容保留 (颜色已由黄蓝渐变取代, 不再使用)
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

    // 泛光 / 光带 / 倒影 用画刷 (每帧只换 shader, 复用对象避免 GC 抖动)
    val glowPaint = remember { Paint() }
    val corePaint = remember { Paint() }
    val reflPaint = remember { Paint() }

    // 始终读取最新参数
    val currentAmplitude by rememberUpdatedState(amplitude)
    val currentReveal by rememberUpdatedState(reveal)
    val currentBg by rememberUpdatedState(backgroundColor)

    // 明亮主题: 混色少 → 颜色突出; 暗黑主题: 混色多 → 柔和
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // 引导卡片同款黄蓝渐变端点 (主题容器色, 需在组合作用域读取)
    val warmTheme = MaterialTheme.colorScheme.primaryContainer   // 暖黄
    val coolTheme = MaterialTheme.colorScheme.secondaryContainer // 冷蓝

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
                // 双软膝: 顶部渐近无硬线; 底部软地板 — 波谷永远悬在底边之上, 不触底
                val kneed = if (stretched > 0.85f) {
                    0.85f + (stretched - 0.85f) * 0.28f
                } else if (stretched < 0.28f) {
                    0.28f + (stretched - 0.28f) * 0.35f
                } else {
                    stretched
                }
                val targetH = kneed.coerceIn(0.16f, 1.1f)

                val cur = heights[i]
                heights[i] = cur + (targetH - cur) * 0.45f
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val r = currentReveal.coerceIn(0f, 1f)

        // 白 → 引导卡片同款黄蓝渐变 (前 25% 保持纯白衔接白球消散)
        val cr = ((r - 0.25f) / 0.75f).coerceIn(0f, 1f)
        val warmBase = lerp(Color.White, warmTheme, cr)   // 暖黄端点
        val coolBase = lerp(Color.White, coolTheme, cr)   // 冷蓝端点
        // 与背景混合: 明亮主题少混 → 颜色突出; 暗黑主题多混 → 柔和
        val mix = if (isDark) 0.55f else 0.85f
        val warm = lerp(currentBg, warmBase, mix)   // 底部暖黄
        val cool = lerp(currentBg, coolBase, mix)   // 顶部冷蓝

        // ── 漂浮光带布局: 基线在 0.52h, 峰顶最高 0.06h, 下方留出倒影区 ──
        val baseY = h * 0.52f
        val maxH = h * 0.46f
        val step = w / (N - 1f)   // 全宽铺满: 首顶点 x=0, 末顶点 x=w

        // 顶点: 覆盖整个宽度, 顶 y 由缓动高度决定
        val pts = List(N) { i ->
            val hh = maxH * heights[i].coerceIn(0f, 1.1f) * r
            Offset(step * i, baseY - hh)
        }

        // Catmull-Rom → 三次贝塞尔: 顶点间 C1 连续, 曲线圆滑无折角
        fun buildPath(p: List<Offset>): Path {
            val path = Path()
            path.moveTo(p[0].x, p[0].y)
            for (i in 0 until N - 1) {
                val p0 = p[(i - 1).coerceAtLeast(0)]
                val p1 = p[i]
                val p2 = p[i + 1]
                val p3 = p[(i + 2).coerceAtMost(N - 1)]
                val c1 = Offset(p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f)
                val c2 = Offset(p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f)
                path.cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
            }
            return path
        }
        val path = buildPath(pts)

        // 镜面倒影: 关于基线镜像 (0.85 系数收敛, 倒影略短于本体, 不溢出底边)
        val reflPts = pts.map { pt ->
            val dy = baseY - pt.y
            Offset(pt.x, baseY + dy * 0.85f)
        }
        val reflPath = buildPath(reflPts)

        // 能量包络 (驱动线宽/泛光强度, 也保持 Canvas 的逐帧订阅)
        val env = smoothAmp.floatValue

        // 渐变上界在可能最高峰之上 → 顶部永远先淡出, 不会露出硬边
        val gradTop = baseY - maxH * 1.15f

        // 线宽: 随能量呼吸 (2.2dp 静默 → 7.7dp 强声)
        val coreWidth = (2.2f + 5.5f * env).dp.toPx()

        // ── 1) 镜面倒影: 下方镜像 + 纵向渐变淡出 + 模糊 ──
        reflPaint.shader = android.graphics.LinearGradient(
            0f, baseY, 0f, h,
            intArrayOf(
                lerp(warm, cool, 0.5f).copy(alpha = 0f).toArgb(),
                warm.copy(alpha = (if (isDark) 0.10f else 0.16f) * r).toArgb(),
            ),
            floatArrayOf(0f, 1f),
            android.graphics.Shader.TileMode.CLAMP,
        )
        reflPaint.style = PaintingStyle.Stroke
        reflPaint.strokeWidth = coreWidth * 0.85f
        reflPaint.strokeCap = StrokeCap.Round
        reflPaint.asFrameworkPaint().maskFilter =
            android.graphics.BlurMaskFilter(w * 0.030f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        drawContext.canvas.drawPath(reflPath, reflPaint)

        // ── 2) 光源泛光: 宽描边大模糊 (光带像发光体) ──
        glowPaint.shader = android.graphics.LinearGradient(
            0f, gradTop, 0f, baseY,
            intArrayOf(
                cool.copy(alpha = 0.16f * r).toArgb(),
                warm.copy(alpha = 0.30f * r).toArgb(),
            ),
            floatArrayOf(0f, 1f),
            android.graphics.Shader.TileMode.CLAMP,
        )
        glowPaint.style = PaintingStyle.Stroke
        glowPaint.strokeWidth = coreWidth * 3.4f
        glowPaint.strokeCap = StrokeCap.Round
        glowPaint.asFrameworkPaint().maskFilter =
            android.graphics.BlurMaskFilter(w * 0.045f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        drawContext.canvas.drawPath(path, glowPaint)

        // ── 3) 主光带: 渐变圆头描边 (底部暖黄 → 顶部冷蓝) ──
        corePaint.shader = android.graphics.LinearGradient(
            0f, gradTop, 0f, baseY,
            intArrayOf(
                cool.copy(alpha = 0.90f * r).toArgb(),
                warm.copy(alpha = 0.95f * r).toArgb(),
            ),
            floatArrayOf(0f, 1f),
            android.graphics.Shader.TileMode.CLAMP,
        )
        corePaint.style = PaintingStyle.Stroke
        corePaint.strokeWidth = coreWidth
        corePaint.strokeCap = StrokeCap.Round
        corePaint.strokeJoin = StrokeJoin.Round
        corePaint.asFrameworkPaint().maskFilter =
            android.graphics.BlurMaskFilter(w * 0.010f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        drawContext.canvas.drawPath(path, corePaint)

        // ── 4) 极淡噪点: 增质感, 让渐变过渡更平滑 (保留极光颗粒感) ──
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
