package com.echo.recorder.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.echo.recorder.ui.theme.EchoMotion

/** 步骤前进方向 (决定滑入滑出的方向). */
enum class StepDirection { Forward, Backward }

/**
 * 方向感知的步骤过渡 —— 供 Tab 内容/对话框多步流程/向导复用,
 * 替代裸 `when` 硬切 ("没有任何东西凭空出现或消失").
 *
 * 前进: 新内容从右侧屏外整幅滑入; 旧内容向左滑出屏外 — 全程零交叠.
 * (v1 滑距仅 1/8 屏宽, 过渡期间新旧两份内容在 90% 区域交叠 ~300ms,
 * 即用户看到的"文字堆叠", 已根治)
 * 尺寸变化不裁剪 (SizeTransform(clip = false)), 高度切换自然.
 */
@Composable
fun <T> AnimatedStep(
    targetState: T,
    direction: StepDirection = StepDirection.Forward,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedContentScope.(T) -> Unit,
) {
    val dir = if (direction == StepDirection.Forward) 1 else -1
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            val enter = fadeIn(tween(200, easing = EchoMotion.EmphasizedDecelerate)) +
                slideInHorizontally(tween(EchoMotion.DurationMedium, easing = EchoMotion.EmphasizedDecelerate)) { it * dir }
            val exit = fadeOut(tween(150, easing = EchoMotion.EmphasizedAccelerate)) +
                slideOutHorizontally(tween(200, easing = EchoMotion.EmphasizedAccelerate)) { -it * dir }
            (enter togetherWith exit).using(SizeTransform(clip = false))
        },
        label = "animated_step",
    ) { content(it) }
}

/**
 * 无方向语义的模式切换过渡 (锁屏模式/加载⇄内容等):
 * 交叉淡化 + 微缩放, 旧内容静止淡出、新内容 0.97→1 落位, 平稳不滑动.
 */
@Composable
fun <T> AnimatedMode(
    targetState: T,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedContentScope.(T) -> Unit,
) {
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            val enter = fadeIn(tween(220, easing = EchoMotion.EmphasizedDecelerate)) +
                scaleIn(initialScale = 0.97f, animationSpec = tween(EchoMotion.DurationMedium, easing = EchoMotion.EmphasizedDecelerate))
            val exit = fadeOut(tween(150, easing = EchoMotion.EmphasizedAccelerate)) +
                scaleOut(targetScale = 1.02f, animationSpec = tween(150, easing = EchoMotion.EmphasizedAccelerate))
            (enter togetherWith exit).using(SizeTransform(clip = false))
        },
        label = "animated_mode",
    ) { content(it) }
}
