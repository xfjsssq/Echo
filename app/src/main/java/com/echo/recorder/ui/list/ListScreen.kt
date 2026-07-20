package com.echo.recorder.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echo.recorder.domain.model.Recording
import com.echo.recorder.ui.formatElapsed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 录音列表页. 按日期分组, 点击条目进入播放. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    viewModel: ListViewModel,
    onOpenPlayer: (String) -> Unit,
) {
    val recordings by viewModel.recordings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("录音列表") }) },
    ) { padding ->
        if (recordings.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("暂无录音", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                val groups = recordings.groupBy { dateKey(it.createdAt) }
                groups.forEach { (day, items) ->
                    item(key = "h-$day") {
                        Text(
                            text = day,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(items, key = { it.id }) { rec ->

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenPlayer(rec.id) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(rec.displayName, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "${timeOnly(rec.createdAt)}   ${formatElapsed(rec.durationMs)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Divider()
                    }
                }
            }
        }
    }
}

private val dayFmt = SimpleDateFormat("yyyy年MM月dd日 EEEE", Locale.getDefault())
private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

private fun dateKey(epochMs: Long): String = dayFmt.format(Date(epochMs))
private fun timeOnly(epochMs: Long): String = timeFmt.format(Date(epochMs))
