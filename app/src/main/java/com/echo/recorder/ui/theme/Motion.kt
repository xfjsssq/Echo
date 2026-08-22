package com.echo.recorder.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * Echo 全局动效 token —— 取自 Material 3 Expressive MotionScheme 的精确参数, 手写实现
 * (当前 material3 1.2.1 无官方 Expressive API, 数值与官方一致, 保证升级后观感连续).
 *
 * 统一规则:
 * - 入场: EmphasizedDecelerate (快起慢停, 内容"稳稳落位")
 * - 出场: EmphasizedAccelerate (慢起快走, 让位不拖沓)
 * - 主角位移/形变: fastSpatial 弹簧 (轻微过冲, 果冻感)
 * - 颜色/透明度: effects 弹簧 (阻尼 1.0, 绝不过冲, 避免色脏)
 * - 时长档位: 150 (微反馈) / 300 (标准过渡) / 450 (大区块)
 */
object EchoMotion {

    // ── 缓动曲线 (M3 Emphasized 家族) ──
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Standard = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    // ── 弹簧 (Expressive MotionScheme 精确值) ──
    /** 主角位移: 轻微过冲的果冻感 (按钮回弹/元素入场). */
    fun <T> fastSpatial(): SpringSpec<T> = spring(dampingRatio = 0.6f, stiffness = 800f)

    /** 一般位移: 柔和跟随, 几乎不过冲 (面板位置/尺寸). */
    fun <T> defaultSpatial(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)

    /** 大区块缓动位移 (整页级, 更慢更稳). */
    fun <T> slowSpatial(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 200f)

    /** 快速颜色/透明度: 立即响应, 无过冲. */
    fun <T> fastEffects(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 3800f)

    /** 标准颜色/透明度. */
    fun <T> defaultEffects(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 1600f)

    /** 慢速颜色/透明度 (大面积氛围色). */
    fun <T> slowEffects(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 800f)

    // ── 时长档位 ──
    const val DurationShort = 150
    const val DurationMedium = 300
    const val DurationLong = 450
}
