package ua.vytraty.app.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class PieSlice(val label: String, val value: Long, val color: Color)
data class BarGroup(val label: String, val values: List<Long>)
data class LinePoint(val label: String, val value: Long)

/** Donut chart with a centered label. */
@Composable
fun DonutChart(slices: List<PieSlice>, centerTop: String, centerBottom: String, modifier: Modifier = Modifier, stroke: Float = 28f) {
    val total = slices.sumOf { it.value }.coerceAtLeast(1)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().height(220.dp).padding(16.dp)) {
            val d = minOf(size.width, size.height)
            val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
            val sz = Size(d, d)
            drawArc(trackColor, 0f, 360f, false, topLeft, sz, style = Stroke(stroke))
            var start = -90f
            slices.forEach { s ->
                val sweep = 360f * s.value / total
                if (sweep > 0f) {
                    drawArc(s.color, start, sweep - 1.5f, false, topLeft, sz, style = Stroke(stroke, cap = StrokeCap.Butt))
                }
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerTop, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(centerBottom, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

/** Grouped bars (e.g. expense vs income per month) with axis labels drawn on the canvas. */
@Composable
fun BarChart(groups: List<BarGroup>, colors: List<Color>, modifier: Modifier = Modifier, height: Int = 200) {
    val max = groups.flatMap { it.values }.maxOrNull()?.coerceAtLeast(1) ?: 1
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val density = LocalDensity.current
    val textPx = with(density) { 11.sp.toPx() }
    Canvas(modifier.fillMaxWidth().height(height.dp).padding(horizontal = 8.dp, vertical = 8.dp)) {
        val labelH = textPx * 1.6f
        val chartH = size.height - labelH
        val n = groups.size.coerceAtLeast(1)
        val groupW = size.width / n
        val barCount = colors.size.coerceAtLeast(1)
        val barW = (groupW * 0.7f) / barCount
        val paint = android.graphics.Paint().apply {
            color = labelColor.toArgbInt()
            textSize = textPx
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        // grid
        for (i in 0..3) {
            val y = chartH * i / 3
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        groups.forEachIndexed { gi, g ->
            val gx = gi * groupW + groupW * 0.15f
            g.values.forEachIndexed { vi, v ->
                val h = chartH * v / max
                val x = gx + vi * barW
                drawRoundRect(
                    colors[vi % colors.size],
                    topLeft = Offset(x, chartH - h),
                    size = Size(barW * 0.9f, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
                )
            }
            drawContext.canvas.nativeCanvas.drawText(g.label, gi * groupW + groupW / 2, size.height - 2f, paint)
        }
    }
}

/** Simple line chart with filled area; used for balance over time. */
@Composable
fun LineChart(points: List<LinePoint>, color: Color, modifier: Modifier = Modifier, height: Int = 180) {
    if (points.isEmpty()) return
    val min = points.minOf { it.value }
    val max = points.maxOf { it.value }
    val span = (max - min).coerceAtLeast(1)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val density = LocalDensity.current
    val textPx = with(density) { 11.sp.toPx() }
    Canvas(modifier.fillMaxWidth().height(height.dp).padding(horizontal = 8.dp, vertical = 8.dp)) {
        val labelH = textPx * 1.6f
        val chartH = size.height - labelH
        val stepX = if (points.size > 1) size.width / (points.size - 1) else size.width
        for (i in 0..3) {
            val y = chartH * i / 3
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        val path = Path()
        val area = Path()
        points.forEachIndexed { i, p ->
            val x = i * stepX
            val y = chartH - chartH * (p.value - min) / span
            if (i == 0) { path.moveTo(x, y); area.moveTo(x, chartH); area.lineTo(x, y) } else { path.lineTo(x, y); area.lineTo(x, y) }
        }
        area.lineTo((points.size - 1) * stepX, chartH); area.close()
        drawPath(area, color.copy(alpha = 0.15f))
        drawPath(path, color, style = Stroke(width = 4f, cap = StrokeCap.Round))
        val paint = android.graphics.Paint().apply {
            this.color = labelColor.toArgbInt(); textSize = textPx; textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true
        }
        val labelEvery = (points.size / 6).coerceAtLeast(1)
        points.forEachIndexed { i, p ->
            if (i % labelEvery == 0 || i == points.lastIndex) {
                drawContext.canvas.nativeCanvas.drawText(p.label, i * stepX, size.height - 2f, paint)
            }
        }
    }
}

private fun Color.toArgbInt(): Int = this.toArgb()
