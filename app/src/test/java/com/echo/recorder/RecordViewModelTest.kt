package com.echo.recorder

import com.echo.recorder.data.RecordingRepositoryImpl
import com.echo.recorder.domain.model.Recording
import com.echo.recorder.domain.recording.RecordingRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** 假的 Recorder, 记录调用并回吐预设的 Recording. */
private class FakeRecorder(
    val started: MutableList<Unit> = mutableListOf(),
    val stopped: MutableList<Unit> = mutableListOf(),
    var toReturn: Recording? = null,
) : Recorder {
    override fun startRecording() { started.add(Unit) }
    override fun stopRecording(): Recording? { stopped.add(Unit); return toReturn }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RecordViewModelTest {

    private class FakeRepository(
        val created: MutableList<Pair<File, Long>> = mutableListOf(),
        val recordings: MutableStateFlow<List<Recording>> = MutableStateFlow(emptyList()),
    ) : RecordingRepository {
        override fun getAll() = recordings
        override suspend fun getById(id: String): Recording = recordings.value.first { it.id == id }
        override suspend fun create(file: File, durationMs: Long): Recording {
            created.add(file to durationMs)
            return Recording(file.name, file.name, file.toURI().toString(), 1L, durationMs)
        }
        override suspend fun delete(id: String): Boolean = false
    }

    private lateinit var repo: FakeRepository
    private lateinit var recorder: FakeRecorder
    private lateinit var vm: RecordViewModel

    @Before
    fun setUp() {
        repo = FakeRepository()
        recorder = FakeRecorder()
        vm = RecordViewModel(repository = repo, recorder = recorder)
    }

    @Test
    fun `startRecording 委派给 recorder`() {
        vm.startRecording()
        assertEquals(1, recorder.started.size)
    }

    @Test
    fun `stopRecording 委派给 recorder`() {
        vm.stopRecording()
        assertEquals(1, recorder.stopped.size)
    }

    @Test
    fun `未注入 recorder 时调用不会抛异常`() {
        val emptyVm = RecordViewModel(repository = repo, recorder = null)
        emptyVm.startRecording()
        emptyVm.stopRecording()
        assertTrue(recorder.started.isEmpty())
    }

    @Test
    fun `syncFrom 更新 recording 与 elapsedMs`() = runTest {
        vm.syncFrom(true, 3000L)
        assertTrue(vm.recording.value)
        assertEquals(3000L, vm.elapsedMs.value)

        vm.syncFrom(false, 0L)
        assertFalse(vm.recording.value)
        assertEquals(0L, vm.elapsedMs.value)
    }
}
