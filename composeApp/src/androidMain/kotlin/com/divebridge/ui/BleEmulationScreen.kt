package com.divebridge.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.divebridge.mares.MaresBleService.BleStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BleEmulationScreen(
    isRunning: Boolean,
    status: BleStatus,
    diveCount: Int,
    progress: Float,
    progressText: String,
    logs: List<String>,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
) {
    var showStopConfirm by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val isTransferring = status == BleStatus.TRANSFERRING_HEADER || status == BleStatus.TRANSFERRING_PROFILE

    val confirmAndLeave = {
        if (isTransferring) {
            showStopConfirm = true
        } else {
            if (isRunning) onStop()
            onBack()
        }
    }

    BackHandler(onBack = confirmAndLeave)

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty() && showLog) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BLE Emulator") },
                navigationIcon = {
                    IconButton(onClick = confirmAndLeave) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { showLog = !showLog }) {
                        Text(if (showLog) "Hide log" else "Log")
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
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Central icon with halo
            BleStatusIcon(
                status = status,
                isRunning = isRunning,
                progress = progress,
                onClick = { if (isRunning) onStop() else onStart() },
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Status text
            Text(
                text = statusTitle(status),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = statusSubtitle(status, diveCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Transfer progress text
            if (isTransferring && progressText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = progressText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Instructions (compact)
            if (status == BleStatus.ADVERTISING || status == BleStatus.IDLE) {
                Text(
                    text = if (status == BleStatus.IDLE) "Tap the icon to start broadcasting"
                           else "Open MySSI on another device, go to Import, and select the Mares dive computer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Debug log (collapsible)
            if (showLog) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(2f),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                ) {
                    if (logs.isEmpty()) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text("No activity yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            items(logs) { log ->
                                Text(text = log, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showStopConfirm) {
        AlertDialog(
            onDismissRequest = { showStopConfirm = false },
            title = { Text("Stop transfer?") },
            text = { Text("A dive transfer is in progress. Stopping now may leave the import incomplete on the SSI side.") },
            confirmButton = {
                TextButton(onClick = {
                    showStopConfirm = false
                    onStop()
                    onBack()
                }) { Text("Stop", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirm = false }) { Text("Continue") }
            },
        )
    }
}

@Composable
private fun BleStatusIcon(
    status: BleStatus,
    isRunning: Boolean,
    progress: Float,
    onClick: () -> Unit,
) {
    val statusColor = statusColor(status)
    val isTransferring = status == BleStatus.TRANSFERRING_HEADER || status == BleStatus.TRANSFERRING_PROFILE

    // Pulse animation for advertising
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseAnim.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )

    // Spin animation for connecting
    val spinAnim = rememberInfiniteTransition(label = "spin")
    val spinAngle by spinAnim.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
        ),
        label = "spinAngle",
    )

    val iconScale = when (status) {
        BleStatus.ADVERTISING -> pulseScale
        else -> 1f
    }

    Box(
        modifier = Modifier
            .size(160.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Halo ring
        Canvas(modifier = Modifier.size(160.dp)) {
            val strokeWidth = 6.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2

            when {
                isTransferring -> {
                    // Background track
                    drawCircle(
                        color = statusColor.copy(alpha = 0.15f),
                        radius = radius,
                        style = Stroke(width = strokeWidth),
                    )
                    // Progress arc
                    drawArc(
                        color = statusColor,
                        startAngle = -90f,
                        sweepAngle = progress * 360f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                        topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2, strokeWidth / 2),
                        size = androidx.compose.ui.geometry.Size(size.width - strokeWidth, size.height - strokeWidth),
                    )
                }
                status == BleStatus.CLIENT_CONNECTED -> {
                    // Spinning partial arc
                    drawArc(
                        color = statusColor,
                        startAngle = spinAngle,
                        sweepAngle = 90f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                        topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2, strokeWidth / 2),
                        size = androidx.compose.ui.geometry.Size(size.width - strokeWidth, size.height - strokeWidth),
                    )
                }
                status != BleStatus.IDLE -> {
                    // Solid ring
                    drawCircle(
                        color = statusColor.copy(alpha = if (status == BleStatus.ADVERTISING) 0.3f else 1f),
                        radius = radius,
                        style = Stroke(width = strokeWidth),
                    )
                }
                else -> {
                    // Gray outline
                    drawCircle(
                        color = statusColor.copy(alpha = 0.3f),
                        radius = radius,
                        style = Stroke(width = strokeWidth),
                    )
                }
            }
        }

        // Icon
        Icon(
            imageVector = when {
                status == BleStatus.COMPLETE -> Icons.Filled.Check
                !isRunning -> Icons.Filled.BluetoothDisabled
                else -> Icons.Filled.Bluetooth
            },
            contentDescription = null,
            tint = statusColor,
            modifier = Modifier
                .size(64.dp)
                .scale(iconScale),
        )
    }
}

@Composable
private fun statusColor(status: BleStatus): Color = when (status) {
    BleStatus.IDLE -> MaterialTheme.colorScheme.outline
    BleStatus.COMPLETE -> Color(0xFF4CAF50) // green
    BleStatus.ERROR -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.primary
}

private fun statusTitle(status: BleStatus): String = when (status) {
    BleStatus.IDLE -> "Stopped"
    BleStatus.ADVERTISING -> "Waiting for connection"
    BleStatus.CLIENT_CONNECTED -> "Initializing"
    BleStatus.DEVICE_READY -> "Connected"
    BleStatus.TRANSFERRING_HEADER -> "Sending dive info"
    BleStatus.TRANSFERRING_PROFILE -> "Sending dive profile"
    BleStatus.COMPLETE -> "Transfer complete"
    BleStatus.ERROR -> "Error"
}

private fun statusSubtitle(status: BleStatus, diveCount: Int): String = when (status) {
    BleStatus.IDLE -> "Tap the Bluetooth icon to start"
    BleStatus.ADVERTISING -> "$diveCount dive(s) ready to transfer"
    BleStatus.CLIENT_CONNECTED -> "SSI app connected"
    BleStatus.DEVICE_READY -> "Waiting for SSI to start import"
    BleStatus.TRANSFERRING_HEADER -> "Transferring dive header"
    BleStatus.TRANSFERRING_PROFILE -> "Transferring depth profile"
    BleStatus.COMPLETE -> "The dive should appear in your SSI logbook"
    BleStatus.ERROR -> "Something went wrong"
}