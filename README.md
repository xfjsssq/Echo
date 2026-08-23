# Echo（小E）· 离线即时回放录音

> **你错过的东西，小E替你记着。**
> A privacy-first, offline instant-replay voice recorder for Android. Like a DVR for your conversations.

Echo 是一款**完全离线**的即时回放录音应用。它始终在后台缓冲最近几分钟的音频——就像英伟达 ShadowPlay 之于游戏、Gemini Live 之于对话——当你意识到"刚才那句话值得记下"时，按下暂停，它已经在那里了。**永远不丢关键瞬间，也永远不把数据交给云端。**

## 为什么用 Echo

| 痛点 | Echo 的答案 |
|---|---|
| 想回放"刚才说过的话"，但没来得及点录音 | **常驻缓冲**：持续缓冲最近 N 分钟，暂停即保存，不错过任何瞬间 |
| 录音 App 都要联网、怕隐私泄露 | **完全离线**：不申请网络权限，应用物理上无法上传任何数据 |
| 手机被借用，担心录音被翻看 | **密码 / 图案双重锁** + 恢复密钥：冷启动与删除均需验证 |
| 卸载 App 怕录音一起丢失 | **公共目录备份**：一键备份到 `Downloads/EchoBackup`，卸载不丢 |
| 录音多了找不到 | **日历定位** + 临时/长期分类：按日期快速翻找，临时录音可一键转长期 |
| 担心 APK 被篡改 | **正式签名 + SHA-256 指纹核验**：Release 页可核对，安装包可信 |

## ✨ 核心特性

- **🎙 即时回放（Instant Replay）**：常驻环形缓冲，可自定义缓冲时长（默认数分钟），按下暂停即可把"刚才"保存为录音
- **🔒 隐私安全（Privacy by Design）**：零网络权限；密码（6 位 PIN / 图案锁）+ 恢复密钥（忘记密码时重置）；删除录音需验证
- **🗂 录音管理**：临时 / 长期分类；日历按日期定位；批量移至长期、批量删除；重命名
- **💾 公共目录备份**：长期录音自动备份到公共目录，卸载应用也不丢失（支持从备份目录导入还原）
- **🎨 精雕细琢的界面**：极光流动频谱条（Gemini Live 风格）、全局动效、玻璃拟态浮层、触感反馈、明亮主题、中英双语
- **📦 离线可用**：无账号、无广告、无云端依赖；录音全部存于设备本地

## 📲 下载

从 **GitHub Releases** 获取最新版（含签名信息）：

- 最新版本：**[v0.28](https://github.com/xfjsssq/Echo/releases/latest)**（正式签名版）
- 国内加速下载（快）：`https://gh-proxy.com/https://github.com/xfjsssq/Echo/releases/download/v0.28/Echo-0.28.apk`
- 国内加速下载（备选）：`https://ghfast.top/https://github.com/xfjsssq/Echo/releases/download/v0.28/Echo-0.28.apk`

> **安装后请在"设置 → 关于"中核对 SHA-256 指纹**，与 Release 页面公布的签名一致即为未被篡改的官方安装包。

## 🛠 技术栈

- **语言**：Kotlin
- **UI**：Jetpack Compose（Material 3）
- **架构**：MVVM + Repository + Flow
- **音频**：MediaRecorder / MediaMuxer（环形缓冲即时回放）
- **存储**：应用私有目录 + SAF（公共目录备份）
- **最低系统**：Android 8.0（API 26）

## 📖 如何使用

1. 打开 App，授予麦克风权限（仅本地使用，不联网）
2. 日常聊天 / 开会 / 通电话时正常使用，Echo 在后台默默缓冲
3. 听到想记住的内容，打开 Echo 按下 **暂停** —— 刚才几分钟的音频已保存为临时录音
4. 重要内容点击 **移至长期**，或一键备份到公共目录
5. 忘记密码？用设置时生成的 **恢复密钥** 重置

## 🏗 开发与构建

```bash
# 需要 JDK 17 + Android SDK 34
./gradlew assembleDebug     # 调试版
./gradlew assembleRelease   # 正式签名版（需配置 release keystore）
```

> Release 构建签名：keystore 与密码存放于本地 `~/.gradle/gradle.properties`（`ECHO_*` 属性），**不会进入仓库**。每次打 `v*` tag 时，GitHub Actions 自动生成 Release 并上传 `releases/` 下的 APK。

## 📄 隐私

- 应用**不申请网络权限**，录音与设置数据全部保存在设备本地
- 完整隐私政策见应用内"设置 → 隐私政策"

## 📜 License

本项目为个人项目，源码仅供学习参考。
