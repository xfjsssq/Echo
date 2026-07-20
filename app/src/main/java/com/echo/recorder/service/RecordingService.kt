package com.echo.recorder.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Binder
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.echo.recorder.R
import com.echo.recorder.ServiceLocator
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
import kotlin.system.exitProcess
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * 即时回放 (ShadowPlay 风格) 前台录音服务 —— 真正的 N 分钟环形缓冲.
 *
 * BUFFERING 由 [CircularBuffer] 实现: 写固定时长段到 _buffer/, 保留覆盖完整 N 分钟的滚动队列.
 * 暂停时将队列无损拼接为一份完整音频(最近 N 分钟, 不足则全部), 边界声音绝不丢失.
 * 界面不显示计时 —— 用户只关心"最近 N 分钟会被保留".
 */
class RecordingService : Service() {

    enum class Phase { IDLE, BUFFERING, REVIEW }

    inner class RecordingServiceBinder : Binder() {
        val service: RecordingService get() = this@RecordingService
    }

    private val binder = RecordingServiceBinder()
    private var buffer: CircularBuffer? = null
    private var bufferSeconds: Int = DEFAULT_BUFFER_SECONDS

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var rotateJob: Job? = null
    private var lastTextIndex: Int = -1

    private val _phase = MutableStateFlow(Phase.IDLE)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    /** "暂停"后在后台保存时为 true, 让 UI 显示加载态而不阻塞主线程. */
    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    /** 彻底退出标记: 由 [shutdownCleanly] 置位, onDestroy 据此跳过紧急保存. */
    @Volatile
    private var cleanShutdown = false

