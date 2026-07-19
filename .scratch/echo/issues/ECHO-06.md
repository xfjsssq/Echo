---
id: ECHO-06
title: "播放能力：MediaPlayer 包装 + PlayerViewModel"
status: ready
blocks: []
blocked_by: ["ECHO-02"]
---

## 目标

沉淀播放器内核，给播放页 UI 做后盾。与 ECHO-03/ECHO-04 工程上并行，仅依赖存储层。

## 交付物

- `playback/AudioPlayer.kt`：封装 `MediaPlayer`
  - `prepare(recording: Recording)`
  - `play()` / `pause()` / `seekTo(ms: Int)`
  - `stateFlow: Flow<AudioPlayerState>`：`isPlaying`、`currentPositionMs`、`durationMs`
  - 播放完自动暂停 + 归零
  - 释放：`release()` 在 ViewModel `onCleared` 里调
- `playback/AudioPlayerFactory`（接口）+ 默认实现（Hilt inject，便于测试替换为 Mock）
- `ui/player/PlayerViewModel.kt`：暴露 `PlayerUiState`
- 单元测试：`AudioPlayerTest`（用 MockK 包裹 MediaPlayer 公开行为，Turbine 断言 StateFlow）+ `PlayerViewModelTest`

## 验收

- 对着任意 `.m4a` 跑通：`play → pause → seekTo → 自动停`
- 单元测试全绿

## 不做

播放页 UI、列表页
