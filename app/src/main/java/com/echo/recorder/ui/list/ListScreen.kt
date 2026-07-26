package com.echo.recorder.ui.list

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echo.recorder.R
import com.echo.recorder.auth.SessionAuth
import com.echo.recorder.common.PublicDirManager
import com.echo.recorder.domain.model.Recording
import com.echo.recorder.playback.AudioPlayer
import com.echo.recorder.playback.DefaultAudioPlayerFactory
import com.echo.recorder.settings.SettingsRepository
import com.echo.recorder.share.ShareHelper
import com.echo.recorder.ui.formatElapsed
import com.echo.recorder.ui.lock.PasswordPromptDialog
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Date

import java.util.Locale

private const val MIN_PLAYABLE_MS = 1000L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ListScreen(viewModel: ListViewModel, onOpenPublicDir: () -> Unit = {}) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val player = remember { DefaultAudioPlayerFactory().create() }
    DisposableEffect(Unit) { onDispose { player.release() } }
    var playingId by remember { mutableStateOf<String?>(null) }

    var showCalendar by remember { mutableStateOf(false) }
    var pendingSaveToPublic by remember { mutableStateOf<Recording?>(null) }
    var showImport by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val items = if (state.tab == ListTab.TEMPORARY) state.temporary else state.longTerm
    val groups = items.groupBy { dateKey(it.createdAt) }
    val orderedDays = groups.keys.sorted()
    // flat list with stable keys for LazyColumn indexing + date scroll.
    data class Entry(val key: String, val isHeader: Boolean, val day: String, val rec: Recording?)
    val flat = orderedDays.flatMap { day ->
        val dayItems = groups.getValue(day)
        listOf(Entry("h-$day", true, day, null)) + dayItems.map { Entry(it.id, false, day, it) }
    }
    var scrollTarget by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(scrollTarget) {
        val target = scrollTarget ?: return@LaunchedEffect
        val idx = flat.indexOfFirst { it.isHeader && it.day == target }
        if (idx >= 0) {
            listState.animateScrollToItem(idx)
        } else {
            Toast.makeText(context, context.getString(R.string.no_recording_that_day), Toast.LENGTH_SHORT).show()
        }
        scrollTarget = null
    }

    val recordingDays = remember(items) { items.map { startOfDay(it.createdAt) }.toSet() }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        // 日历入口放到标题左侧 (导航图标位), 与主页右上角设置按钮完全错开.
                        IconButton(onClick = { showCalendar = true }) {
                            Icon(Icons.Filled.CalendarMonth, contentDescription = stringResource(R.string.calendar))
                        }
                    },
                    title = { Text(stringResource(R.string.record_list)) },
                    actions = {
                        if (state.tab == ListTab.LONG_TERM) {
                            IconButton(onClick = { showImport = true }) {
                                Icon(Icons.Filled.FileOpen, contentDescription = stringResource(R.string.import_from_public_dir))
                            }
                        }
                    },
                )
                TabRow(selectedTabIndex = state.tab.ordinal) {
                    Tab(
                        selected = state.tab == ListTab.TEMPORARY,
                        onClick = { viewModel.switchTab(ListTab.TEMPORARY) },
                        text = { Text(stringResource(R.string.temporary_recordings)) },
                    )
                    Tab(
                        selected = state.tab == ListTab.LONG_TERM,
                        onClick = { viewModel.switchTab(ListTab.LONG_TERM) },
                        text = { Text(stringResource(R.string.longterm_recordings)) },
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
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_recordings), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), state = listState) {
                items(flat, key = { it.key }) { entry ->
                    if (entry.isHeader) {
                        Text(
                            text = entry.day,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    } else {
                        val rec = entry.rec!!
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
                            onShare = { shareRecording(context, rec) },
                            onSaveToPublic = { pendingSaveToPublic = rec },
                        )
                    }
                }
            }
        }
    }

    if (showCalendar) {
        CalendarDialog(
            recordingDays = recordingDays,
            onDateSelected = { date ->
                showCalendar = false
                val dayStr = dayFmt().format(Date(startOfDayFromLocal(date)))
                scrollTarget = dayStr
            },
            onDismiss = { showCalendar = false },
        )
    }

    // 保存到公共目录处理 (含密码门禁).
    pendingSaveToPublic?.let { rec ->
        SaveToPublicHandler(
            rec = rec,
            context = context,
            viewModel = viewModel,
            onDone = { pendingSaveToPublic = null },
        )
    }

    // 从公共目录导入 (含密码门禁).
    if (showImport) {
        ImportFromPublicDirDialog(
            context = context,
            viewModel = viewModel,
            onDismiss = { showImport = false },
        )
    }
}

