package com.echo.recorder

import com.echo.recorder.domain.model.Recording
import com.echo.recorder.playback.AudioPlayerFactory
import com.echo.recorder.ui.player.PlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 注入工厂 -> 真实 AudioPlayer -> 假 MediaPlayer. 验证 PlayerViewModel 的 load/映射/found/转发逻辑.
 */
private class TrackingFactory(
    val adapterFactory: () -> FakeMediaPlayer = { FakeMediaPlayer() },
) : AudioPlayerFactory {
    lateinit var lastAdapter: FakeMediaPlayer
        private set

    override fun create() = com.echo.recorder.playback.AudioPlayer(adapterFactory().also { lastAdapter = it })
}

private fun sampleRecordings() = listOf(
    Recording("a", "echo_a.m4a", "file:///tmp/echo_a.m4a", 1L, 4000L),
    Recording("b", "echo_b.m4a", "file:///tmp/echo_b.m4a", 2L, 6000L),
)

class PlayerViewModelTest {

    private val factory = TrackingFactory()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun tearDown() { scope.cancel() }

    private fun makeVm(recordings: () -> List<Recording>): PlayerViewModel =
        PlayerViewModel(factory = factory, recordings = recordings, scope = scope)

    /** 阻塞等待 StateFlow 满足条件 或超时. channel B 无 runTest 用轮询兜底. */
    private fun PlayerViewModel.waitFor(predicate: (com.echo.recorder.ui.player.PlayerUiState) -> Boolean) {
        val deadline = System.currentTimeMillis() + 3000
        while (System.currentTimeMillis() < deadline) {
            if (predicate(state.value)) return
            Thread.sleep(10)
        }
    }

    @Test
    fun `load 找到录音则 found=true 且带出标题与时长`() {
        val vm = makeVm { sampleRecordings() }

        vm.load("b")
        vm.waitFor { it.found && it.title == "echo_b.m4a" }

        val s = vm.state.value
        assertTrue(s.found)
        assertEquals("echo_b.m4a", s.title)
        assertEquals(5000, s.durationMs) // fake adapter 默认 durationMs=5000
        assertTrue(factory.lastAdapter.preparedUrl?.contains("echo_b.m4a") == true)
    }

    @Test
    fun `load 找不到录音则 found=false`() {
        val vm = makeVm { sampleRecordings() }

        vm.load("ghost")
        vm.waitFor { !it.found }

        assertFalse(vm.state.value.found)
    }

    @Test
    fun `play 转发到 player`() {
        val vm = makeVm { sampleRecordings() }
        vm.load("a")
        vm.waitFor { it.title == "echo_a.m4a" }

        vm.play()

        assertTrue(factory.lastAdapter.isPlaying)
    }

    @Test
    fun `seekTo 转发到 player 并更新位置`() {
        val vm = makeVm { sampleRecordings() }
        vm.load("a")
        vm.waitFor { it.title == "echo_a.m4a" }

        vm.seekTo(1500)
        vm.waitFor { it.currentPositionMs == 1500 }

        assertEquals(1500, factory.lastAdapter.seekTarget)
        assertEquals(1500, vm.state.value.currentPositionMs)
    }
}
