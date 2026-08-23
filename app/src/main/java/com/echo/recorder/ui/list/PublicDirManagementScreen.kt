package com.echo.recorder.ui.list

import android.app.Activity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.echo.recorder.R
import com.echo.recorder.common.PublicDirManager
import com.echo.recorder.settings.PasswordCrypto
import com.echo.recorder.settings.SettingsRepository
import com.echo.recorder.ui.common.LoadingPulse
import com.echo.recorder.ui.common.echoPressScale
import com.echo.recorder.ui.common.rememberEchoHaptics
import com.echo.recorder.ui.common.rememberPublicDirGrant
import com.echo.recorder.ui.common.rememberShakeState
import com.echo.recorder.ui.common.shake
import com.echo.recorder.ui.fmtTime
import com.echo.recorder.ui.lock.MixedPasswordInput
import com.echo.recorder.ui.lock.PasswordInputField
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 公共目录 (备份文件夹) 文件管理界面.
 *
 * 直接扫描备份文件夹, 列出所有 .m4a 文件.
 * 此界面中的文件仅可查看和删除 (删除备份文件夹中的原始文件, 永久删除不可恢复),
 * 不可播放、不可导入 (导入入口在列表页).
 * 删除需双重验证 (若已开密码): 应用密码 + 恢复密钥; 未开密码时仅一次确认.
 * 门槛: 从未设置过密码时不可使用本页 (公共目录属敏感功能, 先设密码).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicDirManagementScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()

    var files by remember { mutableStateOf<List<PublicDirManager.PublicFileInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    suspend fun load() {
        loading = true
        files = PublicDirManager.scanPublic(context)
        loading = false
    }
    LaunchedEffect(Unit) { load() }

    val grant = rememberPublicDirGrant(onGranted = { scope.launch { load() } })

    val passwordEnabled by produceState(initialValue = false) {
        value = settings.passwordEnabled.first()
    }
    val storedHash by produceState<String?>(initialValue = null) {
        value = settings.passwordHash.first()
    }
    val recoveryHash by produceState<String?>(initialValue = null) {
        value = settings.recoveryHash.first()
    }
    // 密码验证输入框按实际类型适配: mixed 用户输不了纯数字框 (此前写死数字键盘,
    // 扩展密码用户无法通过验证 = 删除是空架子).
    val passwordType by produceState(initialValue = "pin") {
        value = settings.passwordType.first() ?: "pin"
    }

    // 公共目录门槛: null=读取中 false=从未设置密码(拦截) true=已设置.
    // 独立读一次 DataStore 而非复用 produceState 初值 —— 后者初值 null 无法区分
    // "加载中"与"无密码", 会把有密码用户误拦一瞬.
    var passwordGate by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { passwordGate = settings.passwordHash.first() != null }

    var pendingDelete by remember { mutableStateOf<PublicDirManager.PublicFileInfo?>(null) }
    // 验证步骤: 0=确认删除 1=输密码 2=输恢复密钥
    var verifyStep by remember { mutableStateOf(0) }
    var input by remember { mutableStateOf("") }
    // 错误文案资源 id (修复此前直接显示裸 key "password_wrong" 的 i18n 缺陷)
    var errorRes by remember { mutableStateOf<Int?>(null) }
    val shake = rememberShakeState()
    val haptics = rememberEchoHaptics()
    LaunchedEffect(errorRes) {
        if (errorRes != null) {
            haptics.reject()
            shake.shake()
        }
    }

    // 删除系统授权返回后重试的目标 (MediaStore 他人条目需用户在系统对话框确认).
    var deleteAfterPermission by remember { mutableStateOf<PublicDirManager.PublicFileInfo?>(null) }
    // 授权成功后的重删不再走系统授权分支 (用户刚确认过; 再需要授权属异常, 按失败处理).
    val deletePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val target = deleteAfterPermission
        deleteAfterPermission = null
        if (result.resultCode == Activity.RESULT_OK && target != null) {
            performDelete(
                context = context,
                scope = scope,
                target = target,
                onCleared = { pendingDelete = null; verifyStep = 0; input = ""; errorRes = null; load() },
                onNeedsPermission = { errorRes = R.string.public_dir_delete_failed },
                onFailed = { errorRes = R.string.public_dir_delete_failed },
            )
        }
    }

    fun deleteNow(target: PublicDirManager.PublicFileInfo) {
        haptics.confirm()
        performDelete(
            context = context,
            scope = scope,
            target = target,
            onCleared = { pendingDelete = null; verifyStep = 0; input = ""; errorRes = null; load() },
            onNeedsPermission = { sender ->
                // 卸载重装等场景: 备份条目归上一次安装所有, 系统要求用户确认授权删除.
                deleteAfterPermission = target
                deletePermissionLauncher.launch(IntentSenderRequest.Builder(sender).build())
            },
            onFailed = {
                // 明确告知失败, 不再静默 (此前返回值被忽略, 删不掉也无任何反馈).
                errorRes = R.string.public_dir_delete_failed
            },
        )
    }

    // 读取密码状态中: 只显示加载态, 不渲染任何文件内容 (门槛判定完成前不泄数据).
    if (passwordGate == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingPulse()
        }
        return
    }

    // 无密码防线: 从未设置过密码时不进入文件管理 (设置页/列表页入口已引导, 此处兜底).
    if (passwordGate == false) {
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text(stringResource(R.string.public_dir_password_required_title)) },
            text = { Text(stringResource(R.string.public_dir_password_required_text)) },
            confirmButton = {
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            },
        )
        return
    }

    // 删除确认 / 双重验证对话框.
    pendingDelete?.let { target ->
        when (verifyStep) {
            0 -> AlertDialog(
                onDismissRequest = { pendingDelete = null },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                title = { Text(stringResource(R.string.delete)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(target.displayName)
                        Text(
                            stringResource(R.string.public_dir_delete_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (passwordEnabled) {
                            haptics.tick()
                            verifyStep = 1; input = ""; errorRes = null
                        } else {
                            deleteNow(target)
                        }
                    }) { Text(stringResource(R.string.confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) }
                },
            )
            1 -> AlertDialog(
                onDismissRequest = { pendingDelete = null; verifyStep = 0 },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                title = { Text(stringResource(R.string.double_verify_needed)) },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.shake(shake),
                    ) {
                        // 按用户实际的密码类型展示对应输入框: mixed 用户无法在纯数字框输入.
                        if (passwordType == "mixed") {
                            MixedPasswordInput(
                                value = input,
                                onValueChange = { input = it; errorRes = null },
                                label = stringResource(R.string.input_password),
                            )
                        } else {
                            PasswordInputField(
                                value = input,
                                onValueChange = { input = it.filter { c -> c.isDigit() }.take(6); errorRes = null },
                                label = stringResource(R.string.input_password),
                                keyboardType = KeyboardType.Number,
                            )
                        }
                        errorRes?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val hash = storedHash
                        if (hash != null && PasswordCrypto.verify(input, hash)) {
                            haptics.tick()
                            verifyStep = 2; input = ""; errorRes = null
                        } else {
                            errorRes = R.string.password_wrong
                        }
                    }) { Text(stringResource(R.string.confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null; verifyStep = 0 }) { Text(stringResource(R.string.cancel)) }
                },
            )
            2 -> AlertDialog(
                onDismissRequest = { pendingDelete = null; verifyStep = 0 },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                title = { Text(stringResource(R.string.double_verify_step2)) },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.shake(shake),
                    ) {
                        PasswordInputField(
                            value = input,
                            onValueChange = { input = it.filter { c -> c.isDigit() }.take(6); errorRes = null },
                            label = stringResource(R.string.recovery_key_title),
                            keyboardType = KeyboardType.Number,
                        )
                        errorRes?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val hash = recoveryHash
                        if (hash != null && PasswordCrypto.verify(input, hash)) {
                            deleteNow(target)
                        } else {
                            errorRes = R.string.recovery_key_wrong
                        }
                    }) { Text(stringResource(R.string.confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null; verifyStep = 0 }) { Text(stringResource(R.string.cancel)) }
                },
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.public_dir_management_title)) },
                navigationIcon = {
                    IconButton(
                        onBack,
                        modifier = Modifier.echoPressScale(0.9f),
                    ) { Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!grant.granted) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.public_dir_choose_folder_note),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = grant.request,
                            modifier = Modifier.padding(top = 20.dp).echoPressScale(0.97f),
                        ) { Text(stringResource(R.string.public_dir_choose_folder)) }
                    }
                }
            } else {
                Text(
                    stringResource(R.string.public_dir_management_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
                // 加载 ⇄ 空态 ⇄ 列表: 交叉淡化而非瞬切
                AnimatedContent(
                    targetState = loading to files.isEmpty(),
                    transitionSpec = {
                        (fadeIn(tween(220)) + scaleIn(
                            initialScale = 0.98f,
                            animationSpec = tween(220),
                        )) togetherWith fadeOut(tween(150))
                    },
                    label = "public_dir_body",
                ) { (isLoading, empty) ->
                    when {
                        isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            LoadingPulse(dotSize = 9.dp)
                        }
                        empty -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.no_recordings))
                        }
                        else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(files, key = { it.fileName }) { info ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                        .echoPressScale(0.99f),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(info.displayName, style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            text = fmtTime(info.lastModified),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    IconButton(
                                        onClick = { pendingDelete = info; verifyStep = 0 },
                                        modifier = Modifier.echoPressScale(0.9f),
                                    ) {
                                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                                    }
                                }
                                Divider()
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 执行删除并分发结果.
 * 顶级函数以打破 "launcher 回调 ↔ deleteNow 局部函数" 的前向引用环
 * (Kotlin 局部函数不能先被引用后定义).
 */
private fun performDelete(
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    target: PublicDirManager.PublicFileInfo,
    onCleared: suspend () -> Unit,
    onNeedsPermission: (android.content.IntentSender) -> Unit,
    onFailed: () -> Unit,
) {
    scope.launch {
        when (val r = PublicDirManager.deletePublic(context, target.fileName)) {
            PublicDirManager.DeleteResult.Success, PublicDirManager.DeleteResult.NotFound -> onCleared()
            is PublicDirManager.DeleteResult.NeedsPermission -> onNeedsPermission(r.intentSender)
            PublicDirManager.DeleteResult.Failed -> onFailed()
        }
    }
}
