package com.echo.recorder

import com.echo.recorder.domain.model.Recording
import java.io.File

/**
 * 测试用 Recording 工厂. 避免每个测试都显式拼 5 个字段.
 */
object TestRecordings {

    /** 生成一条录音记录. fileUrl 用 file://<parent>/ 前缀, 与 FilesystemRecordingDataSource 落盘格式一致. */
    fun make(
        id: String,
        createdAt: Long,
        durationMs: Long = 1000L,
        displayName: String = "$id.m4a",
        parentDir: String = "file:///tmp",
    ): Recording = Recording(
        id = id,
        displayName = displayName,
        fileUrl = "$parentDir/$displayName",
        createdAt = createdAt,
        durationMs = durationMs,
    )

    /** 落盘一个空文件并返回其 File (基于当前工作目录), 供 Repository.create 使用. */
    fun touchFile(displayName: String, parentDir: File): File {
        if (!parentDir.exists()) parentDir.mkdirs()
        val f = File(parentDir, displayName)
        f.writeText("audio-bytes")
        return f
    }
}