/** 保存到公共目录: 首次离开应用后需密码, 本会话内免密. */
@Composable
private fun SaveToPublicHandler(
    rec: Recording,
    context: Context,
    viewModel: ListViewModel,
    onDone: () -> Unit,
) {
    val settings = remember { SettingsRepository(context) }
    val passwordEnabled by produceState(initialValue = false) {
        value = settings.passwordEnabled.first()
    }
    val storedHash by produceState<String?>(initialValue = null) {
        value = settings.passwordHash.first()
    }
    val isPattern by produceState(initialValue = false) {
        value = settings.passwordType.first() == "pattern"
    }
    var verified by remember { mutableStateOf(SessionAuth.savePublicUnlocked) }

    if (verified) {
        LaunchedEffect(rec.id) {
            val ok = viewModel.saveToPublic(context, rec)
            SessionAuth.savePublicUnlocked = true
            Toast.makeText(
                context,
                context.getString(if (ok) R.string.save_to_public_done else R.string.delete),
                Toast.LENGTH_SHORT,
            ).show()
            onDone()
        }
        return
    }

    // 未验证: 若开启密码则弹验证框, 否则直接执行.
    if (passwordEnabled) {
        PasswordPromptDialog(
            storedHash = storedHash,
            recoveryHash = null,
            isPattern = isPattern,
            onVerify = { verified = true },
            onDismiss = { onDone() },
        )
    } else {
        LaunchedEffect(rec.id) { verified = true }
    }
}

/**
 * 从公共目录导入对话框.
 * - 若开启密码保护, 先验证密码.
 * - 验证通过后列出公共目录中尚未导入的 .m4a 文件, 用户多选后导入为虚引用.
 */
