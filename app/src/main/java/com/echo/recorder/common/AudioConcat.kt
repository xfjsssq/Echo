package com.echo.recorder.common

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * 容器级无损拼接: 读取每个 segment 的音频 sample 依次写入一个新 MPEG-4 文件.
 *
 * AAC 流可直接按 sample 级拼接(相同编码参数), 无需重编码, 几乎瞬时完成.
 * 供环形缓冲保存 ([com.echo.recorder.service.CircularBuffer]) 与冷启动残留段
 * 救援 ([com.echo.recorder.data.FilesystemRecordingDataSource]) 共用.
 *
 * @return 拼接后的时长(ms); 无有效音频内容返回 0.
 */
fun concatenateAudioSegments(inputs: List<File>, dest: File): Long {
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
