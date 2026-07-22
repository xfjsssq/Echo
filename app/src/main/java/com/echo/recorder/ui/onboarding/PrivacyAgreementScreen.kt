package com.echo.recorder.ui.onboarding

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.echo.recorder.R

/** 首次启动隐私协议页. */
@Composable
fun PrivacyAgreementScreen(onAgree: () -> Unit) {
    Scaffold(
        bottomBar = {
            TextButton(
                onClick = onAgree,
                modifier = Modifier.padding(16.dp),
            ) { Text(stringResource(R.string.agree)) }
        },
    ) { padding ->
        Text(
            text = stringResource(R.string.privacy_text),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
        )
    }
}
