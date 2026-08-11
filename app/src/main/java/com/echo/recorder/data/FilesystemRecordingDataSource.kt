package com.echo.recorder.data

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
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
 * _buffer/ 是实时环形缓冲, 不进列表 (load 不扫它).
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

    private fun computeDurationMs(file: File): Long {
        // 优先尝试 MediaMetadataRetriever.
        val retriever = MediaMetadataRetriever()
        val metadataResult = runCatching {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        }.getOrDefault(0L)
        runCatching { retriever.release() }
        if (metadataResult > 0L) return metadataResult
        // 回退: 用 MediaExtractor 遍历 sample 计算最大 pts.
        return runCatching {
            val extractor = MediaExtractor()
            extractor.setDataSource(file.absolutePath)
            var audioTrack = -1
            for (t in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(t).getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio")) { audioTrack = t; break }
            }
            if (audioTrack < 0) {
                extractor.release()
                return@runCatching 0L
            }
            extractor.selectTrack(audioTrack)
            var maxPts = 0L
            while (true) {
                val pts = extractor.sampleTime
                if (pts < 0) break
                maxPts = maxOf(maxPts, pts)
                if (!extractor.advance()) break
            }
            extractor.release()
            maxPts / 1000 // pts 单位是 us, 转 ms
        }.getOrDefault(0L)
    }

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
