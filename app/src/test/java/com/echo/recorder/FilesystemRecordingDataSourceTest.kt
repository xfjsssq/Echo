package com.echo.recorder

import com.echo.recorder.data.FilesystemRecordingDataSource
import com.echo.recorder.domain.model.Recording
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 覆盖 FilesystemRecordingDataSource 的 CRUD + 扫盘 + 文件名规则.
 * 用 JUnit TemporaryFolder 做隔离的真实临时目录.
 */
class FilesystemRecordingDataSourceTest {

    @get:Rule
    val tmp: TemporaryFolder = TemporaryFolder()

    private lateinit var dir: File
    private lateinit var ds: FilesystemRecordingDataSource

    @Before
    fun setUp() {
        dir = tmp.newFolder("recordings")
        ds = FilesystemRecordingDataSource(baseDir = dir)
    }

    // ---------- load ----------

    @Test
    fun `load - 空目录返回空列表`() = runBlocking {
        ds.load()
        assertTrue(ds.state.first().isEmpty())
    }

    @Test
    fun `load - 扫出预设的 m4a 文件`() = runBlocking {
        writeFile("echo_20260719_143022.m4a", "x")
        writeFile("echo_20260719_150000.m4a", "y")

        ds.load()

        val got = ds.state.first().map { it.id }.toSet()
        assertEquals(
            setOf("echo_20260719_143022.m4a", "echo_20260719_150000.m4a"),
            got,
        )
    }

    @Test
    fun `load - 忽略非 m4a 文件`() = runBlocking {
        writeFile("echo_20260719_143022.m4a", "x")
        writeFile("notes.txt", "ignore me")
        writeFile("thumbs.jpg", "ignore me too")

        ds.load()

        val got = ds.state.first()
        assertEquals(1, got.size)
        assertEquals("echo_20260719_143022.m4a", got[0].id)
    }

    @Test
    fun `load - fileUrl 为 file URI`() = runBlocking {
        writeFile("echo_20260719_143022.m4a", "x")
        ds.load()

        val r = ds.state.first().single()
        assertTrue(r.fileUrl.startsWith("file:"))
    }

    // ---------- getById ----------

    @Test
    fun `getById - 存在返回对应记录`() = runBlocking {
        writeFile("echo_20260719_143022.m4a", "x")
        ds.load()

        val r = ds.getById("echo_20260719_143022.m4a")
        assertEquals("echo_20260719_143022.m4a", r!!.id)
    }

    @Test
    fun `getById - 不存在返回 null`() = runBlocking {
        ds.load()
        assertNull(ds.getById("ghost.m4a"))
    }

    // ---------- upsert ----------

    @Test
    fun `upsert - 登记落盘文件后出现在 state 中`() = runBlocking {
        val f = writeFile("echo_20260719_143022.m4a", "audio-bytes")
        val now = 1_700_000_000_000L

        val rec: Recording = ds.upsert(f, durationMs = 1234L, now = now)

        assertEquals("echo_20260719_143022.m4a", rec.id)
        assertEquals(1234L, rec.durationMs)
        assertEquals(now, rec.createdAt)
        assertEquals(1, ds.state.first().size)
        assertEquals(rec, ds.state.first().single())
    }

    @Test
    fun `upsert - 同 id 重复登记以最新为准`() = runBlocking {
        val f = writeFile("echo_20260719_143022.m4a", "audio-bytes")
        ds.upsert(f, durationMs = 100L, now = 1L)
        ds.upsert(f, durationMs = 200L, now = 2L)

        val list = ds.state.first()
        assertEquals(1, list.size)
        assertEquals(200L, list.single().durationMs)
    }

    // ---------- delete ----------

    @Test
    fun `delete - 存在则删文件并返回 true`() = runBlocking {
        val f = writeFile("echo_20260719_143022.m4a", "audio-bytes")
        ds.upsert(f, durationMs = 100L, now = 1L)
        assertTrue(f.exists())

        val ok = ds.delete("echo_20260719_143022.m4a")

        assertTrue(ok)
        assertFalse("物理文件应被删除", f.exists())
        assertTrue(ds.state.first().isEmpty())
    }

    @Test
    fun `delete - 不存在返回 false`() = runBlocking {
        val ok = ds.delete("ghost.m4a")
        assertFalse(ok)
    }

    // ---------- load 与 upsert 的幂等性 ----------

    @Test
    fun `load + upsert 后端状态一致`() = runBlocking {
        writeFile("echo_20260719_143022.m4a", "x")
        ds.load()

        val f = writeFile("echo_20260719_150000.m4a", "y")
        ds.upsert(f, durationMs = 500L, now = 7L)

        val ids = ds.state.first().map { it.id }.toSet()
        assertEquals(
            setOf("echo_20260719_143022.m4a", "echo_20260719_150000.m4a"),
            ids,
        )
    }

    private fun writeFile(name: String, content: String): File {
        val f = File(dir, name)
        f.writeText(content)
        return f
    }
}
