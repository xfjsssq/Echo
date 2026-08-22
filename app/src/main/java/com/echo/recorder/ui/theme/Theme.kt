package com.echo.recorder.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * Echo 主题配色 —— 呼应应用图标 (白色三花猫 + 暖橙斑 0xFFB366 + 亮蓝声波 0xFF4D8CFF).
 *
 * 明亮模式: 主色采用类似猫咪暖橙斑的 Mandarin 橙, 强调色使用亮蓝声波色.
 * 深色模式: 更柔和的 Amber/Mandarin 与 Sky 蓝.
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

// 中性色组 (深色) — 细分 surface 容器层级: 与背景同色相暖棕, 极小明度阶梯 (每级约 +3~5),
// 卡片微微上浮而不出现"贴上去的色块"; 避免冷灰与暖背景撞色
private val Neutral90 = Color(0xFFEFE0CF)
private val SurfaceDark = Color(0xFF16120A)
private val SurfaceContainerLowestDark = Color(0xFF100C05)
private val SurfaceContainerLowDark = Color(0xFF1A150D)
private val SurfaceContainerDark = Color(0xFF1F1910)
private val SurfaceContainerHighDark = Color(0xFF241D13)
private val SurfaceContainerHighestDark = Color(0xFF2A2216)
private val OnSurfaceDark = Color(0xFFEFE0CF)

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

// 深色配色方案
private val DarkColors = darkColorScheme(
    primary = Mandarin80,
    onPrimary = Color(0xFF4B3100),
    primaryContainer = Mandarin40,
    onPrimaryContainer = Mandarin90,
    inversePrimary = Mandarin40,
    secondary = Sky80,
    onSecondary = Color(0xFF003564),
    // 柔化冷蓝容器: 原值 0xFF004D96 过饱和, 与暖棕背景冷热对撞突兀;
    // 改为低饱和、略带暖灰倾向的深蓝, 既保留"声波蓝"语义又不割裂
    secondaryContainer = Color(0xFF23436A),
    onSecondaryContainer = Sky90,
    tertiary = Color(0xFFFFB68D),
    onTertiary = Color(0xFF552E1D),
    tertiaryContainer = Color(0xFF68441F),
    onTertiaryContainer = Color(0xFFFFDBC9),
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceContainerDark,
    onSurfaceVariant = Color(0xFFD3C5B6),
    surfaceTint = Mandarin80,
    surfaceContainerLowest = SurfaceContainerLowestDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark,
    inverseSurface = Neutral90,
    inverseOnSurface = Neutral10,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF978D82),
    outlineVariant = Color(0xFF4B453E),
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
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // 主题切换交叉淡化: 捕获旧 scheme 逐色过渡, 而非瞬间全屏变色
    val targetScheme = if (darkTheme) DarkColors else LightColors
    val animatedScheme = animatedColorScheme(targetScheme)
    MaterialTheme(
        colorScheme = animatedScheme,
        typography = AppTypography,
        content = content,
    )
}
