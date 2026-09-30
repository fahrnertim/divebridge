package com.divebridge.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.divebridge.dive.StoredDive
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenNew(
    dives: List<StoredDive>,
    onOpenFile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBle: (() -> Unit)?,
    onDiveTap: (StoredDive) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DiveBridge") },
                actions = {
                    if (onOpenBle != null) {
                        TextButton(onClick = onOpenBle) { Text("BLE") }
                    }
                    TextButton(onClick = onOpenSettings) { Text("Settings") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onOpenFile) {
                Text("+", style = MaterialTheme.typography.headlineSmall)
            }
        },
    ) { padding ->
        if (dives.isEmpty()) {
            // Empty state
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "No dives yet",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Import a FIT file from your dive computer\nor share one from the Garmin Dive app",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onOpenFile) {
                    Text("Import FIT file")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(dives) { stored ->
                    DiveCard(stored = stored, onClick = { onDiveTap(stored) })
                }
            }
        }
    }
}

@Composable
private fun DiveCard(stored: StoredDive, onClick: () -> Unit) {
    val dive = stored.dive
    val dt = dive.dateTime
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "%04d-%02d-%02d  %02d:%02d".format(
                        dt.year, dt.monthNumber, dt.dayOfMonth, dt.hour, dt.minute
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stored.source,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "%.1f m".format(dive.maxDepthMeters),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = formatTime(dive.diveTimeMinutes),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "${dive.minWaterTempCelsius.roundToInt()}-${dive.maxWaterTempCelsius.roundToInt()} C",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatTime(minutes: Double): String {
    val totalSeconds = (minutes * 60).roundToInt()
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}