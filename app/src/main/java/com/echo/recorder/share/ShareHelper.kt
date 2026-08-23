package com.echo.recorder.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * 系统分享工具. 通过 FileProvider 分享 .m4a 文件.
 */
object ShareHelper {

    fun shareAudio(context: Context, file: File, authority: String) {
        val uri = FileProvider.getUriForFile(context, authority, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    /**
     * 分享公共目录虚引用 (content:// URI): 先复制到缓存临时文件再走 FileProvider.
     * 临时文件只是分享载体, 随缓存清理消失, 不会产生持久副本.
     */
    fun shareAudioUri(context: Context, contentUri: Uri) {
        val tmp = File(context.cacheDir, "share_${System.currentTimeMillis()}.m4a")
        val copied = runCatching {
            context.contentResolver.openInputStream(contentUri)?.use { src ->
                tmp.outputStream().use { dst -> src.copyTo(dst) }
            } != null
        }.getOrDefault(false)
        if (!copied) {
            runCatching { tmp.delete() }
            return
        }
        tmp.deleteOnExit()
        shareAudio(context, tmp, "${context.packageName}.fileprovider")
    }
}
