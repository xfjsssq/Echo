package com.echo.recorder.ui.lock

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.selection.SelectionContainer

/**
 * 恢复密钥展示卡 — 修复"密钥塞进 24sp 格式串一行放不下被裁/看不见"的问题.
 *
 * - 密钥每 4 位分组空格分隔: 既可自然换行, 又易抄写核对
 * - 等宽数字 (tnum): 逐位对齐, 不跳宽
 * - SelectionContainer: 长按可复制
 * - 容器化 (圆角 Surface): 与警告文案拉开层次, 一眼锁定
 */
@Composable
fun RecoveryKeyCard(key: String, modifier: Modifier = Modifier) {
    val grouped = key.chunked(4).joinToString(" ")
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
    ) {
        SelectionContainer {
            Text(
                text = grouped,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFeatureSettings = "tnum",
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
            )
        }
    }
}
