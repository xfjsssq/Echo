---
id: ECHO-04
title: "录音页 UI：RecordScreen + 权限请求"
status: ready
blocks: []
blocked_by: ["ECHO-03"]
---

## 目标

把录音功能变成真实可点的大按钮 + 权限请求流程。

## 交付物

- `ui/record/RecordScreen.kt`
  - 居中圆形录音按钮（Material 3 主色），点击切换录音 / 停止
  - 录音中：按钮变红脉冲动画 + 已录时长 `HH:MM:SS` 实时显示
  - 顶部 AppBar "Echo"
- 运行时权限：`rememberLauncherForActivityResult` 请求 `RECORD_AUDIO`；未提供则禁用按钮 + 提示文案
- `ui/record/RecordViewModel.kt`：暴露 `RecordUiState`（`isRecording`、`elapsedMs`、`hasPermission`）；事件 `onRecordPressed()`
- 单元测试：`RecordScreenTest`（Compose UI Test，验证状态切换 + 权限提示）

## 验收

- 首次点录音 → 弹出权限请求 → 同意 → 按钮变红、计时跑起、通知栏通知出现
- 再点一下 → 停、回到 idle、通知消失
- 拒绝权限 → 按钮禁用、出现说明文字

## 不做

列表、播放；动画可以简单（scale + alpha），不引入 Lottie 等重库
