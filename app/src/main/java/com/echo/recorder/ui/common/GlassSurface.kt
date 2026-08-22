package com.echo.recorder.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Shape
import kotlin.random.Random

/**
 * 玻璃浮层 —— 手写多层"玻璃质感" (零新依赖, 全 API 一致):
 * 1. 半透明暖调底色 (上方略染 primaryContainer, 模拟顶光透射)
 * 2. 1px 环形高光描边 (上强下弱, 光从上方来)
 * 3. 细噪点 (玻璃的磨砂颗粒)
 * 4. 柔影 + 暖色 spot 光 (悬浮感)
 *
 * 供浮层使用: 批量操作栏 / 迷你播放器 / 对话框容器等.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val scheme = MaterialThemeColorScheme
    val isDark = scheme.background.luminance() < 0.5f
    val noise = remember { NoiseBrush(makeNoiseBitmap(seed = 0x6A55)) }

    // 底色: 暖纸底略染容器色, 上明下暗 (光自上方)
    val top = lerp(scheme.surface, scheme.primaryContainer, 0.05f).copy(alpha = 0.90f)
    val bottom = scheme.surface.copy(alpha = 0.80f)
    // 环形高光: 顶部强, 向下衰减到接近无
    val rim = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = if (isDark) 0.14f else 0.30f),
            Color.White.copy(alpha = 0.03f),
        ),
    )

    Box(
        modifier
            .shadow(
                elevation = 10.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.28f),
                spotColor = scheme.primary.copy(alpha = 0.28f),
            )
            .clip(shape)
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .border(1.dp, rim, shape)
            .drawWithContent {
                drawContent()
                drawRect(brush = noise, alpha = 0.05f)
            },
    ) {
        content()
    }
}

// ── 噪点实现 (与 FluidWaveform 同款手法, 提为公共组件) ──

private val MaterialThemeColorScheme
    @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme

/** 生成一张细颗粒噪点位图 (一次性, remember 持有). */
private fun makeNoiseBitmap(seed: Int, size: Int = 96): ImageBitmap {
    val rnd = Random(seed)
    val native = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(native)
    val paint = android.graphics.Paint().apply { color = android.graphics.Color.WHITE }
    for (y in 0 until size) {
        for (x in 0 until size) {
            paint.alpha = rnd.nextInt(42)
            canvas.drawPoint(x.toFloat(), y.toFloat(), paint)
        }
    }
    return native.asImageBitmap()
}

/** 平铺噪点笔刷 — 覆盖任意大小区域. */
private class NoiseBrush(private val bitmap: ImageBitmap) : ShaderBrush() {
    override fun createShader(size: Size): Shader =
        ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated)
}
