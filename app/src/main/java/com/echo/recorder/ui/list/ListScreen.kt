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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.echo.recorder.domain.model.RecordingCategory
import com.echo.recorder.playback.AudioPlayer
import com.echo.recorder.playback.DefaultAudioPlayerFactory
import com.echo.recorder.settings.SettingsRepository
import com.echo.recorder.share.ShareHelper
import com.echo.recorder.ui.common.FeatheredOrbIcon
import com.echo.recorder.ui.common.rememberPublicDirGrant
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
    DisposableEffect(Unit) {
        onDispose {
            player.release()
            viewModel.exitSelection()
        }
    }
    var playingId by remember { mutableStateOf<String?>(null) }

    var showCalendar by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    // 重命名目标录音 id.
    var renameTarget by remember { mutableStateOf<String?>(null) }
    // 删除密码门控: 单条删除 / 批量删除需验证密码.
    var verifySingleDelete by remember { mutableStateOf<String?>(null) }
    var verifyBatchDelete by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val items = if (state.tab == ListTab.TEMPORARY) state.temporary else state.longTerm
    // 按天分组, 天序降序 (最新的一天在最上), 天内保持数据源顺序 (最新录音在上)
    val groups = items.groupBy { startOfDay(it.createdAt) }
    val orderedDays = groups.keys.sortedDescending()
    // flat list with stable keys for LazyColumn indexing + date scroll.
    data class Entry(val key: String, val isHeader: Boolean, val day: String, val rec: Recording?)
    val flat = orderedDays.flatMap { dayEpoch ->
        val dayItems = groups.getValue(dayEpoch)
        listOf(Entry("h-$dayEpoch", true, dateKey(dayEpoch), null)) + dayItems.map { Entry(it.id, false, dateKey(dayEpoch), it) }
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
                    onDelete = { verifyBatchDelete = true },
                    onMoveToLongTerm = { viewModel.batchMoveToLongTerm() },
                    onExit = { viewModel.exitSelection() },
                )
            }
        },
    ) { padding ->
        if (items.isEmpty()) {
            EmptyList(recordingTab = state.tab)
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), state = listState) {
                // 临时录音提示条: 提醒 24h 自动删除
                if (state.tab == ListTab.TEMPORARY) {
                    item(key = "temp_expiry_hint") {
                        TempExpiryHint()
                    }
                }
                items(flat, key = { it.key }) { entry ->
                    if (entry.isHeader) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = entry.day,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.weight(1f))
                            // 全选: 选中当前 Tab 下所有录音 (进入多选模式).
                            TextButton(onClick = { viewModel.selectAll() }) {
                                Text(stringResource(R.string.select_all))
                            }
                        }
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
                            onSaveToPublic = { viewModel.saveToPublic(rec.id) },
                            onRename = { renameTarget = rec.id },
                            onDelete = { verifySingleDelete = rec.id },
                            onRequestPlayThis = { playingId = rec.id },
                            onShare = { shareRecording(context, rec) },
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

    // 删除密码门控.
    val listSettings = remember { SettingsRepository(context) }
    val listStoredHash by produceState<String?>(initialValue = null) {
        value = listSettings.passwordHash.first()
    }

    // 重命名录音.
    renameTarget?.let { renameId ->
        val rec = items.firstOrNull { it.id == renameId }
        if (rec != null) {
            var newName by remember(renameId) { mutableStateOf(rec.displayName.removeSuffix(".m4a")) }
            AlertDialog(
                onDismissRequest = { renameTarget = null },
                title = { Text(stringResource(R.string.rename)) },
                text = {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.renameRecording(renameId, newName)
                            renameTarget = null
                        },
                        enabled = newName.isNotBlank(),
                    ) { Text(stringResource(R.string.confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.cancel)) }
                },
            )
        }
    }

    // 单条删除密码门控.
    verifySingleDelete?.let { delId ->
        if (listStoredHash != null) {
            PasswordPromptDialog(
                storedHash = listStoredHash,
                recoveryHash = null,
                onVerify = {
                    verifySingleDelete = null
                    viewModel.delete(delId)
                },
                onDismiss = { verifySingleDelete = null },
            )
        } else {
            LaunchedEffect(delId) { viewModel.delete(delId); verifySingleDelete = null }
        }
    }

    // 批量删除密码门控.
    if (verifyBatchDelete) {
        if (listStoredHash != null) {
            PasswordPromptDialog(
                storedHash = listStoredHash,
                recoveryHash = null,
                onVerify = {
                    verifyBatchDelete = false
                    viewModel.batchDelete()
                },
                onDismiss = { verifyBatchDelete = false },
            )
        } else {
            LaunchedEffect(verifyBatchDelete) {
                viewModel.batchDelete()
                verifyBatchDelete = false
            }
        }
    }

    // 从公共目录导入 (含密码门禁).
    if (showImport) {
        ImportFromPublicDirDialog(
            context = context,
            viewModel = viewModel,
            onDismiss = { showImport = false },
        )
    }

    // 有长期录音尚未备份到安全位置 → 引导授权备份文件夹.
    if (state.needsPublicGrant) {
        val grant = rememberPublicDirGrant(onGranted = { viewModel.retryPendingBackups() })
        AlertDialog(
            onDismissRequest = { viewModel.dismissPublicGrantPrompt() },
            title = { Text(stringResource(R.string.public_dir_backup_pending_title)) },
            text = { Text(stringResource(R.string.public_dir_backup_pending_text)) },
            confirmButton = {
                TextButton(onClick = grant.request) {
                    Text(stringResource(R.string.public_dir_choose_folder))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPublicGrantPrompt() }) {
                    Text(stringResource(R.string.not_now))
                }
            },
        )
    }
}