@Composable
private fun ImportFromPublicDirDialog(
    context: Context,
    viewModel: ListViewModel,
    onDismiss: () -> Unit,
) {
    val settings = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()
    val passwordEnabled by produceState(initialValue = false) {
        value = settings.passwordEnabled.first()
    }
    val storedHash by produceState<String?>(initialValue = null) {
        value = settings.passwordHash.first()
    }
    val isPattern by produceState(initialValue = false) {
        value = settings.passwordType.first() == "pattern"
    }
    var verified by remember { mutableStateOf(SessionAuth.savePublicUnlocked) }
    var importable by remember { mutableStateOf<List<PublicDirManager.PublicFileInfo>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(false) }

    // 验证通过后扫描可导入文件.
    LaunchedEffect(verified) {
        if (verified) {
            loading = true
            importable = viewModel.scanImportable(context)
            loading = false
        }
    }

    if (!verified) {
        if (passwordEnabled) {
            PasswordPromptDialog(
                storedHash = storedHash,
                recoveryHash = null,
                isPattern = isPattern,
                onVerify = { verified = true },
                onDismiss = onDismiss,
            )
        } else {
            LaunchedEffect(Unit) { verified = true }
        }
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_from_public_dir_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.import_from_public_dir_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (loading) {
                    Text("...", style = MaterialTheme.typography.bodyMedium)
                } else if (importable.isEmpty()) {
                    Text(
                        stringResource(R.string.import_no_new),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                        items(importable, key = { it.fileName }) { info ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selected = if (info.fileName in selected) selected - info.fileName else selected + info.fileName
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    if (info.fileName in selected) Icons.Filled.CheckCircle else Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = if (info.fileName in selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(Modifier.size(12.dp))
                                Text(info.displayName, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        val added = viewModel.importFromPublicDir(context, selected.toList())
                        SessionAuth.savePublicUnlocked = true
                        Toast.makeText(
                            context,
                            context.getString(R.string.import_done, added),
                            Toast.LENGTH_SHORT,
                        ).show()
                        onDismiss()
                    }
                },
                enabled = selected.isNotEmpty(),
            ) { Text(stringResource(R.string.import_selected)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

private fun shareRecording(context: Context, rec: Recording) {
    val file = runCatching { java.io.File(java.net.URI(rec.fileUrl)) }.getOrNull() ?: return
    if (!file.exists()) return
    ShareHelper.shareAudio(context, file, "${context.packageName}.fileprovider")
}

@Composable
private fun CalendarDialog(
    recordingDays: Set<Long>,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    val monthLabel: String = remember(month) {
        if (languageIsEn()) {
            java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.ENGLISH).format(
                java.util.Date.from(month.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant())
            )
        } else {
            "%d年%02d月".format(month.year, month.monthValue)
        }
    }
    val leadingBlanks = month.atDay(1).dayOfWeek.value % 7 // 周日=0
    val daysInMonth = month.lengthOfMonth()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { month = month.minusMonths(1) }) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.previous_month))
                }
                Text(
                    monthLabel,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium,
                )
                IconButton(onClick = { month = month.plusMonths(1) }) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.next_month))
                }
            }
        },
        text = {
            Column {
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        stringResource(R.string.weekday_sun),
                        stringResource(R.string.weekday_mon),
                        stringResource(R.string.weekday_tue),
                        stringResource(R.string.weekday_wed),
                        stringResource(R.string.weekday_thu),
                        stringResource(R.string.weekday_fri),
                        stringResource(R.string.weekday_sat),
                    ).forEach { w ->
                        Text(
                            w, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                val total = leadingBlanks + daysInMonth
                val rows = (total + 6) / 7
                for (r in 0 until rows) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (c in 0 until 7) {
                            val idx = r * 7 + c
                            val day = idx - leadingBlanks + 1
                            Box(
                                modifier = Modifier.weight(1f).padding(vertical = 2.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (day in 1..daysInMonth) {
                                    val date = month.atDay(day)
                                    val hasRec = recordingDays.contains(startOfDayFromLocal(date))
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.fillMaxWidth().clip(CircleShape).clickable { onDateSelected(date) }.padding(vertical = 4.dp),
                                    ) {
                                        Text("$day", style = MaterialTheme.typography.bodyMedium)
                                        if (hasRec) {
                                            Box(modifier = Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
    )
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
    onShare: () -> Unit = {},
    onSaveToPublic: () -> Unit = {},
) {
    val painterSelect = if (selected) Icons.Filled.CheckCircle else null
    // 空片段判定: 时长不足 1 秒. 若文件实际存在且大小 > 0 但读不到时长, 视为"已损坏".
    val fileExists = runCatching { java.io.File(java.net.URI(rec.fileUrl)).exists() }.getOrDefault(false)
    val fileSize = runCatching { java.io.File(java.net.URI(rec.fileUrl)).length() }.getOrDefault(0L)
    val isEmpty = rec.durationMs < MIN_PLAYABLE_MS
    val isCorrupted = isEmpty && fileExists && fileSize > 0

    Column(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onTap, onLongClick = onLongPress).padding(horizontal = 16.dp, vertical = 12.dp),
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
                Text(
                    when {
                        isCorrupted -> stringResource(R.string.file_corrupted)
                        isEmpty -> stringResource(R.string.empty_fragment)
                        else -> rec.displayName
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "${timeOnly(rec.createdAt)}   ${formatElapsed(rec.durationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!selectionMode) {
                Text(
                    text = if (rec.category.name == "LONG_TERM") stringResource(R.string.long_term)
                    else stringResource(R.string.temporary),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (expanded) {
            if (isEmpty) {
                Text(
                    if (isCorrupted) stringResource(R.string.file_corrupted_cant_play)
                    else stringResource(R.string.empty_fragment_cant_play),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                MiniPlayer(
                    rec = rec,
                    player = player,
                    isPlayingThis = isPlayingThis,
                    onRequestPlayThis = onRequestPlayThis,
                    onSave = onSave,
                    onDelete = onDelete,
                    onShare = onShare,
                    onSaveToPublic = onSaveToPublic,
                )
            }
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
    onShare: () -> Unit = {},
    onSaveToPublic: () -> Unit = {},
) {
    val ps by player.stateFlow.collectAsStateWithLifecycle()
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
                    contentDescription = stringResource(R.string.play_pause),
                )
            }
            val cur = if (isPlayingThis) ps.currentPositionMs else 0
            Text(
                text = "${formatElapsed(cur.toLong())} / ${formatElapsed(duration.toLong())}",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.weight(1f))
            // 临时录音显示"移至长期", 长期录音显示"保存到公共目录" (虚引用除外).
            if (rec.category.name == "TEMPORARY") {
                IconButton(onClick = onSave) {
                    Icon(Icons.Filled.Archive, contentDescription = stringResource(R.string.move_to_longterm))
                }
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.share))
            }
            if (rec.category.name == "LONG_TERM" && !rec.isPublicVirtual) {
                IconButton(onClick = onSaveToPublic) {
                    Icon(Icons.Filled.Save, contentDescription = stringResource(R.string.save_to_public))
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
            }
        }
    }
}

@Composable
private fun BatchBottomBar(count: Int, onDelete: () -> Unit, onMoveToLongTerm: () -> Unit, onExit: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.selected_count, count),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onMoveToLongTerm) { Icon(Icons.Filled.Archive, contentDescription = stringResource(R.string.batch_move_to_longterm)) }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.batch_delete)) }
        IconButton(onClick = onExit) { Icon(Icons.Filled.CheckCircle, contentDescription = stringResource(R.string.exit_selection)) }
    }
}

private fun dayFmt(): java.text.SimpleDateFormat {
    val locale = java.util.Locale.getDefault()
    // 英文环境使用 "Jul 26, 2026" 格式, 中文环境使用 "2026年07月26日 星期日".
    val pattern = if (languageIsEn()) "MMM dd, yyyy" else "yyyy年MM月dd日 EEEE"
    return java.text.SimpleDateFormat(pattern, locale)
}

private fun timeFmt(): java.text.SimpleDateFormat =
    java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())

private fun languageIsEn(): Boolean = java.util.Locale.getDefault().language == "en"

private fun dateKey(epochMs: Long): String = dayFmt().format(java.util.Date(epochMs))
private fun timeOnly(epochMs: Long): String = timeFmt().format(java.util.Date(epochMs))

private fun startOfDay(epochMs: Long): Long = java.util.Calendar.getInstance().apply {
    timeInMillis = epochMs; set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
}.timeInMillis

private fun startOfDayFromLocal(date: LocalDate): Long =
    date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
