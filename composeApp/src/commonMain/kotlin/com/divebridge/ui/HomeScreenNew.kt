package com.divebridge.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.divebridge.dive.StoredDive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenNew(
    dives: List<StoredDive>,
    bleCutoffDate: String?,
    onOpenFile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBle: (() -> Unit)?,
    onOpenCenters: () -> Unit,
    onDiveTap: (StoredDive) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DiveBridge") },
                actions = {
                    IconButton(onClick = onOpenCenters) {
                        Icon(Icons.Filled.Verified, contentDescription = "Dive Centers")
                    }
                    if (onOpenBle != null) {
                        IconButton(onClick = onOpenBle) {
                            Icon(Icons.Filled.Bluetooth, contentDescription = "BLE Emulator")
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onOpenFile,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Import") },
            )
        },
    ) { padding ->
        if (dives.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "No dives yet",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Share a FIT file from the Garmin Dive app\nor tap Import to open one manually.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(dives) { stored ->
                    val hiddenByCutoff = bleCutoffDate != null &&
                            stored.dive.dateTime.toString() < bleCutoffDate
                    val bleHidden = stored.bleHidden || hiddenByCutoff
                    DiveCard(stored = stored, bleHidden = bleHidden, onClick = { onDiveTap(stored) })
                }
            }
        }
    }
}

@Composable
private fun DiveCard(stored: StoredDive, bleHidden: Boolean, onClick: () -> Unit) {
    val dive = stored.dive
    val dt = dive.dateTime

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = if (bleHidden) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) else CardDefaults.cardColors(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "%04d-%02d-%02d  %02d:%02d".format(
                        dt.year, dt.monthNumber, dt.dayOfMonth, dt.hour, dt.minute
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (bleHidden) {
                    Icon(
                        imageVector = Icons.Filled.BluetoothDisabled,
                        contentDescription = "Hidden from BLE",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "%.1f m".format(dive.maxDepthMeters),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = formatDiveTime(dive.diveTimeMinutes),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = formatTemp(dive.minWaterTempCelsius, dive.maxWaterTempCelsius),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}