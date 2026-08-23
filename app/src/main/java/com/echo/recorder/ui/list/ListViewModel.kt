package com.echo.recorder.ui.list

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echo.recorder.ServiceLocator
import com.echo.recorder.common.PublicDirManager
import com.echo.recorder.common.computeAudioDurationMs
import com.echo.recorder.common.longtermDir
import com.echo.recorder.data.VirtualRefStore
import com.echo.recorder.domain.model.Recording
import com.echo.recorder.domain.model.RecordingCategory
import com.echo.recorder.domain.recording.RecordingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
)

/**
 * 录音列表 ViewModel.
 *
 * - 双分类 Tab (临时/长期), 物理目录即真相.
 * - 多选模式: 长按进入, 批量移至长期 / 批量删除.
 * - 长期目录是私有空间 (随应用卸载删除); 公共目录才是永久存储.
 *   保存到公共目录 = "移动": 查重复制成功后从应用内移除; 没有任何静默自动备份,
 *   不产生用户不知情的副本.
 * - 从公共目录导入 = 建立虚引用 (文件留在公共目录, 列表只加索引, 零物理复制),
 *   同一文件永远不会越导入越多; 已在长期列表的文件不再出现在导入列表.
 */
class ListViewModel(
    private val context: Context,
    private val repository: RecordingRepository = ServiceLocator.repository(context),
) : ViewModel() {

    private val _state = MutableStateFlow(ListUiState())
    val state: StateFlow<ListUiState> = _state.asStateFlow()

    /** 公共目录虚引用索引 (与 Repository 注入的是同一 DataStore 实例). */
    private val virtualRefStore = VirtualRefStore(context)

    init {
        viewModelScope.launch {
            repository.getAll().collect { all ->
                _state.value = _state.value.copy(
                    temporary = all.filter { it.category == RecordingCategory.TEMPORARY },
                    longTerm = all.filter { it.category == RecordingCategory.LONG_TERM },
                )
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

    /** 重命名录音 (改文件系统文件名 + 更新记录). */
    fun renameRecording(id: String, newName: String) {
        viewModelScope.launch {
            repository.rename(id, newName.trim())
        }
    }

    /**
     * 保存到公共目录 ("移动"语义):
     * 查重写入备份文件夹 —— 成功或同名同大小文件已在位 ([PublicDirManager.SaveOutcome.Saved])
     * 则把该录音从应用内移除 (文件实体从此只在公共目录); 冲突/失败则原样保留.
     */
    suspend fun exportToPublic(id: String): PublicDirManager.SaveOutcome {
        val rec = _state.value.longTerm.firstOrNull { it.id == id }
            ?: return PublicDirManager.SaveOutcome.Failed
        if (rec.isPublicVirtual) return PublicDirManager.SaveOutcome.Failed
        val src = runCatching { File(URI(rec.fileUrl)) }.getOrNull()
            ?: return PublicDirManager.SaveOutcome.Failed
        if (!src.exists()) return PublicDirManager.SaveOutcome.Failed
        val outcome = PublicDirManager.saveToPublic(context, src, src.name)
        if (outcome == PublicDirManager.SaveOutcome.Saved) {
            repository.delete(id)
        }
        return outcome
    }

    /**
     * 扫描备份文件夹中尚未进入长期列表的 .m4a 文件.
     * 去重判据 = 文件名: 私有长期目录已有同名文件, 或已建立虚引用的, 一律不再显示
     * (文件本体仍在公共目录, 只是不重复出现在导入列表).
     */
    suspend fun scanImportable(): List<PublicDirManager.PublicFileInfo> {
        val all = PublicDirManager.scanPublic(context)
        if (all.isEmpty()) return emptyList()
        val virtualNames = virtualRefStore.refs.first().map { it.displayName }.toSet()
        val privateNames = longtermDir(context).listFiles()?.map { it.name }?.toSet() ?: emptySet()
        return all.filter { it.fileName !in virtualNames && it.fileName !in privateNames }
    }

    /**
     * 从备份文件夹导入 = 建立虚引用: 文件留在公共目录 (卸载不丢), 列表只加索引,
     * 零物理复制 —— 同一文件不会再越导入越多份. 按文件名去重.
     * @return 实际新增数量.
     */
    suspend fun importFromPublicDir(files: List<PublicDirManager.PublicFileInfo>): Int {
        if (files.isEmpty()) return 0
        val existingNames = virtualRefStore.refs.first().map { it.displayName }.toSet()
        var added = 0
        files.forEach { info ->
            if (info.fileName in existingNames) return@forEach
            val rec = Recording(
                // id 带前缀, 避免与私有目录里恰好同名的文件在列表/删除路由中冲突.
                id = "public/${info.fileName}",
                displayName = info.fileName,
                fileUrl = info.uri.toString(),
                createdAt = info.lastModified,
                durationMs = computeAudioDurationMs(context, info.uri),
                category = RecordingCategory.LONG_TERM,
                isPublicVirtual = true,
            )
            virtualRefStore.add(rec)
            added++
        }
        return added
    }

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
            repository.setCategory(id, cat)
        }
    }
}
