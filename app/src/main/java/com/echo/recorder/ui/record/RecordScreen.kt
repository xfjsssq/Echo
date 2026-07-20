package com.echo.recorder.ui.record

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echo.recorder.service.RecordingService
import com.echo.recorder.ui.formatElapsed
import com.echo.recorder.ui.fmtTime

/**
 * 录音页. 三态界面, 与通知栏 100% 同步:
 * - IDLE       -> 一个大"开始"按钮.
 * - BUFFERING  -> 经过时长 + 一个大"暂停"按钮.
 * - REVIEW     -> 两个按钮"保存" + "删除".
 * 右下角不起眼的"退出"图标 (密码门控占位).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(
    viewModel: RecordViewModel,
    onRequestPermission: () -> Unit,
    onOpenList: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onExit: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 冷启动恢复对话框 (阻塞进入).
    state.pendingRecovery?.let { rec ->
        AlertDialog(
            onDismissRequest = { /* 必须选择, 不可外部关闭 */ },
            title = { Text("你好, 上次退出前的回音小E帮你留下了") },
            text = {
                Text("时间: ${fmtTime(rec.createdAt)}\n要留着它吗?")
            },
            confirmButton = {
                TextButton(onClick = { viewModel.recoverKeep() }) { Text("保留") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.recoverDiscard() }) { Text("删除") }
            },
        )
    }

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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                when (state.phase) {
                    RecordingService.Phase.IDLE -> IdleContent(
                        hasPermission = state.hasPermission,
                        onStart = {
                            if (!state.hasPermission) onRequestPermission()
                            else viewModel.onStartPressed()
                        },
                    )
                    RecordingService.Phase.BUFFERING -> BufferingContent(
                        onPause = { viewModel.onPausePressed() },
                    )
                    RecordingService.Phase.REVIEW -> ReviewContent(
                        onSave = { viewModel.onSavePressed() },
                        onDelete = { viewModel.onDeletePressed() },
                    )
                }
            }

            // 右下角退出按钮 (带文字说明 + 二次确认).
            ExitButton(
                onExit = onExit,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
}

@Composable
private fun IdleContent(hasPermission: Boolean, onStart: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FloatingActionButton(
            onClick = onStart,
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            containerColor = if (hasPermission) MaterialTheme.colorScheme.primary else Color.Gray,
        ) {
            Text("开始", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        if (!hasPermission) {
            Spacer(Modifier.height(24.dp))
            Text(
                "需要麦克风权限才能录音",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BufferingContent(onPause: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FloatingActionButton(
            onClick = onPause,
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            containerColor = Color(0xFFD32F2F),
        ) {
            Icon(
                Icons.Filled.Pause,
                contentDescription = "暂停",
                tint = Color.White,
                modifier = Modifier.size(56.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("即时回放运行中", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReviewContent(onSave: () -> Unit, onDelete: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "已暂停, 请决定这段录音的去留",
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 32.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            ExtendedFloatingActionButton(
                onClick = onSave,
                icon = { Icon(Icons.Filled.List, contentDescription = null) },
                text = { Text("保存") },
                containerColor = MaterialTheme.colorScheme.primary,
            )
            ExtendedFloatingActionButton(
                onClick = onDelete,
                icon = { Icon(Icons.Filled.ExitToApp, contentDescription = null) },
                text = { Text("删除") },
                containerColor = Color(0xFFD32F2F),
            )
        }
    }
}

@Composable
private fun ExitButton(onExit: () -> Unit, modifier: Modifier = Modifier) {
    var askConfirm by remember { mutableStateOf(false) }
    var askPassword by remember { mutableStateOf(false) }

    TextButton(onClick = { askConfirm = true }, modifier = modifier) {
        Icon(
            Icons.Filled.ExitToApp,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(6.dp))
        Text(
            "退出并停止录音",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
    }

    if (askConfirm) {
        AlertDialog(
            onDismissRequest = { askConfirm = false },
            title = { Text("确定要彻底退出小E吗?") },
            text = { Text("当前未保存的录音将被丢弃。") },
            confirmButton = {
                TextButton(onClick = {
                    askConfirm = false
                    askPassword = true
                }) { Text("确认退出") }
            },
            dismissButton = {
                TextButton(onClick = { askConfirm = false }) { Text("取消") }
            },
        )
    }

    // 密码门控占位 (密码功能后期填充, 当前直接放行执行退出).
    if (askPassword) {
        onExit()
        askPassword = false
    }
}
