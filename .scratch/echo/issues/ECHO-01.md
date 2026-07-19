---
id: ECHO-01
title: "项目骨架：Gradle 配置 + 空 Compose 界面"
status: ready
blocks: []
blocked_by: []
---

## 目标

搭出"能在 Android Studio 直接打开、能编译到 APK"的空项目框架。**这是所有后续 ticket 的前置**。

## 交付物

- `settings.gradle.kts`
- `build.gradle.kts`（项目级）
- `app/build.gradle.kts`（模块级），含第 3 节全部依赖
- 包结构 `com.echo.recorder`
- `EchoApplication.kt`（`@HiltAndroidApp`）+ Hilt 注解处理器配置
- `MainActivity.kt`：仅 `setContent { MaterialTheme { Text("Echo - 正在开发中") } }`
- `theme/` 基础 Material 3 三件套（Color / Theme / Type）
- `AndroidManifest.xml` 声明第 4 节权限 + `RECORD_AUDIO`、`FOREGROUND_SERVICE`、`POST_NOTIFICATIONS`、`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
- **不声明 `INTERNET`**（硬性）
- `local.properties` 指向本机的 Android SDK

## 验收

- 在 Android Studio 里打开，Sync 不报错
- `./gradlew :app:assembleDebug` 成功出 APK
- 装到真机后显示 "Echo - 正在开发中"

## 不做

任何业务逻辑、录音 / 播放 / 列表均不在本 ticket。
