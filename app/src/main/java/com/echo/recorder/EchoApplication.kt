package com.echo.recorder

import android.app.Application
import android.content.Context

class EchoApplication : Application() {
    companion object {
        lateinit var context: Context
            private set
    }

    override fun onCreate() {
        super.onCreate()
        context = applicationContext
    }
}

/** 全局 Application Context 访问, 供 ServiceLocator / ViewModel 默认参数使用. */
fun appContext(): Context = EchoApplication.context
