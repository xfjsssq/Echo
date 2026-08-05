package com.echo.recorder

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class EchoApplication : Application() {
    companion object {
        lateinit var context: Context
            private set

        /** 临时录音保留时长: 24 小时 (超过未保存为长期则自动删除). */
        const val TEMP_EXPIRY_MS = 24L * 60 * 60 * 1000
    }

    override fun onCreate() {
        super.onCreate()
        context = applicationContext
        // 惰性清理: 每次冷启动执行一次, 删除超过 24 小时仍未保存为长期的临时录音.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                ServiceLocator.repository(applicationContext)
                    .deleteExpiredTemporary(TEMP_EXPIRY_MS)
            }
        }
    }
}

/** 全局 Application Context 访问, 供 ServiceLocator / ViewModel 默认参数使用. */
fun appContext(): Context = EchoApplication.context
