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

    /** 删文件 + 删库. 成功=true, 找不到=false. */
    suspend fun delete(id: String): Boolean

    /** 把一条录音在临时/长期之间移动. 返回新 Recording, 找不到=null. */
    suspend fun setCategory(id: String, category: RecordingCategory): Recording?
}
