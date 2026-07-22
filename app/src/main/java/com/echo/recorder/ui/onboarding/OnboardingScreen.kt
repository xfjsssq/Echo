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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.echo.recorder.R

private data class OnboardingCard(val titleRes: Int, val textRes: Int)

private val CARDS = listOf(
    OnboardingCard(R.string.onboarding1_title, R.string.onboarding1_text),
    OnboardingCard(R.string.onboarding2_title, R.string.onboarding2_text),
    OnboardingCard(R.string.onboarding3_title, R.string.onboarding3_text),
    OnboardingCard(R.string.onboarding4_title, R.string.onboarding4_text),
)

/** 首次启动引导卡片 (3~5 张, 可跳过/上一步/下一步). */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    val card = CARDS[index]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onFinish) { Text(stringResource(R.string.skip)) }
        }

        // 图片占位区 (后期替换为截图).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            stringResource(card.titleRes),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 32.dp),
        )
        Text(
            stringResource(card.textRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (index > 0) {
                TextButton(onClick = { index-- }) { Text(stringResource(R.string.previous)) }
            }
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                if (index < CARDS.lastIndex) {
                    Button(onClick = { index++ }) { Text(stringResource(R.string.next)) }
                } else {
                    Button(onClick = onFinish) { Text(stringResource(R.string.onboarding_finish)) }
                }
            }
        }
    }
}
