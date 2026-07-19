---
name: Echo
version: 0.1.0
status: ready-for-tickets
---

# Echo — 离线录音应用规格说明书

## 1. 项目定位

一款**纯离线、无网络、无广告**的本地录音应用。所有音频数据仅保存于本设备，**绝不**通过网络传输。核心闭环：录音 → 列表 → 播放。

## 2. 技术基线

| 项 | 值 | 备注 |
|---|---|---|
| 应用名 | Echo | — |
| 包名 | `com.echo.recorder` | — |
| 语言 | Kotlin | — |
| UI 框架 | Jetpack Compose + Material 3 | — |
| 最低 SDK | API 26 (Android 8.0) | 不写 API 级别版本分支 |
| 目标 / 编译 SDK | API 34 / 34 | — |
| Gradle | Kotlin DSL (`.gradle.kts`) | — |
| 架构 | MVVM (ViewModel + StateFlow + Compose) | — |
| 状态管理 | Jetpack ViewModel + StateFlow | — |
| 依赖注入 | Hilt (hilt-android) | — |
| 音频格式 | AAC，封装 `.m4a` | MediaRecorder 原生支持 |
| 保存位置 | app 私有目录 `context.filesDir/recordings` | 其他 app 不可见 |
| 网络许可 | **不声明 `INTERNET`** | 硬性约束 |
| 网络库 | **零**（无 OkHttp / Retrofit / Ktor） | 硬性约束 |
| 广告 SDK | **零** | 硬性约束 |

## 3. 依赖清单（精确到用途）

### 3.1 Compose 栈
- `androidx.compose:compose-bom`（BOM 统一版本）
- `androidx.compose.ui:ui`
- `androidx.compose.ui:ui-tooling-preview`
- `androidx.compose.material3:material3`
- `androidx.compose.material:material-icons-extended`

### 3.2 AndroidX 生命周期 / 导航
- `androidx.lifecycle:lifecycle-runtime-ktx`
- `androidx.lifecycle:lifecycle-viewmodel-compose`
- `androidx.lifecycle:lifecycle-runtime-compose`
- `androidx.activity:activity-compose`
- `androidx.navigation:navigation-compose`

### 3.3 协程
- `org.jetbrains.kotlinx:kotlinx-coroutines-android`

### 3.4 Hilt 依赖注入
- `com.google.dagger:hilt-android`
- `com.google.dagger:hilt-android-compiler`（`kapt` / `ksp`）
- `androidx.hilt:hilt-navigation-compose`

### 3.5 序列化
- `org.jetbrains.kotlinx:kotlinx-serialization-json`

### 3.6 测试
- JUnit 4 / `junit:junit`
- `androidx.test.ext:junit`
- `androidx.test.espresso:espresso-core`
- `org.jetbrains.kotlinx:kotlinx-coroutines-test`
- `app.cash.turbine:turbine`（StateFlow 断言）
- `io.mockk:mockk`

## 4. 权限（AndroidManifest.xml 必须声明）

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
```

运行时权限：`RECORD_AUDIO`、`POST_NOTIFICATIONS` 须在录音启动前取得；未提供则优雅禁用录音按钮并解释。

## 5. 模块与包结构（项目内部骨架）

```
com.echo.recorder
├─ EchoApplication.kt            // @HiltAndroidApp
├─ MainActivity.kt               // Compose 宿主，setContent { EchoApp() }
├─ navigation/
│  └─ EchoNavHost.kt             // NavHost + Destinations
├─ ui/
│  ├─ record/                    // 录音页
│  │  ├─ RecordScreen.kt
│  │  └─ RecordViewModel.kt
│  ├─ list/                      // 录音列表页
│  │  ├─ RecordingsListScreen.kt
│  │  └─ RecordingsListViewModel.kt
│  ├─ player/                    // 播放页
│  │  ├─ PlayerScreen.kt
│  │  └─ PlayerViewModel.kt
│  └─ theme/                     // Material 3 主题
│     ├─ Color.kt
│     ├─ Theme.kt
│     └─ Type.kt
├─ service/
│  └─ RecordingService.kt        // 前台服务，执行录音
├─ domain/
│  ├─ model/Recording.kt         // 数据类
│  └─ recording/RecordingRepository.kt
├─ data/
│  └─ FilesystemRecordingDataSource.kt
└─ di/
   └─ AppModule.kt               // Hilt 模块
```

## 6. 屏幕规格

### 6.1 录音页（RecordScreen）
- 顶部：一个大圆形录音按钮（Material 3 主色）
- 录音进行中：按钮变红脉冲动画 + 已录时长计时显示（`HH:MM:SS`）
- 顶部 AppBar 标题 "Echo"
- 底部无其他按钮

### 6.2 录音列表页（RecordingsListScreen）
- 顶部 AppBar 标题 "我的录音"
- LazyColumn 逐项：文件名 / 录制时间 / 时长（`MM:SS`）
- 点击任一项 → 导航到播放页
- 空状态：居中显示 "暂无录音，去录一条吧"

### 6.3 播放页（PlayerScreen）
- 顶部：文件名 + 录制时间
- 中间：圆形大播放/暂停按钮
- 底部：
  - 可拖动进度条（`Slider`，Material 3）
  - 当前时间（左）/ 总时长（右）
- **不做**：倍速、均衡器、循环模式、波形图

## 7. 业务规则

- 文件名：`"echo_yyyyMMdd_HHmmss.m4a"`（时间戳，不重复）
- 列表按录制时间倒序（最新在前）
- 播放页退出时暂停播放
- 录制期间切到后台：前台服务持续录音，通知栏显示 "正在录音…"，不丢录音；回到前台继续看到计时
- 播放完最后一秒自动暂停并归零
- 删除录音（二期不做；本规格仅预留接口）

## 8. 非功能性要求

- 全离线、无网络交互
- **应用绝不请求 `INTERNET` 权限（AndroidManifest.xml 不声明 `INTERNET`）**
- **所有录音数据永不上传：不引入任何网络库（OkHttp / Retrofit / Ktor 等）、不引入广告 SDK、不向任何远端发送任何数据**
- 录音文件不出 app 私有目录
- 所有用户提示用中文
- 主题随系统深色模式
- 测试：协程用 Turbine 断言 StateFlow；Repository / ViewModel 必须有单元测试覆盖

## 9. 验收

每个 ticket 交付后用户拿 APK 到真机跑对应功能。编译命令默认 `./gradlew :app:assembleDebug`。
