---
id: ECHO-08
title: "播放页 UI：PlayerScreen"
status: ready
blocks: []
blocked_by: ["ECHO-07"]
---

## 目标

把播放能力包装成朴素但完整的播放 UI。

## 交付物

- `ui/player/PlayerScreen.kt`
  - 顶部：文件名 + 录制时间
  - 中部：圆形大播放/暂停按钮（Material 3）
  - 底部：
    - 可拖进度条（Material 3 `Slider`，`onValueChangeFinished` 才 seek）
    - 当前时间（左）/ 总时长（右）
  - 进入页面自动 `prepare`，退出时 `pause`
- 单元测试：`PlayerScreenTest`（验证 play/pause 按钮反映状态、进度条拖动）

## 验收

- 播放 → 按钮切暂停图标、进度条走动、时间更新
- 暂停 → 按钮切播放图标
- 拖到某处松手 → 音频跳到对应位置
- 播完 → 自动停 + 归零
- 返回 → 暂停

## 不做

倍速、均衡器、循环、波形图
