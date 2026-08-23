package com.echo.recorder.common

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 公共目录 (备份文件夹) 管理 —— 固定路径 Downloads/EchoBackup, 免手动选文件夹.
 *
 * 相比 SAF 文件夹选择器 (用户要手动选文件夹, 门槛高), 这里改为:
 * - 固定路径: Download/EchoBackup, 应用内不展示路径.
 * - API 30+: 通过 MediaStore **Downloads 集合**按 RELATIVE_PATH 写入公共 Downloads/EchoBackup.
 *   ⚠️ 必须走 Downloads 集合: MediaProvider 禁止向 Audio 集合插入 Download/ 下的文件
 *   (抛 IllegalArgumentException), 此前误用 Audio 集合导致备份从未真正写入成功.
 *   写入本应用自己的条目无需权限; 读取/删除公共目录下所有 .m4a (包括卸载重装后
 *   其他身份创建的旧文件) 需要读权限 (API 33+ READ_MEDIA_AUDIO / 30-32 READ_EXTERNAL_STORAGE).
 *   条目按 MIME 归为音频类型, Audio 集合的查询 (扫描/查重) 仍可命中.
 *   按 RELATIVE_PATH 过滤而非按 UID 查询, 卸载重装后旧文件仍可读 (不再有"空壳"问题).
 * - API 26-29: 需要 WRITE_EXTERNAL_STORAGE (Android 10 向 Downloads 集合写入也要求它).
 *   26-28 直接读写文件系统路径; 29 走 MediaStore Downloads 集合.
 *
 * 用户只需在系统弹出的权限对话框中点一次"允许", 无需手动选择文件夹.
 */
object PublicDirManager {

    /** 备份文件夹名 (固定, 不向用户展示路径). */
    const val DIR_NAME = "EchoBackup"

    /** MediaStore 相对路径 (API 29+). */
    const val RELATIVE_PATH = "Download/$DIR_NAME"

    data class PublicFileInfo(
        val fileName: String,
        val displayName: String,
        val uri: Uri,
        val lastModified: Long,
    )

    /** 当前 API 级别下访问备份文件夹所需的运行时权限. */
    fun requiredPermission(): String = when {
        Build.VERSION.SDK_INT >= 33 -> Manifest.permission.READ_MEDIA_AUDIO
        Build.VERSION.SDK_INT >= 30 -> Manifest.permission.READ_EXTERNAL_STORAGE
        // 26-29: Android 10 向 Downloads 集合写入要求 WRITE (同时隐含读权限); 26-28 直写文件也需它.
        else -> Manifest.permission.WRITE_EXTERNAL_STORAGE
    }

    /**
     * 是否已获得备份文件夹访问授权.
     *
     * 运行时权限是系统级全局状态: 任一入口 (列表页/设置页) 授权一次即永久生效,
     * 所有入口只读它即可, 绝不应各自反复弹授权. 同步读取, 供生命周期回调直接使用.
     */
    fun hasGrant(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, requiredPermission()) == PackageManager.PERMISSION_GRANTED

    /** 写入备份文件夹是否需要运行时权限: API 30+ 插入本应用自己的条目免权限, 29 及以下需要. */
    fun writeNeedsGrant(): Boolean = Build.VERSION.SDK_INT <= 29

