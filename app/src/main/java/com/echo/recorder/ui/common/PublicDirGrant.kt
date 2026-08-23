package com.echo.recorder.ui.common

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.echo.recorder.common.PublicDirManager

/**
 * 公共目录访问授权状态 (固定路径 Downloads/EchoBackup).
 *
 * 运行时权限是系统级全局状态: 任一入口 (列表页导入/保存、设置页文件管理) 授权一次,
 * 所有入口永久免授权。因此:
 * - 每次回到前台 (ON_RESUME) 刷新一次 [PublicDirGrantState.granted],
 *   杜绝"别处已授权、此处仍显示未授权"的陈旧状态;
 * - [PublicDirGrantState.request] 已授权时直接短路成功, 绝不重复弹系统对话框.
 */
@Composable
fun rememberPublicDirGrant(onGranted: () -> Unit = {}): PublicDirGrantState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(PublicDirManager.hasGrant(context)) }
    val currentOnGranted by rememberUpdatedState(onGranted)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = PublicDirManager.hasGrant(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permission = PublicDirManager.requiredPermission()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok || PublicDirManager.hasGrant(context)
        if (granted) currentOnGranted()
    }

    return remember(granted) {
        PublicDirGrantState(granted) {
            if (PublicDirManager.hasGrant(context)) {
                granted = true
                currentOnGranted()
            } else {
                launcher.launch(permission)
            }
        }
    }
}

class PublicDirGrantState internal constructor(
    val granted: Boolean,
    val request: () -> Unit,
)
