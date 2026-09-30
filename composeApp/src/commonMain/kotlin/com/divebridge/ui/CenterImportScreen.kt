package com.divebridge.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.divebridge.center.DiveCenter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CenterImportScreen(
    onSave: (DiveCenter) -> Unit,
    onScanQr: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var rawInput by remember { mutableStateOf("") }
    var parseError by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<DiveCenter?>(null) }

    // Live preview as user types
    LaunchedEffect(rawInput) {
        if (rawInput.isBlank()) {
            preview = null
            parseError = null
        } else {
            val parsed = DiveCenter.parse(rawInput)
            if (parsed != null) {
                preview = parsed
                parseError = null
            } else {
                preview = null
                parseError = "Invalid format. Expected: center;ID;name:Name or buddy;ID;firstName:..."
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Dive Center") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel")
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
            // Scan button (primary action)
            Button(
                onClick = onScanQr,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan QR Code")
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text(
                    text = "  or paste manually  ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = rawInput,
                onValueChange = { rawInput = it },
                label = { Text("Center QR payload") },
                placeholder = { Text("center;720008;name:... or buddy;ID;firstName:...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                isError = parseError != null && rawInput.isNotBlank(),
                supportingText = {
                    if (parseError != null && rawInput.isNotBlank()) {
                        Text(parseError!!)
                    }
                },
            )

            // Preview card
            if (preview != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Preview", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(preview!!.name, style = MaterialTheme.typography.titleMedium)
                        Text("Center #${preview!!.centerId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { onSave(preview!!) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save Center")
                }
            }
        }
    }
}