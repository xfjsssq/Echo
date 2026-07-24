package com.echo.recorder.ui.lock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

/**
 * 3×3 图案锁视图.
 *
 * 点序编号:
 * 0 1 2
 * 3 4 5
 * 6 7 8
 *
 * 交互: 手指按下并拖拽经过点时实时选中并连线, 抬起时通过 [onPatternComplete] 回调完整点序.
 * 最少连接 4 个点才视为有效.
 *
 * @param resetKey 递增此值可重置图案显示 (由父组件控制).
 * @param PatternLockView 使用 [resetKey] 重置时, 下一次手势会重新开始.
 */
@Composable
fun PatternLockView(
    modifier: Modifier = Modifier,
    size: Dp = 280.dp,
    resetKey: Int = 0,
    onPatternComplete: (List<Int>) -> Unit,
) {
    val dotColor = Color(0xFF3F51B5)
    val lineColor = Color(0xFF3F51B5)
    var pattern by remember(resetKey) { mutableStateOf(listOf<Int>()) }
    var current by remember(resetKey) { mutableStateOf<Offset?>(null) }
    val density = LocalDensity.current

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .size(size)
                .pointerInput(resetKey) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            hitTest(offset, size, density)?.let {
                                pattern = listOf(it)
                                current = centerOf(it, size, density)
                            }
                        },
                        onDragEnd = {
                            current = null
                            if (pattern.size >= 4) onPatternComplete(pattern)
                            pattern = emptyList()
                        },
                    ) { change, _ ->
                        val p = pattern
                        hitTest(change.position, size, density)?.let { idx ->
                            if (idx !in p) {
                                pattern = p + idx
                                current = centerOf(idx, size, density)
                            }
                        } ?: run {
                            current = change.position
                        }
                    }
                },
        ) {
            val step = size.toPx() / 3f
            val r = step * 0.12f
            // 点.
            for (i in 0 until 9) {
                val c = centerOfPx(i, step)
                drawCircle(
                    color = dotColor,
                    radius = if (i in pattern) r * 1.4f else r,
                    center = c,
                )
            }
            // 已选点内圆.
            pattern.forEach { i ->
                val c = centerOfPx(i, step)
                drawCircle(color = dotColor.copy(alpha = 0.25f), radius = r * 2.2f, center = c)
            }
            // 连线.
            if (pattern.size >= 2) {
                for (k in 0 until pattern.size - 1) {
                    val a = centerOfPx(pattern[k], step)
                    val b = centerOfPx(pattern[k + 1], step)
                    drawLine(
                        color = lineColor,
                        start = a,
                        end = b,
                        strokeWidth = r * 0.8f,
                        cap = StrokeCap.Round,
                    )
                }
            }
            // 手指到当前点的动态线.
            val last = current
            if (last != null && pattern.isNotEmpty()) {
                drawLine(
                    color = lineColor.copy(alpha = 0.5f),
                    start = centerOfPx(pattern.last(), step),
                    end = last,
                    strokeWidth = r * 0.6f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

private fun indexToRowCol(i: Int) = (i / 3) to (i % 3)

private fun centerOfPx(i: Int, step: Float): Offset {
    val (row, col) = indexToRowCol(i)
    return Offset(step * (col + 0.5f), step * (row + 0.5f))
}

private fun centerOf(i: Int, size: Dp, density: androidx.compose.ui.unit.Density): Offset {
    val step = with(density) { size.toPx() / 3f }
    return centerOfPx(i, step)
}

/** 命中测试: 返回命中的点序号, 未命中返回 null. */
private fun hitTest(position: Offset, size: Dp, density: androidx.compose.ui.unit.Density): Int? {
    val step = with(density) { size.toPx() / 3f }
    val r = step * 0.35f
    for (i in 0 until 9) {
        val c = centerOfPx(i, step)
        if (hypot((position.x - c.x).toDouble(), (position.y - c.y).toDouble()) < r) return i
    }
    return null
}
