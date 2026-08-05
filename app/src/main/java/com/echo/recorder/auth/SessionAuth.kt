package com.echo.recorder.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 会话级密码门禁状态 (进程内单例).
 *
 * 规则 (B2: 以 onStop 为会话边界):
 * - 冷启动: 必须通过锁屏验证 (由 [isUnlocked] 控制, 进入后台即失效).
 * - 主动锁定: 用户点击锁定按钮后 [isUnlocked] 置 false, 需重新验证.
 * - 删除录音 / 保存到公共目录: 每次离开应用后首次需密码, 验证通过后本会话内免密.
 * - 彻底退出 / 切换公共目录开关: 每次都需密码 (不缓存).
 *
 * 宿主在 Activity.onStop 时调 [reset], 回到前台后恢复"首次需密"状态.
 *
 * 所有状态使用 MutableStateFlow, 确保 Compose 能感知变化并触发重组.
 */
object SessionAuth {

    private val _isUnlocked = MutableStateFlow(false)

    /** 冷启动锁屏是否已通过. */
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _deleteUnlocked = MutableStateFlow(false)

    /** 本会话是否已通过"删除录音"验证. */
    val deleteUnlocked: StateFlow<Boolean> = _deleteUnlocked.asStateFlow()

    private val _savePublicUnlocked = MutableStateFlow(false)

    /** 本会话是否已通过"保存到公共目录"验证. */
    val savePublicUnlocked: StateFlow<Boolean> = _savePublicUnlocked.asStateFlow()

    /** 进入后台时调用: 清空所有会话缓存, 下次回到前台恢复需密状态. */
    fun reset() {
        _isUnlocked.value = false
        _deleteUnlocked.value = false
        _savePublicUnlocked.value = false
    }

    /** 主动锁定: 将 isUnlocked 置 false, 触发锁屏. */
    fun lock() {
        _isUnlocked.value = false
    }

    /** 锁屏验证通过后调用. */
    fun unlock() {
        _isUnlocked.value = true
    }

    /** 删除操作验证通过后调用. */
    fun unlockDelete() {
        _deleteUnlocked.value = true
    }

    /** 保存公共目录验证通过后调用. */
    fun unlockSavePublic() {
        _savePublicUnlocked.value = true
    }
}
