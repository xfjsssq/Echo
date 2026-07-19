---
id: ECHO-03
title: "前台录音服务：RecordingService"
status: ready
blocks: []
blocked_by: ["ECHO-02"]
---

## 目标

把录音执行从 UI 线程搬到前台服务，保证切后台 / 锁屏录音不丢。

## 交付物

- `service/RecordingService.kt`：`AndroidService`，`@AndroidEntryPoint`
  - 启动时提升到前台，通知栏显示 "正在录音…" + `FOREGROUND_SERVICE_MICROPHONE` 类型
  - 用 `MediaRecorder` 录制 AAC → `.m4a` 输出到 `filesDir/recordings/`
  - `Binder` 暴露状态：`isRecording`、`elapsedMs: LiveData/Long`
  - 停止时调 `RecordingRepository.create(...)` 落库，把 Recording 回传
- `RecordViewModel` 与服务的绑定（`bindService`），StateFlow 推送录音状态
- AndroidManifest 注册 `<service android:name=".service.RecordingService" android:foregroundServiceType="microphone" android:exported="false"/>`
- 单元测试：`RecordViewModelTest`（用 MockK mock Repository + Service binder）

## 验收

- 启动录音 → 通知栏出现常驻通知 → 锁屏 / 切到其他 app → 录音仍在继续 → 回到 Echo → 计时未断
- 停止后新录音出现在 Repository 返回的 Flow 里

## 不做

录音 UI（按钮 / 动画）本身——留到下一 ticket
