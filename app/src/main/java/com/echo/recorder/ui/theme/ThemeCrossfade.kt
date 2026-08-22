package com.echo.recorder.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.lerp

/**
 * 主题交叉淡化 —— 暗/亮切换时全 App 颜色在 ~400ms 内逐色过渡,
 * 消灭"整个界面瞬间变色"的硬切 (颜色状态可见变化的最后一块拼图).
 *
 * 原理: scheme 变化时捕获"上一帧实际显示的 scheme", 每帧 lerp 全部色彩槽位;
 * 动画被打断时从当前混合态继续, 不会跳变.
 */
@Composable
fun animatedColorScheme(target: ColorScheme): ColorScheme {
    val lastShown = remember { mutableStateOf<ColorScheme?>(null) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(target) {
        val from = lastShown.value
        if (from != null && from != target) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(400, easing = EchoMotion.Emphasized))
        }
    }

    val from = lastShown.value ?: target
    val t = progress.value
    val scheme = if (t >= 1f) target else lerpScheme(from, target, t)

    SideEffect { lastShown.value = scheme }
    return scheme
}

/** 逐槽位颜色插值 (copy 的默认参数会取 target 值, 因此全部显式 lerp). */
private fun lerpScheme(from: ColorScheme, to: ColorScheme, t: Float): ColorScheme = to.copy(
    primary = lerp(from.primary, to.primary, t),
    onPrimary = lerp(from.onPrimary, to.onPrimary, t),
    primaryContainer = lerp(from.primaryContainer, to.primaryContainer, t),
    onPrimaryContainer = lerp(from.onPrimaryContainer, to.onPrimaryContainer, t),
    inversePrimary = lerp(from.inversePrimary, to.inversePrimary, t),
    secondary = lerp(from.secondary, to.secondary, t),
    onSecondary = lerp(from.onSecondary, to.onSecondary, t),
    secondaryContainer = lerp(from.secondaryContainer, to.secondaryContainer, t),
    onSecondaryContainer = lerp(from.onSecondaryContainer, to.onSecondaryContainer, t),
    tertiary = lerp(from.tertiary, to.tertiary, t),
    onTertiary = lerp(from.onTertiary, to.onTertiary, t),
    tertiaryContainer = lerp(from.tertiaryContainer, to.tertiaryContainer, t),
    onTertiaryContainer = lerp(from.onTertiaryContainer, to.onTertiaryContainer, t),
    background = lerp(from.background, to.background, t),
    onBackground = lerp(from.onBackground, to.onBackground, t),
    surface = lerp(from.surface, to.surface, t),
    onSurface = lerp(from.onSurface, to.onSurface, t),
    surfaceVariant = lerp(from.surfaceVariant, to.surfaceVariant, t),
    onSurfaceVariant = lerp(from.onSurfaceVariant, to.onSurfaceVariant, t),
    surfaceTint = lerp(from.surfaceTint, to.surfaceTint, t),
    inverseSurface = lerp(from.inverseSurface, to.inverseSurface, t),
    inverseOnSurface = lerp(from.inverseOnSurface, to.inverseOnSurface, t),
    error = lerp(from.error, to.error, t),
    onError = lerp(from.onError, to.onError, t),
    errorContainer = lerp(from.errorContainer, to.errorContainer, t),
    onErrorContainer = lerp(from.onErrorContainer, to.onErrorContainer, t),
    outline = lerp(from.outline, to.outline, t),
    outlineVariant = lerp(from.outlineVariant, to.outlineVariant, t),
    scrim = lerp(from.scrim, to.scrim, t),
    surfaceBright = lerp(from.surfaceBright, to.surfaceBright, t),
    surfaceDim = lerp(from.surfaceDim, to.surfaceDim, t),
    surfaceContainer = lerp(from.surfaceContainer, to.surfaceContainer, t),
    surfaceContainerHigh = lerp(from.surfaceContainerHigh, to.surfaceContainerHigh, t),
    surfaceContainerHighest = lerp(from.surfaceContainerHighest, to.surfaceContainerHighest, t),
    surfaceContainerLow = lerp(from.surfaceContainerLow, to.surfaceContainerLow, t),
    surfaceContainerLowest = lerp(from.surfaceContainerLowest, to.surfaceContainerLowest, t),
)
