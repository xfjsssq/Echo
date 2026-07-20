package com.echo.recorder.ui.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echo.recorder.domain.model.Recording
import com.echo.recorder.playback.AudioPlayer
import com.echo.recorder.playback.DefaultAudioPlayerFactory
import com.echo.recorder.ui.formatElapsed

/**
 * 录音列表页 —— 极简主义, 无独立播放页.
 *
 * - 顶部双 Tab (临时/长期).
 * - 按日期分组, 日历图标入口 (占位).
 * - 点击条目 -> 原地展开迷你播放器 (进度条/播放暂停/时间).
 * - 长按条目 -> 多选模式, 底部批量操作栏.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ListScreen(viewModel: ListViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 列表页共享一个播放器; 展开哪条就准备哪条.
    val player = remember { DefaultAudioPlayerFactory().create() }
    DisposableEffect(Unit) { onDispose { player.release() } }
    var playingId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("录音列表") },
                    actions = {
                        IconButton(onClick = { /* 日历: 后期 */ }) {
                            Icon(Icons.Filled.CalendarMonth, contentDescription = "日历")
                        }
                    },
                )
                TabRow(selectedTabIndex = state.tab.ordinal) {
                    Tab(
                        selected = state.tab == ListTab.TEMPORARY,
                        onClick = { viewModel.switchTab(ListTab.TEMPORARY) },
                        text = { Text("临时录音") },
                    )
                    Tab(
                        selected = state.tab == ListTab.LONG_TERM,
                        onClick = { viewModel.switchTab(ListTab.LONG_TERM) },
                        text = { Text("长期录音") },
                    )
                }
            }
        },
        bottomBar = {
            if (state.selectionMode) {
                BatchBottomBar(
                    count = state.selected.size,
                    onDelete = { viewModel.batchDelete() },
                    onMoveToLongTerm = { viewModel.batchMoveToLongTerm() },
                    onExit = { viewModel.exitSelection() },
                )
            }
        },
    ) { padding ->
        val items = if (state.tab == ListTab.TEMPORARY) state.temporary else state.longTerm
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("暂无录音", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                val groups = items.groupBy { dateKey(it.createdAt) }
                groups.forEach { (day, dayItems) ->
                    item(key = "h-$day") {
                        Text(
                            text = day,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(dayItems, key = { it.id }) { rec ->
                        RecordingRow(
                            rec = rec,
                            expanded = state.expandedId == rec.id,
                            selectionMode = state.selectionMode,
                            selected = rec.id in state.selected,
                            player = player,
                            isPlayingThis = playingId == rec.id,
                            onTap = {
                                if (state.selectionMode) viewModel.toggleSelect(rec.id)
                                else viewModel.toggleExpanded(rec.id)
                            },
                            onLongPress = { viewModel.enterSelection(rec.id) },
                            onSave = { viewModel.moveToLongTerm(rec.id) },
                            onDelete = { viewModel.delete(rec.id) },
                            onRequestPlayThis = { playingId = rec.id },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordingRow(
    rec: Recording,
    expanded: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    player: AudioPlayer,
    isPlayingThis: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onRequestPlayThis: () -> Unit,
) {
    val painterSelect = if (selected) Icons.Filled.CheckCircle else null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                Icon(
                    painterSelect ?: Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.size(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(rec.displayName, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "${timeOnly(rec.createdAt)}   ${formatElapsed(rec.durationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!selectionMode) {
                Text(
                    text = if (rec.category.name == "LONG_TERM") "长期" else "临时",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // 原地展开的迷你播放器.
        if (expanded) {
            MiniPlayer(
                rec = rec,
                player = player,
                isPlayingThis = isPlayingThis,
                onRequestPlayThis = onRequestPlayThis,
                onSave = onSave,
                onDelete = onDelete,
            )
        }
    }
    Divider()
}

@Composable
private fun MiniPlayer(
    rec: Recording,
    player: AudioPlayer,
    isPlayingThis: Boolean,
    onRequestPlayThis: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val ps by player.stateFlow.collectAsStateWithLifecycle()
    // 展开时准备本条 (仅当播放器当前没在播这条时).
    LaunchedEffect(rec.id, isPlayingThis) {
        if (!isPlayingThis) {
            player.prepare(rec)
            onRequestPlayThis()
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        val duration = if (ps.durationMs > 0) ps.durationMs else rec.durationMs.toInt().coerceAtLeast(1)
        var seekByUser by remember { mutableStateOf(false) }
        var seekValue by remember { mutableStateOf(0f) }

        Slider(
            value = if (isPlayingThis && !seekByUser) ps.currentPositionMs.toFloat() else seekValue,
            onValueChange = { seekByUser = true; seekValue = it },
            onValueChangeFinished = {
                if (isPlayingThis) player.seekTo(seekValue.toInt())
                seekByUser = false
            },
            valueRange = 0f..duration.toFloat(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {
                if (isPlayingThis) {
                    if (ps.isPlaying) player.pause() else player.play()
                } else {
                    player.prepare(rec)
                    onRequestPlayThis()
                    player.play()
                }
            }) {
                Icon(
                    if (isPlayingThis && ps.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "播放/暂停",
                )
            }
            val cur = if (isPlayingThis) ps.currentPositionMs else 0
            Text(
                text = "${formatElapsed(cur.toLong())} / ${formatElapsed(duration.toLong())}",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSave) {
                Icon(Icons.Filled.Archive, contentDescription = "移至长期")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "删除")
            }
        }
    }
}

@Composable
private fun BatchBottomBar(
    count: Int,
    onDelete: () -> Unit,
    onMoveToLongTerm: () -> Unit,
    onExit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("已选 $count", modifier = Modifier.weight(1f))
        IconButton(onClick = onMoveToLongTerm) {
            Icon(Icons.Filled.Archive, contentDescription = "批量移至长期")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "批量删除")
        }
        IconButton(onClick = onExit) {
            Icon(Icons.Filled.CheckCircle, contentDescription = "退出多选")
        }
    }
}

private val dayFmt = java.text.SimpleDateFormat("yyyy年MM月dd日 EEEE", java.util.Locale.getDefault())
private val timeFmt = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())

private fun dateKey(epochMs: Long): String = dayFmt.format(java.util.Date(epochMs))
private fun timeOnly(epochMs: Long): String = timeFmt.format(java.util.Date(epochMs))
