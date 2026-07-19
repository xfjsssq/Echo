package com.echo.recorder

import androidx.lifecycle.ViewModel
import com.echo.recorder.domain.recording.RecordingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 录音界面 ViewModel. 负责驱动录音并把状态推给 UI.
 *
 * 依赖 [Recorder] 接口而非具体服务, 因此单元测试注入假实现即可, 无需绑定真实服务.
 *
 * @param repository   录音仓库
 * @param recorder     可为空: 未绑定时为空; 测试可直接注入假的; 真实 app 在服务绑定后设置.
 */
class RecordViewModel(
    private val repository: RecordingRepository = ServiceLocator.repository(appContext()),
    private var recorder: Recorder? = null,
) : ViewModel() {

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()

    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    /** 真实 app 在 ServiceConnection.onServiceConnected 里调用; 测试直接注入 Recorder 后也可省略. */
    fun setRecorder(recorder: Recorder) {
        this.recorder = recorder
    }

    fun startRecording() {
        recorder?.startRecording()
    }

    fun stopRecording() {
        recorder?.stopRecording()
    }

    /** 供 UI 镜像 Recorder 的 StateFlow (简化版, 真实 app 中通过 binder 监听 service.state). */
    fun syncFrom(isRecording: Boolean, elapsedMs: Long) {
        _recording.value = isRecording
        _elapsedMs.value = elapsedMs
    }
}
