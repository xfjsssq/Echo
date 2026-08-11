package com.echo.recorder.ui.list

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echo.recorder.ServiceLocator
import com.echo.recorder.common.PublicDirManager
import com.echo.recorder.common.longtermDir
import com.echo.recorder.domain.model.Recording
import com.echo.recorder.domain.model.RecordingCategory
import com.echo.recorder.domain.recording.RecordingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.net.URI

/** 列表顶部分类 Tab. */
enum class ListTab { TEMPORARY, LONG_TERM }

data class ListUiState(
    val tab: ListTab = ListTab.TEMPORARY,
    val temporary: List<Recording> = emptyList(),
    val longTerm: List<Recording> = emptyList(),
    /** 多选模式. */
    val selectionMode: Boolean = false,
    val selected: Set<String> = emptySet(),
    /** 当前原地展开的条目 id. */
    val expandedId: String? = null,
    /** 存在尚未备份到安全位置的长期录音, 需要用户授权备份文件夹. */
    val needsPublicGrant: Boolean = false,
)

/**
 * 录音列表 ViewModel.
 *
 * - 双分类 Tab (临时/长期), 物理目录即真相.
 * - 多选模式: 长按进入, 批量移至长期 / 批量删除.
 * - 自动备份: 录音成为长期录音时自动复制到备份文件夹 (SAF), 永远默认开启;
 *   尚未授权时置 [ListUiState.needsPublicGrant], 由 UI 引导用户选择文件夹后重试.
 * - 从备份文件夹导入 = 复制到应用私有长期目录, 作为普通长期录音 (不再有虚引用).
 */