    /** 备份文件夹的本地绝对路径 (API 26-28 直接用; API 29+ 仅兜底). */
    private fun legacyDir(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), DIR_NAME)

    /** 扫描备份文件夹根目录下所有 .m4a 文件. */
    suspend fun scanPublic(context: Context): List<PublicFileInfo> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= 29) scanViaMediaStore(context) else scanViaFile()
    }

    private fun scanViaMediaStore(context: Context): List<PublicFileInfo> = runCatching {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATE_MODIFIED,
        )
        val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
        val args = arrayOf("$RELATIVE_PATH/%")
        val result = mutableListOf<PublicFileInfo>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, selection, args,
            "${MediaStore.Audio.Media.DATE_MODIFIED} DESC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameCol) ?: continue
                if (!name.endsWith(".m4a", ignoreCase = true)) continue
                val id = cursor.getLong(idCol)
                val lastModified = cursor.getLong(dateCol) * 1000L // DATE_MODIFIED 单位是秒
                result += PublicFileInfo(
                    fileName = name,
                    displayName = name,
                    uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                    lastModified = lastModified,
                )
            }
        }
        result
    }.getOrDefault(emptyList())

    private fun scanViaFile(): List<PublicFileInfo> = runCatching {
        legacyDir().listFiles()
            ?.filter { it.isFile && it.name.endsWith(".m4a", ignoreCase = true) }
            ?.map { PublicFileInfo(it.name, it.name, Uri.fromFile(it), it.lastModified()) }
            ?.sortedByDescending { it.lastModified }
            .orEmpty()
    }.getOrDefault(emptyList())

    /** 私有文件保存到备份文件夹的结果. */
    sealed interface SaveOutcome {
        /** 已就位 (新写入成功, 或公共目录已有同名同大小的同一文件). 私有侧可安全移除. */
        object Saved : SaveOutcome
        /** 公共目录已有同名但大小不同的文件 —— 拒绝覆盖/堆叠, 私有侧保留原件. */
        object NameConflict : SaveOutcome
        /** 写入失败. */
        object Failed : SaveOutcome
    }

    /**
     * 保存私有文件到备份文件夹 (幂等, 绝不产生重复副本):
     * - 同名同大小已存在 → 视为同一文件已就位 ([SaveOutcome.Saved]), 不重复写入;
     * - 同名但大小不同 → 冲突 ([SaveOutcome.NameConflict]), 绝不覆盖也不改名堆叠;
     * - 不存在 → 新写入.
     */
    suspend fun saveToPublic(context: Context, src: File, fileName: String): SaveOutcome = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= 29) saveViaMediaStore(context, src, fileName) else saveViaFile(src, fileName)
    }

    private fun saveViaMediaStore(context: Context, src: File, fileName: String): SaveOutcome {
        if (Build.VERSION.SDK_INT < 29) return saveViaFile(src, fileName)
        if (!src.exists() || src.length() == 0L) return SaveOutcome.Failed
        // 查重: 同名条目已存在时按大小区分"同一文件"与"冲突", 绝不二次插入堆叠副本.
        findEntry(context, fileName)?.let { (_, size) ->
            return if (size == src.length()) SaveOutcome.Saved else SaveOutcome.NameConflict
        }
        // Android 10+ MediaProvider 禁止向 Audio 集合插入 Download/ 下的文件
        // (抛 IllegalArgumentException: "Primary directory Download not allowed"),
        // 必须走 Downloads 集合; 条目按 MIME 归为音频类型, Audio 集合的查询/扫描仍能命中.
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp4") // .m4a 正确类型 (mp4a-latm 是 AAC 裸流类型)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$RELATIVE_PATH/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        var uri: Uri? = null
        return runCatching {
            val inserted = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return@runCatching SaveOutcome.Failed
            uri = inserted
            context.contentResolver.openOutputStream(inserted)?.use { dst ->
                src.inputStream().use { it.copyTo(dst) }
            } ?: return@runCatching SaveOutcome.Failed
            // 写入完成后清除 IS_PENDING, 让系统媒体库/文件管理器可见.
            context.contentResolver.update(
                inserted,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null, null,
            )
            SaveOutcome.Saved
        }.onFailure {
            // 失败时清理残留 pending 条目, 否则系统保留不可见的 .pending-* 文件数天
            uri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
        }.getOrDefault(SaveOutcome.Failed)
    }

    private fun saveViaFile(src: File, fileName: String): SaveOutcome = runCatching {
        if (!src.exists() || src.length() == 0L) return@runCatching SaveOutcome.Failed
        val dir = legacyDir()
        dir.mkdirs()
        val dest = File(dir, fileName)
        if (dest.exists()) {
            return@runCatching if (dest.length() == src.length()) SaveOutcome.Saved else SaveOutcome.NameConflict
        }
        src.copyTo(dest)
        SaveOutcome.Saved
    }.getOrDefault(SaveOutcome.Failed)

    /**
     * 删除备份文件夹中的指定文件的结果.
     *
     * MediaStore 的条目有归属 (owner package): 卸载重装后 UID 变化,
     * 旧安装创建的备份条目对本安装而言是"他人文件", 直接 delete 会抛
     * RecoverableSecurityException / SecurityException —— 必须由用户在系统
     * 对话框中确认授权 ([DeleteResult.NeedsPermission] 返回授权入口).
     */
    sealed interface DeleteResult {
        /** 删除成功. */
        object Success : DeleteResult
        /** 需要用户在系统对话框中授权后才能删除. */
        data class NeedsPermission(val intentSender: IntentSender) : DeleteResult
        /** 备份文件夹中不存在该文件 (可能已被删除, 目标已达成). */
        object NotFound : DeleteResult
        /** 删除失败. */
        object Failed : DeleteResult
    }

    /** 删除备份文件夹中的指定文件. */
    suspend fun deletePublic(context: Context, fileName: String): DeleteResult = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= 29) {
            val uri = findEntry(context, fileName)?.first ?: return@withContext DeleteResult.NotFound
            try {
                if (context.contentResolver.delete(uri, null, null) > 0) {
                    DeleteResult.Success
                } else {
                    DeleteResult.Failed
                }
            } catch (e: RecoverableSecurityException) {
                // API 29: 他人条目, 系统提供授权入口.
                DeleteResult.NeedsPermission(e.userAction.actionIntent.intentSender)
            } catch (e: SecurityException) {
                // API 30+: 通用 SecurityException 时走 MediaStore.createDeleteRequest 让用户确认.
                createDeleteRequest(context, uri)?.let { DeleteResult.NeedsPermission(it) } ?: DeleteResult.Failed
            } catch (e: Exception) {
                DeleteResult.Failed
            }
        } else {
            if (runCatching { File(legacyDir(), fileName).delete() }.getOrDefault(false)) {
                DeleteResult.Success
            } else {
                DeleteResult.Failed
            }
        }
    }

    /** API 30+: 构造系统"确认删除"授权请求的 IntentSender. */
    private fun createDeleteRequest(context: Context, uri: Uri): IntentSender? {
        if (Build.VERSION.SDK_INT < 30) return null
        return runCatching {
            MediaStore.createDeleteRequest(context.contentResolver, listOf(uri)).intentSender
        }.getOrNull()
    }

    /** 按文件名在 MediaStore 中查找条目 (API 29+), 返回 URI 与文件大小 (供保存查重). */
    private fun findEntry(context: Context, fileName: String): Pair<Uri, Long>? = runCatching {
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.SIZE)
        val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} = ? AND ${MediaStore.Audio.Media.DISPLAY_NAME} = ?"
        val args = arrayOf("$RELATIVE_PATH/", fileName)
        var found: Pair<Uri, Long>? = null
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, selection, args, null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)),
                )
                val size = runCatching {
                    cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE))
                }.getOrDefault(-1L)
                found = uri to size
            }
        }
        found
    }.getOrNull()
}
