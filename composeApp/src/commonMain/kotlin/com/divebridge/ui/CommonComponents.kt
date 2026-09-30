package com.divebridge.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.divebridge.dive.DiveSport
import kotlin.math.roundToInt

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

fun formatDiveTime(minutes: Double): String {
    val totalSeconds = (minutes * 60).roundToInt()
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun DiveSport.displayName(): String = when (this) {
    DiveSport.SCUBA -> "Scuba"
    DiveSport.FREEDIVING -> "Freediving"
    DiveSport.EXTENDED_RANGE -> "Extended Range"
    DiveSport.REBREATHER_SCR -> "Rebreather (SCR)"
    DiveSport.REBREATHER_CCR -> "Rebreather (CCR)"
    DiveSport.UNKNOWN -> "Unknown"
}

fun formatTemp(min: Double, max: Double): String {
    return "${min.roundToInt()}${Typography.ndash}${max.roundToInt()} ${Typography.degree}C"
}

object Typography {
    const val degree = "\u00B0"
    const val ndash = "\u2013"
    const val rarrow = "\u2192"
}