package com.echo.recorder.common

import android.content.Context
import java.io.File

/**
 * 录音存放根目录: context.filesDir/recordings.
 *
 * 物理子目录即分类真相:
 * - _buffer/      实时环形缓冲 (隐藏, 不进入列表)
 * - pending/      临时录音 (默认分类)
 * - longterm/     长期录音
 * - unprocessed/  强制退出时遗留的 "未处理" 录音 (冷启动恢复用)
 *
 * load() 扫 pending/longterm/unprocessed 三个目录, 按路径反推 category.
 */
fun recordingsDir(context: Context): File {
    val dir = File(context.filesDir, "recordings")
    if (!dir.exists()) dir.mkdirs()
    return dir
}

fun bufferDir(context: Context): File = subdir(context, "_buffer")
fun pendingDir(context: Context): File = subdir(context, "pending")
fun longtermDir(context: Context): File = subdir(context, "longterm")
fun unprocessedDir(context: Context): File = subdir(context, "unprocessed")

private fun subdir(context: Context, name: String): File {
    val dir = File(recordingsDir(context), name)
    if (!dir.exists()) dir.mkdirs()
    return dir
}
