package com.echo.recorder.ui.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** 列表页占位, 后续 ticket 填充. */
@Composable
fun ListPlaceholderScreen(
    onOpenPlayer: (String) -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("列表页占位")
    }
}
