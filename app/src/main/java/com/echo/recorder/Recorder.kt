package com.echo.recorder

import com.echo.recorder.service.RecordingService

/** 录音行为抽象. [RecordingService] 实现它; 测试注入假的实现, 无需绑定真实服务. */
interface Recorder {
    fun startRecording()
    fun stopRecording(): com.echo.recorder.domain.model.Recording?
}
