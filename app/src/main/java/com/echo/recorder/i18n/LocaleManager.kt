package com.echo.recorder.i18n

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * 语言切换工具.
 *
 * 由于本应用为纯 ComponentActivity + Compose, 不使用 AppCompatActivity,
 * 故无法用 AppCompatDelegate.setApplicationLocales. 改为在 MainActivity.attachBaseContext
 * 中用保存的 locale 包装 Context, 切换语言后 recreate() 生效.
 */
object LocaleManager {

    const val ZH = "zh"
    const val EN = "en"

    /** 用指定语言代码包装 Context. null 则跟随系统. */
    fun wrap(context: Context, language: String?): Context {
        val locale = when (language) {
            EN -> Locale.ENGLISH
            else -> Locale.CHINESE
        }
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    /** 当前生效的语言代码. */
    fun current(language: String?): String = when (language) {
        EN -> EN
        else -> ZH
    }

    /** 切换语言后重建 Activity 以生效, 禁用切换动画防闪烁. */
    fun recreate(activity: Activity) {
        activity.recreate()
        activity.overridePendingTransition(0, 0)
    }
}
