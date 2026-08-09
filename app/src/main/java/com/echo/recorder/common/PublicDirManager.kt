package com.echo.recorder.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.echo.recorder.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 公共目录 (备份文件夹) 管理 —— 基于 Storage Access Framework (SAF).
 *
 * 为什么弃用 MediaStore / 直接 File 路径 (第一性原理):
 * Android 10+ 分区存储下, MediaStore 查询默认只返回"本应用 UID 创建"的媒体条目,
 * 卸载重装后 UID 变化, 旧文件在查询中直接消失; 而直接访问
 * /storage/emulated/0/Download/... 这类公共路径会被系统拒绝 (EACCES).
 * 两者叠加就是"公共目录只是个空壳, 保存的文件完全读不到"的病根.
 *
 * SAF 方案: 用户通过系统文件夹选择器授权一个文件夹 (默认 Downloads/EchoBackup),
 * 用 takePersistableUriPermission 持久化授权, 之后所有读写走 DocumentFile,
 * 与安装身份/UID 无关 —— 卸载重装后重新选择同一文件夹即可恢复所有旧文件.
 */
object PublicDirManager {

    /** 默认备份文件夹名 (仅用于引导用户在系统选择器中选取, 应用界面不展示). */
    const val DIR_NAME = "EchoBackup"

    data class PublicFileInfo(
        val fileName: String,
        val displayName: String,
        val uri: Uri,
        val lastModified: Long,
    )

    /** 是否已获得备份文件夹授权. */
    suspend fun hasGrant(context: Context): Boolean =
        SettingsRepository(context).publicTreeUri.first() != null

    /** 保存用户选择的文件夹授权, 并申请持久化读写权限. */
    suspend fun saveGrant(context: Context, treeUri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        SettingsRepository(context).setPublicTreeUri(treeUri.toString())
    }

    private suspend fun root(context: Context): DocumentFile? = withContext(Dispatchers.IO) {
        val uriStr = SettingsRepository(context).publicTreeUri.first() ?: return@withContext null
        runCatching { DocumentFile.fromTreeUri(context, Uri.parse(uriStr)) }.getOrNull()
    }

    /** 扫描备份文件夹根目录下所有 .m4a 文件. */
    suspend fun scanPublic(context: Context): List<PublicFileInfo> = withContext(Dispatchers.IO) {
        val root = root(context) ?: return@withContext emptyList()
        runCatching {
            root.listFiles()
                .filter { it.isFile && it.name.orEmpty().endsWith(".m4a", ignoreCase = true) }
                .map { PublicFileInfo(it.name.orEmpty(), it.name.orEmpty(), it.uri, it.lastModified()) }
                .sortedByDescending { it.lastModified }
        }.getOrDefault(emptyList())
    }

    /**
     * 复制私有文件到备份文件夹. 同名文件已存在则视为已备份 (幂等), 直接返回成功.
     */
    suspend fun copyToPublic(context: Context, src: File, fileName: String): Boolean = withContext(Dispatchers.IO) {
        val root = root(context) ?: return@withContext false
        runCatching {
            if (root.findFile(fileName) != null) return@withContext true // 已备份过
            val doc = root.createFile("audio/mp4a-latm", fileName) ?: return@withContext false
            // 个别文件提供器会补/改扩展名, 保证最终文件名一致, 便于后续按名查找.
            if (doc.name != fileName) runCatching { doc.renameTo(fileName) }
            val out = context.contentResolver.openOutputStream(doc.uri, "w") ?: return@withContext false
            out.use { dst -> src.inputStream().use { it.copyTo(dst) } }
            true
        }.getOrDefault(false)
    }

    /** 删除备份文件夹中的指定文件. */
    suspend fun deletePublic(context: Context, fileName: String): Boolean = withContext(Dispatchers.IO) {
        val root = root(context) ?: return@withContext false
        runCatching { root.findFile(fileName)?.delete() ?: false }.getOrDefault(false)
    }
}