    companion object {
        const val DEFAULT_BUFFER_SECONDS = 180
        const val CHANNEL_ID = "echo_recording"
        const val NOTIFICATION_ID = 1001
        const val ROTATE_INTERVAL_MS = 3000L
        const val ACTION_START = "com.echo.recorder.action.START"
        const val ACTION_STOP = "com.echo.recorder.action.STOP"
        const val ACTION_PAUSE = "com.echo.recorder.action.PAUSE"
        const val ACTION_SAVE = "com.echo.recorder.action.SAVE"
        const val ACTION_DELETE = "com.echo.recorder.action.DELETE"
        const val ACTION_RESTART = "com.echo.recorder.action.RESTART"
        const val EXTRA_SAVE_PENDING = "extra_save_pending"
        const val ACTION_KILL = "com.echo.recorder.action.KILL"
        val BUFFERING_TEXTS = listOf(
            "嘘～小E正在认真聆听",
            "小E的耳朵竖起来了，正在捕捉周围的声音",
            "别担心，小E帮你记住每一句重要的话",
            "正在缓冲中，按下暂停即可保存最近几分钟",
            "小E默默守护你的每一次对话",
            "所有音频只在本地，小E绝不上传",
            "正在记录中，你可以随时取出刚刚说过的话",
            "小声说，小E连听见风声都会帮你存下来哦",
            "别让重要的话溜走，小E在听",
            "录制不受干扰，你可以去做别的事",
            "小E是个安静的小助手，只记录，不打扰",
            "记得用通知栏按钮快速暂停和保存",
            "安心去聊，回放的事交给小E",
            "你永远可以信任小E的录音兜底能力",
            "小E提醒你：录音需遵守当地法律哦",
        )

        /** 低于此时长(ms)的缓冲视为空片段, 不创建文件. */
        const val MIN_SAVE_DURATION_MS = 1000L
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
                buffer?.release()
                buffer = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_RESTART -> {
                val savePending = intent.getBooleanExtra(EXTRA_SAVE_PENDING, false)
                handleRestart(savePending)
            }
            ACTION_KILL -> {
                buffer?.releaseTo(File(unprocessedDir(this), "kill_${System.currentTimeMillis()}.m4a"))
                buffer = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                restartProcess()
            }
            else -> startForeground(NOTIFICATION_ID, buildNotification())
        }
        return START_STICKY
    }

    // ---- 状态转换 (由 App 通过 binder 调用) ----

    fun startBuffer() {
        if (_phase.value == Phase.BUFFERING) return
        bufferSeconds = readBufferSeconds()
        val cb = CircularBuffer(this).also { it.setBufferSeconds(bufferSeconds) }
        cb.start()
        buffer = cb
        _phase.value = Phase.BUFFERING
        startRotate()
        rebuildNotification()
    }

    fun pause() {
        if (_phase.value != Phase.BUFFERING) return
        val cb = buffer ?: return
        if (_saving.value) return
        _saving.value = true
        scope.launch {
            try {
                val dest = File(pendingDir(this@RecordingService), "echo_${System.currentTimeMillis()}.m4a")
                val dur = cb.save(dest)
                // 有效时长不足 1 秒则不创建文件 (避免产生 0 秒空片段).
                if (dur >= MIN_SAVE_DURATION_MS) {
                    runBlocking { repo().create(dest, dur) }
                } else {
                    runCatching { if (dest.exists()) dest.delete() }
                }
                _phase.value = Phase.REVIEW
                stopRotate()
                rebuildNotification()
            } finally {
                _saving.value = false
            }
        }
    }

    fun save() {
        if (_phase.value != Phase.REVIEW) return
        _phase.value = Phase.BUFFERING
        startRotate()
        rebuildNotification()
    }

    fun deletePending() {
        if (_phase.value != Phase.REVIEW) return
        runBlocking {
            val latest = repo().getAll().first()
                .firstOrNull { it.category == RecordingCategory.TEMPORARY }
            if (latest != null) runCatching { repo().delete(latest.id) }
        }
        buffer?.discard()
        _phase.value = Phase.BUFFERING
        startRotate()
        rebuildNotification()
    }

    private fun handleRestart(savePending: Boolean) {
        val cb = buffer
        if (savePending && cb != null) {
            val dest = File(pendingDir(this), "echo_${System.currentTimeMillis()}.m4a")
            val dur = cb.save(dest)
            if (dur > 0L) runBlocking { repo().create(dest, dur) }
        } else {
            cb?.discard()
        }
        stopRotate()
        _phase.value = Phase.IDLE
        rebuildNotification()
        Handler(Looper.getMainLooper()).postDelayed({ restartProcess() }, 400)
    }

    private fun restartProcess() {
        val ctx = applicationContext
        val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            runCatching { ctx.startActivity(intent) }
        }
        stopSelf()
        Handler(Looper.getMainLooper()).postDelayed({ exitProcess(0) }, 600)
    }
    private fun startRotate() {
        stopRotate()
        rotateJob = scope.launch {
            while (isActive) {
                rebuildNotification()
                delay(ROTATE_INTERVAL_MS)
            }
        }
    }

    private fun stopRotate() {
        rotateJob?.cancel(); rotateJob = null
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

    private fun randomBufferingText(): String {
        val list = BUFFERING_TEXTS
        if (list.size <= 1) return list.first()
        var idx = (Math.random() * list.size).toInt()
        if (idx == lastTextIndex) idx = (idx + 1) % list.size
        lastTextIndex = idx
        return list[idx]
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
                        setContentTitle(randomBufferingText())
                        setContentText("所有音频只在本地, 按下暂停即可保存最近几分钟")
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

    /** 彻底退出: 标记为干净退出并停服务, 不产生紧急保存文件. */
    fun shutdownCleanly() {
        cleanShutdown = true
        buffer?.release()
        buffer = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 从任务管理器划掉属于非正常退出 -> 紧急保存.
        saveUnprocessed()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // 彻底退出(cleanShutdown)不保存; 其余异常退出(系统杀死)才紧急保存.
        if (!cleanShutdown) {
            saveUnprocessed()
        }
        stopRotate()
        buffer?.release(); buffer = null
        scope.cancel()
        super.onDestroy()
    }

    /**
     * 紧急保存当前缓冲为带特殊标记的文件, 供下次启动恢复.
     * 有效时长不足 1 秒则不创建.
     */
    private fun saveUnprocessed() {
        val cb = buffer ?: return
        val dest = File(unprocessedDir(this), "recording_emergency_${System.currentTimeMillis()}.m4a")
        val dur: Long = cb.releaseTo(dest)
        if (dur < MIN_SAVE_DURATION_MS) {
            runCatching { if (dest.exists()) dest.delete() }
            return
        }
        runBlocking {
            val created = repo().create(dest, dur)
            runCatching { repo().setCategory(created.id, RecordingCategory.UNPROCESSED) }
        }
    }
}
