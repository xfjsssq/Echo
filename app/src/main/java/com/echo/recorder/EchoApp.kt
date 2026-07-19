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
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.echo.recorder.service.RecordingService
import com.echo.recorder.ui.navigation.EchoNavHost
import com.echo.recorder.ui.record.RecordViewModel

/**
 * 应用根组合. 持有 [RecordViewModel], 绑定前台录音服务, 渲染 [EchoNavHost].
 */
@Composable
fun EchoApp(
    onRequestPermission: () -> Unit = {},
) {
    val navController = rememberNavController()
    val viewModel = remember { RecordViewModel() }
    val context = LocalContext.current

    var recorder by remember { mutableStateOf<RecordingService?>(null) }

    DisposableEffect(context) {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val b = binder as? RecordingService.RecordingServiceBinder ?: return
                recorder = b.service
                viewModel.setRecorder(b.service)
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
