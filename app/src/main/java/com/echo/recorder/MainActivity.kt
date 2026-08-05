package com.echo.recorder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echo.recorder.auth.SessionAuth
import com.echo.recorder.i18n.LocaleManager
import com.echo.recorder.settings.SettingsRepository
import com.echo.recorder.ui.theme.EchoTheme
import com.echo.recorder.ui.theme.ThemeManager
import com.echo.recorder.ui.theme.ThemeMode
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
            val context = LocalContext.current
            val settings = remember { SettingsRepository(context) }

            // 响应式观察 themeMode: DataStore 变化时自动重组, 无需 runBlocking.
            val themeMode by settings.themeMode.collectAsStateWithLifecycle(
                initialValue = ThemeMode.LIGHT,
            )
            val darkTheme = ThemeManager.resolveDarkTheme(
                themeMode,
                ThemeManager.isSystemDark(context),
            )

            EchoTheme(darkTheme = darkTheme) {
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
}
