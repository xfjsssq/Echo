package com.echo.recorder.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.echo.recorder.R
import com.echo.recorder.Recorder
import com.echo.recorder.ServiceLocator
import com.echo.recorder.common.recordingsDir
import com.echo.recorder.domain.model.Recording
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
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * 前台录音服务. 保证切后台 / 锁屏录音不丢.
 *
 * 状态通过 [state] StateFlow 对外; Activity 通过 [RecordingServiceBinder] 拿到实例后监听.
 */
class RecordingService : Service(), Recorder {

    inner class RecordingServiceBinder : Binder() {
        val service: RecordingService get() = this@RecordingService
    }

    private val binder = RecordingServiceBinder()
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startElapsedMs: Long = 0L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickJob: Job? = null

    private val _state = MutableStateFlow(RecorderState())
    val state: StateFlow<RecorderState> = _state.asStateFlow()

    data class RecorderState(
        val isRecording: Boolean = false,
        val elapsedMs: Long = 0L,
    )

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startRecording()
                startForeground(NOTIFICATION_ID, buildNotification())
            }
            ACTION_STOP -> {
                stopRecording()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun startRecording() {
        if (_state.value.isRecording) return
        val dir = recordingsDir(this)
        val out = File(dir, fileName())
        outputFile = out

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

        startElapsedMs = System.currentTimeMillis()
        _state.value = RecorderState(isRecording = true, elapsedMs = 0L)
        startTicking()
    }

    override fun stopRecording(): Recording? {
        val out = outputFile ?: return null
        val durationMs = System.currentTimeMillis() - startElapsedMs
        try {
            recorder?.stop()
        } catch (_: RuntimeException) {
            // stop() 若录音时长过短会抛 IllegalStateException/RuntimeException, 视为空录音.
        } finally {
            recorder?.release()
            recorder = null
        }
        stopTicking()
        _state.value = RecorderState(isRecording = false, elapsedMs = 0L)

        val repo = ServiceLocator.repository(this)
        return runOnMain {
            repo.create(out, durationMs)
        }
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (isActive) {
                _state.value = _state.value.copy(elapsedMs = System.currentTimeMillis() - startElapsedMs)
                delay(100L)
            }
        }
    }

    private fun stopTicking() {
        tickJob?.cancel()
        tickJob = null
    }

    private fun runOnMain(block: suspend () -> Recording): Recording {
        return runBlocking { block() }
    }

    private fun fileName(): String {
        val now = System.currentTimeMillis()
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
        fun p(n: Int) = n.toString().padStart(2, '0')
        return "echo_${cal.get(java.util.Calendar.YEAR)}${p(cal.get(java.util.Calendar.MONTH) + 1)}${p(cal.get(java.util.Calendar.DAY_OF_MONTH))}_" +
            "${p(cal.get(java.util.Calendar.HOUR_OF_DAY))}${p(cal.get(java.util.Calendar.MINUTE))}${p(cal.get(java.util.Calendar.SECOND))}.m4a"
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "录音",
                NotificationManager.IMPORTANCE_LOW,
            )
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("正在录音…")
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        stopTicking()
        scope.cancel()
        recorder?.release()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "echo_recording"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.echo.recorder.action.START"
        const val ACTION_STOP = "com.echo.recorder.action.STOP"
    }
}
