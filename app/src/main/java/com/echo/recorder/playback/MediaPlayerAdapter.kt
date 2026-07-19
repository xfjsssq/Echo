package com.echo.recorder.playback

import android.media.MediaPlayer

/**
 * MediaPlayer 的薄封装接口. 把 Android 框架类隔离到接口后, [AudioPlayer] 的纯逻辑可在 JVM 单元测试里用假实现验证.
 */
interface MediaPlayerAdapter {
    fun prepare(recordingUrl: String)
    fun play()
    fun pause()
    fun seekTo(ms: Int)
    fun release()
    val isPlaying: Boolean
    val currentPositionMs: Int
    val durationMs: Int
    fun setOnCompletionListener(onComplete: () -> Unit)
}

/** 默认实现: 包一个真实的 Android [MediaPlayer]. */
class AndroidMediaPlayerAdapter : MediaPlayerAdapter {
    private var player: MediaPlayer? = null
    private var completionListener: (() -> Unit)? = null

    override fun prepare(recordingUrl: String) {
        release()
        val p = MediaPlayer()
        // recordingUrl 形如 file:///data/.../recording.m4a, 去掉 file:// 前缀作为路径.
        val path = recordingUrl.removePrefix("file://")
        p.setDataSource(path)
        p.setOnCompletionListener { completionListener?.invoke() }
        p.prepare()
        player = p
    }

    override fun play() {
        player?.start()
    }

    override fun pause() {
        if (player?.isPlaying == true) player?.pause()
    }

    override fun seekTo(ms: Int) {
        player?.seekTo(ms)
    }

    override fun release() {
        player?.setOnCompletionListener(null)
        player?.release()
        player = null
    }

    override val isPlaying: Boolean
        get() = player?.isPlaying ?: false

    override val currentPositionMs: Int
        get() = player?.currentPosition ?: 0

    override val durationMs: Int
        get() = player?.duration ?: 0

    override fun setOnCompletionListener(onComplete: () -> Unit) {
        completionListener = onComplete
    }
}
