package com.echo.recorder.data

import com.echo.recorder.domain.model.Recording
import kotlinx.coroutines.flow.Flow

/**
 * 录音数据源接口. 主代码通过此接口交互, 测试时注入假的实现.
 *
 * StateFlow<List<Recording>> 是承诺: 任意增删都会 emit 新 list.
 */
interface RecordingDataSource {
    val state: Flow<List<Recording>>

    /** 扫盘一次. */
    fun load()

    fun getById(id: String): Recording?

    /** 登记一条已经落盘的录音. */
    fun upsert(file: java.io.File, durationMs: Long, now: Long): Recording

    /** 删文件 + 删库. 成功=true, 找不到=false. */
    fun delete(id: String): Boolean
}
