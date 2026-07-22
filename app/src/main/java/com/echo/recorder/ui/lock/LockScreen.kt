package com.echo.recorder.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.echo.recorder.R
import com.echo.recorder.auth.SessionAuth
import com.echo.recorder.settings.SettingsRepository

/**
 * 冷启动锁屏层.
 *
 * 全屏覆盖, 验证密码/图案通过后设置 [SessionAuth.isUnlocked] 并进入主界面.
 */
@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    var verified by remember { mutableStateOf(false) }

    val storedHash by produceState<String?>(initialValue = null) {
        value = repo.passwordHash.first()
    }
    val isPattern by produceState(initialValue = false) {
        value = repo.passwordType.first() == "pattern"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Echo", style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.lock_screen_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        if (!verified) {
            PasswordPromptDialog(
                storedHash = storedHash,
                recoveryHash = null,
                isPattern = isPattern,
                onVerify = {
                    verified = true
                    SessionAuth.isUnlocked = true
                    onUnlocked()
                },
                onDismiss = { /* 锁屏不可取消 */ },
            )
        }
    }
}
