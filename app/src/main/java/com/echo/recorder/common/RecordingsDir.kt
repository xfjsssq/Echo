package com.echo.recorder.common

import android.content.Context
import java.io.File

/** 录音存放目录: context.filesDir/recordings. 测试可注入 overrideDir. */
fun recordingsDir(context: Context, overrideDir: File? = null): File {
    val dir = overrideDir ?: File(context.filesDir, "recordings")
    if (!dir.exists()) dir.mkdirs()
    return dir
}
