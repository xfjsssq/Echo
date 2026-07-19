package com.echo.recorder

import com.echo.recorder.domain.model.Recording
import com.echo.recorder.domain.recording.RecordingRepository
import com.echo.recorder.ui.record.RecordViewModel
import com.echo.recorder.ui.record.RecordUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

private class FakeRecorder(
    val started: Int = 0,
) : Recorder {
    var startedCount = 0
    var stoppedCount = 0
    override fun startRecording() { startedCount++ }
    override fun stopRecording(): Recording? { stoppedCount++; return null }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RecordViewModelTest {

    private class FakeRepository(
        val recordings: MutableStateFlow<List<Recording>> = MutableStateFlow(emptyList()),
    ) : RecordingRepository {
        override fun getAll() = recordings
        override suspend fun getById(id: String): Recording = recordings.value.first { it.id == id }
        override suspend fun create(file: File, durationMs: Long): Recording =
            Recording(file.name, file.name, file.toURI().toString(), 1L, durationMs)
        override suspend fun delete(id: String): Boolean = false
    }

    private lateinit var recorder: FakeRecorder
    private lateinit var vm: RecordViewModel

    @Before
    fun setUp() {
        recorder = FakeRecorder()
        vm = RecordViewModel(repository = FakeRepository())
        vm.setRecorder(recorder)
    }

    @Test
    fun `初始状态 idle 且无权限`() {
        val s = vm.state.value
        assertFalse(s.isRecording)
        assertEquals(0L, s.elapsedMs)
        assertFalse(s.hasPermission)
    }

    @Test
    fun `无权限时 onRecordPressed 不启动录音`() {
        vm.setHasPermission(false)
        vm.onRecordPressed()
        assertEquals(0, recorder.startedCount)
    }

    @Test
    fun `有权限时 onRecordPressed 启动录音`() {
        vm.setHasPermission(true)
        vm.onRecordPressed()
        assertEquals(1, recorder.startedCount)
    }

    @Test
    fun `录音中再次点击 onRecordPressed 停止`() {
        vm.setHasPermission(true)
        vm.onRecordPressed() // start
        vm.syncFrom(true, 1000L)
        vm.onRecordPressed() // stop
        assertEquals(1, recorder.stoppedCount)
        assertEquals(1, recorder.startedCount)
    }

    @Test
    fun `setHasPermission 写入权限状态`() {
        vm.setHasPermission(true)
        assertTrue(vm.state.value.hasPermission)
    }

    @Test
    fun `syncFrom 更新 recording 与 elapsedMs`() {
        vm.syncFrom(true, 5000L)
        val s = vm.state.value
        assertTrue(s.isRecording)
        assertEquals(5000L, s.elapsedMs)
    }
}
