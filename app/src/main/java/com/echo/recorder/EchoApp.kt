package com.echo.recorder

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.echo.recorder.ui.theme.ThemeManager
import androidx.navigation.compose.rememberNavController
import com.echo.recorder.auth.SessionAuth
import com.echo.recorder.i18n.LocaleManager
import com.echo.recorder.service.RecordingService
import com.echo.recorder.settings.SettingsRepository
import com.echo.recorder.ui.theme.ThemeMode
import com.echo.recorder.ui.lock.LockScreen
import com.echo.recorder.ui.lock.PasswordPromptDialog
import com.echo.recorder.ui.navigation.EchoNavHost
import com.echo.recorder.ui.onboarding.LanguagePickerScreen
import com.echo.recorder.ui.onboarding.OnboardingScreen
import com.echo.recorder.ui.onboarding.PrivacyAgreementScreen
import com.echo.recorder.ui.record.RecordViewModel
import kotlinx.coroutines.launch

/**
 * 应用根组合. 持有 [RecordViewModel], 绑定前台录音服务, 渲染 [EchoNavHost].
 *
 * - 仅授权后才启动/绑定时服务 (targetSdk=34 + microphone FGS 未授权会崩溃).
 * - 启动时做一次 UNPROCESSED 检查 (冷启动恢复弹窗).
 * - 冷启动密码锁屏: 开启密码保护时, 进入主界面需先验证.
 * - onRestartService: 设置页改缓冲时长后重启服务 (按新 N 重新绑定).
 *
 * 初始化流程 (F1):
 *   step 0: 语言选择 (选完重建 Activity, 重建后 step 进入 1)
 *   step 1: 隐私协议
 *   step 2: 引导卡片 (含公共目录备份 + 主题选择)
 *   step 3: 主界面 (此时才触发权限请求)
 */
@Composable
fun EchoApp(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit = {},
    onExit: () -> Unit = {},
) {
    val navController = rememberNavController()
    val viewModel = remember { RecordViewModel() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var service by remember { mutableStateOf<RecordingService?>(null) }
    var askExitPassword by remember { mutableStateOf(false) }

    val settings = remember { SettingsRepository(context) }
    val passwordEnabled by produceState(initialValue = false) {
        settings.passwordEnabled.collect { value = it }
    }

    // 主题模式: 响应式观察 DataStore, 主题切换无需重建 Activity, 直接由 Compose 状态驱动.
    val themeMode by produceState(initialValue = ThemeMode.LIGHT) {
        settings.themeMode.collect { value = it }
    }

    // 首次启动引导: 语言选择 -> 隐私协议 -> 引导卡片 -> 主界面.
    // 初始值 false, 等 DataStore 读到真实值后再决定是否跳过.
    val onboardingDone by produceState(initialValue = false) {
        settings.onboardingDone.collect { value = it }
    }

    // 读取已保存的语言 (用于判断重建后是否已选过语言).
    val savedLanguage by produceState<String?>(initialValue = null) {
        settings.language.collect { value = it }
    }

    // step 用 rememberSaveable, 语言选择重建 Activity 后仍能恢复.
    if (!onboardingDone) {
        var step by rememberSaveable { mutableStateOf(0) }
        // 重建后若已有语言且 step 还在 0, 自动跳到隐私协议 (避免无限重建).
        LaunchedEffect(savedLanguage, step) {
            if (step == 0 && savedLanguage != null) {
                step = 1
            }
        }
        when (step) {
            0 -> LanguagePickerScreen(onPick = { code ->
                scope.launch { settings.setLanguage(code) }
                // 重建 Activity 以应用新语言; 重建后 step 恢复为 0,
                // 但此时语言已是目标语言, 用户再次选择会再重建.
                // 为避免无限重建, 语言选完后直接跳到隐私协议.
                step = 1
                val activity = context as? android.app.Activity
                if (activity != null && !activity.isFinishing) {
                    LocaleManager.recreate(activity)
                }
            })
            1 -> PrivacyAgreementScreen(onAgree = { step = 2 })
            2 -> OnboardingScreen(
                onFinish = {
                    scope.launch { settings.setOnboardingDone(true) }
                    step = 3
                },
                onEnablePublicDir = {
                    scope.launch { settings.setPublicDirEnabled(true) }
                },
                // 主题切换: 仅更新 Compose 状态 + 持久化, 不重建 Activity, 避免打断引导流程.
                themeMode = themeMode,
                onThemeChange = { mode -> scope.launch { settings.setThemeMode(mode) } },
            )
            3 -> {
                // 引导完成, 进入主界面前先请求权限.
                LaunchedEffect(Unit) {
                    if (!hasPermission) onRequestPermission()
                }
                // 继续往下执行主界面逻辑 (不 return).
            }
        }
        // step 0~2 时直接 return, 不渲染主界面.
        if (step < 3) return
    }

    // 冷启动锁屏: 开启密码且本会话未解锁时, 全屏覆盖锁屏层.
    if (passwordEnabled && !SessionAuth.isUnlocked) {
        Surface(modifier = Modifier.fillMaxSize()) {
            LockScreen(onUnlocked = { /* SessionAuth.isUnlocked 已在 LockScreen 内置位 */ })
        }
        return
    }

    // 彻底退出密码门控 (实时验证).
    if (askExitPassword) {
        val storedHash by produceState<String?>(initialValue = null) {
            value = settings.passwordHash.first()
        }
        val passwordType by produceState<String?>(initialValue = null) {
            value = settings.passwordType.first()
        }
        val isPattern = passwordType == "pattern"
        PasswordPromptDialog(
            storedHash = storedHash,
            recoveryHash = null,
            isPattern = isPattern,
            onVerify = {
                askExitPassword = false
                viewModel.exitCompletely()
                onExit()
            },
            onDismiss = { askExitPassword = false },
        )
    }

    LaunchedEffect(hasPermission) {
        viewModel.setHasPermission(hasPermission)
    }

    // 冷启动恢复检查 (仅授权后, 绑定前做一次).
    LaunchedEffect(hasPermission) {
        if (hasPermission) viewModel.checkUnprocessed()
    }

    DisposableEffect(context, hasPermission) {
        if (!hasPermission) {
            return@DisposableEffect onDispose { }
        }
        val connection : ServiceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val b = binder as? RecordingService.RecordingServiceBinder ?: return
                service = b.service
                viewModel.setRecorder(b.service)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
            }
        }
        val intent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)

        onDispose {
            try { context.unbindService(connection) } catch (_: IllegalArgumentException) { }
        }
    }

    // 重启服务 (设置页改缓冲时长): 发 RESTART 让服务回到 IDLE 并用新设置.
    val onRestartService: (Boolean) -> Unit = { savePending ->
        val intent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_RESTART
            putExtra(RecordingService.EXTRA_SAVE_PENDING, savePending)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        // 刷新 ViewModel 的 phase 到 IDLE (服务 handleRestart 会推 REVIEW->IDLE).
        service = null
    }

    // 用户点退出: 若开启密码门控则弹对话框, 否则直接退出.
    val onRequestExit: () -> Unit = {
        if (viewModel.state.value.passwordEnabled) askExitPassword = true
        else { viewModel.exitCompletely(); onExit() }
    }

    EchoNavHost(
        navController = navController,
        recordViewModel = viewModel,
        onRequestPermission = onRequestPermission,
        onRestartService = onRestartService,
        onRequestExit = onRequestExit,
        themeMode = themeMode,
        onThemeChange = { mode -> scope.launch { settings.setThemeMode(mode) } },
    )
}
