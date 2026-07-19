package com.echo.recorder.ui.record

import androidx.lifecycle.ViewModel
import com.echo.recorder.Recorder
import com.echo.recorder.ServiceLocator
import com.echo.recorder.appContext
import com.echo.recorder.domain.recording.RecordingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 录音页 UI 状态. */
data class RecordUiState(
    val isRecording: Boolean = false,
    val elapsedMs: Long = 0L,
    val hasPermission: Boolean = false,
)

/**
 * 录音页 ViewModel.
 *
 * - 持有权限状态 [RecordUiState.hasPermission] (由 Activity 的权限回调写入)
 * - [onRecordPressed] 切换录音 / 停止; 未授权时不作为
 * - 通过 [setRecorder] 注入真实服务; 单元测试注入假的
 */
class RecordViewModel(
    private val repository: RecordingRepository = ServiceLocator.repository(appContext()),
    private var recorder: Recorder? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(RecordUiState())
    val state: StateFlow<RecordUiState> = _state.asStateFlow()

    /** Activity 在服务绑定后注入 Recorder. */
    fun setRecorder(recorder: Recorder) {
        this.recorder = recorder
    }

    /** Activity 在权限回调里写入授权结果. */
    fun setHasPermission(granted: Boolean) {
        _state.value = _state.value.copy(hasPermission = granted)
    }

    /** 点击录音按钮. 无权限时不作为. */
    fun onRecordPressed() {
        val cur = _state.value
        if (!cur.hasPermission) return
        if (cur.isRecording) {
            recorder?.stopRecording()
        } else {
            recorder?.startRecording()
        }
    }

    /** Activity 把服务的实时状态镜像到 UI. */
    fun syncFrom(isRecording: Boolean, elapsedMs: Long) {
        _state.value = _state.value.copy(isRecording = isRecording, elapsedMs = elapsedMs)
    }
}
