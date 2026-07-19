package com.echo.recorder.playback

import com.echo.recorder.domain.model.Recording
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 播放状态. */
data class AudioPlayerState(
    val isPlaying: Boolean = false,
    val currentPositionMs: Int = 0,
    val durationMs: Int = 0,
)

/**
 * 回放内核. 封装 [MediaPlayerAdapter], 把裸 MediaPlayer 能力转成 [StateFlow].
 *
 * 生命周期: 宿主 (PlayerViewModel) 销毁时必须调 [release].
 */
class AudioPlayer(
    private val adapter: MediaPlayerAdapter,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _state = MutableStateFlow(AudioPlayerState())
    val stateFlow: StateFlow<AudioPlayerState> = _state.asStateFlow()

    private var tickJob: Job? = null

    /** 准备一条录音进入待播. */
    fun prepare(recording: Recording) {
        stopTick()
        adapter.prepare(recording.fileUrl)
        adapter.setOnCompletionListener { onPlaybackComplete() }
        _state.value = AudioPlayerState(
            isPlaying = false,
            currentPositionMs = 0,
            durationMs = adapter.durationMs,
        )
    }

    fun play() {
        adapter.play()
        // 立即同步一次, 避免依赖 tick 延迟 (也让无 tick 的单元测试可断言).
        _state.value = _state.value.copy(
            isPlaying = true,
            currentPositionMs = adapter.currentPositionMs,
            durationMs = adapter.durationMs,
        )
        startTick()
    }

    fun pause() {
        stopTick()
        adapter.pause()
        _state.value = _state.value.copy(isPlaying = false, currentPositionMs = adapter.currentPositionMs)
    }

    fun seekTo(ms: Int) {
        adapter.seekTo(ms)
        _state.value = _state.value.copy(currentPositionMs = adapter.currentPositionMs)
    }

    /** 释放底层资源; 调用后本实例不可再用. */
    fun release() {
        stopTick()
        adapter.release()
        scope.cancel()
    }

    private fun onPlaybackComplete() {
        stopTick()
        adapter.seekTo(0)
        _state.value = _state.value.copy(isPlaying = false, currentPositionMs = 0)
    }

    private fun startTick() {
        stopTick()
        tickJob = scope.launch {
            while (isActive) {
                if (adapter.isPlaying) {
                    _state.value = _state.value.copy(
                        currentPositionMs = adapter.currentPositionMs,
                        durationMs = adapter.durationMs,
                    )
                }
                delay(TICK_INTERVAL_MS)
            }
        }
    }

    private fun stopTick() {
        tickJob?.cancel()
        tickJob = null
    }

    companion object {
        private const val TICK_INTERVAL_MS = 200L
    }
}
