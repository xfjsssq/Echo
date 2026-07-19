---
id: ECHO-05
title: "导航骨架：EchoNavHost + 三个屏幕占位"
status: ready
blocks: []
blocked_by: ["ECHO-04"]
---

## 目标

把目前孤立的 RecordScreen 装进 NavHost，并给列表页、播放页先立好**空占位**，让后续 ticket 直接填肉。

## 交付物

- `navigation/EchoNavHost.kt`：`NavHost` + 三个 `composable` 路由
  - `record`（起始页）
  - `list`
  - `player/{recordingId}`（参数化路由）
- `MainActivity` 改成 `setContent { EchoApp() }`，`EchoApp` 里套 `EchoNavHost`
- 列表页、播放页先放一个 `Text("占位")` 即可
- 单元测试：`EchoNavHostTest`（验证路由跳转）

## 验收

- 启动后默认在录音页
- 能从录音页通过（临时）按钮跳到列表页、播放页占位

## 不做

列表页 / 播放页的真实 UI
