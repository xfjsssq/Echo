package com.echo.recorder

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.echo.recorder.service.RecordingService
import com.echo.recorder.ui.navigation.EchoNavHost
import com.echo.recorder.ui.record.RecordViewModel
import kotlinx.coroutines.launch

/**
 * 应用根组合. 持有 [RecordViewModel], 绑定前台录音服务, 渲染 [EchoNavHost].
 */
@Composable
fun EchoApp(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit = {},
) {
    val navController = rememberNavController()
    val viewModel = remember { RecordViewModel() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var recorder by remember { mutableStateOf<RecordingService?>(null) }

    // 同步权限到 ViewModel, 否则按钮禁用态永远不变.
    androidx.compose.runtime.LaunchedEffect(hasPermission) {
        viewModel.setHasPermission(hasPermission)
    }

    // 仅授权后才启动/绑定前台服务.
    // targetSdk=34 + foregroundServiceType="microphone": 未授权就 startForeground 会抛 SecurityException 崩溃.
    DisposableEffect(context, hasPermission) {
        if (!hasPermission) {
            return@DisposableEffect onDispose { }
        }
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val b = binder as? RecordingService.RecordingServiceBinder ?: return
                recorder = b.service
                viewModel.setRecorder(b.service)
                // 订阅服务状态, 同步到 ViewModel, 否则界面不知道正在录音.
                observeService(b.service)
            }

            private fun observeService(service: RecordingService) {
                scope.launch {
                    service.state.collect { s ->
                        viewModel.syncFrom(s.isRecording, s.elapsedMs)
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                recorder = null
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

    EchoNavHost(
        navController = navController,
        recordViewModel = viewModel,
        onRequestPermission = onRequestPermission,
    )
}
