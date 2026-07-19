---
id: ECHO-02
title: "数据模型与存储层：Recording + 文件系统数据源"
status: ready
blocks: []
blocked_by: ["ECHO-01"]
---

## 目标

实现最底层的数据模型与本地文件读写能力。**录音、列表、播放都依赖它**，所以要早做、稳做。

## 交付物

- `domain/model/Recording.kt`：`@Serializable` 数据类，字段含 `id: String`、`displayName: String`、`fileUri: String`、`createdAt: Long`、`durationMs: Long`
- `domain/recording/RecordingRepository.kt`：接口
  - `getAll(): Flow<List<Recording>>`（按 `createdAt` 倒序）
  - `getById(id: String): Recording?`
  - `create(file: File, durationMs: Long): Recording`
  - `delete(id: String): Boolean`
- `data/FilesystemRecordingDataSource.kt`：实现
  - 存于 `context.filesDir/recordings/`
  - 文件名 `echo_yyyyMMdd_HHmmss.m4a`
  - CRUD 直读文件系统
  - 创建时用 `MediaPlayer` 或文件元数据提取 duration（AAC 文件时长提取是本 ticket 的难点）
- `di/AppModule.kt`：Hilt `@Module` + `@InstallIn(SingletonComponent::class)`，提供 `RecordingRepository` 单例
- 单元测试：`FilesystemRecordingDataSourceTest`（临时目录，覆盖 CRUD + 倒序 + 文件名规则）+ `RecordingRepositoryTest`

## 验收

- `./gradlew :app:testDebugUnitTest` 新增测试全部绿
- 手动写一个 `.m4a` 到 `filesDir/recordings`，读出来 duration 正确、排序正确

## 不做

UI；不实现"删除"按钮（仅保留接口，二期再做）
