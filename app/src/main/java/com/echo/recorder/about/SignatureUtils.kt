package com.echo.recorder.about

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * 应用签名工具: 读取当前安装包的 SHA-256 指纹.
 */
object SignatureUtils {

    /** 计算应用签名的 SHA-256 十六进制字符串 (冒号分隔的大写), 失败返回 null. */
    fun sha256Fingerprint(context: Context): String? = runCatching {
        val packageName = context.packageName
        val pm = context.packageManager
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(
                packageName, PackageManager.GET_SIGNING_CERTIFICATES,
            )
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            @Suppress("DEPRECATION")
            info.signatures
        }
        val sig = signatures?.firstOrNull() ?: return null
        val digest = MessageDigest.getInstance("SHA-256")
        digest.digest(sig.toByteArray()).joinToString(":") { "%02X".format(it) }
    }.getOrNull()
}
