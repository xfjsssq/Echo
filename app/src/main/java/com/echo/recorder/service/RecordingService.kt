package com.echo.recorder.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.echo.recorder.R
import com.echo.recorder.ServiceLocator
import com.echo.recorder.common.bufferDir
import com.echo.recorder.common.pendingDir
import com.echo.recorder.common.unprocessedDir
import com.echo.recorder.domain.model.RecordingCategory
import com.echo.recorder.domain.recording.RecordingRepository
import com.echo.recorder.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * 即时回放 (ShadowPlay 风格) 前台录音服务.
 *
 * 状态机:
 * - IDLE      服务已启动但不录音. 通知"小E随时等待您的指令", 无按钮.
 * - BUFFERING 连续录音, 每 N 分钟把已完成的片段翻为 pending (恒定磁盘). 通知"嘘～小E在认真聆听...", 一个"暂停".
 * - REVIEW    暂停待决定: 通知与 App 同步出现"保存"+"删除"两个按钮.
 *
 * 流程: 冷启动 IDLE -> "开始" -> BUFFERING (永不回 IDLE) -> 暂停 -> REVIEW -> 保存/删除 -> BUFFERING.
 * 强制退出时把 pending 片段标为 UNPROCESSED 存入 unprocessed/, 冷启动由 App 弹恢复对话框.
 */
class RecordingService : Service() {

    enum class Phase { IDLE, BUFFERING, REVIEW }

    inner class RecordingServiceBinder : Binder() {
        val service: RecordingService get() = this@RecordingService
    }

    private val binder = RecordingServiceBinder()
    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var pendingFile: File? = null
    private var pendingDurationMs: Long = 0L
    private var chunkStartMs: Long = 0L
    private var bufferSeconds: Int = DEFAULT_BUFFER_SECONDS

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickJob: Job? = null

    private val _phase = MutableStateFlow(Phase.IDLE)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    companion object {
        const val DEFAULT_BUFFER_SECONDS = 180
        const val CHANNEL_ID = "echo_recording"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.echo.recorder.action.START"
        const val ACTION_STOP = "com.echo.recorder.action.STOP"
        const val ACTION_PAUSE = "com.echo.recorder.action.PAUSE"
        const val ACTION_SAVE = "com.echo.recorder.action.SAVE"
        const val ACTION_DELETE = "com.echo.recorder.action.DELETE"
        const val ACTION_RESTART = "com.echo.recorder.action.RESTART"
        const val EXTRA_SAVE_PENDING = "extra_save_pending"
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        bufferSeconds = readBufferSeconds()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    private fun repo(): RecordingRepository = ServiceLocator.repository(this)

    private fun readBufferSeconds(): Int = runBlocking {
        try {
            SettingsRepository(this@RecordingService).bufferSeconds.first()
        } catch (_: Throwable) {
            DEFAULT_BUFFER_SECONDS
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> pause()
            ACTION_SAVE -> save()
            ACTION_DELETE -> deletePending()
            ACTION_STOP -> {
                stopRecorder()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_RESTART -> {
                val savePending = intent?.getBooleanExtra(EXTRA_SAVE_PENDING, false) ?: false
                handleRestart(savePending)
            }
            else -> startForeground(NOTIFICATION_ID, buildNotification())
        }
        return START_STICKY
    }

    private fun handleRestart(savePending: Boolean) {
        // 保存当前 pending 片段 (若用户选择保存), 然后回到 IDLE 并用新设置.
        if (savePending) {
            val file = pendingFile
            val dur = pendingDurationMs
            if (file != null && file.exists() && dur > 0L) {
                val target = File(pendingDir(this), file.name)
                runCatching { file.copyTo(target, overwrite = true) }
                runBlocking { repo().create(target, dur) }
            }
        }
        stopRecorder()
        pendingFile = null
        pendingDurationMs = 0L
        bufferSeconds = readBufferSeconds()
        _phase.value = Phase.IDLE
        rebuildNotification()
    }

    fun startBuffer() {
        if (_phase.value == Phase.BUFFERING) return
        bufferSeconds = readBufferSeconds()
        startRecording()
    }

    fun pause() {
        if (_phase.value != Phase.BUFFERING) return
        stopRecorderAndBumpPending()
        _phase.value = Phase.REVIEW
        rebuildNotification()
    }

    fun save() {
        if (_phase.value != Phase.REVIEW) return
        val file = pendingFile
        val dur = pendingDurationMs
        pendingFile = null
        pendingDurationMs = 0L
        if (file != null && file.exists() && dur > 0L) {
            val target = File(pendingDir(this), file.name)
            runCatching { file.copyTo(target, overwrite = true) }
            runBlocking { repo().create(target, dur) }
        }
        startRecording()
    }

    fun deletePending() {
        if (_phase.value != Phase.REVIEW) return
        pendingFile?.let { runCatching { if (it.exists()) it.delete() } }
        pendingFile = null
        pendingDurationMs = 0L
        startRecording()
    }

    private fun startRecording() {
        val dir = bufferDir(this)
        val out = File(dir, "buf_${System.currentTimeMillis()}.m4a")
        currentFile = out
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        recorder = rec.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(16 * 44100)
            setAudioSamplingRate(44100)
            setOutputFile(out.absolutePath)
            prepare()
            start()
        }
        chunkStartMs = System.currentTimeMillis()
        _elapsedMs.value = 0L
        _phase.value = Phase.BUFFERING
        startTicking()
        rebuildNotification()
    }

    private fun stopRecorderAndBumpPending() {
        val out = currentFile
        val dur = System.currentTimeMillis() - chunkStartMs
        stopRecorder()
        if (out != null && out.exists() && dur > 0L) {
            pendingFile?.let { runCatching { if (it.exists()) it.delete() } }
            pendingFile = out
            pendingDurationMs = dur
        }
    }

    private fun stopRecorder() {
        try {
            recorder?.stop()
        } catch (_: RuntimeException) {
            // stop() 若录音时长过短会抛异常, 视为空.
        } finally {
            recorder?.release()
            recorder = null
        }
        stopTicking()
        currentFile = null
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (isActive) {
                _elapsedMs.value = System.currentTimeMillis() - chunkStartMs
                if (_elapsedMs.value >= bufferSeconds * 1000L) {
                    stopRecorderAndBumpPending()
                    startRecording()
                }
                delay(500L)
            }
        }
    }

