package com.echo.recorder.playback

/**
 * 创建 [AudioPlayer] 的工厂. 默认实现产出带真实 Android 回放能力的 player;
 * 测试注入假的即可隔离 MediaPlayer.
 */
interface AudioPlayerFactory {
    fun create(): AudioPlayer
}

class DefaultAudioPlayerFactory(
    private val adapterFactory: () -> MediaPlayerAdapter = ::AndroidMediaPlayerAdapter,
) : AudioPlayerFactory {
    override fun create(): AudioPlayer = AudioPlayer(adapterFactory())
}
