package com.echo.recorder.playback

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri

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

/** 默认实现: 包一个真实的 Android [MediaPlayer], 支持 file:// 和 content:// URI. */
class AndroidMediaPlayerAdapter(
    private val context: Context,
) : MediaPlayerAdapter {
    private var player: MediaPlayer? = null
    private var completionListener: (() -> Unit)? = null

    override fun prepare(recordingUrl: String) {
        release()
        val p = MediaPlayer()
        if (recordingUrl.startsWith("content://")) {
            // 虚引用录音: 通过 ContentResolver 读取, 需要 Context.
            p.setDataSource(context, Uri.parse(recordingUrl))
        } else {
            // 本地录音: file:///data/.../recording.m4a, 去掉 file:// 前缀作为路径.
            p.setDataSource(recordingUrl.removePrefix("file://"))
        }
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
