package com.echo.recorder

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.echo.recorder.service.RecordingService
import com.echo.recorder.ui.navigation.EchoNavHost
import com.echo.recorder.ui.record.RecordViewModel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton


/**
 * 应用根组合. 持有 [RecordViewModel], 绑定前台录音服务, 渲染 [EchoNavHost].
 *
 * - 仅授权后才启动/绑定时服务 (targetSdk=34 + microphone FGS 未授权会崩溃).
 * - 启动时做一次 UNPROCESSED 检查 (冷启动恢复弹窗).
 * - onRestartService: 设置页改缓冲时长后重启服务 (按新 N 重新绑定).
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
    var service by remember { mutableStateOf<RecordingService?>(null) }
    var askExitPassword by remember { mutableStateOf(false) }

    // 彻底退出密码门控 (功能占位: 密码功能留空, 直接放行).
    if (askExitPassword) {
        AlertDialog(
            onDismissRequest = { askExitPassword = false },
            title = { Text("输入密码以退出") },
            text = { Text("密码保护功能占位, 点击确认直接退出") },
            confirmButton = {
                TextButton(onClick = {
                    askExitPassword = false
                    viewModel.exitCompletely()
                    onExit()
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { askExitPassword = false }) { Text("取消") }
            },
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
        val connection = object : ServiceConnection {
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
    )
}
