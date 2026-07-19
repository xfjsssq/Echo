package com.echo.recorder

import com.echo.recorder.domain.model.Recording
import com.echo.recorder.domain.recording.RecordingRepository
import com.echo.recorder.ui.navigation.EchoRoutes
import com.echo.recorder.ui.record.RecordViewModel
import com.echo.recorder.ui.record.RecordUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 * 导航骨架的可测核心. 路由跳转的完整验证依赖 Compose UI Test (设备), 此处覆盖:
 * - [EchoRoutes] 常量与参数化路由构造
 * - 首页 VM 在导航宿主下仍暴露正确初始状态
 */
class EchoNavHostTest {

    @Test
    fun `路由常量正确`() {
        assertEquals("record", EchoRoutes.RECORD)
        assertEquals("list", EchoRoutes.LIST)
        assertEquals("player/{recordingId}", EchoRoutes.PLAYER_WITH_ARG)
    }

    @Test
    fun `playerRoute 拼出带参路由`() {
        assertEquals("player/abc123", EchoRoutes.playerRoute("abc123"))
    }

    @Test
    fun `playerRoute 处理特殊字符 id`() {
        assertEquals("player/id-2026_07", EchoRoutes.playerRoute("id-2026_07"))
    }

    @Test
    fun `首页 VM 初始状态为 idle 无权限`() {
        val vm = RecordViewModel(repository = NoopRepository)
        val s: RecordUiState = vm.state.value
        assertFalse(s.isRecording)
        assertEquals(0L, s.elapsedMs)
        assertFalse(s.hasPermission)
    }

    private object NoopRepository : RecordingRepository {
        override fun getAll(): Flow<List<Recording>> = emptyFlow()
        override suspend fun getById(id: String) =
            Recording(id, id, "file:///$id", 0L, 0L)
        override suspend fun create(file: File, durationMs: Long) =
            Recording(file.name, file.name, "file:///${file.name}", 0L, durationMs)
        override suspend fun delete(id: String) = false
    }
}
