package com.echo.recorder.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.echo.recorder.R
import com.echo.recorder.settings.ThemeMode

/** 引导卡片类型. */
private enum class OnboardingCardType {
    /** 普通卡片: 标题 + 文本 + 下一步/完成按钮. */
    PLAIN,
    /** 公共目录备份卡片: 额外一个"立即开启"按钮. */
    PUBLIC_DIR,
    /** 主题选择卡片: 三个单选选项. */
    THEME,
}

private data class OnboardingCard(
    val type: OnboardingCardType,
    val titleRes: Int,
    val textRes: Int,
    val actionRes: Int? = null,
)

private val CARDS = listOf(
    OnboardingCard(OnboardingCardType.PLAIN, R.string.onboarding1_title, R.string.onboarding1_text),
    OnboardingCard(OnboardingCardType.PLAIN, R.string.onboarding2_title, R.string.onboarding2_text),
    OnboardingCard(OnboardingCardType.PLAIN, R.string.onboarding3_title, R.string.onboarding3_text),
    OnboardingCard(OnboardingCardType.PUBLIC_DIR, R.string.onboarding4_title, R.string.onboarding4_text, R.string.onboarding4_action),
    OnboardingCard(OnboardingCardType.PLAIN, R.string.onboarding5_title, R.string.onboarding5_text),
    OnboardingCard(OnboardingCardType.THEME, R.string.onboarding6_title, R.string.onboarding6_text),
)

/**
 * 首次启动引导卡片 (居中卡片样式, 半透明遮罩).
 *
 * @param onFinish 引导完成回调
 * @param onEnablePublicDir 用户点击"立即开启"公共目录备份
 * @param onSelectTheme 用户在引导中选择主题
 */
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    onEnablePublicDir: () -> Unit = {},
    onSelectTheme: (ThemeMode) -> Unit = {},
) {
    var index by remember { mutableIntStateOf(0) }
    val card = CARDS[index]

    Dialog(
        onDismissRequest = { /* 引导卡片通过右上角 × 或完成按钮关闭 */ },
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
                // 顶部: 右上角 × 跳过.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(
                        onClick = onFinish,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.skip),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // 图片占位区 (后期替换为截图).
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${index + 1} / ${CARDS.size}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // 内容区.
                when (card.type) {
                    OnboardingCardType.THEME -> ThemeCardContent(
                        titleRes = card.titleRes,
                        textRes = card.textRes,
                        onSelect = onSelectTheme,
                    )
                    else -> PlainCardContent(
                        card = card,
                        onEnablePublicDir = onEnablePublicDir,
                    )
                }

                // 底部导航: 上一步 / 下一步 / 完成.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (index > 0) {
                        TextButton(onClick = { index-- }) {
                            Text(stringResource(R.string.previous))
                        }
                    }
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                        if (index < CARDS.lastIndex) {
                            Button(onClick = { index++ }) {
                                Text(stringResource(R.string.onboarding_next))
                            }
                        } else {
                            Button(onClick = onFinish) {
                                Text(stringResource(R.string.onboarding_finish))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlainCardContent(
    card: OnboardingCard,
    onEnablePublicDir: () -> Unit,
) {
    Text(
        stringResource(card.titleRes),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 24.dp),
    )
    Text(
        stringResource(card.textRes),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 12.dp),
    )
    if (card.type == OnboardingCardType.PUBLIC_DIR && card.actionRes != null) {
        Button(
            onClick = onEnablePublicDir,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
        ) { Text(stringResource(card.actionRes)) }
    }
}

@Composable
private fun ThemeCardContent(
    titleRes: Int,
    textRes: Int,
    onSelect: (ThemeMode) -> Unit,
) {
    var selected by remember { mutableStateOf(ThemeMode.SYSTEM) }
    Text(
        stringResource(titleRes),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 24.dp),
    )
    Text(
        stringResource(textRes),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 12.dp),
    )
    Column(modifier = Modifier.padding(top = 16.dp)) {
        ThemeMode.values().forEach { mode ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selected == mode,
                        onClick = {
                            selected = mode
                            onSelect(mode)
                        },
                        role = Role.RadioButton,
                    )
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == mode, onClick = null)
                Text(
                    text = stringResource(
                        when (mode) {
                            ThemeMode.LIGHT -> R.string.theme_light
                            ThemeMode.DARK -> R.string.theme_dark
                            ThemeMode.SYSTEM -> R.string.theme_system
                        }
                    ),
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}
