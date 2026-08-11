package com.echo.recorder.ui.navigation

/** 路由常量. */
object EchoRoutes {
    const val RECORD = "record"
    const val LIST = "list"
    const val SETTINGS = "settings"
    const val PASSWORD_SETUP = "password_setup?isChangePassword={isChangePassword}"
    const val PASSWORD_SETUP_IS_CHANGE = "isChangePassword"
    const val PUBLIC_DIR = "public_dir"
    const val ABOUT = "about"
    const val ONBOARDING = "onboarding"

    /** 密码设置路由 (isChangePassword=true 表示修改密码: 跳过类型选择与恢复密钥展示). */
    fun passwordSetupRoute(isChangePassword: Boolean): String =
        "password_setup?isChangePassword=$isChangePassword"
}
