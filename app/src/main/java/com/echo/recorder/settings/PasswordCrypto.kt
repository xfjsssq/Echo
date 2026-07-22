package com.echo.recorder.settings

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * 密码工具: 加盐 SHA-256 哈希 (不可逆).
 *
 * 存储格式为 "saltHex:hashHex". 验证时用存储的盐重新哈希输入, 与存储的哈希比对.
 * 适用于 6 位数字密码 / 图案点序 / 恢复密钥.
 */
object PasswordCrypto {

    private const val SALT_LENGTH = 16
    private const val DIGEST = "SHA-256"

    /** 生成随机盐 (16 字节). */
    fun newSalt(): ByteArray = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }

    /** 对 password 用给定盐做 SHA-256, 返回 hash 字节. */
    fun hash(password: String, salt: ByteArray): ByteArray {
        val md = MessageDigest.getInstance(DIGEST)
        md.update(salt)
        return md.digest(password.toByteArray(Charsets.UTF_8))
    }

    /** 随机生成 6 位恢复密钥 (字符串, 允许前导零). */
    fun generateRecoveryKey(): String =
        "%06d".format(SecureRandom().nextInt(1_000_000))

    /** 生成 "saltHex:hashHex". */
    fun encode(password: String, salt: ByteArray): String =
        "${toHex(salt)}:${toHex(hash(password, salt))}"

    /** 验证 password 是否匹配 stored (saltHex:hashHex). */
    fun verify(password: String, stored: String): Boolean {
        val parts = stored.split(":")
        if (parts.size != 2) return false
        val salt = fromHex(parts[0]) ?: return false
        val expected = parts[1]
        return toHex(hash(password, salt)) == expected
    }

    fun toHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    fun fromHex(hex: String): ByteArray? = runCatching {
        ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }.getOrNull()
}
