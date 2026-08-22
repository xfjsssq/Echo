package com.echo.recorder.ui.list

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import com.echo.recorder.ui.common.AnimatedMode
import com.echo.recorder.ui.common.AnimatedStep
import com.echo.recorder.ui.common.EchoHaptics
import com.echo.recorder.ui.common.FeatheredOrbIcon
import com.echo.recorder.ui.common.GlassSurface
import com.echo.recorder.ui.common.LoadingPulse
import com.echo.recorder.ui.common.StepDirection
import com.echo.recorder.ui.common.animatedListEntrance
import com.echo.recorder.ui.common.echoPressScale
import com.echo.recorder.ui.common.rememberEchoHaptics
import com.echo.recorder.ui.common.rememberPublicDirGrant
import com.echo.recorder.ui.formatElapsed
import com.echo.recorder.ui.lock.PasswordPromptDialog
import com.echo.recorder.ui.theme.EchoMotion
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Date

import java.util.Locale

private const val MIN_PLAYABLE_MS = 1000L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ListScreen(
    viewModel: ListViewModel,
    onOpenPublicDir: () -> Unit = {},
    onOpenPasswordSetup: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 删除/导入密码门控共用的哈希读取 (需在 Scaffold 之前声明供顶部按钮捕获).
    val listSettings = remember { SettingsRepository(context) }
    val listStoredHash by produceState<String?>(initialValue = null) {
        value = listSettings.passwordHash.first()
    }

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
    // 公共目录功能门槛: 从未设置过密码时引导先去设置密码.
    var showImportNeedPassword by remember { mutableStateOf(false) }
    // 重命名目标录音 id.
    var renameTarget by remember { mutableStateOf<String?>(null) }
    // 删除密码门控: 单条删除 / 批量删除需验证密码.
    var verifySingleDelete by remember { mutableStateOf<String?>(null) }
    var verifyBatchDelete by remember { mutableStateOf(false) }
    // 每个 Tab 独立滚动状态: 临时 tab 顶部比长期 tab 多一条提示栏, 同一索引在两 tab 对应
    // 不同内容, 共享锚点会在切换后错位; 过渡期间两个 LazyColumn 也不能再挂同一个 state.
    val tempListState = rememberLazyListState()
    val longTermListState = rememberLazyListState()

    val items = if (state.tab == ListTab.TEMPORARY) state.temporary else state.longTerm
    val flat = buildFlat(items)
    var scrollTarget by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(scrollTarget) {
        val target = scrollTarget ?: return@LaunchedEffect
        val idx = flat.indexOfFirst { it.isHeader && it.day == target }
        if (idx >= 0) {
            val st = if (state.tab == ListTab.TEMPORARY) tempListState else longTermListState
            st.animateScrollToItem(idx)
        } else {
            Toast.makeText(context, context.getString(R.string.no_recording_that_day), Toast.LENGTH_SHORT).show()
        }
        scrollTarget = null
    }

    val recordingDays = remember(items) { items.map { startOfDay(it.createdAt) }.toSet() }
    val haptics = rememberEchoHaptics()

    // 进入多选模式时给一次触感 (长按已有 LongPress, 这里补批量栏出现的确认感)
    LaunchedEffect(state.selectionMode) {
        if (state.selectionMode) haptics.gestureStart()
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        // 日历入口放到标题左侧 (导航图标位), 与主页右上角设置按钮完全错开.
                        IconButton(
                            onClick = { showCalendar = true },
                            modifier = Modifier.echoPressScale(0.9f),
                        ) {
                            Icon(Icons.Filled.CalendarMonth, contentDescription = stringResource(R.string.calendar))
                        }
                    },
                    title = { Text(stringResource(R.string.record_list)) },
                    actions = {
                        if (state.tab == ListTab.LONG_TERM) {
                            IconButton(
                                onClick = {
                                    // 公共目录功能门槛: 从未设置过密码时先引导设置, 不允许直接导入.
                                    if (listStoredHash != null) showImport = true else showImportNeedPassword = true
                                },
                                modifier = Modifier.echoPressScale(0.9f),
                            ) {
                                Icon(Icons.Filled.FileOpen, contentDescription = stringResource(R.string.import_from_public_dir))
                            }
                        }
                    },
                )
                TabRow(
                    selectedTabIndex = state.tab.ordinal,
                    indicator = { tabPositions ->
                        // 弹簧渐变胶囊: 黄→蓝品牌渐变, 贴 TabRow 底边 (不遮文字), 位置/宽度弹性跟随
                        if (state.tab.ordinal < tabPositions.size) {
                            val pos = tabPositions[state.tab.ordinal]
                            val left by animateDpAsState(
                                pos.left + pos.width * 0.22f,
                                EchoMotion.fastSpatial(),
                                label = "tab_left",
                            )
                            val width by animateDpAsState(
                                pos.width * 0.56f,
                                EchoMotion.fastSpatial(),
                                label = "tab_width",
                            )
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.BottomStart,
                            ) {
                                Box(
                                    Modifier
                                        .offset(x = left)
                                        .size(width = width, height = 3.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(
                                                    MaterialTheme.colorScheme.primary,
                                                    MaterialTheme.colorScheme.secondary,
                                                ),
                                            ),
                                        ),
                                )
                            }
                        }
                    },
                ) {
                    Tab(
                        selected = state.tab == ListTab.TEMPORARY,
                        onClick = {
                            haptics.tick()
                            viewModel.switchTab(ListTab.TEMPORARY)
                        },
                        text = { Text(stringResource(R.string.temporary_recordings)) },
                    )
                    Tab(
                        selected = state.tab == ListTab.LONG_TERM,
                        onClick = {
                            haptics.tick()
                            viewModel.switchTab(ListTab.LONG_TERM)
                        },
                        text = { Text(stringResource(R.string.longterm_recordings)) },
                    )
                }
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = state.selectionMode,
                enter = slideInVertically(
                    animationSpec = tween(280, easing = FastOutSlowInEasing),
                    initialOffsetY = { it },
                ) + fadeIn(tween(220)),
                exit = slideOutVertically(
                    animationSpec = tween(240),
                    targetOffsetY = { it },
                ) + fadeOut(tween(180)),
                label = "batch_bar",
            ) {
                BatchBottomBar(
                    count = state.selected.size,
                    onDelete = { verifyBatchDelete = true },
                    onMoveToLongTerm = { viewModel.batchMoveToLongTerm() },
                    onExit = { viewModel.exitSelection() },
                )
            }
        },
    ) { padding ->
        // Tab 内容过渡: 交叉淡化+微缩放, 列表的错峰入场/模糊揭示随之重放
        AnimatedMode(
            targetState = state.tab,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) { tab ->
            // 每页只读自己 Tab 的数据与滚动状态: 退出页保留旧 Tab 画面,
            // 过渡期间两个 LazyColumn 不再共享同一个 LazyListState.
            val tabItems = if (tab == ListTab.TEMPORARY) state.temporary else state.longTerm
            val tabFlat = buildFlat(tabItems)
            val tabListState = if (tab == ListTab.TEMPORARY) tempListState else longTermListState
            if (tabItems.isEmpty()) {
                EmptyList(
                    recordingTab = tab,
                    modifier = Modifier.animatedListEntrance(index = 0, withBlur = false),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), state = tabListState) {
                // 临时录音提示条: 提醒 24h 自动删除
                if (tab == ListTab.TEMPORARY) {
                    item(key = "temp_expiry_hint") {
                        TempExpiryHint(modifier = Modifier.animatedListEntrance(index = 0, withBlur = false))
                    }
                }
                itemsIndexed(tabFlat, key = { _, entry -> entry.key }) { index, entry ->
                    if (entry.isHeader) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .animateItemPlacement()
                                .animatedListEntrance(index = index, withBlur = false),
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
                            modifier = Modifier
                                .animateItemPlacement()
                                .animatedListEntrance(index = index),
                            expanded = state.expandedId == rec.id,
                            selectionMode = state.selectionMode,
                            selected = rec.id in state.selected,
                            player = player,
                            isPlayingThis = playingId == rec.id,
                            onTap = {
                                if (state.selectionMode) {
                                    haptics.tick()
                                    viewModel.toggleSelect(rec.id)
                                } else {
                                    viewModel.toggleExpanded(rec.id)
                                }
                            },
                            onLongPress = {
                                haptics.longPress()
                                viewModel.enterSelection(rec.id)
                            },
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
    }

    if (showCalendar) {
        CalendarDialog(
            recordingDays = recordingDays,
            onDateSelected = { date ->
                haptics.confirm()
                showCalendar = false
                val dayStr = dayFmt().format(Date(startOfDayFromLocal(date)))
                scrollTarget = dayStr
            },
            onDismiss = { showCalendar = false },
        )
    }

    // 删除密码门控 (listSettings/listStoredHash 已前置到函数开头).

    // 重命名录音.
    renameTarget?.let { renameId ->
        val rec = items.firstOrNull { it.id == renameId }
        if (rec != null) {
            var newName by remember(renameId) { mutableStateOf(rec.displayName.removeSuffix(".m4a")) }
            AlertDialog(
                onDismissRequest = { renameTarget = null },
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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

    // 公共目录功能门槛: 未设置密码时引导先设置 (列表页导入入口的拦截对话框).
    if (showImportNeedPassword) {
        AlertDialog(
            onDismissRequest = { showImportNeedPassword = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(stringResource(R.string.public_dir_password_required_title)) },
            text = { Text(stringResource(R.string.public_dir_password_required_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showImportNeedPassword = false
                    onOpenPasswordSetup()
                }) { Text(stringResource(R.string.go_set_password)) }
            },
            dismissButton = {
                TextButton(onClick = { showImportNeedPassword = false }) { Text(stringResource(R.string.cancel)) }
            },
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
    // ⚠️ 初始值取 verified: 同会话二次进入时 SessionAuth 已免密 (verified 直接为 true),
    //    若固定初始化 false 会卡死在下方 "if (!showImportDialog) return" —— 什么都不渲染,
    //    用户只能杀掉应用重进 (导入"只能一次"的病根).
    var showImportDialog by remember { mutableStateOf(verified) }
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
        if (storedHash == null) {
            // 防线: 从未设置过密码 → 不允许使用导入 (主入口已引导, 此处兜底拦截).
            AlertDialog(
                onDismissRequest = onDismiss,
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                title = { Text(stringResource(R.string.public_dir_password_required_title)) },
                text = { Text(stringResource(R.string.public_dir_password_required_text)) },
                confirmButton = {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                },
            )
            return
        }
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
            // 设置过密码但当前关闭了验证: 与删除录音门控同语义, 直接放行.
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
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                    LoadingPulse()
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
                    val importHaptics = rememberEchoHaptics()
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                        items(importable, key = { it.fileName }) { info ->
                            val picked = info.fileName in selected
                            ImportFileRow(
                                displayName = info.displayName,
                                picked = picked,
                                onClick = {
                                    importHaptics.tick()
                                    selected = if (picked) selected - info.fileName else selected + info.fileName
                                },
                            )
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

/** 导入文件行: 按压反馈 + 选中勾弹性缩放 (与录音列表行同语言). */
@Composable
private fun ImportFileRow(displayName: String, picked: Boolean, onClick: () -> Unit) {
    val checkScale = remember { Animatable(1f) }
    LaunchedEffect(picked) {
        if (picked) {
            checkScale.snapTo(0.5f)
            checkScale.animateTo(1f, EchoMotion.fastSpatial())
        }
    }
    val checkTint by animateColorAsState(
        targetValue = if (picked) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        animationSpec = EchoMotion.fastEffects(),
        label = "import_check_tint",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .echoPressScale(0.985f)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = checkTint,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    scaleX = checkScale.value
                    scaleY = checkScale.value
                },
        )
        Spacer(Modifier.size(12.dp))
        Text(displayName, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun CalendarDialog(
    recordingDays: Set<Long>,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    // 翻月方向: 决定网格滑入方向 (前翻向前, 后翻向后)
    var navDir by remember { mutableStateOf(StepDirection.Forward) }
    val haptics = rememberEchoHaptics()
    val monthLabel: String = remember(month) {
        if (languageIsEn()) {
            java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.ENGLISH).format(
                java.util.Date.from(month.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant())
            )
        } else {
            "%d年%02d月".format(month.year, month.monthValue)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        haptics.tick()
                        navDir = StepDirection.Backward
                        month = month.minusMonths(1)
                    },
                    modifier = Modifier.echoPressScale(0.9f),
                ) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.previous_month))
                }
                Text(
                    monthLabel,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium,
                )
                IconButton(
                    onClick = {
                        haptics.tick()
                        navDir = StepDirection.Forward
                        month = month.plusMonths(1)
                    },
                    modifier = Modifier.echoPressScale(0.9f),
                ) {
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
                // 翻月: 网格方向性滑动而非瞬切
                AnimatedStep(targetState = month, direction = navDir) { m ->
                    MonthGrid(m, recordingDays, onDateSelected)
                }
            }
        },
    )
}

/** 单月日期网格 (按传入月份计算, 供翻月过渡冻结旧月面). */
@Composable
private fun MonthGrid(month: YearMonth, recordingDays: Set<Long>, onDateSelected: (LocalDate) -> Unit) {
    val leadingBlanks = month.atDay(1).dayOfWeek.value % 7 // 周日=0
    val daysInMonth = month.lengthOfMonth()
    val total = leadingBlanks + daysInMonth
    val rows = (total + 6) / 7
    Column {
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(CircleShape)
                                    .echoPressScale(0.9f)
                                    .clickable { onDateSelected(date) }
                                    .padding(vertical = 4.dp),
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordingRow(
    rec: Recording,
    modifier: Modifier = Modifier,
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
    // remember 缓存文件系统查询 — 此前每次重组都 File.exists()/length() (播放中每帧重组
    // = 每帧主线程磁盘 I/O, 是列表页掉帧元凶), 现只在 rec 变化时查一次.
    val fileExists by remember(rec.id, rec.fileUrl) {
        mutableStateOf(runCatching { java.io.File(java.net.URI(rec.fileUrl)).exists() }.getOrDefault(false))
    }
    val fileSize by remember(rec.id, rec.fileUrl) {
        mutableStateOf(runCatching { java.io.File(java.net.URI(rec.fileUrl)).length() }.getOrDefault(0L))
    }
    val isEmpty = rec.durationMs < MIN_PLAYABLE_MS
    val isCorrupted = isEmpty && fileExists && fileSize > 0

    // 选中/展开状态背景色平滑过渡, 避免跳变
    val rowBg by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.secondaryContainer
            expanded -> MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = tween(240),
        label = "row_bg",
    )

    // 选中勾: 选中瞬间从 0.5 弹到 1 (轻微过冲), 不再瞬移
    val checkScale = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) {
            checkScale.snapTo(0.5f)
            checkScale.animateTo(1f, EchoMotion.fastSpatial())
        }
    }
    val checkTint by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        animationSpec = EchoMotion.fastEffects(),
        label = "check_tint",
    )

    // 正在播放本行: 品牌色描边呼吸提示 (状态一眼可见)
    val playingBorder by animateColorAsState(
        targetValue = if (isPlayingThis) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        else MaterialTheme.colorScheme.primary.copy(alpha = 0f),
        animationSpec = EchoMotion.slowEffects(),
        label = "playing_border",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(shape = RoundedCornerShape(16.dp))
            .background(rowBg)
            .echoPressScale(0.985f)
            .border(1.dp, playingBorder, RoundedCornerShape(16.dp))
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
                    tint = checkTint,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer {
                            scaleX = checkScale.value
                            scaleY = checkScale.value
                        },
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

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = tween(280, easing = FastOutSlowInEasing),
            ) + fadeIn(tween(220)),
            exit = shrinkVertically(
                animationSpec = tween(240),
            ) + fadeOut(tween(160)),
            label = "row_expand",
        ) {
            Column {
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
    val haptics = rememberEchoHaptics()

    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        val duration = if (ps.durationMs > 0) ps.durationMs else rec.durationMs.toInt().coerceAtLeast(1)
        var seekByUser by remember { mutableStateOf(false) }
        var seekValue by remember { mutableStateOf(0f) }

        GlowSlider(
            value = if (isPlayingThis && !seekByUser) ps.currentPositionMs.toFloat() else seekValue,
            onValueChange = { seekByUser = true; seekValue = it },
            onValueChangeFinished = {
                if (isPlayingThis) player.seekTo(seekValue.toInt())
                seekByUser = false
            },
            valueRange = 0f..duration.toFloat(),
            modifier = Modifier.fillMaxWidth(),
            haptics = haptics,
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

/**
 * 自绘发光滑杆 — 播放进度条:
 * - 轨道: 已播段黄→蓝品牌渐变, 未播段低调底色
 * - 拇指: 品牌色圆点 + 径向辉光 + 顶部高光点 (宝石感), 按下弹性放大
 * - 触感: 起手 gestureStart, 落位 tick
 */
@Composable
private fun GlowSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    haptics: EchoHaptics,
) {
    var pressed by remember { mutableStateOf(false) }
    val thumbScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 1.4f else 1f,
        animationSpec = EchoMotion.fastSpatial(),
        label = "thumb_press",
    )

    val warm = MaterialTheme.colorScheme.primary
    val cool = MaterialTheme.colorScheme.secondary
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant
    val thumbColor = MaterialTheme.colorScheme.primary

    fun posToValue(x: Float, width: Float): Float {
        val frac = (x / width).coerceIn(0f, 1f)
        return valueRange.start + frac * (valueRange.endInclusive - valueRange.start)
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(valueRange) {
                detectTapGestures(
                    onTap = { offset ->
                        pressed = true
                        haptics.gestureStart()
                        val v = posToValue(offset.x, size.width.toFloat())
                        onValueChange(v)
                        pressed = false
                        haptics.tick()
                        onValueChangeFinished()
                    },
                )
            }
            .pointerInput(valueRange) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        pressed = true
                        haptics.gestureStart()
                        onValueChange(posToValue(offset.x, size.width.toFloat()))
                    },
                    onDragEnd = {
                        pressed = false
                        haptics.tick()
                        onValueChangeFinished()
                    },
                    onDragCancel = {
                        pressed = false
                        onValueChangeFinished()
                    },
                ) { change, _ ->
                    onValueChange(posToValue(change.position.x, size.width.toFloat()))
                    change.consume()
                }
            }
            .drawBehind {
                val cy = size.height / 2f
                val trackH = 6.dp.toPx()
                val trackR = CornerRadius(trackH / 2f, trackH / 2f)
                val fraction = ((value - valueRange.start) /
                    (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)

                // 未播轨道
                drawRoundRect(
                    color = trackColor.copy(alpha = 0.22f),
                    topLeft = Offset(0f, cy - trackH / 2f),
                    size = Size(size.width, trackH),
                    cornerRadius = trackR,
                )
                // 已播: 黄→蓝渐变
                drawRoundRect(
                    brush = Brush.horizontalGradient(listOf(warm, cool)),
                    topLeft = Offset(0f, cy - trackH / 2f),
                    size = Size(size.width * fraction, trackH),
                    cornerRadius = trackR,
                )
                // 拇指: 辉光 + 主体 + 高光点
                val cx = size.width * fraction
                val r = 7.dp.toPx() * thumbScale
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(thumbColor.copy(alpha = 0.35f), Color.Transparent),
                        center = Offset(cx, cy),
                        radius = r * 2.6f,
                    ),
                    radius = r * 2.6f,
                    center = Offset(cx, cy),
                )
                drawCircle(color = thumbColor, radius = r, center = Offset(cx, cy))
                drawCircle(
                    color = Color.White.copy(alpha = 0.45f),
                    radius = r * 0.35f,
                    center = Offset(cx - r * 0.25f, cy - r * 0.3f),
                )
            },
    )
}

