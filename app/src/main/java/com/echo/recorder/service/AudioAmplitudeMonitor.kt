package com.echo.recorder.service

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * 音频振幅实时采集器 (独立 AudioRecord, 与 MediaRecorder 并行).
 *
 * 从麦克风读取 PCM 短帧, 计算 RMS 振幅, 经**感知增益曲线** + **快攻慢放包络**平滑后
 * 暴露为 [amplitude] StateFlow (0.0 ~ 1.0), 供 Compose Canvas 驱动 Gemini Live 风格波形.
 *
 * 为什么不是直接用 RMS/32768:
 *  16-bit 人声的 RMS 通常只有 0.005 ~ 0.05, 直接线性映射会让波形几乎不动.
 *  这里使用 20*log10 分贝映射 (-60dB ~ 0dB → 0.0 ~ 1.0), 把轻声说话也映射到可见幅度.
 *
 * 包络采用快攻 (attack) + 慢放 (release):
 *  - 声音到来时波形迅速抬起, 跟随真实语音节奏
 *  - 声音停顿时波形缓慢回落, 形成 Gemini Live 那种"液体拖尾"质感
 */
object AudioAmplitudeMonitor {

    private const val SAMPLE_RATE = 44100
    private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
    private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

    /** 每次读取帧数 (约 11.6ms, 低延迟). */
    private const val FRAMES_PER_READ = 512

    /** 读取循环间隔 (ms), ~60Hz 更新, 足够驱动 60fps Canvas. */
    private const val READ_INTERVAL_MS = 12L

    /** 包络: 攻击系数 (越大抬升越快). */
    private const val ATTACK = 0.45f

    /** 包络: 释放系数 (越小回落越慢, 拖尾越长). */
    private const val RELEASE = 0.07f

    /** 感知增益映射的底噪门限 (dB). 低于此值视为静音. */
    private const val FLOOR_DB = -62f

    private val _amplitude = MutableStateFlow(0f)

    /** 归一化感知振幅 (0.0 ~ 1.0), 60fps 更新. */
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var envelope = 0f

    /** 启动振幅采集. 失败时静默降级 (波形仅显示 idle 微动, 不崩溃). */
    fun start() {
        if (audioRecord != null) return
        val record = try {
            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
                .coerceAtLeast(FRAMES_PER_READ * 2)
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL,
                ENCODING,
                bufferSize,
            ).also { it.startRecording() }
        } catch (_: Exception) {
            null
        } ?: return
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            try { record.release() } catch (_: Exception) {}
            return
        }

        audioRecord = record
        envelope = 0f
        _amplitude.value = 0f

        job = scope.launch {
            val buffer = ShortArray(FRAMES_PER_READ)
            while (isActive && audioRecord != null) {
                val read = runCatching { audioRecord?.read(buffer, 0, FRAMES_PER_READ) ?: -1 }.getOrDefault(-1)
                if (read > 0) {
                    // 感知增益: 分贝映射
                    val db = 20f * log10(rms(buffer, read) / 32768f + 1e-9f)
                    val target = ((db - FLOOR_DB) / -FLOOR_DB).coerceIn(0f, 1f)
                    // 快攻慢放包络
                    envelope = if (target > envelope) {
                        envelope + (target - envelope) * ATTACK
                    } else {
                        envelope + (target - envelope) * RELEASE
                    }
                    _amplitude.value = envelope.coerceIn(0f, 1f)
                }
                delay(READ_INTERVAL_MS)
            }
        }
    }

    /** 停止采集并释放 AudioRecord. */
    fun stop() {
        job?.cancel()
        job = null
        audioRecord?.apply {
            try { stop() } catch (_: Exception) {}
            try { release() } catch (_: Exception) {}
        }
        audioRecord = null
        envelope = 0f
        _amplitude.value = 0f
    }

    private fun rms(data: ShortArray, count: Int): Float {
        var sum = 0L
        for (i in 0 until count) {
            val v = data[i].toInt()
            sum += (v * v).toLong()
        }
        return sqrt(sum.toDouble() / count).toFloat()
    }
}
