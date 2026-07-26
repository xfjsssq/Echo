package com.echo.recorder.auth

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
 */
object SessionAuth {

    /** 冷启动锁屏是否已通过. */
    @Volatile
    var isUnlocked: Boolean = false

    /** 本会话是否已通过"删除录音"验证. */
    @Volatile
    var deleteUnlocked: Boolean = false

    /** 本会话是否已通过"保存到公共目录"验证. */
    @Volatile
    var savePublicUnlocked: Boolean = false

    /** 进入后台时调用: 清空所有会话缓存, 下次回到前台恢复需密状态. */
    fun reset() {
        isUnlocked = false
        deleteUnlocked = false
        savePublicUnlocked = false
    }

    /** 主动锁定: 将 isUnlocked 置 false, 触发锁屏. */
    fun lock() {
        isUnlocked = false
    }
}
