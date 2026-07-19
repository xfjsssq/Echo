package com.echo.recorder.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echo.recorder.domain.model.Recording
import com.echo.recorder.playback.AudioPlayer
import com.echo.recorder.playback.AudioPlayerFactory
import com.echo.recorder.playback.AudioPlayerState
import com.echo.recorder.playback.DefaultAudioPlayerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 播放页 UI 状态. */
data class PlayerUiState(
    val title: String = "",
    val isPlaying: Boolean = false,
    val currentPositionMs: Int = 0,
    val durationMs: Int = 0,
    val found: Boolean = true,
)

/**
 * 播放页 ViewModel. 持有 [AudioPlayer], 在 [onCleared] 里释放.
 *
 * @param scope 收集 player 状态的作用域; 生产传入 viewModelScope, 测试传入 TestScope 等.
 */
class PlayerViewModel(
    private val factory: AudioPlayerFactory = DefaultAudioPlayerFactory(),
    private val recordings: () -> List<Recording>,
    scope: CoroutineScope = CoroutineScope(SupervisorJob()),
) : ViewModel() {

    private val player: AudioPlayer = factory.create()
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var current: Recording? = null

    init {
        // 把底层 player 状态映射到 UI 状态.
        scope.launch {
            player.stateFlow.collect { s -> _state.value = s.toUi(current?.displayName ?: "") }
        }
    }

    private fun AudioPlayerState.toUi(title: String) = PlayerUiState(
        title = title,
        isPlaying = isPlaying,
        currentPositionMs = currentPositionMs,
        durationMs = durationMs,
        found = current != null,
    )

    /** 按 id 找到录音并准备播放. 找不到则置 found=false. */
    fun load(recordingId: String) {
        val rec = recordings().firstOrNull { it.id == recordingId }
        current = rec
        if (rec == null) {
            _state.value = PlayerUiState(found = false)
            return
        }
        player.prepare(rec)
        _state.value = player.stateFlow.value.toUi(rec.displayName)
    }

    fun play() = player.play()
    fun pause() = player.pause()
    fun seekTo(ms: Int) = player.seekTo(ms)

    override fun onCleared() {
        super.onCleared()
        player.release()
    }
}
