package com.echo.recorder.ui.record

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echo.recorder.R
import com.echo.recorder.service.AudioAmplitudeMonitor
import com.echo.recorder.service.RecordingService
import com.echo.recorder.settings.SettingsRepository
import com.echo.recorder.ui.common.FeatheredPillButton
import com.echo.recorder.ui.common.LoadingPulse
import com.echo.recorder.ui.common.echoPressScale
import com.echo.recorder.ui.common.rememberEchoHaptics
import com.echo.recorder.ui.fmtTime
import com.echo.recorder.ui.lock.PasswordPromptDialog
import com.echo.recorder.ui.theme.EchoMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/** Logo 下落动画阶段. */
private enum class AnimPhase {
    IDLE,
    /** Logo 物理下落: 变白球 → 加速 → 拉长 → 残影 → 摊开消失. */
    FALLING,
    /** 流体能量区域激活. */
    VISUALIZER,
}

/** 重力加速缓动 (ease-in, 模拟自由落体). */
private val GravityEasing = CubicBezierEasing(0.32f, 0f, 0.67f, 0f)

/** Logo 飞回缓动 (easeOutExpo 类: 起飞快, 到中心急停). */
private val LogoReturnEasing = CubicBezierEasing(0.12f, 0.9f, 0.28f, 1f)

