package com.echo.recorder.service

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.media.MediaRecorder
import com.echo.recorder.common.bufferDir
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.ceil

/**
 * 真正的 N 分钟环形缓冲区.
 *
 * 实现方式: 连续写入固定时长(10s)的 segment 文件到 _buffer/,
 * 维护一个覆盖完整 N 分钟(多保留一段冗余)的滚动队列, 超出则删除最旧段.
 * 暂停时将队列内的段 **无损拼接**(MediaExtractor -> MediaMuxer, 容器级, 不重编码)为一个文件,
 * 因此用户拿到的是连续、完整的最近 N 分钟, 边界声音绝不丢失.
 *
 * 用协程 [rollLoop] 驱动"结束当前段 -> 开启下一段", 主线程不阻塞.
 */
class CircularBuffer(
    private val context: Context,
) {
    private val dir: File = bufferDir(context)
    private val segDurationMs: Long = 10_000L
    private var maxSegments: Int = DEFAULT_MAX_SEGMENTS

    private val segments = ArrayDeque<File>()
    private var recorder: MediaRecorder? = null
    private var current: File? = null
    private var segIndex: Int = 0

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var rollJob: Job? = null

    /**
     * 串行化所有对 [recorder] / [segments] 的操作.
     *
     * MediaRecorder 不是线程安全的: rollLoop 的段翻转 (IO 线程) 与
     * save/discard/release 可能在另一个 IO 线程同时执行 stopRecorder/startSegment,
     * 并发调用 rec.stop()/prepare() 会导致 IllegalStateException 或 native 崩溃.
     * cancel() 是协作式取消, 不能阻止 rollLoop 正在执行的同步代码, 必须用锁互斥.
     */
    private val mutex = Mutex()

    companion object {
        const val DEFAULT_MAX_SEGMENTS = 19 // ~ 3 min @10s + 冗余
    }

    /** 根据设置里的缓冲时长更新保留段数. */
    fun setBufferSeconds(seconds: Int) = runBlocking {
        mutex.withLock { setBufferSecondsLocked(seconds) }
    }

    private fun setBufferSecondsLocked(seconds: Int) {
        maxSegments = ceil(seconds * 1000.0 / segDurationMs).toInt() + 1
        trimToMax()
    }

    /** 开启缓冲: 立即录第一段, 并按段时长循环翻转. */
    fun start() = runBlocking {
        mutex.withLock { startLocked() }
    }

    private fun startLocked() {
        if (rollJob != null) return
        startSegment()
        rollJob = scope.launch { rollLoop() }
    }

    private suspend fun rollLoop() {
        while (currentCoroutineContext().isActive) {
            delay(segDurationMs)
            // 当前段到点: 结束它, 开启下一段 (之间可能有 <100ms 极小间隙, 不影响语音).
            mutex.withLock {
                // 锁内再次确认仍在缓冲 (save/discard 可能已取消并重建 rollJob).
                if (rollJob != null && currentCoroutineContext().isActive) {
                    stopRecorder()
                    startSegment()
                }
            }
        }
    }

    private fun startSegment() {
        val f = File(dir, "seg_${segIndex++}.m4a")
        current = f
        var success = false
        var rec: MediaRecorder? = null
        runCatching {
            rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION") MediaRecorder()
            }
            rec!!.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec!!.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec!!.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec!!.setAudioEncodingBitRate(16 * 44100)
            rec!!.setAudioSamplingRate(44100)
            rec!!.setOutputFile(f.absolutePath)
            rec!!.prepare()
            rec!!.start()
            recorder = rec
            success = true
        }
        // 仅在录制成功启动时才将段文件加入队列, 避免空文件污染拼接结果.
        if (success) {
            segments.addLast(f)
            trimToMax()
        } else {
            // 启动失败: 释放未成功启动的 recorder, 删除空文件.
            runCatching { rec?.release() }
            runCatching { if (f.exists()) f.delete() }
            current = null
        }
    }

    private fun trimToMax() {
        while (segments.size > maxSegments) {
            val old = segments.removeFirst()
            runCatching { if (old.exists()) old.delete() }
        }
    }

    private fun stopRecorder() {
        val rec = recorder
        if (rec != null) {
            val stopResult = runCatching { rec.stop() }
            runCatching { rec.release() }
            recorder = null
            // stop() 失败 (通常因录制时间过短) 时删除损坏的段文件, 防止空文件进入拼接.
            if (stopResult.isFailure) {
                current?.let { runCatching { if (it.exists()) it.delete() } }
            }
            current = null
        }
    }

    /**
     * 停止录制并把当前缓冲拼接为一份完整音频 -> dest.
     * 返回拼接后的时长(ms); 若缓冲为空返回 0.
     */
    fun save(dest: File): Long = runBlocking {
        mutex.withLock { saveLocked(dest) }
    }

    private suspend fun saveLocked(dest: File): Long {
        rollJob?.cancel(); rollJob = null
        stopRecorder()
        val existing = segments.filter { it.exists() && it.length() > 0 }
        val durationMs = if (existing.isEmpty()) 0L else concatenateSegments(existing, dest)
        // 清空缓冲, 立即重新开始缓冲.
        clearSegments()
        startLocked()
        return durationMs
    }

    /** 丢弃当前缓冲, 立即重新开始. */
    fun discard() = runBlocking {
        mutex.withLock { discardLocked() }
    }

    private suspend fun discardLocked() {
        rollJob?.cancel(); rollJob = null
        stopRecorder()
        clearSegments()
        startLocked()
    }

    /** 释放资源 (彻底退出时). */
    fun release() = runBlocking {
        mutex.withLock { releaseLocked() }
    }

    private suspend fun releaseLocked() {
        rollJob?.cancel(); rollJob = null
        stopRecorder()
        clearSegments()
        scope.cancel()
    }

    /** 残留清理: 把缓冲转存到 dest 后释放, 不重启. */
    fun releaseTo(dest: File): Long = runBlocking {
        mutex.withLock { releaseToLocked(dest) }
    }

    private suspend fun releaseToLocked(dest: File): Long {
        rollJob?.cancel(); rollJob = null
        stopRecorder()
        val existing = segments.filter { it.exists() && it.length() > 0 }
        val dur = if (existing.isNotEmpty()) concatenateSegments(existing, dest) else 0L
        clearSegments()
        scope.cancel()
        return dur
    }

    private fun clearSegments() {
        segments.forEach { runCatching { if (it.exists()) it.delete() } }
        segments.clear()
    }

    /**
     * 容器级无损拼接: 读取每个 segment 的音频 sample 依次写入一个新 MPEG-4 文件.
     * AAC 流可直接按 sample 级拼接(相同编码参数), 无需重编码, 几乎瞬时完成.
     */
    private fun concatenateSegments(inputs: List<File>, dest: File): Long {
        runCatching { if (dest.exists()) dest.delete() }
        val muxer = MediaMuxer(dest.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var offsetUs = 0L
        var first = true
        try {
            val buffer = ByteBuffer.allocate(2 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            for (input in inputs) {
                val extractor = MediaExtractor()
                runCatching { extractor.setDataSource(input.absolutePath) }
                var audioTrack = -1
                for (t in 0 until extractor.trackCount) {
                    if (extractor.getTrackFormat(t).getString(MediaFormat.KEY_MIME)?.startsWith("audio") == true) {
                        audioTrack = t; break
                    }
                }
                if (audioTrack < 0) { extractor.release(); continue }
                extractor.selectTrack(audioTrack)
                if (first) {
                    val trackFormat = extractor.getTrackFormat(audioTrack)
                    trackIndex = muxer.addTrack(trackFormat)
                    muxer.start()
                    first = false
                }
                var maxPts = 0L
                while (true) {
                    info.offset = 0
                    info.size = extractor.readSampleData(buffer, 0)
                    if (info.size < 0) break
                    info.presentationTimeUs = extractor.sampleTime + offsetUs
                    info.flags = extractor.sampleFlags
                    muxer.writeSampleData(trackIndex, buffer, info)
                    maxPts = maxOf(maxPts, extractor.sampleTime)
                    extractor.advance()
                }
                offsetUs += maxPts + 1
                extractor.release()
            }
        } finally {
            // 确保 muxer 资源一定被释放, 即使 writeSampleData 抛异常.
            if (!first) { runCatching { muxer.stop() } }
            runCatching { muxer.release() }
        }
        return offsetUs / 1000
    }
}
