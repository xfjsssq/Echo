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
)
