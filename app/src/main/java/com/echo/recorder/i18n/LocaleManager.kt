package com.echo.recorder.i18n

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * 语言切换工具.
 *
 * 由于本应用为纯 ComponentActivity + Compose, 不使用 AppCompatActivity,
 * 故无法用 AppCompatDelegate.setApplicationLocales.
 *
 * 关键实现: 通过 Activity.applyOverrideConfiguration(configuration) 把 locale 应用到
 * Activity 自身的 resources, 这样 Compose 的 stringResource() 才能感知语言切换.
 *
 * 注意: createConfigurationContext() 包装的上下文无法传播到 Compose 的 stringResource(),
 * 这是 Android 的已知限制, 必须走 applyOverrideConfiguration 路径.
 */
object LocaleManager {

    const val ZH = "zh"
    const val EN = "en"

    /** 用指定语言代码包装 Context. 供服务等非 Compose 场景使用. */
    fun wrap(context: Context, language: String?): Context {
        val locale = localeOf(language)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    /** 创建应用了目标语言的 Configuration, 供 Activity.applyOverrideConfiguration 使用. */
    fun configuration(context: Context, language: String?): Configuration {
        val locale = localeOf(language)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return config
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

    private fun localeOf(language: String?): Locale = when (language) {
        EN -> Locale.ENGLISH
        else -> Locale.CHINESE
    }
}
