package com.echo.recorder.domain.recording

import com.echo.recorder.domain.model.Recording
import com.echo.recorder.domain.model.RecordingCategory
import kotlinx.coroutines.flow.Flow
import java.io.File

interface RecordingRepository {
    /** 所有录音, 按 createdAt 倒序. */
    fun getAll(): Flow<List<Recording>>

    @Throws(NoSuchElementException::class)
    suspend fun getById(id: String): Recording

    /** 落盘 + 写库. 返回带 id 的新 Recording. */
    suspend fun create(file: File, durationMs: Long): Recording

    /** 落盘 + 写库, 指定 createdAt (导入历史文件时保留原始时间, 用于日期分组). */
    suspend fun create(file: File, durationMs: Long, createdAt: Long): Recording

    /** 删文件 + 删库. 成功=true, 找不到=false. */
    suspend fun delete(id: String): Boolean

    /** 删除超过 maxAgeMs 的临时录音 (惰性清理). 返回删除数量. */
    suspend fun deleteExpiredTemporary(maxAgeMs: Long): Int

    /** 把一条录音在临时/长期之间移动. 返回新 Recording, 找不到=null. */
    suspend fun setCategory(id: String, category: RecordingCategory): Recording?

    /** 重命名录音 (改文件系统文件名 + 更新记录). 成功返回新 Recording, 失败=null. */
    suspend fun rename(id: String, newName: String): Recording?
}
