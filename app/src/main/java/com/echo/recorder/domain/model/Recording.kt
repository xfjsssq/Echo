package com.echo.recorder.domain.model

/**
 * 一条录音记录.
 *
 * @param id          唯一标识 (时间戳字符串, 与文件名对齐)
 * @param displayName 展示名  "echo_20260719_143022.m4a"
 * @param fileUrl     本地文件 URL  "file:///data/.../recordings/echo_....m4a"
 * @param createdAt   创建时间 (epoch ms), 用于列表倒序
 * @param durationMs  时长 (ms)
 */
data class Recording(
    val id: String,
    val displayName: String,
    val fileUrl: String,
    val createdAt: Long,
    val durationMs: Long,
)