@Composable
private fun BatchBottomBar(count: Int, onDelete: () -> Unit, onMoveToLongTerm: () -> Unit, onExit: () -> Unit) {
    // 玻璃浮层批量栏: 半透明+高光描边+噪点+暖色柔影, 悬浮于列表之上
    GlassSurface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.selected_count, count),
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            IconButton(onClick = onMoveToLongTerm, modifier = Modifier.echoPressScale(0.9f)) {
                Icon(Icons.Filled.Archive, contentDescription = stringResource(R.string.batch_move_to_longterm), tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete, modifier = Modifier.echoPressScale(0.9f)) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.batch_delete), tint = MaterialTheme.colorScheme.error)
            }
            IconButton(onClick = onExit, modifier = Modifier.echoPressScale(0.9f)) {
                Icon(Icons.Filled.CheckCircle, contentDescription = stringResource(R.string.exit_selection))
            }
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

/** 日期分组拍平条目 (表头 + 天内录音), 供 LazyColumn 索引 + 日期定位. */
private data class DayEntry(val key: String, val isHeader: Boolean, val day: String, val rec: Recording?)

/** 按天分组, 天序降序 (最新的一天在最上), 天内保持数据源顺序 (最新录音在上). */
private fun buildFlat(items: List<Recording>): List<DayEntry> {
    val groups = items.groupBy { startOfDay(it.createdAt) }
    return groups.keys.sortedDescending().flatMap { dayEpoch ->
        val dayItems = groups.getValue(dayEpoch)
        listOf(DayEntry("h-$dayEpoch", true, dateKey(dayEpoch), null)) +
            dayItems.map { DayEntry(it.id, false, dateKey(dayEpoch), it) }
    }
}

/** 空列表占位: 图标 + 文案. */
@Composable
private fun EmptyList(recordingTab: ListTab, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
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
private fun TempExpiryHint(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
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

