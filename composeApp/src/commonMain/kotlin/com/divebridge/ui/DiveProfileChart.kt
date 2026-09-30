package com.divebridge.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.divebridge.dive.DiveProfile
import kotlin.math.*

@OptIn(ExperimentalTextApi::class)
@Composable
fun DiveProfileChart(
    profile: DiveProfile,
    modifier: Modifier = Modifier,
) {
    val samples = profile.samples
    if (samples.isEmpty()) return

    val depthColor = MaterialTheme.colorScheme.primary
    val tempColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()

    Column(modifier = modifier) {
        // Legend
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LegendItem(color = depthColor, label = "Depth")
            LegendItem(color = tempColor, label = "Temperature")
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
        ) {
            val leftPad = 40.dp.toPx()
            val rightPad = 45.dp.toPx()
            val topPad = 8.dp.toPx()
            val bottomPad = 24.dp.toPx()

            val chartWidth = size.width - leftPad - rightPad
            val chartHeight = size.height - topPad - bottomPad

            val maxTime = samples.last().timeSeconds.toFloat()
            val maxDepth = samples.maxOf { it.depthMeters }.toFloat().let { ceil(it).coerceAtLeast(1f) }
            val minTemp = samples.minOf { it.temperatureCelsius }.toFloat()
            val maxTemp = samples.maxOf { it.temperatureCelsius }.toFloat()
            val tempRange = (maxTemp - minTemp).coerceAtLeast(1f)

            fun timeToX(t: Float) = leftPad + (t / maxTime) * chartWidth
            fun depthToY(d: Float) = topPad + (d / maxDepth) * chartHeight
            fun tempToY(t: Float) = topPad + chartHeight - ((t - minTemp) / tempRange) * chartHeight

            // Grid lines (depth)
            val depthStep = niceStep(maxDepth, 4)
            var d = 0f
            while (d <= maxDepth) {
                val y = depthToY(d)
                drawLine(gridColor, Offset(leftPad, y), Offset(leftPad + chartWidth, y), strokeWidth = 1f)
                drawText(
                    textMeasurer = textMeasurer,
                    text = "${d.roundToInt()}m",
                    topLeft = Offset(2.dp.toPx(), y - 6.sp.toPx()),
                    style = TextStyle(color = labelColor, fontSize = 10.sp),
                )
                d += depthStep
            }

            // Time labels
            val timeStep = niceStep(maxTime / 60f, 4) * 60f
            var t = 0f
            while (t <= maxTime) {
                val x = timeToX(t)
                drawLine(gridColor, Offset(x, topPad), Offset(x, topPad + chartHeight), strokeWidth = 1f)
                val minutes = (t / 60).roundToInt()
                drawText(
                    textMeasurer = textMeasurer,
                    text = "${minutes}m",
                    topLeft = Offset(x - 8.sp.toPx(), topPad + chartHeight + 4.dp.toPx()),
                    style = TextStyle(color = labelColor, fontSize = 10.sp),
                )
                t += timeStep
            }

            // Depth line
            val depthPath = Path()
            samples.forEachIndexed { i, s ->
                val x = timeToX(s.timeSeconds.toFloat())
                val y = depthToY(s.depthMeters.toFloat())
                if (i == 0) depthPath.moveTo(x, y) else depthPath.lineTo(x, y)
            }
            drawPath(depthPath, depthColor, style = Stroke(width = 2.dp.toPx()))

            // Temperature line
            val tempPath = Path()
            samples.forEachIndexed { i, s ->
                val x = timeToX(s.timeSeconds.toFloat())
                val y = tempToY(s.temperatureCelsius.toFloat())
                if (i == 0) tempPath.moveTo(x, y) else tempPath.lineTo(x, y)
            }
            drawPath(tempPath, tempColor, style = Stroke(width = 1.5.dp.toPx()))

            // Temp labels on right axis
            val tStep = niceStep(tempRange, 3)
            var tv = minTemp
            while (tv <= maxTemp + 0.01f) {
                val y = tempToY(tv)
                drawText(
                    textMeasurer = textMeasurer,
                    text = "${tv.roundToInt()}${Typography.degree}",
                    topLeft = Offset(leftPad + chartWidth + 4.dp.toPx(), y - 6.sp.toPx()),
                    style = TextStyle(color = tempColor, fontSize = 10.sp),
                )
                tv += tStep
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(modifier = Modifier.size(12.dp).padding(top = 4.dp)) {
            drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 2.dp.toPx())
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun niceStep(range: Float, targetSteps: Int): Float {
    if (range <= 0f) return 1f
    val raw = range / targetSteps
    val log10 = ln(raw.toDouble()) / ln(10.0)
    val magnitude = 10.0.pow(floor(log10)).toFloat()
    val normalized = raw / magnitude
    val nice = when {
        normalized <= 1.5f -> 1f
        normalized <= 3.5f -> 2f
        normalized <= 7.5f -> 5f
        else -> 10f
    }
    return (nice * magnitude).coerceAtLeast(1f)
}