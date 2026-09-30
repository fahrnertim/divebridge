package com.divebridge.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.divebridge.mares.MaresBleService.BleStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BleEmulationScreen(
    isRunning: Boolean,
    status: BleStatus,
    progress: Float,
    progressText: String,
    logs: List<String>,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = {
        if (isRunning) onStop()
        onBack()
    })

    var showLog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty() && showLog) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BLE Dive Computer") },
                navigationIcon = {
                    TextButton(onClick = {
                        if (isRunning) onStop()
                        onBack()
                    }) { Text("Back") }
                },
                actions = {
                    TextButton(onClick = { showLog = !showLog }) {
                        Text(if (showLog) "Hide log" else "Show log")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            // Status card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = statusTitle(status),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = statusSubtitle(status),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        if (isRunning) {
                            Button(onClick = onStop) { Text("Stop") }
                        } else {
                            Button(onClick = onStart) { Text("Start") }
                        }
                    }

                    // Progress bar for transfers
                    if (status == BleStatus.TRANSFERRING_HEADER ||
                        status == BleStatus.TRANSFERRING_PROFILE
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = progressText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Indeterminate progress for waiting states
                    if (status == BleStatus.ADVERTISING || status == BleStatus.CLIENT_CONNECTED) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    // Ready state
                    if (status == BleStatus.DEVICE_READY) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Select a dive to import in the SSI app.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Success indicator
                    if (status == BleStatus.COMPLETE) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Dive profile transferred successfully!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // Instructions
            if (status == BleStatus.ADVERTISING) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("How to import", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("1. Open MySSI on another device", style = MaterialTheme.typography.bodySmall)
                        Text("2. Go to Logbook and tap Import", style = MaterialTheme.typography.bodySmall)
                        Text("3. Select Mares Puck 4", style = MaterialTheme.typography.bodySmall)
                        Text("4. Wait for the transfer to complete", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Log output (collapsible)
            if (showLog) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Debug Log", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                ) {
                    if (logs.isEmpty()) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Text(
                                "No activity yet",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            items(logs) { log ->
                                Text(
                                    text = log,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun statusTitle(status: BleStatus): String = when (status) {
    BleStatus.IDLE -> "Stopped"
    BleStatus.ADVERTISING -> "Waiting for connection..."
    BleStatus.CLIENT_CONNECTED -> "Initializing..."
    BleStatus.DEVICE_READY -> "Connected"
    BleStatus.TRANSFERRING_HEADER -> "Sending dive info..."
    BleStatus.TRANSFERRING_PROFILE -> "Sending dive profile..."
    BleStatus.COMPLETE -> "Transfer complete"
    BleStatus.ERROR -> "Error"
}

private fun statusSubtitle(status: BleStatus): String = when (status) {
    BleStatus.IDLE -> "Tap Start to begin emulating a Mares dive computer"
    BleStatus.ADVERTISING -> "Broadcasting via Bluetooth"
    BleStatus.CLIENT_CONNECTED -> "SSI app connected, setting up..."
    BleStatus.DEVICE_READY -> "Waiting for SSI to start the dive import"
    BleStatus.TRANSFERRING_HEADER -> "Transferring dive header data"
    BleStatus.TRANSFERRING_PROFILE -> "Transferring depth profile samples"
    BleStatus.COMPLETE -> "The dive should now appear in your SSI logbook"
    BleStatus.ERROR -> "Something went wrong"
}