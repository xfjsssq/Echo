package com.echo.recorder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.echo.recorder.auth.SessionAuth
import com.echo.recorder.i18n.LocaleManager
import com.echo.recorder.settings.SettingsRepository
import com.echo.recorder.ui.theme.EchoTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    private var hasPermission by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
    }

    // B2: 进入后台时重置会话认证状态, 回到前台后恢复"首次需密".
    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            SessionAuth.reset()
        }
    }

    /** 用保存的语言包装 Context (语言切换在 attachBaseContext 生效). */
    override fun attachBaseContext(newBase: Context) {
        val language = runBlocking {
            runCatching { SettingsRepository(newBase).language.first() }.getOrNull()
        }
        super.attachBaseContext(LocaleManager.wrap(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(lifecycleObserver)

        hasPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        setContent {
            EchoTheme {
                EchoApp(
                    hasPermission = hasPermission,
                    onRequestPermission = { requestMicPermission() },
                    onExit = { finishAndRemoveTask() },
                )
            }
        }

        // 首次未授权时主动拉起请求, 否则按钮禁用态无法触发请求.
        if (!hasPermission) {
            requestMicPermission()
        }
    }

    private fun requestMicPermission() {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}
