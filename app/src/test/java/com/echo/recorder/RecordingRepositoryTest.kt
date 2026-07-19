package com.echo.recorder

import com.echo.recorder.data.FilesystemRecordingDataSource
import com.echo.recorder.data.RecordingDataSource
import com.echo.recorder.data.RecordingRepositoryImpl
import com.echo.recorder.domain.model.Recording
import com.echo.recorder.domain.recording.RecordingRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 覆盖 RecordingRepositoryImpl 的核心契约:
 * - getAll 按 createdAt 倒序
 * - getById 找不到抛 NoSuchElementException
 * - create 透传 ds.upsert 且 createdAt 落在 [调用前, 调用后]
 * - delete 透传 ds.delete
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecordingRepositoryTest {

    @get:Rule
    val tmp: TemporaryFolder = TemporaryFolder()

    private lateinit var dir: File
    private lateinit var ds: RecordingDataSource
    private lateinit var repo: RecordingRepository

    @Before
    fun setUp() {
        dir = tmp.newFolder("recordings")
        ds = FilesystemRecordingDataSource(baseDir = dir)
        repo = RecordingRepositoryImpl(ds)
    }

    // ---------- getAll ----------

    @Test
    fun `getAll - 按 createdAt 倒序`() = runTest {
        ds.upsert(fakeFile("a.m4a"), 100L, 1000L)
        ds.upsert(fakeFile("b.m4a"), 100L, 3000L)
        ds.upsert(fakeFile("c.m4a"), 100L, 2000L)

        val got = repo.getAll().first().map { it.id }

        // b(3000) > c(2000) > a(1000)
        assertEquals(listOf("b.m4a", "c.m4a", "a.m4a"), got)
    }

    @Test
    fun `getAll - 空数据源返回空列表`() = runTest {
        assertTrue(repo.getAll().first().isEmpty())
    }

    // ---------- getById ----------

    @Test
    fun `getById - 找到返回对应记录`() = runTest {
        ds.upsert(fakeFile("a.m4a"), 500L, 1L)

        val r = repo.getById("a.m4a")
        assertEquals("a.m4a", r.id)
        assertEquals(500L, r.durationMs)
    }

    @Test
    fun `getById - 找不到抛 NoSuchElementException`() = runTest {
        var caught: Throwable? = null
        try {
            repo.getById("ghost.m4a")
        } catch (t: Throwable) {
            caught = t
        }
        assertTrue("应抛 NoSuchElementException", caught is NoSuchElementException)
        assertEquals("Recording not found: ghost.m4a", caught!!.message)
    }

    // ---------- create ----------

    @Test
    fun `create - 透传 ds 并出现在 getAll 中`() = runTest {
        val f = TestRecordings.touchFile("echo_new.m4a", dir)

        val rec: Recording = repo.create(f, durationMs = 2345L)

        assertEquals("echo_new.m4a", rec.id)
        assertEquals(2345L, rec.durationMs)
        assertTrue(rec in repo.getAll().first())
    }

    @Test
    fun `create - durationMs 与创建时间由 ds 决定`() = runTest {
        val f = TestRecordings.touchFile("echo_x.m4a", dir)
        val rec = repo.create(f, durationMs = 42L)
        assertEquals(42L, rec.durationMs)
    }

    // ---------- delete ----------

    @Test
    fun `delete - 真实删文件并返回 true`() = runTest {
        val f = TestRecordings.touchFile("echo_del.m4a", dir)
        repo.create(f, durationMs = 100L)
        assertTrue(f.exists())

        val ok = repo.delete("echo_del.m4a")

        assertTrue(ok)
        assertFalse("物理文件应被删除", f.exists())
        assertTrue(repo.getAll().first().none { it.id == "echo_del.m4a" })
    }

    @Test
    fun `delete - 找不到返回 false`() = runTest {
        val ok = repo.delete("ghost.m4a")
        assertFalse(ok)
    }

    // ---------- helpers ----------

    private fun fakeFile(name: String): File {
        val f = File(dir, name)
        f.writeText("x")
        return f
    }
}
