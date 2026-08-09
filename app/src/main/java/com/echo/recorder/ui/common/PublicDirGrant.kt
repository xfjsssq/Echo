package com.echo.recorder.ui.common

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.echo.recorder.common.PublicDirManager
import kotlinx.coroutines.launch

/**
 * 公共目录 SAF 授权状态.
 *
 * [granted] = false 时调用 [PublicDirGrantState.request] 弹出系统文件夹选择器
 * (默认定位到 Download 目录, 即原 EchoBackup 所在位置). 授权成功后持久化
 * (takePersistableUriPermission + DataStore), 并回调 [onGranted] 供调用方补做
 * 备份/刷新等操作.
 *
 * 卸载重装后授权丢失, 重新进入任一使用点即可再次引导授权, 选择同一文件夹即恢复旧文件.
 */
@Composable
fun rememberPublicDirGrant(onGranted: () -> Unit = {}): PublicDirGrantState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(false) }
    val currentOnGranted by rememberUpdatedState(onGranted)

    LaunchedEffect(Unit) { granted = PublicDirManager.hasGrant(context) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                PublicDirManager.saveGrant(context, uri)
                granted = true
                currentOnGranted()
            }
        }
    }

    return remember(granted) {
        PublicDirGrantState(granted) {
            runCatching { launcher.launch(initialDownloadUri()) }
                .onFailure { launcher.launch(null) }
        }
    }
}

/** 系统选择器初始位置: 尽量定位到 Download 目录, 失败则交给系统默认. */
private fun initialDownloadUri(): Uri? = runCatching {
    Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload")
}.getOrNull()

class PublicDirGrantState internal constructor(
    val granted: Boolean,
    val request: () -> Unit,
)
