package com.echo.recorder.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.echo.recorder.BuildConfig
import com.echo.recorder.R
import com.echo.recorder.about.SignatureUtils
import com.echo.recorder.ui.common.EntranceAnimation
import com.echo.recorder.ui.common.LoadingPulse
import com.echo.recorder.ui.common.echoPressScale

/**
 * 关于页面: 显示版本号 + SHA-256 签名指纹.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val fingerprint by produceState<String?>(initialValue = null) {
        value = SignatureUtils.sha256Fingerprint(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about)) },
                navigationIcon = {
                    IconButton(
                        onBack,
                        modifier = Modifier.echoPressScale(0.9f),
                    ) { Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        EntranceAnimation(rise = 14.dp) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = stringResource(R.string.version) + ": " + BuildConfig.VERSION_NAME,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(stringResource(R.string.signature), style = MaterialTheme.typography.titleMedium)
                if (fingerprint != null) {
                    Text(
                        text = fingerprint!!,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    LoadingPulse(dotSize = 6.dp)
                }
                Text(
                    text = stringResource(R.string.signature_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
