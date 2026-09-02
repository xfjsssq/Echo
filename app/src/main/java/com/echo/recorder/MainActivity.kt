package com.echo.recorder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
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
import com.echo.recorder.service.RecordingService
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

    /**
     * 启动时按保存的语言包装 base context.
     *
     * 与 [applyOverrideConfiguration] 双保险: 部分机型/场景下 applyOverrideConfiguration
     * 的 Configuration 不会传入 Compose 的 stringResource(), 而 attachBaseContext 包装
     * 直接改写 Activity 的 Resources, 两者叠加后英文模式全 UI 生效 (含引导页/设置页).
     */
    override fun attachBaseContext(newBase: Context) {
        val language = runBlocking {
            runCatching { SettingsRepository(newBase).language.first() }.getOrNull()
        }
        super.attachBaseContext(LocaleManager.wrap(newBase, language))
    }

    /**
     * 应用目标语言到 Activity 自身的 resources.
     *
     * 这是 Compose stringResource() 感知语言切换的关键: 必须通过 applyOverrideConfiguration
     * 把 locale 应用到 Activity 的 resources, 而非 createConfigurationContext (后者无法传播到 Compose).
     */
    override fun applyOverrideConfiguration(overrideConfiguration: Configuration?) {
        val language = runBlocking {
            runCatching { SettingsRepository(this@MainActivity).language.first() }.getOrNull()
        }
        val config = LocaleManager.configuration(this, language)
        // 保留系统原始配置的其他字段 (屏幕方向/DPI 等), 仅覆盖 locale.
        if (overrideConfiguration != null) {
            config.setTo(overrideConfiguration)
            val locale = if (language == LocaleManager.EN) java.util.Locale.ENGLISH else java.util.Locale.CHINESE
            config.setLocale(locale)
        }
        super.applyOverrideConfiguration(config)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(lifecycleObserver)

        hasPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        setContent {
            // 恒为明亮主题 (暗黑模式已移除)
            EchoTheme {
                EchoApp(
                    hasPermission = hasPermission,
                    onRequestPermission = { requestMicPermission() },
                    onExit = { finishAndRemoveTask() },
                )
            }
        }
    }

    private fun requestMicPermission() {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // ---- 音量上下双键 = 快照截取 (第一期: 仅 Echo 前台生效) ----
    private var comboFirstKey = 0
    private var comboFirstAt = 0L

    /**
     * 音量上+下键在 [COMBO_WINDOW_MS] 内先后按下 → 触发 [RecordingService.captureBuffer]
     * 并消费事件 (第二次按键不再调音量). 单键按下完全放行, 正常调音量不受影响;
     * 长按自动重复 (repeatCount>0) 不参与配对, 防止长按误触.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) &&
            event?.repeatCount == 0
        ) {
            val now = SystemClock.elapsedRealtime()
            if (comboFirstKey != 0 && keyCode != comboFirstKey && now - comboFirstAt <= COMBO_WINDOW_MS) {
                comboFirstKey = 0
                val svc = RecordingService.instance
                if (svc != null && svc.phase.value == RecordingService.Phase.BUFFERING) {
                    svc.captureBuffer()
                    return true
                }
            } else {
                comboFirstKey = keyCode
                comboFirstAt = now
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        /** 双键判定窗口: 真人"同时按"两事件间隔通常 <150ms. */
        private const val COMBO_WINDOW_MS = 300L
    }
}
