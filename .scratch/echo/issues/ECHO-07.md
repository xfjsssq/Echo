---
id: ECHO-07
title: "录音列表页：RecordingsListScreen"
status: ready
blocks: []
blocked_by: ["ECHO-05", "ECHO-06"]
---

## 目标

把用户所有录音一览出来，点一条能跳到播放页。

## 交付物

- `ui/list/RecordingsListScreen.kt`
  - LazyColumn，单行：文件名 / 录制时间 / 时长 `MM:SS`
  - 空状态：居中 "暂无录音，去录一条吧"
  - 顶部 AppBar "我的录音"，返回键回上一页
- `ui/list/RecordingsListViewModel.kt`：订阅 `RecordingRepository.getAll()`，StateFlow 推 `ListUiState`
- 点一条 → 导航到 `player/{recordingId}`
- 单元测试：`RecordingsListViewModelTest`

## 验收

- 录几条进 App → 列表倒序 → 点一条进播放页
- 空态文案在无录音时正确显示

## 不做

删除录音按钮（仅保留接口入口）
