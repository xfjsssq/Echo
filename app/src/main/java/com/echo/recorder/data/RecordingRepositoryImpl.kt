package com.echo.recorder.data

import com.echo.recorder.domain.model.Recording
import com.echo.recorder.domain.model.RecordingCategory
import com.echo.recorder.domain.recording.RecordingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.io.File

/**
 * 默认 Repository: 订阅数据源 Flow, 合并公共目录虚引用, 排序后对外.
 *
 * 虚引用 (isPublicVirtual) 文件位于公共目录, 不在数据源扫描范围内, 由 [VirtualRefStore] 持久化.
 * virtualRefs 可空: 纯数据源测试 (无虚引用) 可直接构造, 生产由 ServiceLocator 注入.
 */
class RecordingRepositoryImpl(
    private val ds: RecordingDataSource,
    private val virtualRefs: VirtualRefStore? = null,
) : RecordingRepository {

    override fun getAll(): Flow<List<Recording>> {
        val refsFlow = virtualRefs?.refs ?: flowOf(emptyList())
        return combine(ds.state, refsFlow) { scanned, virtual ->
            (scanned + virtual).sortedByDescending { it.createdAt }
        }
    }

    override suspend fun getById(id: String): Recording =
        ds.getById(id)
            ?: throw NoSuchElementException("Recording not found: $id")

    override suspend fun create(file: File, durationMs: Long): Recording =
        create(file, durationMs, System.currentTimeMillis())

    override suspend fun create(file: File, durationMs: Long, createdAt: Long): Recording =
        ds.upsert(file, durationMs, createdAt)

    override suspend fun delete(id: String): Boolean = ds.delete(id)

    override suspend fun deleteExpiredTemporary(maxAgeMs: Long): Int =
        ds.deleteExpiredTemporary(maxAgeMs)

    override suspend fun setCategory(id: String, category: RecordingCategory): Recording? =
        ds.setCategory(id, category)

    override suspend fun rename(id: String, newName: String): Recording? =
        ds.rename(id, newName)

    /** 所有虚引用. */
    fun virtualRefsFlow(): Flow<List<Recording>> = virtualRefs?.refs ?: flowOf(emptyList())

    /** 添加一条公共目录虚引用. */
    suspend fun addVirtualRef(rec: Recording) = virtualRefs?.add(rec)

    /** 移除一条公共目录虚引用. */
    suspend fun removeVirtualRef(id: String) = virtualRefs?.remove(id)
}
