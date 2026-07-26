package com.echo.recorder.ui.theme

import android.app.Activity
import android.content.Context
import android.content.res.Configuration

/**
 * 主题模式.
 *
 * 仅保留明亮和黑暗两种. 旧版本"跟随系统"已删除, 统一回退为明亮.
 */
enum class ThemeMode { LIGHT, DARK }

/**
 * 主题管理工具.
 *
 * 把散落在 MainActivity / SettingsScreen / OnboardingScreen 三处的主题计算、
 * 系统深色判断、Activity 重建逻辑收拢到单一对象，避免状态冗余。
 */
object ThemeManager {

    /** 解析 ThemeMode → 是否深色 (纯函数, 可单测). */
    fun resolveDarkTheme(mode: ThemeMode, isSystemDark: Boolean): Boolean = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    /** 当前系统是否深色. */
    fun isSystemDark(context: Context): Boolean {
        val uiMode = context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK
        return uiMode == Configuration.UI_MODE_NIGHT_YES
    }

    /**
     * 重建 Activity 并禁用切换动画.
     *
     * 对应 next-themes 的 disableTransitionOnChange：防止主题切换时
     * Activity 重建的默认过渡动画造成闪烁。
     *
     * 注意：仅用于语言切换等必须重建的场景. 主题切换应由 Compose 状态驱动, 无需重建.
     */
    fun recreateWithDisabledTransition(activity: Activity) {
        activity.recreate()
        activity.overridePendingTransition(0, 0)
    }
}
