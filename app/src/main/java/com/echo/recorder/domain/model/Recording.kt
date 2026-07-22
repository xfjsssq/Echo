package com.echo.recorder.domain.model

/** 分类. */
enum class RecordingCategory { TEMPORARY, LONG_TERM, UNPROCESSED }

data class Recording(
    val id: String,
    val displayName: String,
    val fileUrl: String,
    val createdAt: Long,
    val durationMs: Long,
    val category: RecordingCategory = RecordingCategory.TEMPORARY,
    /** 公共目录虚引用: 文件已复制到公共目录, 播放从公共目录读取, 不再占用私有存储. */
    val isPublicVirtual: Boolean = false,
)
