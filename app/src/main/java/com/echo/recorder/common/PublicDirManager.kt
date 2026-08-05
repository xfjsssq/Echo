package com.echo.recorder.common

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.echo.recorder.domain.model.Recording
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 公共目录 (Downloads/EchoBackup) 管理.
 *
 * 用于"保存到公共目录"和"同步公共目录". 公共目录下的文件为虚引用, 不占用私有存储.
 *
 * API 29+ 使用 MediaStore.Downloads (分区存储), 26-28 直接写公共 Downloads 目录.
 */
object PublicDirManager {

    const val DIR_NAME = "EchoBackup"

    /** 公共目录 File (API 26-28 直接可用; 29+ 仅作扫描回退). */
    fun publicDir(): File {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * 将私有文件复制到公共目录并返回目标 File.
     * API 29+ 通过 MediaStore 插入, 返回的 File 路径可能为 RELATIVE_PATH 形式, 实际播放用 content URI.
     */
    suspend fun copyToPublic(context: Context, rec: Recording, src: File): File? = withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // 分区存储: 同名文件不重复插入.
                val existing = findUri(context, rec.id)
                if (existing != null) return@withContext File(publicDir(), src.name)
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, src.name)
                    put(MediaStore.Downloads.MIME_TYPE, "audio/mp4a-latm")
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$DIR_NAME")
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@withContext null
                resolver.openOutputStream(uri)?.use { out ->
                    src.inputStream().use { it.copyTo(out) }
                }
                File(publicDir(), src.name)
            } else {
                val dest = File(publicDir(), src.name)
                if (!dest.exists()) src.copyTo(dest, overwrite = true)
                dest
            }
        }.getOrNull()
    }

    /** 查找公共目录中该文件名的 content URI (API 29+), 用于播放. */
    fun findUri(context: Context, fileName: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return File(publicDir(), fileName).takeIf { it.exists() }?.let { Uri.fromFile(it) }
        val projection = arrayOf(MediaStore.Downloads._ID)
        val selection = "${MediaStore.Downloads.DISPLAY_NAME}=?"
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, arrayOf(fileName), null,
        )?.use { c ->
            if (c.moveToFirst()) {
                val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                return Uri.parse("${MediaStore.Downloads.EXTERNAL_CONTENT_URI}/$id")
            }
        }
        return null
    }

    /** 扫描公共目录中所有 .m4a 文件 (同步用). 返回 文件名 -> 显示名. */
    suspend fun scanPublic(context: Context): List<PublicFileInfo> = withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val list = mutableListOf<PublicFileInfo>()
                val projection = arrayOf(MediaStore.Downloads.DISPLAY_NAME, MediaStore.Downloads._ID)
                context.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection,
                    "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?", arrayOf("%$DIR_NAME/%"), null,
                )?.use { c ->
                    val nameIdx = c.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                    while (c.moveToNext()) {
                        val name = c.getString(nameIdx)
                        if (name.endsWith(".m4a")) list.add(PublicFileInfo(name, name))
                    }
                }
                list
            } else {
                publicDir().listFiles()?.filter { it.isFile && it.name.endsWith(".m4a") }
                    ?.map { PublicFileInfo(it.name, it.name) } ?: emptyList()
            }
        }.getOrDefault(emptyList())
    }

    /** 删除公共目录中指定文件 (虚引用删除). */
    suspend fun deletePublic(context: Context, fileName: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val projection = arrayOf(MediaStore.Downloads._ID)
                val selection = "${MediaStore.Downloads.DISPLAY_NAME}=?"
                context.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, arrayOf(fileName), null,
                )?.use { c ->
                    if (c.moveToFirst()) {
                        val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                        return@withContext context.contentResolver.delete(
                            Uri.parse("${MediaStore.Downloads.EXTERNAL_CONTENT_URI}/$id"), null, null,
                        ) > 0
                    }
                }
                false
            } else {
                File(publicDir(), fileName).let { it.exists() && it.delete() }
            }
        }.getOrDefault(false)
    }

    data class PublicFileInfo(val fileName: String, val displayName: String)
}
