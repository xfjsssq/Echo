package com.echo.recorder.ui.list

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echo.recorder.ServiceLocator
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
    context: Context,
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
        _state.value = _state.value.copy(tab = tab, expandedId = null)
    }

    // ---- 展开 (原地播放) ----
    fun toggleExpanded(id: String) {
        val cur = _state.value.expandedId
        _state.value = _state.value.copy(expandedId = if (cur == id) null else id)
    }

    // ---- 单条操作 ----
    fun moveToLongTerm(id: String) = act(id, RecordingCategory.LONG_TERM)
    fun delete(id: String) = viewModelScope.launch { repository.delete(id) }

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
