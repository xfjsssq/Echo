package com.echo.recorder.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * Echo 主题配色 —— 呼应应用图标 (白色三花猫 + 暖橙斑 0xFFB366 + 亮蓝声波 0xFF4D8CFF).
 *
 * 恒为明亮模式: 主色采用类似猫咪暖橙斑的 Mandarin 橙, 强调色使用亮蓝声波色.
 * (暗黑模式已移除)
 */

// 暖橙 (猫咪斑纹) —— 主色
private val Mandarin30 = Color(0xFF7D5800)
private val Mandarin40 = Color(0xFF9A6B00)
private val Mandarin80 = Color(0xFFFDCE5C)
private val Mandarin90 = Color(0xFFFFE1A0)
private val MandarinPrimary = Color(0xFFF59E0B) // amber-500, 与斑纹暖橙呼应

// 亮蓝 (声波) —— 强调色
private val Sky40 = Color(0xFF4D8CFF)
private val SkyPrimary = Color(0xFF4D8CFF)
private val Sky80 = Color(0xFF9DBDFF)
private val Sky90 = Color(0xFFD6E2FF)

// 中性色组 (明亮) — 细分 surface 容器层级, 让卡片/底栏与背景之间是柔和的明度阶梯而非硬边界
private val Neutral10 = Color(0xFF201B12)
private val Neutral99 = Color(0xFFFFF8F1)
private val SurfaceLight = Color(0xFFFFF8F1)
private val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
private val SurfaceContainerLowLight = Color(0xFFFEFAF3)
private val SurfaceContainerLight = Color(0xFFFFF1DF)
private val SurfaceContainerHighLight = Color(0xFFFDE8CE)
private val SurfaceContainerHighestLight = Color(0xFFFBE0BC)
private val OnSurfaceLight = Color(0xFF201B12)
private val OutLineLight = Color(0xFF7D7570)

private val ErrorColor = Color(0xFFB3261E)

// 明亮配色方案
private val LightColors = lightColorScheme(
    primary = MandarinPrimary,
    onPrimary = Color.White,
    primaryContainer = Mandarin90,
    onPrimaryContainer = Mandarin30,
    inversePrimary = Mandarin80,
    secondary = Sky40,
    onSecondary = Color.White,
    secondaryContainer = Sky90,
    onSecondaryContainer = Color(0xFF0D2A6E),
    tertiary = Color(0xFFEF8F5A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDBC9),
    onTertiaryContainer = Color(0xFF412115),
    background = SurfaceLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceContainerLight,
    onSurfaceVariant = Color(0xFF4C4548),
    surfaceTint = MandarinPrimary,
    surfaceContainerLowest = SurfaceContainerLowestLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight,
    inverseSurface = Neutral10,
    inverseOnSurface = Neutral99,
    error = ErrorColor,
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410002),
    outline = OutLineLight,
    outlineVariant = Color(0xFFD0C4B3),
    scrim = Color(0xFF000000),
)

// 应用级排版: 略加大标题字重, 更现代清爽
private val AppTypography = Typography(
    headlineLarge = androidx.compose.ui.text.TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = androidx.compose.ui.text.TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 34.sp,
    ),
    titleLarge = androidx.compose.ui.text.TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = androidx.compose.ui.text.TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = androidx.compose.ui.text.TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = androidx.compose.ui.text.TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = androidx.compose.ui.text.TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
)

@Composable
fun EchoTheme(
    content: @Composable () -> Unit,
) {
    // 恒为明亮主题 (暗黑模式已按用户要求移除 — 双套容器色适配成本高, 亮色打磨到极致)
    MaterialTheme(
        colorScheme = LightColors,
        typography = AppTypography,
        content = content,
    )
}
