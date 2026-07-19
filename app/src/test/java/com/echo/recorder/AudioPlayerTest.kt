package com.echo.recorder

import com.echo.recorder.domain.model.Recording
import com.echo.recorder.playback.AudioPlayer
import com.echo.recorder.playback.MediaPlayerAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 假 MediaPlayer: 记录调用 + 可供测试驱动内部状态. 跨测试文件可见. */
internal class FakeMediaPlayer : MediaPlayerAdapter {
    @Volatile var preparedUrl: String? = null
    @Volatile var playing = false
    @Volatile var positionMs = 0
    @Volatile override var durationMs = 5_000
    @Volatile var released = false
    var seekTarget: Int? = null
    var complete: (() -> Unit)? = null

    override fun prepare(recordingUrl: String) {
        preparedUrl = recordingUrl
        playing = false
        positionMs = 0
    }

    override fun play() { playing = true }
    override fun pause() { playing = false }

    override fun seekTo(ms: Int) {
        seekTarget = ms
        positionMs = ms
    }

    override fun release() {
        released = true
        playing = false
    }

    override val isPlaying: Boolean get() = playing
    override val currentPositionMs: Int get() = positionMs

    override fun setOnCompletionListener(onComplete: () -> Unit) {
        this.complete = onComplete
    }
}

internal fun testRec(url: String = "file:///tmp/echo_test.m4a") = Recording(
    id = "test", displayName = "echo_test.m4a", fileUrl = url, createdAt = 1L, durationMs = 5_000L,
)

class AudioPlayerTest {

    /** 默认 scope 的 player; 不传测试作用域, 避免 runTest 作用域被取消时拖挂. */
    private fun player(fake: MediaPlayerAdapter): AudioPlayer = AudioPlayer(fake)

    @Test
    fun `prepare 准备录音并带出时长`() {
        val fake = FakeMediaPlayer()
        val p = player(fake)

        p.prepare(testRec())

        assertEquals("file:///tmp/echo_test.m4a", fake.preparedUrl)
        val s = p.stateFlow.value
        assertEquals(5000, s.durationMs)
        assertEquals(0, s.currentPositionMs)
        assertFalse(s.isPlaying)
        p.release()
    }

    @Test
    fun `play 切换为 playing`() {
        val fake = FakeMediaPlayer()
        val p = player(fake)
        p.prepare(testRec())

        p.play()

        assertTrue(p.stateFlow.value.isPlaying)
        p.release()
    }

    @Test
    fun `pause 切换回非 playing 并保留位置`() {
        val fake = FakeMediaPlayer()
        val p = player(fake)
        p.prepare(testRec())
        p.play()
        fake.positionMs = 2000

        p.pause()

        val s = p.stateFlow.value
        assertFalse(s.isPlaying)
        assertEquals(2000, s.currentPositionMs)
        p.release()
    }

    @Test
    fun `seekTo 跳到指定位置`() {
        val fake = FakeMediaPlayer()
        val p = player(fake)
        p.prepare(testRec())

        p.seekTo(1500)

        assertEquals(1500, fake.seekTarget)
        assertEquals(1500, p.stateFlow.value.currentPositionMs)
        p.release()
    }

    @Test
    fun `播放完成后自动暂停并归零`() {
        val fake = FakeMediaPlayer()
        val p = player(fake)
        p.prepare(testRec())
        p.play()

        fake.complete?.invoke()

        val s = p.stateFlow.value
        assertFalse(s.isPlaying)
        assertEquals(0, s.currentPositionMs)
        p.release()
    }

    @Test
    fun `release 释放底层资源`() {
        val fake = FakeMediaPlayer()
        val p = player(fake)
        p.prepare(testRec())

        p.release()

        assertTrue(fake.released)
    }
}
