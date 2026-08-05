package com.echo.recorder

import com.echo.recorder.data.RecordingDataSource
import com.echo.recorder.data.RecordingRepositoryImpl
import com.echo.recorder.domain.model.Recording
import com.echo.recorder.domain.recording.RecordingRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

private class FakeDataSource(
    val mutable: MutableStateFlow<List<Recording>> = MutableStateFlow(emptyList()),
) : RecordingDataSource {
    override val state get() = mutable
    override fun load() {}
    override fun getById(id: String): Recording? = mutable.value.firstOrNull { it.id == id }
    override fun upsert(file: File, durationMs: Long, now: Long): Recording {
        val rec = Recording(file.name, file.name, file.toURI().toString(), now, durationMs)
        mutable.value = mutable.value.filterNot { it.id == rec.id } + rec
        return rec
    }
    override fun delete(id: String): Boolean {
        val before = mutable.value.size
        mutable.value = mutable.value.filterNot { it.id == id }
        return mutable.value.size < before
    }

    override fun deleteExpiredTemporary(maxAgeMs: Long): Int {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        val expired = mutable.value.filter {
            it.category == com.echo.recorder.domain.model.RecordingCategory.TEMPORARY && it.createdAt < cutoff
        }
        if (expired.isEmpty()) return 0
        val ids = expired.map { it.id }.toSet()
        mutable.value = mutable.value.filterNot { it.id in ids }
        return expired.size
    }

    override fun setCategory(id: String, category: com.echo.recorder.domain.model.RecordingCategory): Recording? {
        val target = mutable.value.firstOrNull { it.id == id } ?: return null
        val updated = target.copy(category = category)
        mutable.value = mutable.value.map { if (it.id == id) updated else it }
        return updated
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingRepositorySmokeTest {

    @Test
    fun `getAll - 订阅时拿到按 createdAt 倒序的快照`() = runTest {
        val fake = FakeDataSource()
        val repo: RecordingRepository = RecordingRepositoryImpl(fake)

        fake.mutable.value = listOf(
            rec("a", 1000L),
            rec("b", 3000L),
            rec("c", 2000L),
        )
        val got = repo.getAll().first()

        assertEquals(listOf("b", "c", "a"), got.map { it.id })
    }

    private fun rec(id: String, createdAt: Long) = Recording(
        id = id,
        displayName = "echo_$id.m4a",
        fileUrl = "file:///tmp/echo_$id.m4a",
        createdAt = createdAt,
        durationMs = 1000L,
    )
}
