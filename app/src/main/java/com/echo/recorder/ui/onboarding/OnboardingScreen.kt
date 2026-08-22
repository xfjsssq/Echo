package com.echo.recorder.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.echo.recorder.R
import com.echo.recorder.ui.common.EntranceAnimation
import com.echo.recorder.ui.common.echoPressScale
import com.echo.recorder.ui.common.rememberEchoHaptics
import com.echo.recorder.ui.theme.EchoMotion
import com.echo.recorder.ui.theme.ThemeMode

/** 引导卡片类型. (主题选择卡片已随暗黑主题移除 — 应用恒为明亮主题) */
private enum class OnboardingCardType {
    /** 普通卡片: 标题 + 文本 + 下一步/完成按钮. */
    PLAIN,
}

private data class OnboardingCard(
    val type: OnboardingCardType,
    val titleRes: Int,
    val textRes: Int,
    val actionRes: Int? = null,
)

private val CARDS = listOf(
    OnboardingCard(OnboardingCardType.PLAIN, R.string.onboarding1_title, R.string.onboarding1_text),
    OnboardingCard(OnboardingCardType.PLAIN, R.string.onboarding2_title, R.string.onboarding2_text),
    OnboardingCard(OnboardingCardType.PLAIN, R.string.onboarding3_title, R.string.onboarding3_text),
    // 公共目录备份: 永远默认开启, 卡片仅告知功能, 不提供开关/按钮, 不显示路径.
    OnboardingCard(OnboardingCardType.PLAIN, R.string.onboarding4_title, R.string.onboarding4_text),
    OnboardingCard(OnboardingCardType.PLAIN, R.string.onboarding5_title, R.string.onboarding5_text),
)

/** 卡片类型 → 展示图标. */
private fun cardIcon(type: OnboardingCardType): ImageVector = when (type) {
    OnboardingCardType.PLAIN -> Icons.Filled.Mic
}

/**
 * 首次启动引导卡片 (居中卡片样式, 半透明遮罩).
 *
 * @param onFinish 引导完成回调
 * @param onSelectTheme 用户在引导中选择主题
 */
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
) {
    var index by remember { mutableIntStateOf(0) }
    val haptics = rememberEchoHaptics()

    Dialog(
        onDismissRequest = { /* 引导卡片通过右上角 × 或完成按钮关闭 */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            // 卡片入场: 淡入+上浮落位 (Dialog 无法用 AnimatedContent 过渡出现)
            EntranceAnimation(rise = 20.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .background(
                        MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(24.dp),
                    )
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 顶部: 右上角 × 跳过.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(
                        onClick = onFinish,
                        modifier = Modifier
                            .size(32.dp)
                            .echoPressScale(0.9f),
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.skip),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // 卡片主体: 徽章 + 圆点 + 内容 — 整幅滑入/滑出 (零交叠, 不再"文字叠文字")
                AnimatedContent(
                    targetState = index,
                    transitionSpec = {
                        if (targetState > initialState) {
                            // 前进: 新卡从右侧屏外整幅滑入
                            (fadeIn(tween(300, easing = EchoMotion.EmphasizedDecelerate)) +
                                slideInHorizontally(animationSpec = tween(300, easing = EchoMotion.EmphasizedDecelerate)) { it }) togetherWith
                                (fadeOut(tween(200, easing = EchoMotion.EmphasizedAccelerate)) +
                                    slideOutHorizontally(animationSpec = tween(200, easing = EchoMotion.EmphasizedAccelerate)) { -it })
                        } else {
                            // 后退: 新卡从左侧屏外整幅滑入
                            (fadeIn(tween(300, easing = EchoMotion.EmphasizedDecelerate)) +
                                slideInHorizontally(animationSpec = tween(300, easing = EchoMotion.EmphasizedDecelerate)) { -it }) togetherWith
                                (fadeOut(tween(200, easing = EchoMotion.EmphasizedAccelerate)) +
                                    slideOutHorizontally(animationSpec = tween(200, easing = EchoMotion.EmphasizedAccelerate)) { it })
                        }
                    },
                    label = "onboarding_card",
                ) { i ->
                    val card = CARDS[i]

                    // 图标徽章: 圆形渐变小徽章 (独立分区, 与文字彻底分离, 不可能重叠)
                    val icon: ImageVector = cardIcon(card.type)
                    val badgeBreath = rememberInfiniteTransition(label = "badge_breath")
                    val badgeScale by badgeBreath.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.04f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2400, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "badge_scale",
                    )
                    Box(
                        modifier = Modifier.size(128.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        // 渐变底 + 顶部高光, 猫咪图标的暖橙→声波蓝
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .scale(badgeScale)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            MaterialTheme.colorScheme.secondaryContainer,
                                        )
                                    )
                                ),
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            modifier = Modifier.size(88.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(44.dp),
                                )
                            }
                        }
                    }

                    // 页面指示圆点: 尺寸/颜色弹簧过渡 (不再瞬跳).
                    Spacer(Modifier.height(20.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        repeat(CARDS.size) { dot ->
                            val active = dot == i
                            val dotSize by animateDpAsState(
                                targetValue = if (active) 8.dp else 6.dp,
                                animationSpec = EchoMotion.fastSpatial(),
                                label = "dot_size",
                            )
                            val dotColor by animateColorAsState(
                                targetValue = if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                                animationSpec = EchoMotion.fastEffects(),
                                label = "dot_color",
                            )
                            Box(
                                modifier = Modifier
                                    .size(dotSize)
                                    .background(dotColor, CircleShape),
                            )
                        }
                    }

                    // 内容区 (标题/正文与徽章明确分区, 充足间距).
                    when (card.type) {
                        else -> PlainCardContent(
                            card = card,
                        )
                    }
                }

                // 底部导航: 上一步 / 下一步 / 完成 (带触感反馈).
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (index > 0) {
                        TextButton(
                            onClick = {
                                haptics.tick()
                                index--
                            },
                            modifier = Modifier.echoPressScale(0.96f),
                        ) {
                            Text(stringResource(R.string.previous))
                        }
                    }
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                        if (index < CARDS.lastIndex) {
                            Button(
                                onClick = {
                                    haptics.tick()
                                    index++
                                },
                                modifier = Modifier.echoPressScale(0.96f),
                            ) {
                                Text(stringResource(R.string.onboarding_next))
                            }
                        } else {
                            Button(
                                onClick = {
                                    haptics.confirm()
                                    onFinish()
                                },
                                modifier = Modifier.echoPressScale(0.96f),
                            ) {
                                Text(stringResource(R.string.onboarding_finish))
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun PlainCardContent(
    card: OnboardingCard,
) {
    Text(
        stringResource(card.titleRes),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 24.dp),
    )
    Text(
        stringResource(card.textRes),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 12.dp),
    )
}
