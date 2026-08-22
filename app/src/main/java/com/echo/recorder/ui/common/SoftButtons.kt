package com.echo.recorder.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echo.recorder.ui.theme.EchoMotion

/**
 * 实色药丸按钮 — 完整包住图标+文字:
 * - 主体: 圆角药丸, 实色背景完整包裹内容 (不再做边缘羽化, 避免裁掉文字)
 * - 背景: 同色系柔和径向光晕, 让按钮像"悬浮的光"
 * - 按压: 0.96 缩放反馈 (可中断, 松手平滑回弹)
 * - 光学对齐: 图标侧内边距比文字侧少 2dp
 */
@Composable
fun FeatheredPillButton(
    icon: ImageVector,
    label: String,
    color: Color,
    glowColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = Color.White,
    labelColor: Color = Color.White,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(150),
        label = "pill_press",
    )

    Box(modifier = modifier.scale(pressScale), contentAlignment = Alignment.Center) {
        // 柔和光晕 (径向渐变, 向外扩散消失)
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 2.dp, vertical = 6.dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            glowColor.copy(alpha = 0.32f),
                            glowColor.copy(alpha = 0.10f),
                            Color.Transparent,
                        ),
                    ),
                    RoundedCornerShape(50),
                ),
        )
        // 实色药丸主体 — 完整包住图标+文字 (去掉径向 DstIn 羽化, 避免边缘裁掉内容)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(color)
                .clickableNoRipple(interaction, onClick)
                // 光学对齐: 图标侧 (左) 内边距 = 文字侧 (右) - 2dp
                .padding(start = 20.dp, end = 24.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            Text(
                label,
                color = labelColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
        }
    }
}

/**
 * 羽化圆形图标按钮 — 用于列表播放/操作按钮 / 录音页暂停按钮:
 * - 中心实色圆, 边缘径向 DstIn 渐隐 (发光球体质感)
 * - 后方带同色系柔和光晕
 * - 按压 0.96 缩放反馈
 */
@Composable
fun FeatheredOrbIcon(
    icon: ImageVector,
    color: Color,
    glowColor: Color,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = Color.White,
    buttonSize: Dp = 44.dp,
    iconSize: Dp = 22.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(150),
        label = "orb_press",
    )

    Box(modifier = modifier.size(buttonSize).scale(pressScale), contentAlignment = Alignment.Center) {
        // 光晕 (柔和, 不抢注意力)
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        listOf(
                            glowColor.copy(alpha = 0.32f),
                            glowColor.copy(alpha = 0.10f),
                            Color.Transparent,
                        ),
                    ),
                    CircleShape,
                ),
        )
        // 羽化球体: 中心实色, 边缘渐隐
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.radialGradient(
                            listOf(Color.Black, Color.Black, Color.Transparent),
                            center = center,
                            radius = minOf(size.width, size.height) * 0.60f,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
                .clip(CircleShape)
                .background(color)
                .clickableNoRipple(interaction, onClick),
            contentAlignment = Alignment.Center,
        ) {
            // 图标切换弹簧变形: 播放⇄暂停等状态翻转时缩放+旋转过场, 不再瞬切
            AnimatedContent(
                targetState = icon,
                transitionSpec = {
                    (scaleIn(
                        initialScale = 0.5f,
                        animationSpec = EchoMotion.fastSpatial(),
                    ) + fadeIn(tween(120))) togetherWith
                        (scaleOut(
                            targetScale = 0.5f,
                            animationSpec = EchoMotion.fastEffects(),
                        ) + fadeOut(tween(80)))
                },
                label = "orb_icon",
            ) { currentIcon ->
                Icon(currentIcon, contentDescription = contentDescription, tint = iconTint, modifier = Modifier.size(iconSize))
            }
        }
    }
}

/** 无涟漪点击 (按压反馈由外层 0.96 缩放提供, 避免双层反馈). */
private fun Modifier.clickableNoRipple(
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
): Modifier = this.then(
    Modifier.clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick,
    ),
)
