package com.echo.recorder.service

import android.content.Context
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
import kotlin.math.ceil

/**
 * 真正的 N 分钟环形缓冲区.
 *
 * 实现方式: 连续写入固定段数的 segment 文件到 _buffer/ (段长 = 缓冲时长 ÷ 段数,
 * 夹在 [SEG_MIN_MS, SEG_MAX_MS]), 维护一个覆盖完整 N 分钟(多保留一段冗余)的滚动队列,
 * 超出则删除最旧段.
 * 暂停时将队列内的段 **无损拼接**(MediaExtractor -> MediaMuxer, 容器级, 不重编码)为一个文件,
 * 因此用户拿到的是连续、完整的最近 N 分钟, 边界声音绝不丢失.
 *
 * 用协程 [rollLoop] 驱动"结束当前段 -> 开启下一段", 主线程不阻塞.
 */
class CircularBuffer(
    private val context: Context,
) {
    private val dir: File = bufferDir(context)

    /** 段长 (ms), 随缓冲档位自适应; 默认 3 分钟档 → 10s. 运行首段开录前由 setBufferSeconds 定档. */
    private var segDurationMs: Long = SEG_MIN_MS

    /** 滚动队列保留段数 (含 1 段冗余); 默认 3 分钟档 → 21 段. */
    private var maxSegments: Int = (DEFAULT_3MIN_SEGMENTS + 1)

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
        /** 滚动队列保留的段数 (不含正在写的当前段). */
        const val TARGET_SEGMENTS = 20

        /** 段长下限 (ms): 低于此值翻转过于频繁, 编码器重启开销反噬. */
        const val SEG_MIN_MS = 10_000L

        /** 段长上限 (ms): 高于此值突发被杀时未提交的当前段丢失窗口过大. */
        const val SEG_MAX_MS = 30_000L

        /** 默认 3 分钟档的保留段数 (3min/10s=18, 向上取整并留余量到 20). */
        const val DEFAULT_3MIN_SEGMENTS = 20

        /**
         * 按缓冲时长计算段长 (夹在 [SEG_MIN_MS, SEG_MAX_MS]):
         * 3 分钟档 → 10s 段 (与旧版固定 10s 行为一致); 10 分钟档 → 30s 段,
         * 翻转频率降 3 倍 (每 10s 一次编码器 stop/prepare/start 的 CPU 尖峰 → 30s 一次).
         */
        fun segmentDurationMsFor(bufferSeconds: Int): Long =
            (bufferSeconds * 1000L / TARGET_SEGMENTS).coerceIn(SEG_MIN_MS, SEG_MAX_MS)
    }

    /** 根据设置里的缓冲时长更新段长与保留段数. */
    fun setBufferSeconds(seconds: Int) = runBlocking {
        mutex.withLock { setBufferSecondsLocked(seconds) }
    }

    private fun setBufferSecondsLocked(seconds: Int) {
        // 段长随档位自适应 (3min→10s 段, 10min→30s 段), 段数按段长反推 + 1 段冗余.
        // RecordingService 总是先 setBufferSeconds 再 start, 段长在首段开录前确定;
        // 运行中重复调用同值无副作用.
        segDurationMs = segmentDurationMsFor(seconds)
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
            // 128kbps: AAC 对 44.1kHz 单声道语音的透明音质档 (旧值 16*44100=705.6kbps
            // 是 PCM 级码率, 对语音纯属浪费). 写盘流量 88KB/s → 16KB/s, 约 5.5 倍降幅.
            rec!!.setAudioEncodingBitRate(128_000)
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

    /**
     * 快照: 截取当前缓冲为一份完整音频 -> dest, **不中断缓冲**.
     *
     * 封口当前段后**立即续录**, 已封口段在录音继续的同时后台无损拼接 ——
     * 停录间隙压缩到与常规段翻转同级 (<100ms), 谈话连续性几乎无损.
     * 调用方保持 phase=BUFFERING 不变. 返回快照时长(ms); 缓冲为空返回 0.
     */
    fun snapshot(dest: File): Long = runBlocking {
        mutex.withLock { snapshotLocked(dest) }
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

    /**
     * 快照的锁内实现: 封口 -> 立即续录 -> 录音继续的同时拼接旧段.
     * 与 [saveLocked] 的区别: 续录先于拼接, 停录窗口只有段翻转级.
     * mutex 全程持有, 拼接期间 rollLoop 到点翻转会在锁上稍等 (段录得稍长, 无损失).
     */
    private suspend fun snapshotLocked(dest: File): Long {
        rollJob?.cancel(); rollJob = null
        // 1. 封口当前段 (普通翻转级间隙).
        stopRecorder()
        val toConcat = segments.filter { it.exists() && it.length() > 0 }
        // 2. 只摘引用不删文件 (拼接还要读), 立即续录新段 + 重启翻转循环.
        segments.clear()
        startLocked()
        // 3. 拼接已封口旧段; 成败都清理旧段文件. 若进程恰在此间被杀,
        //    残留段由冷启动 rescueOrphanedSegments 按时间序救援, 内容不丢.
        val durationMs = try {
            if (toConcat.isEmpty()) 0L else concatenateSegments(toConcat, dest)
        } finally {
            toConcat.forEach { runCatching { it.delete() } }
        }
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

    /** 容器级无损拼接 (委托共享实现, 冷启动残留段救援同用). */
    private fun concatenateSegments(inputs: List<File>, dest: File): Long =
        com.echo.recorder.common.concatenateAudioSegments(inputs, dest)
}
