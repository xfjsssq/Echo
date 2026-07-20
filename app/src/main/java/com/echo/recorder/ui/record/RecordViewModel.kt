package com.echo.recorder.ui.record

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echo.recorder.ServiceLocator
import com.echo.recorder.appContext
import com.echo.recorder.domain.model.Recording
import com.echo.recorder.domain.model.RecordingCategory
import com.echo.recorder.domain.recording.RecordingRepository
import com.echo.recorder.service.RecordingService
import com.echo.recorder.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 录音页 UI 状态. */
data class RecordUiState(
    val phase: RecordingService.Phase = RecordingService.Phase.IDLE,
    val bufferSeconds: Int = 180,
    val hasPermission: Boolean = false,
    /** 冷启动恢复: 非 null 表示有未处理录音等待用户决定进入. */
    val pendingRecovery: Recording? = null,
    /** 彻底退出是否被密码门控. */
    val passwordEnabled: Boolean = false,
    /** 暂停后正在后台保存文件, UI 显示加载态. */
    val saving: Boolean = false,
)

/**
 * 录音页 ViewModel.
 *
 * - 通过 [setRecorder] 注入真实服务后, 订阅其 phase 镜像到 UI.
 * - 启动时检查 UNPROCESSED 录音, 若有则置 [RecordUiState.pendingRecovery] 阻塞进入.
 * - 驱动 IDLE -> BUFFERING -> REVIEW -> BUFFERING 流程.
 */
class RecordViewModel(
    context: Context = appContext(),
    private val repository: RecordingRepository = ServiceLocator.repository(context),
) : ViewModel() {

    private val _state = MutableStateFlow(RecordUiState())
    val state: StateFlow<RecordUiState> = _state.asStateFlow()

    private var service: RecordingService? = null
    private val settings = SettingsRepository(context)

    init {
        // 监听设置里的缓冲时长 + 密码门控, 反映到 UI.
        viewModelScope.launch {
            settings.bufferSeconds.collect { sec ->
                _state.value = _state.value.copy(bufferSeconds = sec)
            }
        }
        viewModelScope.launch {
            settings.passwordEnabled.collect { on ->
                _state.value = _state.value.copy(passwordEnabled = on)
            }
        }
    }

    /** Activity 在服务绑定后注入服务. */
    fun setRecorder(recorder: RecordingService) {
        service = recorder
        viewModelScope.launch {
            recorder.phase.collect { p -> _state.value = _state.value.copy(phase = p) }
        }
        viewModelScope.launch {
            recorder.saving.collect { s -> _state.value = _state.value.copy(saving = s) }
        }
    }

    /** Activity 写入权限结果. */
    fun setHasPermission(granted: Boolean) {
        _state.value = _state.value.copy(hasPermission = granted)
    }

    /** 检查未处理录音 (冷启动恢复). 有则阻塞, 等用户决定. */
    fun checkUnprocessed() {
        viewModelScope.launch {
            // 取首个 UNPROCESSED (应只有一个). 取首次发射的列表即可.
            val list = repository.getAll().first()
            val found = list.firstOrNull { it.category == RecordingCategory.UNPROCESSED }
            if (found != null) {
                _state.value = _state.value.copy(pendingRecovery = found)
            }
        }
    }

    // ---- 按钮动作 ----

    fun onStartPressed() = service?.startBuffer()

    fun onPausePressed() = service?.pause()

    fun onSavePressed() = service?.save()

    fun onDeletePressed() = service?.deletePending()

    /** 恢复对话框: 保留 (标为临时). */
    fun recoverKeep() {
        val rec = _state.value.pendingRecovery ?: return
        viewModelScope.launch {
            repository.setCategory(rec.id, RecordingCategory.TEMPORARY)
            _state.value = _state.value.copy(pendingRecovery = null)
        }
    }

    /** 恢复对话框: 删除. */
    fun recoverDiscard() {
        val rec = _state.value.pendingRecovery ?: return
        viewModelScope.launch {
            repository.delete(rec.id)
            _state.value = _state.value.copy(pendingRecovery = null)
        }
    }

    /** 彻底退出: 干净退出, 不产生紧急保存文件 (下次启动不弹恢复). */
    fun exitCompletely() {
        service?.shutdownCleanly()
        service = null
    }
}
