package com.echo.recorder.data

import android.content.Context
import android.media.MediaMetadataRetriever
import com.echo.recorder.common.bufferDir
import com.echo.recorder.common.longtermDir
import com.echo.recorder.common.pendingDir
import com.echo.recorder.common.unprocessedDir
import com.echo.recorder.domain.model.Recording
import com.echo.recorder.domain.model.RecordingCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * 本地文件系统录音数据源. 纯 JVM 操作, 不依赖 MediaMetadataRetriever.
 *
 * 分类即目录: pending/ longterm/ unprocessed/ 各自对应一个 RecordingCategory.
 * _buffer/ 是实时环形缓冲, 不进列表 (load 不扫它).
 */
class FilesystemRecordingDataSource(
    private val context: android.content.Context? = null,
    private val baseDir: File? = null,
    private val initialState: List<Recording> = emptyList(),
) : RecordingDataSource {
    private val dir: File by lazy { baseDir ?: com.echo.recorder.common.recordingsDir(context!!) }
    private val _state = MutableStateFlow(initialState)
    override val state: StateFlow<List<Recording>> = _state.asStateFlow()

    /** 扫盘一次. 只扫 pending/longterm/unprocessed. */
    override fun load() {
        val dirs = listOf(
            pendingDir(context!!) to RecordingCategory.TEMPORARY,
            longtermDir(context!!) to RecordingCategory.LONG_TERM,
            unprocessedDir(context!!) to RecordingCategory.UNPROCESSED,
        )
        val all = mutableListOf<Recording>()
        for ((d, cat) in dirs) {
            val files = d.listFiles()?.filter { f -> f.isFile && f.name.endsWith(".m4a") } ?: continue
            all += files.map { f -> toRecording(f, cat) }
        }
        _state.value = all
    }

    private fun toRecording(f: File, cat: RecordingCategory): Recording = Recording(
        id = f.name,
        displayName = f.name,
        fileUrl = f.toURI().toString(),
        createdAt = f.lastModified(),
        durationMs = readDurationMs(f),
        category = cat,
    )

    /**
     * 读取音频文件时长 (ms). 读取失败或文件损坏返回 0.
     *
     * 使用 MediaMetadataRetriever 而非 MediaPlayer: 元数据读取更快, 不需要完整解码.
     * 若文件实际存在且大小 > 0 但读不到时长, 返回 0 并由上层按"文件存在"处理.
     */
    private fun readDurationMs(file: File): Long {
        if (!file.exists() || file.length() == 0L) return 0L
        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(file.absolutePath)
            val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            ms
        }.getOrElse { 0L }.also {
            runCatching { retriever.release() }
        }
    }

    override fun getById(id: String): Recording? = _state.value.firstOrNull { it.id == id }

    /** 登记一条已经落盘的录音. category 由它落在的物理目录决定 (默认临时). */
    override fun upsert(file: File, durationMs: Long, now: Long): Recording {
        val cat = categoryOf(file)
        val rec = Recording(
            id = file.name,
            displayName = file.name,
            fileUrl = file.toURI().toString(),
            createdAt = now,
            durationMs = durationMs,
            category = cat,
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

    /** 把录音在临时/长期之间移动 (改物理目录 + 改分类). unprocessed 也可被保留为临时. */
    override fun setCategory(id: String, category: RecordingCategory): Recording? {
        val target = _state.value.firstOrNull { it.id == id } ?: return null
        if (target.category == category) return target
        val src = File(java.net.URI(target.fileUrl))
        val destDir = when (category) {
            RecordingCategory.LONG_TERM -> longtermDir(context!!)
            RecordingCategory.TEMPORARY -> pendingDir(context!!)
            RecordingCategory.UNPROCESSED -> unprocessedDir(context!!)
        }
        val dest = File(destDir, src.name)
        if (src.exists()) {
            runCatching { src.copyTo(dest, overwrite = true) }
            runCatching { src.delete() }
        }
        val moved = target.copy(fileUrl = dest.toURI().toString(), category = category)
        _state.value = _state.value.map { if (it.id == id) moved else it }
        return moved
    }

    /** 由物理路径反推分类. */
    private fun categoryOf(file: File): RecordingCategory {
        val parent = file.parentFile?.name ?: return RecordingCategory.TEMPORARY
        return when (parent) {
            "longterm" -> RecordingCategory.LONG_TERM
            "unprocessed" -> RecordingCategory.UNPROCESSED
            else -> RecordingCategory.TEMPORARY
        }
    }
}
