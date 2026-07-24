package com.echo.recorder.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.echo.recorder.R

/**
 * 语言选择页 (居中卡片样式, 半透明遮罩).
 *
 * 选完立即通过 [onPick] 回调 (由调用方写 DataStore + 重建 Activity).
 */
@Composable
fun LanguagePickerScreen(
    onPick: (String) -> Unit,
) {
    // 全屏透明 Dialog, 自带半透明 scrim.
    Dialog(
        onDismissRequest = { /* 语言选择不可跳过 */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(20.dp),
                    )
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.language_selection_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    OutlinedButton(
                        onClick = { onPick("zh") },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.language_zh)) }
                    OutlinedButton(
                        onClick = { onPick("en") },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.language_en)) }
                }
            }
        }
    }
}
