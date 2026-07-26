package com.echo.recorder.auth

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 密码/恢复密钥错误次数统计与锁定管理 (进程内单例).
 *
 * 规则:
 * - 累计错误 3 次 → 锁定 30 秒
 * - 累计错误 5 次 → 锁定 1 分钟
 * - 累计错误 7 次及以上 → 锁定 3 分钟 (封顶)
 * - 验证成功清零计数
 * - 冷启动时重置 (进程内单例, 不持久化)
 */
object LockoutManager {

    @Volatile
    private var failureCount: Int = 0

    @Volatile
    private var lockedUntilMs: Long = 0L

    /** 当前剩余锁定秒数, 未锁定时返回 0. */
    fun remainingSeconds(): Long {
        val now = System.currentTimeMillis()
        val remain = lockedUntilMs - now
        return if (remain > 0) (remain / 1000) + 1 else 0
    }

    /** 当前是否处于锁定状态. */
    fun isLocked(): Boolean = remainingSeconds() > 0

    /** 剩余锁定秒数流, 每秒自动更新. */
    fun remainingSecondsFlow(): Flow<Int> = flow {
        while (true) {
            emit(remainingSeconds().toInt())
            delay(1000)
        }
    }

    /** 记录一次失败, 并根据累计次数触发锁定. */
    fun recordFailure(): Long {
        failureCount++
        val durationMs = when {
            failureCount >= 7 -> 3 * 60 * 1000L
            failureCount >= 5 -> 1 * 60 * 1000L
            failureCount >= 3 -> 30 * 1000L
            else -> 0L
        }
        if (durationMs > 0) {
            lockedUntilMs = System.currentTimeMillis() + durationMs
        }
        return durationMs
    }

    /** 验证成功时调用, 清零计数. */
    fun recordSuccess() {
        failureCount = 0
        lockedUntilMs = 0L
    }

    /** 获取当前错误计数 (用于测试/调试). */
    fun currentFailureCount(): Int = failureCount
}
