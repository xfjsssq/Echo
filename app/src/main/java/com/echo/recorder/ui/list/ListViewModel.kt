package com.echo.recorder.ui.list

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echo.recorder.ServiceLocator
import com.echo.recorder.common.PublicDirManager
import com.echo.recorder.domain.model.Recording
import com.echo.recorder.domain.model.RecordingCategory
import com.echo.recorder.domain.recording.RecordingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
 * - 单条: 移至长期 / 删除.
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
     * 保存到公共目录: 复制到公共目录后立即从列表移除 (不保留虚引用).
     * 文件从此只存在于公共目录, 应用内不再可见.
     * @return 成功返回 true.
     */
    suspend fun saveToPublic(context: Context, rec: Recording): Boolean {
        val src = runCatching { java.io.File(java.net.URI(rec.fileUrl)) }.getOrNull() ?: return false
        if (!src.exists()) return false
        // 复制到公共目录 (仅校验是否成功, 不保留 dest).
        val ok = PublicDirManager.copyToPublic(context, rec, src) != null
        if (!ok) return false
        // 删除私有原文件并从数据源移除 (不登记虚引用).
        runCatching { if (src.exists()) src.delete() }
        repository.delete(rec.id)
        return true
    }

    /**
     * 扫描公共目录中尚未导入的 .m4a 文件.
     * @return 可导入的文件信息列表.
     */
    suspend fun scanImportable(context: Context): List<PublicDirManager.PublicFileInfo> {
        val all = PublicDirManager.scanPublic(context)
        val vfs = ServiceLocator.virtualRefStore(context)
        return all.filter { !vfs.exists(java.io.File(PublicDirManager.publicDir(), it.fileName).toURI().toString()) }
    }

    /**
     * 从公共目录导入指定文件为虚引用 (不复制文件).
     * @return 实际新增数量.
     */
    suspend fun importFromPublicDir(context: Context, fileNames: List<String>): Int {
        val repoImpl = repository as? com.echo.recorder.data.RecordingRepositoryImpl ?: return 0
        val vfs = ServiceLocator.virtualRefStore(context)
        var added = 0
        val now = System.currentTimeMillis()
        fileNames.forEach { name ->
            val file = java.io.File(PublicDirManager.publicDir(), name)
            val fileUrl = file.toURI().toString()
            if (vfs.exists(fileUrl)) return@forEach
            repoImpl.addVirtualRef(
                Recording(
                    id = name,
                    displayName = name,
                    fileUrl = fileUrl,
                    createdAt = now,
                    durationMs = 0L,
                    category = RecordingCategory.LONG_TERM,
                    isPublicVirtual = true,
                ),
            )
            added++
        }
        return added
    }

    private val vfs get() = ServiceLocator.virtualRefStore(context)

    // ---- 多选 ----
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
        viewModelScope.launch { repository.setCategory(id, cat) }
    }
}
