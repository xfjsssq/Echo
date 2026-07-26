package com.echo.recorder.auth

import com.echo.recorder.settings.PasswordCrypto

/**
 * 密码验证纯函数.
 *
 * 封装所有验证逻辑, 不依赖 Compose/Android 框架, 可直接单元测试.
 * 集成 LockoutManager 错误次数锁定.
 */
object PasswordVerifier {

    /** 验证结果. */
    sealed class Result {
        object Success : Result()
        object Wrong : Result()
        object Locked : Result()
    }

    /** 检查当前是否处于锁定状态. */
    fun isLocked(): Boolean = LockoutManager.isLocked()

    /** 获取剩余锁定秒数. */
    fun remainingSeconds(): Int = LockoutManager.remainingSeconds().toInt()

    /**
     * 验证 PIN / 图案.
     *
     * @param input 用户输入 (PIN 为 6 位数字, 图案为逗号分隔的点序 "0,1,2,3")
     * @param storedHash 存储的哈希 (saltHex:hashHex)
     */
    fun verifyPassword(input: String, storedHash: String?): Result {
        if (storedHash == null) return Result.Success
        if (LockoutManager.isLocked()) return Result.Locked
        return if (PasswordCrypto.verify(input, storedHash)) {
            LockoutManager.recordSuccess()
            Result.Success
        } else {
            LockoutManager.recordFailure()
            Result.Wrong
        }
    }

    /**
     * 验证恢复密钥.
     *
     * @param input 6 位恢复密钥
     * @param recoveryHash 存储的恢复密钥哈希
     */
    fun verifyRecovery(input: String, recoveryHash: String?): Result {
        if (recoveryHash == null) return Result.Success
        if (LockoutManager.isLocked()) return Result.Locked
        return if (PasswordCrypto.verify(input, recoveryHash)) {
            LockoutManager.recordSuccess()
            Result.Success
        } else {
            LockoutManager.recordFailure()
            Result.Wrong
        }
    }
}
