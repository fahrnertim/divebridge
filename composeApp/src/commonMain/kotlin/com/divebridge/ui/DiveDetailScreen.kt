package com.divebridge.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.divebridge.dive.StoredDive

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
                title = { Text("Dive Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onGenerateQr) {
                        Icon(Icons.Filled.QrCode, contentDescription = "Generate QR Code")
                    }
                    IconButton(onClick = { onToggleBleHidden(!storedDive.bleHidden) }) {
                        Icon(
                            imageVector = if (storedDive.bleHidden) Icons.Filled.BluetoothDisabled else Icons.Filled.Bluetooth,
                            contentDescription = if (storedDive.bleHidden) "Hidden from BLE" else "Included in BLE",
                            tint = if (storedDive.bleHidden) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                        )
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
                    InfoRow("Bottom time", formatDiveTime(dive.diveTimeMinutes))
                    InfoRow("Water temp", formatTemp(dive.minWaterTempCelsius, dive.maxWaterTempCelsius))
                    InfoRow("Sport", dive.sport.displayName())
                    if (dive.tank != null) {
                        InfoRow("Tank", "${dive.tank.startPressureBar.toInt()} ${Typography.rarrow} ${dive.tank.endPressureBar.toInt()} bar")
                    }
                    if (dive.waterType != com.divebridge.dive.WaterType.UNKNOWN) {
                        InfoRow("Water", dive.waterType.name.lowercase().replaceFirstChar { it.uppercase() })
                    }
                    if (dive.gps != null) {
                        InfoRow("GPS", "%.4f, %.4f".format(dive.gps.latitude, dive.gps.longitude))
                    }
                    if (dive.profile != null) {
                        InfoRow("Profile", "Depth profile available")
                    }
                    InfoRow("Source", storedDive.source)
                }
            }

            // Dive profile chart
            if (dive.profile != null && dive.profile.samples.size > 2) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        DiveProfileChart(
                            profile = dive.profile,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Delete
            OutlinedButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text("Delete Dive")
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete dive?") },
            text = { Text("This will permanently remove this dive from your dive log. This cannot be undone.") },
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