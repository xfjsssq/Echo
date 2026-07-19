package com.echo.recorder.data

import android.content.Context
import com.echo.recorder.common.recordingsDir
import com.echo.recorder.domain.model.Recording
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * 本地文件系统录音数据源. 纯 JVM 操作, 不依赖 MediaMetadataRetriever.
 */
class FilesystemRecordingDataSource(
    private val context: Context? = null,
    private val baseDir: File? = null,
    private val initialState: List<Recording> = emptyList(),
) : RecordingDataSource {
    private val dir: File by lazy { baseDir ?: recordingsDir(context!!) }
    private val _state = MutableStateFlow(initialState)
    override val state: StateFlow<List<Recording>> = _state.asStateFlow()

    /** 扫盘一次. */
    override fun load() {
        val files = dir.listFiles()?.filter { f -> f.isFile && f.name.endsWith(".m4a") } ?: emptyList()
        _state.value = files.map { f ->
            Recording(
                id = f.name,
                displayName = f.name,
                fileUrl = f.toURI().toString(),
                createdAt = f.lastModified(),
                durationMs = 0L,
            )
        }
    }

    override fun getById(id: String): Recording? = _state.value.firstOrNull { it.id == id }

    /** 登记一条已经落盘的录音 (由 service 填好真实时长后调用). */
    override fun upsert(file: File, durationMs: Long, now: Long): Recording {
        val rec = Recording(
            id = file.name,
            displayName = file.name,
            fileUrl = file.toURI().toString(),
            createdAt = now,
            durationMs = durationMs,
        )
        val withoutOld = _state.value.filterNot { it.id == rec.id }
        _state.value = withoutOld + rec
        return rec
    }

    override fun delete(id: String): Boolean {
        val target = _state.value.firstOrNull { it.id == id } ?: return false
        val f = File(java.net.URI(target.fileUrl))
        if (f.exists()) f.delete()
        _state.value = _state.value.filterNot { it.id == id }
        return true
    }
}