/**
 * 从公共目录导入对话框.
 * - 若开启密码保护, 先验证密码.
 * - 未授权备份文件夹时, 先引导用户通过系统选择器授权 (SAF).
 * - 授权后列出备份文件夹中尚未导入的 .m4a 文件, 用户多选后复制到应用长期目录.
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
    var verified by remember { mutableStateOf(SessionAuth.savePublicUnlocked.value) }
    // 验证通过后先关闭密码对话框 (让关闭动画走完), 延迟片刻再打开导入对话框,
    // 避免两个 Dialog 窗口一关一开在部分 ROM 上互相抢占 (扫描不执行/输入法丢失).
    var showImportDialog by remember { mutableStateOf(false) }
    var importable by remember { mutableStateOf<List<PublicDirManager.PublicFileInfo>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(false) }
    val grant = rememberPublicDirGrant()

    // 验证 + 授权都通过后扫描可导入文件.
    LaunchedEffect(verified, grant.granted) {
        if (verified && grant.granted) {
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
                onVerify = {
                    verified = true
                    scope.launch {
                        delay(300)
                        showImportDialog = true
                    }
                },
                onDismiss = onDismiss,
            )
        } else {
            LaunchedEffect(Unit) {
                verified = true
                showImportDialog = true
            }
        }
        return
    }

    // 验证刚通过、密码对话框关闭动画未结束时, 暂不渲染导入框.
    if (!showImportDialog) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_from_public_dir_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!grant.granted) {
                    Text(
                        stringResource(R.string.public_dir_choose_folder_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = grant.request) {
                        Text(stringResource(R.string.public_dir_choose_folder))
                    }
                } else if (loading) {
                    Text("...", style = MaterialTheme.typography.bodyMedium)
                } else if (importable.isEmpty()) {
                    Text(
                        stringResource(R.string.import_no_new),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        stringResource(R.string.import_from_public_dir_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                                    Icons.Filled.CheckCircle,
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
                    val chosen = importable.filter { it.fileName in selected }
                    scope.launch {
                        val added = viewModel.importFromPublicDir(context, chosen)
                        SessionAuth.unlockSavePublic()
                        Toast.makeText(
                            context,
                            context.getString(R.string.import_done, added),
                            Toast.LENGTH_SHORT,
                        ).show()
                        onDismiss()
                    }
                },
                enabled = selected.isNotEmpty() && grant.granted,
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
    onSaveToPublic: () -> Unit = {},
    onRename: () -> Unit = {},
    onDelete: () -> Unit,
    onRequestPlayThis: () -> Unit,
    onShare: () -> Unit = {},
) {
    val painterSelect = if (selected) Icons.Filled.CheckCircle else null
    // 空片段判定: 时长不足 1 秒. 若文件实际存在且大小 > 0 但读不到时长, 视为"已损坏".
    val fileExists = runCatching { java.io.File(java.net.URI(rec.fileUrl)).exists() }.getOrDefault(false)
    val fileSize = runCatching { java.io.File(java.net.URI(rec.fileUrl)).length() }.getOrDefault(0L)
    val isEmpty = rec.durationMs < MIN_PLAYABLE_MS
    val isCorrupted = isEmpty && fileExists && fileSize > 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(shape = RoundedCornerShape(16.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer
                else if (expanded) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
            .padding(horizontal = 14.dp, vertical = 12.dp),
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
                // 标题: 有录音时长图标前缀
                val titleText = when {
                    isCorrupted -> stringResource(R.string.file_corrupted)
                    isEmpty -> stringResource(R.string.empty_fragment)
                    else -> rec.displayName
                }
                Text(
                    titleText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.KeyboardVoice,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = "${timeOnly(rec.createdAt)}  ·  ${formatElapsed(rec.durationMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!selectionMode) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (rec.category.name == "LONG_TERM") MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = if (rec.category.name == "LONG_TERM") stringResource(R.string.long_term)
                        else stringResource(R.string.temporary),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (rec.category.name == "LONG_TERM") MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }

        if (expanded) {
            Divider(
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            if (isEmpty) {
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        if (isCorrupted) stringResource(R.string.file_corrupted_cant_play)
                        else stringResource(R.string.empty_fragment_cant_play),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                MiniPlayer(
                    rec = rec,
                    player = player,
                    isPlayingThis = isPlayingThis,
                    onRequestPlayThis = onRequestPlayThis,
                    onSave = onSave,
                    onSaveToPublic = onSaveToPublic,
                    onRename = onRename,
                    onDelete = onDelete,
                    onShare = onShare,
                )
            }
        }
    }
}

@Composable
private fun MiniPlayer(
    rec: Recording,
    player: AudioPlayer,
    isPlayingThis: Boolean,
    onRequestPlayThis: () -> Unit,
    onSave: () -> Unit,
    onSaveToPublic: () -> Unit = {},
    onRename: () -> Unit = {},
    onDelete: () -> Unit,
    onShare: () -> Unit = {},
) {
    val ps by player.stateFlow.collectAsStateWithLifecycle()
    // 播放仅在用户点击播放按钮时触发 prepare, 展开不自动抢占播放.

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
            // 播放按钮: 羽化发光球体 (替代棱角分明的扁平圆)
            FeatheredOrbIcon(
                icon = if (isPlayingThis && ps.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                color = if (isPlayingThis) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.secondaryContainer,
                glowColor = if (isPlayingThis) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.secondary,
                contentDescription = stringResource(R.string.play_pause),
                onClick = {
                    if (isPlayingThis) {
                        if (ps.isPlaying) player.pause() else player.play()
                    } else {
                        player.prepare(rec)
                        onRequestPlayThis()
                        player.play()
                    }
                },
                iconTint = if (isPlayingThis) Color.White else MaterialTheme.colorScheme.onSecondaryContainer,
                buttonSize = 44.dp,
                iconSize = 22.dp,
            )
            Spacer(Modifier.size(8.dp))
            val cur = if (isPlayingThis) ps.currentPositionMs else 0
            Text(
                text = "${formatElapsed(cur.toLong())} / ${formatElapsed(duration.toLong())}",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.weight(1f))
            // 临时录音: 移至长期 (存档); 长期录音: 保存到公共目录 (手动重试备份).
            if (rec.category == RecordingCategory.TEMPORARY) {
                FeatheredOrbIcon(
                    icon = Icons.Filled.Archive,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    glowColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f),
                    contentDescription = stringResource(R.string.move_to_longterm),
                    onClick = onSave,
                    iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                    buttonSize = 38.dp,
                    iconSize = 18.dp,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            } else if (!rec.isPublicVirtual) {
                FeatheredOrbIcon(
                    icon = Icons.Filled.Save,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    glowColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f),
                    contentDescription = stringResource(R.string.save_to_public_dir),
                    onClick = onSaveToPublic,
                    iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                    buttonSize = 38.dp,
                    iconSize = 18.dp,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
            // 长期录音不再有"保存到公共目录"按钮 —— 自动备份永远开启, 移入长期即自动备份.
            FeatheredOrbIcon(
                icon = Icons.Filled.Share,
                color = MaterialTheme.colorScheme.secondaryContainer,
                glowColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f),
                contentDescription = stringResource(R.string.share),
                onClick = onShare,
                iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                buttonSize = 38.dp,
                iconSize = 18.dp,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
            // 重命名.
            FeatheredOrbIcon(
                icon = Icons.Filled.Edit,
                color = MaterialTheme.colorScheme.secondaryContainer,
                glowColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f),
                contentDescription = stringResource(R.string.rename),
                onClick = onRename,
                iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                buttonSize = 38.dp,
                iconSize = 18.dp,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
            FeatheredOrbIcon(
                icon = Icons.Filled.Delete,
                color = Color(0xFFF48FB1),
                glowColor = Color(0xFFFFB3C6).copy(alpha = 0.55f),
                contentDescription = stringResource(R.string.delete),
                onClick = onDelete,
                iconTint = Color.White,
                buttonSize = 38.dp,
                iconSize = 18.dp,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}

@Composable
private fun BatchBottomBar(count: Int, onDelete: () -> Unit, onMoveToLongTerm: () -> Unit, onExit: () -> Unit) {
    Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.selected_count, count),
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            IconButton(onClick = onMoveToLongTerm) { Icon(Icons.Filled.Archive, contentDescription = stringResource(R.string.batch_move_to_longterm), tint = MaterialTheme.colorScheme.primary) }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.batch_delete), tint = MaterialTheme.colorScheme.error) }
            IconButton(onClick = onExit) { Icon(Icons.Filled.CheckCircle, contentDescription = stringResource(R.string.exit_selection)) }
        }
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

/** 空列表占位: 图标 + 文案. */
@Composable
private fun EmptyList(recordingTab: ListTab) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(88.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.LibraryMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(44.dp),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.no_recordings),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(
                if (recordingTab == ListTab.TEMPORARY) R.string.empty_temporary_hint
                else R.string.empty_longterm_hint
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** 临时录音自动删除提示条 (仅临时 tab 顶部显示). */
@Composable
private fun TempExpiryHint() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.50f))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            stringResource(R.string.temp_auto_delete_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

