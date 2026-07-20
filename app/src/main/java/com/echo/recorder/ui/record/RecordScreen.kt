package com.echo.recorder.ui.record

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echo.recorder.ui.formatElapsed

/**
 * 录音页. 居中圆形录音按钮 + 顶部 AppBar.
 *
 * 权限本身由宿主 Activity 的 launcher 处理; 本组件只读 VM 中的 [RecordUiState.hasPermission]
 * 来决定按钮是否可用与提示文案.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(
    viewModel: RecordViewModel,
    onRequestPermission: () -> Unit,
    onOpenList: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Echo") },
                actions = {
                    IconButton(onClick = onOpenList) {
                        Icon(Icons.Filled.List, contentDescription = "录音列表")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (state.isRecording) {
                Text(
                    text = formatElapsed(state.elapsedMs),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 32.dp),
                )
            }

            RecordButton(
                isRecording = state.isRecording,
                enabled = state.hasPermission,
                onClick = {
                    if (!state.hasPermission) {
                        onRequestPermission()
                    } else {
                        viewModel.onRecordPressed()
                    }
                },
            )

            if (!state.hasPermission) {
                Text(
                    text = "需要麦克风权限才能录音",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun RecordButton(
    isRecording: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )
    val bg = when {
        !enabled -> Color.Gray
        isRecording -> Color(0xFFD32F2F)
        else -> MaterialTheme.colorScheme.primary
    }
    val appliedScale = if (isRecording) scale else 1f

    IconButton(
        onClick = onClick,
        enabled = enabled || isRecording,
        modifier = Modifier
            .size(120.dp)
            .scale(appliedScale)
            .background(bg, CircleShape),
    ) {
        Icon(
            imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
            contentDescription = if (isRecording) "停止录音" else "开始录音",
            tint = Color.White,
            modifier = Modifier.size(56.dp),
        )
    }
}
