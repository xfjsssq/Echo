package com.echo.recorder.data

import com.echo.recorder.domain.model.Recording
import com.echo.recorder.domain.model.RecordingCategory
import com.echo.recorder.domain.recording.RecordingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

/** 默认 Repository: 订阅数据源 Flow, 排序后对外. */
class RecordingRepositoryImpl(
    private val ds: RecordingDataSource,
) : RecordingRepository {

    override fun getAll(): Flow<List<Recording>> =
        ds.state.map { list -> list.sortedByDescending { it.createdAt } }

    override suspend fun getById(id: String): Recording =
        ds.getById(id) ?: throw NoSuchElementException("Recording not found: $id")

    override suspend fun create(file: File, durationMs: Long): Recording =
        ds.upsert(file, durationMs, System.currentTimeMillis())

    override suspend fun delete(id: String): Boolean = ds.delete(id)

    override suspend fun setCategory(id: String, category: RecordingCategory): Recording? =
        ds.setCategory(id, category)
}
