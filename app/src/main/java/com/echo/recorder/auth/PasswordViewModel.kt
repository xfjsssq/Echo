package com.echo.recorder.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echo.recorder.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 密码状态集中管理.
 *
 * 持有密码启用状态、锁定状态、倒计时, 提供统一验证入口.
 * 锁定状态仅存内存, 不持久化 (冷启动后由 SessionAuth.isUnlocked 控制).
 */
class PasswordViewModel(private val repo: SettingsRepository) : ViewModel() {

    /** 密码是否启用. */
    val passwordEnabled: StateFlow<Boolean> = repo.passwordEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 密码类型 ("pin" / "pattern"). */
    val passwordType: StateFlow<String?> = repo.passwordType
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** 是否处于主动锁定状态 (内存). */
    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked

    /** 锁定倒计时 (秒). */
    val lockoutSeconds: StateFlow<Int> = kotlinx.coroutines.flow.flow {
        while (true) {
            emit(LockoutManager.remainingSeconds().toInt())
            kotlinx.coroutines.delay(1000)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** 主动锁定. */
    fun lock() {
        if (passwordEnabled.value) _isLocked.value = true
    }

    /** 解锁. */
    fun unlock() {
        _isLocked.value = false
        SessionAuth.isUnlocked = true
    }

    /**
     * 验证密码 (PIN 或 图案).
     *
     * @return 验证结果: Success / Wrong / Locked
     */
    suspend fun verify(input: String): PasswordVerifier.Result {
        val hash = repo.passwordHash.first()
        return PasswordVerifier.verifyPassword(input, hash)
    }

    /**
     * 验证恢复密钥.
     */
    suspend fun verifyRecovery(input: String): PasswordVerifier.Result {
        val hash = repo.recoveryHash.first()
        return PasswordVerifier.verifyRecovery(input, hash)
    }

    /** 设置新密码 (首次设置或重置). */
    suspend fun setPassword(type: String, password: String) {
        val salt = com.echo.recorder.settings.PasswordCrypto.newSalt()
        repo.setPassword(com.echo.recorder.settings.PasswordCrypto.encode(password, salt))
        repo.setPasswordType(type)
        repo.setPasswordEnabled(true)
    }

    /** 清除密码 (关闭密码保护). */
    suspend fun clearPassword() {
        repo.setPassword(null)
        repo.setPasswordType(null)
        repo.setPasswordEnabled(false)
    }
}
