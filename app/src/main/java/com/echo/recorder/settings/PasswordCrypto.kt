package com.echo.recorder.settings

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * 密码工具: PBKDF2-HMAC-SHA256 (不可逆, 抗暴力破解).
 *
 * 存储格式为 "saltHex:hashHex". 验证时用存储的盐重新哈希输入, 与存储的哈希比对.
 * 使用常量时间比较, 防止时序攻击.
 * 适用于 6 位数字密码 / 扩展密码 / 恢复密钥.
 *
 * 参考实现: Android Security 最佳实践 + GitHub App-Locker 项目.
 * PBKDF2 迭代 100,000 次, 6 位 PIN 爆破从 <1ms 提升到 ~100s.
 */
object PasswordCrypto {

    private const val SALT_LENGTH = 16
    private const val ITERATIONS = 100_000
    private const val KEY_LENGTH = 256

    private val secureRandom = SecureRandom()

    /** 生成随机盐 (16 字节). */
    fun newSalt(): ByteArray = ByteArray(SALT_LENGTH).also { secureRandom.nextBytes(it) }

    /** 对 password 用 PBKDF2-HMAC-SHA256, 返回 hash 字节. */
    fun hash(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec)
            .encoded
    }

    /** 随机生成 6 位恢复密钥 (字符串, 允许前导零). */
    fun generateRecoveryKey(): String =
        "%06d".format(secureRandom.nextInt(1_000_000))

    /** 生成 "saltHex:hashHex". */
    fun encode(password: String, salt: ByteArray): String =
        "${toHex(salt)}:${toHex(hash(password, salt))}"

    /** 验证 password 是否匹配 stored (saltHex:hashHex). 常量时间比较. */
    fun verify(password: String, stored: String): Boolean {
        val parts = stored.split(":")
        if (parts.size != 2) return false
        val salt = fromHex(parts[0]) ?: return false
        val expectedBytes = fromHex(parts[1]) ?: return false
        val actualBytes = hash(password, salt)
        return constantTimeEquals(actualBytes, expectedBytes)
    }

    fun toHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    fun fromHex(hex: String): ByteArray? = runCatching {
        ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }.getOrNull()

    /** 常量时间字节数组比较, 防止时序攻击. */
    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }
        return result == 0
    }
}
