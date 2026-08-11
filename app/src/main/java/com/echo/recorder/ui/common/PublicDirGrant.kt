package com.echo.recorder.ui.common

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.echo.recorder.common.PublicDirManager

/**
 * 公共目录访问授权状态 (固定路径 Downloads/EchoBackup).
 *
 * [granted] = false 时调用 [PublicDirGrantState.request] 弹出系统运行时权限对话框
 * (API 33+ READ_MEDIA_AUDIO / 29-32 READ_EXTERNAL_STORAGE / 26-28 WRITE_EXTERNAL_STORAGE),
 * 用户点一次"允许"即可, 无需手动选择文件夹. 授权成功后回调 [onGranted] 供调用方
 * 补做备份/刷新等操作.
 */
@Composable
fun rememberPublicDirGrant(onGranted: () -> Unit = {}): PublicDirGrantState {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(false) }
    val currentOnGranted by rememberUpdatedState(onGranted)

    LaunchedEffect(Unit) { granted = PublicDirManager.hasGrant(context) }

    val permission = PublicDirManager.requiredPermission()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (ok) currentOnGranted()
    }

    return remember(granted) {
        PublicDirGrantState(granted) { launcher.launch(permission) }
    }
}

class PublicDirGrantState internal constructor(
    val granted: Boolean,
    val request: () -> Unit,
)
