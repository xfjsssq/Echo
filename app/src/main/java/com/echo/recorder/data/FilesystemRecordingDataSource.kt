package com.echo.recorder.data

import android.content.Context
import com.echo.recorder.common.computeAudioDurationMs
import com.echo.recorder.common.concatenateAudioSegments
import com.echo.recorder.domain.model.Recording
import com.echo.recorder.domain.model.RecordingCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * 本地文件系统录音数据源.
 *
 * 分类即目录: pending/ longterm/ unprocessed/ 各自对应一个 RecordingCategory.
 * _buffer/ 是实时环形缓冲, 不进列表 (load 不扫它); 但冷启动时 load 会先把其中
 * 异常退出残留的缓冲段救援到 unprocessed/ (恢复弹窗素材), 见 [rescueOrphanedSegments].
 */
class FilesystemRecordingDataSource(
    private val context: android.content.Context? = null,
    private val baseDir: File? = null,
    private val initialState: List<Recording> = emptyList(),
) : RecordingDataSource {
    private val dir: File by lazy { baseDir ?: com.echo.recorder.common.recordingsDir(context!!) }
    private val _state = MutableStateFlow(initialState)
    override val state: StateFlow<List<Recording>> = _state.asStateFlow()

    /**
     * 时长缓存: 文件名 -> (mtime, durationMs).
     * load() 每次扫盘都会为每个文件创建 MediaMetadataRetriever (native 对象),
     * 文件多时开销明显; 文件未变化时直接命中缓存, 避免重复 native 调用.
     */
    private val durationCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Long>>()

    /** 扫盘一次. 只扫分类目录 (pending/longterm/unprocessed), 不扫 _buffer. */
    override fun load() {
        // 冷启动救援必须先于扫盘: 把上次异常退出残留在 _buffer/ 的缓冲段抢救进 unprocessed/,
        // 本次扫描才能看到它们, 恢复弹窗才有素材.
        rescueOrphanedSegments()
        val all = mutableListOf<Recording>()
        // 兼容两种用法: 生产传 context (扫 recordingsDir 分类子目录), 测试传 baseDir (扫其根目录).
        dir.listFiles()?.forEach { f ->
            if (f.isFile && f.name.endsWith(".m4a")) {
                // 根目录直接文件视为临时.
                all += toRecording(f, RecordingCategory.TEMPORARY)
            } else if (f.isDirectory && f.name != "_buffer") {
                val cat = when (f.name) {
                    "longterm" -> RecordingCategory.LONG_TERM
                    "unprocessed" -> RecordingCategory.UNPROCESSED
                    else -> RecordingCategory.TEMPORARY
                }
                f.listFiles()?.filter { it.isFile && it.name.endsWith(".m4a") }
                    ?.forEach { all += toRecording(it, cat) }
            }
        }
        // 最新录音在最上方 (用户要求: 最近的在顶部)
        _state.value = all.sortedByDescending { it.createdAt }
    }

    /**
     * 冷启动残留缓冲段救援.
     *
     * 正常划掉后台时, 服务的 onTaskRemoved 紧急保存会把缓冲拼接到 unprocessed/ 并清空 _buffer/.
     * 但进程若被更快杀死 (新系统/部分 ROM 划掉即杀, onTaskRemoved 来不及跑完; 或崩溃/被系统回收),
     * _buffer/ 里会残留 seg_*.m4a 段. 此处把它们按时间顺序无损拼接到 unprocessed/,
     * 让启动恢复弹窗能询问用户去留; 无论成败都清理残留段 (下次缓冲会复用同名文件).
     */
    private fun rescueOrphanedSegments() {
        val segs = File(dir, "_buffer").listFiles()
            ?.filter { it.isFile && it.name.endsWith(".m4a") && it.length() > 0L }
            ?.sortedBy { it.lastModified() }
            .orEmpty()
        if (segs.isEmpty()) return
        val destDir = File(dir, "unprocessed")
        runCatching { destDir.mkdirs() }
        val dest = File(destDir, "rescue_${System.currentTimeMillis()}.m4a")
        val dur = runCatching { concatenateAudioSegments(segs, dest) }.getOrDefault(0L)
        segs.forEach { runCatching { it.delete() } }
        // 拼出来不足 1 秒视为空片段, 不打扰用户.
        if (dur < 1000L) runCatching { if (dest.exists()) dest.delete() }
    }

    private fun toRecording(f: File, cat: RecordingCategory): Recording = Recording(
        id = f.name,
        displayName = f.name,
        fileUrl = f.toURI().toString(),
        createdAt = f.lastModified(),
        durationMs = readDurationMs(f),
        category = cat,
    )

    /**
     * 读取音频文件时长 (ms). 读取失败或文件损坏返回 0.
     *
     * 优先使用 MediaMetadataRetriever (快, 不需要解码).
     * 失败时回退到 MediaExtractor 遍历 sample 计算时长 (解决部分设备上 MetadataRetriever 不稳定的问题).
     */
    private fun readDurationMs(file: File): Long {
        if (!file.exists() || file.length() == 0L) return 0L
        // 缓存命中 (mtime 未变) 直接返回, 避免重复创建 native MediaMetadataRetriever.
        val key = file.name
        val mtime = file.lastModified()
        durationCache[key]?.let { (cachedMtime, cachedDur) ->
            if (cachedMtime == mtime) return cachedDur
        }
        val result = computeDurationMs(file)
        durationCache[key] = mtime to result
        return result
    }

    /**
     * 读取音频文件时长 (ms). 读取失败或文件损坏返回 0.
     *
     * 委托共享实现 [computeAudioDurationMs] (MetadataRetriever + MediaExtractor PTS 回退),
     * 解决 MediaMuxer 拼接产出的 m4a 在部分设备上读不到时长的问题.
     */
    private fun computeDurationMs(file: File): Long = computeAudioDurationMs(file)

    override fun getById(id: String): Recording? = _state.value.firstOrNull { it.id == id }

    /** 登记一条已经落盘的录音. category 由它落在的物理目录决定 (默认临时). */
    override fun upsert(file: File, durationMs: Long, now: Long): Recording {
        val cat = categoryOf(file)
        val rec = Recording(
            id = file.name,
            displayName = file.name,
            fileUrl = file.toURI().toString(),
            createdAt = now,
            durationMs = durationMs,
            category = cat,
        )
        val withoutOld = _state.value.filterNot { it.id == rec.id }
        // 新录音插到最前 (最新在上)
        _state.value = listOf(rec) + withoutOld
        return rec
    }

    override fun delete(id: String): Boolean {
        val target = _state.value.firstOrNull { it.id == id } ?: return false
        val f = File(java.net.URI(target.fileUrl))
        if (f.exists()) f.delete()
        _state.value = _state.value.filterNot { it.id == id }
        return true
    }

    /** 删除超过 maxAgeMs 的临时录音 (惰性清理: 应用启动/进入时调用). */
    override fun deleteExpiredTemporary(maxAgeMs: Long): Int {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        val expired = _state.value.filter {
            it.category == RecordingCategory.TEMPORARY && it.createdAt < cutoff
        }
        if (expired.isEmpty()) return 0
        var removed = 0
        for (rec in expired) {
            val f = File(java.net.URI(rec.fileUrl))
            if (f.exists()) runCatching { f.delete() }
            removed++
        }
        if (removed > 0) {
            val ids = expired.map { it.id }.toSet()
            _state.value = _state.value.filterNot { it.id in ids }
        }
        return removed
    }

    /** 把录音在临时/长期之间移动 (改物理目录 + 改分类). unprocessed 也可被保留为临时. */
    override fun setCategory(id: String, category: RecordingCategory): Recording? {
        val target = _state.value.firstOrNull { it.id == id } ?: return null
        if (target.category == category) return target
        val src = File(java.net.URI(target.fileUrl))
        val destDir = when (category) {
            RecordingCategory.LONG_TERM -> File(dir, "longterm")
            RecordingCategory.TEMPORARY -> File(dir, "pending")
            RecordingCategory.UNPROCESSED -> File(dir, "unprocessed")
        }
        runCatching { destDir.mkdirs() }
        val dest = File(destDir, src.name)
        if (src.exists()) {
            // 优先原子移动 (同分区 renameTo, 快且不会中途丢失).
            val renamed = runCatching { src.renameTo(dest) }.getOrDefault(false)
            if (!renamed) {
                // 回退: 复制 + 删除. 复制失败时绝不删除原文件, 防止数据丢失.
                val copied = runCatching { src.copyTo(dest, overwrite = true) }.getOrDefault(null) != null
                if (!copied) return null
                runCatching { src.delete() }
            }
        }
        val moved = target.copy(fileUrl = dest.toURI().toString(), category = category)
        _state.value = _state.value.map { if (it.id == id) moved else it }
        return moved
    }

    /**
     * 重命名录音: 改物理文件名 + 更新记录 (id/displayName/fileUrl 同步).
     * 新名自动补 .m4a 扩展名; 重名冲突或失败返回 null.
     */
    override fun rename(id: String, newName: String): Recording? {
        val target = _state.value.firstOrNull { it.id == id } ?: return null
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return null
        val safeName = if (trimmed.endsWith(".m4a", ignoreCase = true)) trimmed else "$trimmed.m4a"
        val src = File(java.net.URI(target.fileUrl))
        val dest = File(src.parentFile, safeName)
        if (dest.exists()) return null // 重名冲突, 拒绝.
        val ok = runCatching { src.renameTo(dest) }.getOrDefault(false)
        if (!ok) return null
        val renamed = target.copy(id = dest.name, displayName = dest.name, fileUrl = dest.toURI().toString())
        _state.value = _state.value.map { if (it.id == id) renamed else it }
        return renamed
    }

    /** 由物理路径反推分类. */
    private fun categoryOf(file: File): RecordingCategory {
        val parent = file.parentFile?.name ?: return RecordingCategory.TEMPORARY
        return when (parent) {
            "longterm" -> RecordingCategory.LONG_TERM
            "unprocessed" -> RecordingCategory.UNPROCESSED
            else -> RecordingCategory.TEMPORARY
        }
    }
}
