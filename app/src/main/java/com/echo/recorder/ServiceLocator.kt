package com.echo.recorder

import android.content.Context
import com.echo.recorder.data.FilesystemRecordingDataSource
import com.echo.recorder.data.RecordingRepositoryImpl
import com.echo.recorder.domain.recording.RecordingRepository

/**
 * 极简服务定位器. 无 Hilt 时充当手动 DI 容器, 给 Service/ViewModel 提供 Repository 单例.
 */
object ServiceLocator {

    @Volatile
    private var repository: RecordingRepository? = null

    fun repository(context: Context): RecordingRepository =
        repository ?: synchronized(this) {
            repository ?: buildRepository(context).also { repository = it }
        }

    private fun buildRepository(context: Context): RecordingRepository {
        val ds = FilesystemRecordingDataSource(context.applicationContext)
        ds.load()
        // 不再注入虚引用: 公共目录方案已改为 SAF 授权 + 私有副本, 旧虚引用一并废弃.
        return RecordingRepositoryImpl(ds)
    }
}
