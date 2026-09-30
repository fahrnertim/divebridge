package com.divebridge.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.divebridge.dive.DiveSport
import com.divebridge.dive.StoredDive
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiveDetailScreen(
    storedDive: StoredDive,
    onGenerateQr: () -> Unit,
    onToggleBleHidden: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val dive = storedDive.dive

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val dt = dive.dateTime
                    Text("%04d-%02d-%02d %02d:%02d".format(
                        dt.year, dt.monthNumber, dt.dayOfMonth, dt.hour, dt.minute
                    ))
                },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                },
                actions = {
                    TextButton(onClick = { showDeleteConfirm = true }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // Dive info
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val dt = dive.dateTime
                    InfoRow("Date", "%04d-%02d-%02d %02d:%02d".format(
                        dt.year, dt.monthNumber, dt.dayOfMonth, dt.hour, dt.minute
                    ))
                    InfoRow("Max depth", "%.1f m".format(dive.maxDepthMeters))
                    InfoRow("Bottom time", formatTime(dive.diveTimeMinutes))
                    InfoRow("Water temp", "${dive.minWaterTempCelsius.roundToInt()} - ${dive.maxWaterTempCelsius.roundToInt()} C")
                    InfoRow("Sport", dive.sport.displayName())
                    if (dive.tank != null) {
                        InfoRow("Tank", "${dive.tank.startPressureBar.roundToInt()} -> ${dive.tank.endPressureBar.roundToInt()} bar")
                    }
                    if (dive.profile != null) {
                        InfoRow("Profile", "${dive.profile.samples.size} samples")
                    }
                    InfoRow("Source", storedDive.source)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Actions
            Button(
                onClick = onGenerateQr,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Generate QR Code")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // BLE visibility toggle
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Hide from BLE emulator", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = if (storedDive.bleHidden) "This dive won't be sent via Bluetooth"
                                   else "This dive will be sent via Bluetooth",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = storedDive.bleHidden,
                        onCheckedChange = onToggleBleHidden,
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete dive?") },
            text = { Text("This will remove the dive from the store. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun DiveSport.displayName(): String = when (this) {
    DiveSport.SCUBA -> "Scuba"
    DiveSport.FREEDIVING -> "Freediving"
    DiveSport.EXTENDED_RANGE -> "Extended Range"
    DiveSport.REBREATHER_SCR -> "Rebreather (SCR)"
    DiveSport.REBREATHER_CCR -> "Rebreather (CCR)"
    DiveSport.UNKNOWN -> "Unknown"
}

private fun formatTime(minutes: Double): String {
    val totalSeconds = (minutes * 60).roundToInt()
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}