    private fun stopTicking() {
        tickJob?.cancel()
        tickJob = null
    }

    private fun rebuildNotification() {
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "即时回放", NotificationManager.IMPORTANCE_LOW)
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.createNotificationChannel(channel)
        }
    }

    private fun pendingIntent(action: String, flags: Int): PendingIntent {
        val intent = Intent(this, RecordingService::class.java).apply { this.action = action }
        val req = action.hashCode()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(this, req, intent, flags)
        } else {
            PendingIntent.getService(this, req, intent, flags)
        }
    }

    private fun buildNotification(): Notification {
        val pendingFlag = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setSilent(true)
            .apply {
                when (_phase.value) {
                    Phase.IDLE -> {
                        setContentTitle("小E随时等待您的指令")
                        setContentText("点击主界面开始按钮开启即时回放")
                    }
                    Phase.BUFFERING -> {
                        setContentTitle("嘘～小E在认真聆听...")
                        setContentText("即时回放运行中, 随时可暂停保存")
                        addAction(0, "暂停", pendingIntent(ACTION_PAUSE, pendingFlag))
                    }
                    Phase.REVIEW -> {
                        setContentTitle("已暂停, 请决定这段录音的去留")
                        setContentText("保存到列表, 或丢弃继续回放")
                        addAction(0, "保存", pendingIntent(ACTION_SAVE, pendingFlag))
                        addAction(0, "删除", pendingIntent(ACTION_DELETE, pendingFlag))
                    }
                }
            }
            .build()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        saveUnprocessed()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        saveUnprocessed()
        stopTicking()
        scope.cancel()
        recorder?.release()
        super.onDestroy()
    }

    /** 把当前 pending 片段标为 UNPROCESSED 存入 unprocessed/, 冷启动恢复. 同步. */
    private fun saveUnprocessed() {
        val file = pendingFile ?: return
        if (pendingDurationMs <= 0L) return
        val target = File(unprocessedDir(this), file.name)
        runCatching { file.copyTo(target, overwrite = true) }
        runBlocking {
            val created = repo().create(target, pendingDurationMs)
            runCatching { repo().setCategory(created.id, RecordingCategory.UNPROCESSED) }
        }
    }
}