class ListViewModel(
    private val context: Context,
    private val repository: RecordingRepository = ServiceLocator.repository(context),
) : ViewModel() {

    private val _state = MutableStateFlow(ListUiState())
    val state: StateFlow<ListUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAll().collect { all ->
                _state.value = _state.value.copy(
                    temporary = all.filter { it.category == RecordingCategory.TEMPORARY },
                    longTerm = all.filter { it.category == RecordingCategory.LONG_TERM },
                )
                // 冷启动补扫: 把之前没备份过的长期录音补备份/提示授权.
                viewModelScope.launch { sweepUnbacked() }
            }
        }
    }

    fun switchTab(tab: ListTab) {
        _state.value = _state.value.copy(tab = tab, expandedId = null, selected = emptySet(), selectionMode = false)
    }

    // ---- 展开 (原地播放) ----
    fun toggleExpanded(id: String) {
        val cur = _state.value.expandedId
        _state.value = _state.value.copy(expandedId = if (cur == id) null else id)
    }

    // ---- 单条操作 ----
    fun moveToLongTerm(id: String) = act(id, RecordingCategory.LONG_TERM)
    fun delete(id: String) = viewModelScope.launch { repository.delete(id) }

    /**
     * 自动备份: 录音成为长期录音后, 自动复制到备份文件夹.
     * 未授权或复制失败时置 needsPublicGrant, 由 UI 引导授权后重试.
     */
    private suspend fun backupToPublic(rec: Recording) {
        if (rec.isPublicVirtual) return
        val src = runCatching { File(URI(rec.fileUrl)) }.getOrNull() ?: return
        if (!src.exists()) return
        if (!PublicDirManager.copyToPublic(context, src, src.name)) {
            _state.value = _state.value.copy(needsPublicGrant = true)
        }
    }

    /**
     * 用户授权备份文件夹后调用: 补备份所有尚未备份的长期录音, 并关闭授权提示.
     */
    fun retryPendingBackups() {
        viewModelScope.launch {
            val backedUp = PublicDirManager.scanPublic(context).map { it.fileName }.toSet()
            val pending = _state.value.longTerm.filter { rec ->
                val name = runCatching { File(URI(rec.fileUrl)).name }.getOrNull()
                name != null && name !in backedUp && !rec.isPublicVirtual
            }
            pending.forEach { backupToPublic(it) }
            _state.value = _state.value.copy(needsPublicGrant = false)
        }
    }

    /** 关闭"需要备份文件夹"提示 (用户选择暂不). */
    fun dismissPublicGrantPrompt() {
        _state.value = _state.value.copy(needsPublicGrant = false)
    }

    /**
     * 冷启动/进入时补扫: 已有长期录音但未备份 → 提示授权; 已授权 → 自动补备份.
     */
    private suspend fun sweepUnbacked() {
        if (!PublicDirManager.hasGrant(context)) {
            val longs = _state.value.longTerm
            if (longs.any { !it.isPublicVirtual }) {
                _state.value = _state.value.copy(needsPublicGrant = true)
            }
            return
        }
        retryPendingBackups()
    }

    /**
     * 扫描备份文件夹中尚未导入的 .m4a 文件 (同名已存在于长期目录的跳过).
     */
    suspend fun scanImportable(context: Context): List<PublicDirManager.PublicFileInfo> {
        val all = PublicDirManager.scanPublic(context)
        val existing = longtermDir(context).listFiles()?.map { it.name }?.toSet() ?: emptySet()
        return all.filter { it.fileName !in existing }
    }

    /**
     * 从备份文件夹导入: 复制到应用私有长期目录, 作为普通长期录音.
     * 删除这类录音只删除私有副本, 备份文件夹中的原始文件保持不动 (永久备份).
     * @return 实际新增数量.
     */
    suspend fun importFromPublicDir(context: Context, files: List<PublicDirManager.PublicFileInfo>): Int {
        val destDir = longtermDir(context)
        var added = 0
        files.forEach { info ->
            val dest = File(destDir, info.fileName)
            if (dest.exists()) return@forEach // 已导入过
            val copied = runCatching {
                val input = context.contentResolver.openInputStream(info.uri) ?: return@runCatching false
                input.use { src -> dest.outputStream().use { dst -> src.copyTo(dst) } }
                true
            }.getOrDefault(false)
            if (copied) {
                repository.create(dest, readDurationMs(dest))
                added++
            }
        }
        return added
    }

    /** 读取音频时长 (ms), 失败返回 0. */
    private fun readDurationMs(file: File): Long = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            runCatching { retriever.release() }
        }
    }.getOrDefault(0L)

    // ---- 多选 ----
    /** 全选当前 Tab 下所有录音 (同时进入多选模式). */
    fun selectAll() {
        val current = if (_state.value.tab == ListTab.TEMPORARY) _state.value.temporary else _state.value.longTerm
        _state.value = _state.value.copy(
            selectionMode = true,
            selected = current.map { it.id }.toSet(),
            expandedId = null,
        )
    }

    /** 手动保存到公共目录 (备份失败/未授权时供用户手动触发重试). */
    fun saveToPublic(id: String) {
        viewModelScope.launch {
            val rec = _state.value.temporary.firstOrNull { it.id == id }
                ?: _state.value.longTerm.firstOrNull { it.id == id }
                ?: return@launch
            backupToPublic(rec)
        }
    }

    fun enterSelection(id: String) {
        _state.value = _state.value.copy(selectionMode = true, selected = setOf(id), expandedId = null)
    }

    fun toggleSelect(id: String) {
        val cur = _state.value.selected
        _state.value = _state.value.copy(
            selected = if (id in cur) cur - id else cur + id,
        )
    }

    fun exitSelection() {
        _state.value = _state.value.copy(selectionMode = false, selected = emptySet())
    }

    fun batchMoveToLongTerm() {
        viewModelScope.launch {
            _state.value.selected.forEach { id -> act(id, RecordingCategory.LONG_TERM) }
            exitSelection()
        }
    }

    fun batchDelete() {
        viewModelScope.launch {
            _state.value.selected.forEach { id -> repository.delete(id) }
            exitSelection()
        }
    }

    private fun act(id: String, cat: RecordingCategory) {
        viewModelScope.launch {
            val moved = repository.setCategory(id, cat) ?: return@launch
            // 移入长期 = 触发自动备份.
            if (cat == RecordingCategory.LONG_TERM) backupToPublic(moved)
        }
    }
}