/**
 * 录音页 — Gemini Live 风格.
 *
 * 交互时间线:
 *  IDLE     → 全屏渐变 + 猫咪 Logo 呼吸 + 轻触开始
 *  BUFFERING → Logo 变白球→加速下落→拉长→残影→摊开消失 → 流体能量区域出现
 *  REVIEW   → 保存/删除
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(
    viewModel: RecordViewModel,
    onRequestPermission: () -> Unit,
    onOpenList: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onExit: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val amplitude by AudioAmplitudeMonitor.amplitude.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val screenHeightPx = with(density) {
        LocalConfiguration.current.screenHeightDp.dp.toPx()
    }
    val screenWidthPx = with(density) {
        LocalConfiguration.current.screenWidthDp.dp.toPx()
    }

    var verifyDelete by remember { mutableStateOf(false) }
    val haptics = rememberEchoHaptics()

    // ── 动画状态 ──
    var animPhase by remember { mutableStateOf(AnimPhase.IDLE) }
    val dropProgress = remember { Animatable(0f) } // 0→1 下落进度
    val waveReveal = remember { Animatable(0f) }   // 0→1 波形展开
    val logoReturn = remember { Animatable(0f) }   // 0→1 logo 飞回进度

    LaunchedEffect(state.phase) {
        when (state.phase) {
            RecordingService.Phase.BUFFERING -> {
                AudioAmplitudeMonitor.start()
                // 阶段 1: Logo 物理下落, 直到彻底摊开消失 (900ms, 重力加速)
                animPhase = AnimPhase.FALLING
                dropProgress.snapTo(0f)
                dropProgress.animateTo(1f, tween(900, easing = GravityEasing))
                // 阶段 2: logo 彻底消失后, 音频条才开始出现 (极光幕布展开)
                animPhase = AnimPhase.VISUALIZER
                waveReveal.snapTo(0f)
                waveReveal.animateTo(1f, tween(800, easing = LinearOutSlowInEasing))
                // 阶段 3: 音频条彻底出现后, logo 从左边屏幕外飞回中心
                // (速度曲线 → 急停变形 → 停稳后呼吸浮动, 点击即暂停)
                logoReturn.snapTo(0f)
                logoReturn.animateTo(1f, tween(760, easing = LogoReturnEasing))
            }
            else -> {
                AudioAmplitudeMonitor.stop()
                animPhase = AnimPhase.IDLE
                dropProgress.snapTo(0f)
                waveReveal.snapTo(0f)
                logoReturn.snapTo(0f)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { AudioAmplitudeMonitor.stop() }
    }

    // ── 删除密码门控 ──
    if (verifyDelete) {
        val context = LocalContext.current
        val settings = remember { SettingsRepository(context) }
        val storedHash by produceState<String?>(initialValue = null) {
            value = settings.passwordHash.first()
        }
        if (storedHash != null) {
            PasswordPromptDialog(
                storedHash = storedHash,
                recoveryHash = null,
                onVerify = {
                    verifyDelete = false
                    viewModel.onDeletePressed()
                },
                onDismiss = { verifyDelete = false },
            )
        } else {
            LaunchedEffect(verifyDelete) {
                if (verifyDelete) { viewModel.onDeletePressed(); verifyDelete = false }
            }
        }
    }

    // ── 冷启动恢复对话框 ──
    state.pendingRecovery?.let { rec ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.recovery_title), fontWeight = FontWeight.SemiBold) },
            text = { Text(stringResource(R.string.recovery_text, fmtTime(rec.createdAt))) },
            confirmButton = {
                TextButton(onClick = { viewModel.recoverKeep() }) {
                    Text(stringResource(R.string.keep), fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.recoverDiscard() }) {
                    Text(
                        stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
        )
    }

    // 暗黑主题判定 (提前计算: 用于背景渐变 / logo / 按钮配色)
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // ── 根布局: 全屏渐变背景 (通顶通底) ──
    // 暗黑: 极淡暖琥珀光自顶部缓慢溶解进背景, 全程无硬边 (消除"色块分界线");
    //       用 primary(暖琥珀) 而非 primaryContainer(浊金), 避免暗色下泛橄榄灰.
    // 明亮: 暖黄容器色自上而下柔和过渡 (三段递减 alpha, 不再留平直 surface 段造成台阶).
    val glowColor = if (isDark) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.primaryContainer

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    if (isDark) listOf(
                        glowColor.copy(alpha = 0.14f),
                        glowColor.copy(alpha = 0.07f),
                        glowColor.copy(alpha = 0.025f),
                        MaterialTheme.colorScheme.surface,
                    ) else listOf(
                        glowColor.copy(alpha = 0.42f),
                        glowColor.copy(alpha = 0.18f),
                        glowColor.copy(alpha = 0.05f),
                        MaterialTheme.colorScheme.surface,
                    ),
                ),
            ),
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            // 清空内容区系统栏 inset: 音频光带要贴到屏幕物理底边 (TopAppBar 自带状态栏处理)
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.app_name),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        )
                    },
                    actions = {
                        // 列表入口 (主操作, 品牌色大图标) + 设置 — 大触区 + 按压缩放
                        // (锁屏按钮已移除: 切后台/冷启动会自动上锁, 无需手动入口)
                        IconButton(
                            onClick = onOpenList,
                            modifier = Modifier.size(46.dp).echoPressScale(0.88f),
                        ) {
                            Icon(
                                Icons.Filled.List,
                                contentDescription = stringResource(R.string.record_list),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.size(46.dp).echoPressScale(0.88f),
                        ) {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = stringResource(R.string.settings),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                // 阶段切换交叉淡化 (IDLE ⇄ BUFFERING ⇄ REVIEW), 轻微缩放避免生硬
                AnimatedContent(
                    targetState = state.phase,
                    transitionSpec = {
                        (fadeIn(tween(300, easing = FastOutSlowInEasing)) +
                            scaleIn(initialScale = 0.985f, animationSpec = tween(300, easing = FastOutSlowInEasing))) togetherWith
                            fadeOut(tween(220))
                    },
                    label = "record_phase",
                ) { phase ->
                    when (phase) {
                        RecordingService.Phase.IDLE -> IdleContent(
                            hasPermission = state.hasPermission,
                            darkTheme = isDark,
                            onStart = {
                                if (!state.hasPermission) onRequestPermission()
                                else {
                                    haptics.confirm()
                                    viewModel.onStartPressed()
                                }
                            },
                        )
                        RecordingService.Phase.BUFFERING -> BufferingContent(
                            amplitude = amplitude,
                            animPhase = animPhase,
                            dropProgress = dropProgress.value,
                            waveReveal = waveReveal.value,
                            logoReturn = logoReturn.value,
                            screenWidthPx = screenWidthPx,
                            screenHeightPx = screenHeightPx,
                            onPause = remember(haptics, viewModel) {
                                {
                                    haptics.confirm()
                                    viewModel.onPausePressed()
                                }
                            },
                            saving = state.saving,
                        )
                        RecordingService.Phase.REVIEW -> ReviewContent(
                            darkTheme = isDark,
                            onSave = {
                                haptics.confirm()
                                viewModel.onSavePressed()
                            },
                            onDelete = {
                                haptics.reject()
                                verifyDelete = true
                            },
                        )
                    }
                }

                ExitButton(
                    onExit = onExit,
                    // inset 已清零 (光带贴底), 底部垫高避让系统手势区
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 28.dp),
                )
            }
        }

        // 全屏噪点: 让整页大色块过渡更温和 (置于最上层, 极淡颗粒)
        GrainOverlay(alpha = 0.08f)
    }
}

// ═══════════════════════════════════════════════════════════
// IDLE: 居中 Logo + 呼吸动画 + 羽化光晕
// ═══════════════════════════════════════════════════════════

@Composable
private fun IdleContent(hasPermission: Boolean, darkTheme: Boolean, onStart: () -> Unit) {
    val infinite = rememberInfiniteTransition(label = "breathe")
    val breathScale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath_scale",
    )
    // 提示文字极缓呼吸 (0.75↔1.0), 让静止页也有生命感
    val hintAlpha by infinite.animateFloat(
        initialValue = 0.75f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "hint_alpha",
    )

    // 开始键点击反馈: 无 ripple (消除点击时矩形黑底), 用整体缩放模拟"圆形"按压.
    // 点击判定范围不变 (仍是整个 200dp 区域), 只是视觉反馈变圆.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(150),
        label = "idle_press",
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            if (hasPermission) stringResource(R.string.ready_tap_to_start)
            else stringResource(R.string.mic_permission_needed),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            modifier = Modifier.alpha(hintAlpha),
        )
        Spacer(Modifier.height(36.dp))

        Box(
            modifier = Modifier
                .size(200.dp)
                .clickable(
                    interactionSource = interaction,
                    indication = null, // 无 ripple → 无矩形黑底
                    onClick = onStart,
                )
                .scale(pressScale),
            contentAlignment = Alignment.Center,
        ) {
            // 光晕: 暗黑主题用暖琥珀光晕包裹 logo (桥接亮 logo 与暗背景, 避免白块突兀);
            //       明亮主题保持主色光晕
            val haloColor = MaterialTheme.colorScheme.primary
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            listOf(
                                haloColor.copy(alpha = if (darkTheme) 0.16f else 0.20f),
                                haloColor.copy(alpha = if (darkTheme) 0.06f else 0.06f),
                                Color.Transparent,
                            ),
                        ),
                        CircleShape,
                    ),
            )
            // Logo: 明亮主题用阴影做过渡; 暗黑主题边缘径向渐隐 + 暖色叠加,
            //       让原本为亮色设计的白猫融入暖琥珀基调, 不再是"贴上去的白块"
            val warmTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
            val logoModifier = if (darkTheme) {
                Modifier
                    .size(160.dp)
                    .scale(breathScale)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        // 暖色叠加: 把白色猫身染成暖琥珀, 与橙斑/主题统一 (默认 SrcOver,
                        // warmTint 带 0.22 alpha, 对猫身像素做半透明暖色染色; 后续 DstIn 羽化只保留圆形区域)
                        drawRect(warmTint)
                        // 边缘径向羽化: 融化进背景, 无硬边
                        drawRect(
                            Brush.radialGradient(
                                listOf(Color.Black, Color.Black, Color.Transparent),
                                center = center,
                                radius = minOf(size.width, size.height) * 0.58f,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                    .clip(CircleShape)
            } else {
                Modifier
                    .size(160.dp)
                    .scale(breathScale)
                    .shadow(16.dp, CircleShape)
                    .clip(CircleShape)
            }
            Image(
                painter = painterResource(R.drawable.ic_echo_logo),
                contentDescription = stringResource(R.string.start),
                modifier = logoModifier,
                contentScale = ContentScale.Crop,
            )
        }

        Spacer(Modifier.height(28.dp))
        Text(
            if (hasPermission) stringResource(R.string.start_recording)
            else stringResource(R.string.mic_permission_needed_short),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            modifier = Modifier.alpha(hintAlpha),
        )
    }
}

// ═══════════════════════════════════════════════════════════
// BUFFERING: Logo 物理下落 → 流体能量区域
// ═══════════════════════════════════════════════════════════

@Composable
private fun BufferingContent(
    amplitude: Float,
    animPhase: AnimPhase,
    dropProgress: Float,
    waveReveal: Float,
    logoReturn: Float,
    screenWidthPx: Float,
    screenHeightPx: Float,
    onPause: () -> Unit,
    saving: Boolean,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (saving) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                LoadingPulse(dotSize = 10.dp)
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.saving),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                )
            }
        } else {
            // ── 音频能量光带 (Logo 消失后自底部涌起, 曲面闭合到屏幕物理底边) ──
            if (animPhase == AnimPhase.VISUALIZER) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.30f)
                        .align(Alignment.BottomCenter)
                        .graphicsLayer {
                            alpha = waveReveal
                        },
                ) {
                    SpectrumBars(
                        amplitude = amplitude,
                        waveColor = MaterialTheme.colorScheme.primary,
                        // 与背景混合柔化, 消除纯色块的割裂感
                        backgroundColor = MaterialTheme.colorScheme.surface,
                        reveal = waveReveal,
                    )
                }

                // ── Logo 即暂停键: 音频条彻底出现后从左边屏幕外飞回中心, 点击暂停 ──
                // 振幅经 State 传入: 绘制阶段直读, 不触发本组件 83Hz 重组 (掉帧根因)
                val ampState = rememberUpdatedState(amplitude)
                ReturningLogo(
                    progress = logoReturn,
                    screenWidthPx = screenWidthPx,
                    ampState = ampState,
                    onClick = onPause,
                )
            }

            // ── Logo 物理下落 (与波形重叠: 下落摊开消散的末期, 白色 blob 已开始晕开) ──
            if (dropProgress > 0f && dropProgress < 1f) {
                FallingLogo(progress = dropProgress, screenHeightPx = screenHeightPx)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// Logo 物理下落: 变白球 → 加速 → 拉长 → 残影 → 摊开消失
// ═══════════════════════════════════════════════════════════

@Composable
private fun FallingLogo(progress: Float, screenHeightPx: Float) {
    val p = progress

    // ── 物理属性 (全部由 progress 数学驱动) ──
    // 二次方下落 = 重力加速
    val fallY = screenHeightPx * 0.45f * p * p
    // 球缩小
    val ballScale = 1f - p * 0.45f
    // 变白 (极速变白: p=0.45 前完全变白, 猫图细节不再滞留 → 消除深色残留)
    val whiteAmount = (p * 2.2f).coerceAtMost(1f)
    // 下落速度越快 → 拉长越多 (垂直拉伸, 水平压缩)
    val velocity = p * p // 模拟瞬时速度
    val stretchY = 1f + velocity * 0.9f
    val stretchX = 1f - velocity * 0.35f
    // 末尾摊开 (最后 15% 进度)
    val splatT = ((p - 0.85f) / 0.15f).coerceIn(0f, 1f)
    val splatX = 1f + splatT * splatT * 3.5f
    val splatY = 1f - splatT * 0.85f
    // 整体透明度 (摊开时消失)
    val ballAlpha = (1f - splatT).coerceAtLeast(0f)

    val ballSizeDp = (160 * ballScale).dp

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        // ── 残影 (3 个幽灵球, 在主球后方) ──
        for (i in 1..3) {
            val tp = (p - i * 0.06f).coerceAtLeast(0f)
            if (tp <= 0f) continue
            val ghostFall = screenHeightPx * 0.45f * tp * tp
            val ghostWhite = (tp * 1.3f).coerceAtMost(1f)
            val ghostVel = tp * tp
            val ghostScale = (1f - tp * 0.45f) * (1f - i * 0.08f)
            val ghostAlpha = (1f - tp * tp) * 0.22f * (1f - i * 0.25f) * ballAlpha

            Box(
                modifier = Modifier
                    .offset { IntOffset(0, ghostFall.roundToInt()) }
                    .size((160 * ghostScale).dp)
                    .graphicsLayer {
                        scaleX = 1f - ghostVel * 0.35f
                        scaleY = 1f + ghostVel * 0.9f
                        alpha = ghostAlpha
                    }
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = ghostWhite)),
            )
        }

        // ── 主球体 (边缘径向羽化, 软球体而不是硬圆片) ──
        Box(
            modifier = Modifier
                .offset { IntOffset(0, fallY.roundToInt()) }
                .size(ballSizeDp)
                .graphicsLayer {
                    scaleX = stretchX * splatX
                    scaleY = stretchY * splatY
                    alpha = ballAlpha
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.radialGradient(
                            listOf(Color.Black, Color.Black, Color.Transparent),
                            center = center,
                            radius = minOf(size.width, size.height) * 0.62f,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            // 猫咪原图 (随白度增加而淡出)
            if (whiteAmount < 1f) {
                Image(
                    painter = painterResource(R.drawable.ic_echo_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(1f - whiteAmount),
                    contentScale = ContentScale.Crop,
                )
            }
            // 白球覆盖层 (随白度增加而显现)
            if (whiteAmount > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = whiteAmount)),
                )
                // 白球外发光
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    Color.White.copy(alpha = whiteAmount * 0.4f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
            }
        }

        // ── 摊开扩散圈 (末尾) ──
        if (splatT > 0f) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(0, fallY.roundToInt()) }
                    .size((160 * ballScale * (1f + splatT * 2f)).dp)
                    .graphicsLayer {
                        alpha = splatT * 0.3f
                    }
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f)),
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════
// Logo 飞回: 从左边屏幕外 → 中心 (速度曲线 → 急停变形 → 呼吸浮动)
// ═══════════════════════════════════════════════════════════

@Composable
private fun ReturningLogo(
    progress: Float,
    screenWidthPx: Float,
    ampState: State<Float>,
    onClick: () -> Unit,
) {
    val p = progress.coerceIn(0f, 1f)

    // 按压果冻感: 按下快速收缩, 松手弹性回弹
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = if (pressed) EchoMotion.fastEffects() else EchoMotion.fastSpatial(),
        label = "pause_press",
    )

    // 飞行位置: p=0 → 屏幕左外; p=1 → 屏幕中心 (缓动已由动画驱动, 起飞快/到点急停)
    val xOffset = screenWidthPx * (p - 1f)
    // 剩余速度感: 越快水平拉得越长
    val vel = (1f - p)
    // 急停果冻形变: 到达瞬间压扁 → 阻尼回弹 → 停稳
    val impact = ((p - 0.86f) / 0.14f).coerceIn(0f, 1f)
    val jelly = sin(impact * PI.toFloat() * 2f) * exp(-impact * 3.2f)
    val deformX = 1f + 0.10f * vel + 0.14f * jelly
    val deformY = 1f - 0.08f * vel - 0.14f * jelly

    // 停稳后: 与开始键一致的呼吸浮动
    val infinite = rememberInfiniteTransition(label = "return_breathe")
    val breathScale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "return_breathe_scale",
    )
    val floatScale = if (p >= 1f) breathScale else 1f
    // 前 1/3 淡入 (滑入时避免生硬弹现)
    val fadeAlpha = (p * 3f).coerceAtMost(1f)

    // 光晕笔刷只创建一次; 强度在绘制层随振幅调制 → 不产生每帧分配
    val haloColor = MaterialTheme.colorScheme.primary
    val haloBrush = remember(haloColor) {
        Brush.radialGradient(listOf(haloColor, Color.Transparent))
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .offset { IntOffset(xOffset.roundToInt(), 0) }
                .size(160.dp)
                .graphicsLayer {
                    scaleX = deformX * floatScale * pressScale
                    scaleY = deformY * floatScale * pressScale
                    alpha = fadeAlpha
                }
                // 光晕: 绘制阶段读振幅 (仅失效重绘, 不重组) — 它也是一枚随声音呼吸的能量球
                .drawBehind {
                    drawCircle(
                        brush = haloBrush,
                        radius = size.minDimension * 0.62f,
                        center = center,
                        alpha = 0.20f + 0.14f * ampState.value,
                    )
                }
                .clip(CircleShape)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Logo 本体: 径向羽化遮罩 → 边缘融化进背景 (发光球体质感, 无硬边)
            Image(
                painter = painterResource(R.drawable.ic_echo_logo),
                contentDescription = stringResource(R.string.pause),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            Brush.radialGradient(
                                listOf(Color.Black, Color.Black, Color.Transparent),
                                center = center,
                                radius = minOf(size.width, size.height) * 0.62f,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════
// REVIEW: 保存 / 删除
// ═══════════════════════════════════════════════════════════

@Composable
private fun ReviewContent(darkTheme: Boolean, onSave: () -> Unit, onDelete: () -> Unit) {
    // 删除键: 淡粉色调 (用户要求"淡一点, 粉红一点"), 暗黑主题下用更亮的粉色
    val deleteColor = if (darkTheme) Color(0xFFE85D7F) else Color(0xFFF48FB1)
    val deleteGlow = if (darkTheme) Color(0xFFFF7A9C) else Color(0xFFFFB3C6)
    val saveColor = MaterialTheme.colorScheme.primary
    val saveGlow = MaterialTheme.colorScheme.primary

    // 错峰弹入: 保存先落位, 删除 80ms 后跟上 (弹簧轻微过冲, 而非同时弹出)
    val appearSave = remember { Animatable(0f) }
    val appearDelete = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appearSave.animateTo(1f, EchoMotion.fastSpatial()) }
    LaunchedEffect(Unit) {
        delay(80)
        appearDelete.animateTo(1f, EchoMotion.fastSpatial())
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.review_title),
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 48.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            Box(
                modifier = Modifier.graphicsLayer {
                    val a = appearSave.value
                    scaleX = 0.6f + 0.4f * a
                    scaleY = 0.6f + 0.4f * a
                    alpha = a.coerceIn(0f, 1f)
                },
            ) {
                FeatheredPillButton(
                    icon = Icons.Filled.Save,
                    label = stringResource(R.string.save),
                    color = saveColor,
                    glowColor = saveGlow,
                    onClick = onSave,
                )
            }
            Box(
                modifier = Modifier.graphicsLayer {
                    val a = appearDelete.value
                    scaleX = 0.6f + 0.4f * a
                    scaleY = 0.6f + 0.4f * a
                    alpha = a.coerceIn(0f, 1f)
                },
            ) {
                FeatheredPillButton(
                    icon = Icons.Filled.Delete,
                    label = stringResource(R.string.delete),
                    color = deleteColor,
                    glowColor = deleteGlow,
                    onClick = onDelete,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// 退出按钮 (含密码门控)
// ═══════════════════════════════════════════════════════════

@Composable
private fun ExitButton(onExit: () -> Unit, modifier: Modifier = Modifier) {
    var askConfirm by remember { mutableStateOf(false) }
    var askPassword by remember { mutableStateOf(false) }

    TextButton(onClick = { askConfirm = true }, modifier = modifier) {
        Icon(
            Icons.Filled.ExitToApp,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(6.dp))
        Text(
            stringResource(R.string.exit_stop_recording),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
    }

    if (askConfirm) {
        AlertDialog(
            onDismissRequest = { askConfirm = false },
            title = { Text(stringResource(R.string.exit_confirm_title), fontWeight = FontWeight.SemiBold) },
            text = { Text(stringResource(R.string.exit_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    askConfirm = false
                    askPassword = true
                }) { Text(stringResource(R.string.exit_confirm_button), fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { askConfirm = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (askPassword) {
        val context = LocalContext.current
        val settings = remember { SettingsRepository(context) }
        val storedHash by produceState<String?>(initialValue = null) {
            value = settings.passwordHash.first()
        }
        if (storedHash != null) {
            PasswordPromptDialog(
                storedHash = storedHash,
                recoveryHash = null,
                onVerify = {
                    askPassword = false
                    onExit()
                },
                onDismiss = { askPassword = false },
            )
        } else {
            LaunchedEffect(askPassword) {
                if (askPassword) { onExit(); askPassword = false }
            }
        }
    }
}