package com.echo.recorder.data

import com.echo.recorder.domain.model.Recording
import com.echo.recorder.domain.model.RecordingCategory
import kotlinx.coroutines.flow.Flow

/**
 * 录音数据源接口. 主代码通过此接口交互, 测试时注入假的实现.
 *
 * StateFlow<List<Recording>> 是承诺: 任意增删都会 emit 新 list.
 */
interface RecordingDataSource {
    val state: Flow<List<Recording>>

    /** 扫盘一次 (pending/longterm/unprocessed). */
    fun load()

    fun getById(id: String): Recording?

    /** 登记一条已经落盘的录音. */
    fun upsert(file: java.io.File, durationMs: Long, now: Long): Recording

    /** 删文件 + 删库. 成功=true, 找不到=false. */
    fun delete(id: String): Boolean

    /** 删除超过 maxAgeMs 的临时录音 (文件 + 记录). 返回删除数量. */
    fun deleteExpiredTemporary(maxAgeMs: Long): Int

    /** 把一条录音在临时/长期之间移动 (改物理目录 + 改分类). 返回新 Recording. */
    fun setCategory(id: String, category: RecordingCategory): Recording?
}
