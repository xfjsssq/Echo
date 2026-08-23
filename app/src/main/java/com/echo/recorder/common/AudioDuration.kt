package com.echo.recorder.common

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File

/**
 * 读取音频文件时长 (ms). 读取失败或文件损坏返回 0.
 *
 * 优先使用 MediaMetadataRetriever (快, 不需要解码).
 * 失败时回退到 MediaExtractor 遍历 sample 计算最大 pts (解决部分设备/部分容器上
 * MetadataRetriever 读不到时长的问题 —— 尤其 Echo 录音由 MediaMuxer 拼接产出,
 * 这类 m4a 的 METADATA_KEY_DURATION 经常返回 null, 必须靠 PTS 回退才能拿到真实时长).
 */
fun computeAudioDurationMs(file: File): Long {
    if (!file.exists() || file.length() == 0L) return 0L
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
        extractorDurationMs(extractor)
    }.getOrDefault(0L)
}

/**
 * 读取音频时长 (ms), Content URI 版本 —— 供公共目录虚引用使用
 * (文件实体在公共目录, 应用只持 MediaStore URI, 不复制到私有目录).
 */
fun computeAudioDurationMs(context: Context, uri: Uri): Long {
    val retriever = MediaMetadataRetriever()
    val metadataResult = runCatching {
        retriever.setDataSource(context, uri)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    }.getOrDefault(0L)
    runCatching { retriever.release() }
    if (metadataResult > 0L) return metadataResult
    return runCatching {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)
        extractorDurationMs(extractor)
    }.getOrDefault(0L)
}

/** 遍历音频轨 sample 计算最大 pts (ms). 结束时负责释放 extractor. */
private fun extractorDurationMs(extractor: MediaExtractor): Long {
    try {
        var audioTrack = -1
        for (t in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(t).getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio")) { audioTrack = t; break }
        }
        if (audioTrack < 0) return 0L
        extractor.selectTrack(audioTrack)
        var maxPts = 0L
        while (true) {
            val pts = extractor.sampleTime
            if (pts < 0) break
            maxPts = maxOf(maxPts, pts)
            if (!extractor.advance()) break
        }
        return maxPts / 1000 // pts 单位是 us, 转 ms
    } finally {
        runCatching { extractor.release() }
    }
}
