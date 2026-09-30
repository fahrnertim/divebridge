package com.divebridge.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.divebridge.ssi.SsiUserInfo
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    initialUserInfo: SsiUserInfo,
    initialBleCutoff: String?,
    onSave: (SsiUserInfo) -> Unit,
    onSaveBleCutoff: (String?) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var firstName by remember { mutableStateOf(initialUserInfo.firstName) }
    var lastName by remember { mutableStateOf(initialUserInfo.lastName) }
    var masterId by remember { mutableStateOf(initialUserInfo.masterId) }
    var bleCutoff by remember { mutableStateOf(initialBleCutoff ?: "") }
    var bleCutoffEnabled by remember { mutableStateOf(initialBleCutoff != null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                },
                actions = {
                    TextButton(onClick = {
                        onSave(SsiUserInfo(masterId = masterId, firstName = firstName, lastName = lastName))
                        onSaveBleCutoff(if (bleCutoffEnabled && bleCutoff.isNotBlank()) bleCutoff else null)
                        onBack()
                    }) { Text("Save") }
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
            Text("SSI Profile", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = firstName,
                onValueChange = { firstName = it },
                label = { Text("First name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = lastName,
                onValueChange = { lastName = it },
                label = { Text("Last name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = masterId,
                onValueChange = { masterId = it.filter { c -> c.isDigit() } },
                label = { Text("SSI User Master ID") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Find your Master ID in the MySSI app under Profile.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text("BLE Emulator", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Hide dives before date", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = bleCutoffEnabled,
                    onCheckedChange = { bleCutoffEnabled = it },
                )
            }

            if (bleCutoffEnabled) {
                var showDatePicker by remember { mutableStateOf(false) }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = bleCutoff,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Cutoff date") },
                    placeholder = { Text("Tap to select") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                        .clickable { showDatePicker = true },
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Dives before this date won't be sent via Bluetooth.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (showDatePicker) {
                    val datePickerState = rememberDatePickerState()
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                datePickerState.selectedDateMillis?.let { millis ->
                                    val date = Instant.fromEpochMilliseconds(millis)
                                        .toLocalDateTime(TimeZone.currentSystemDefault()).date
                                    bleCutoff = date.toString()
                                }
                                showDatePicker = false
                            }) { Text("OK") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                        },
                    ) {
                        DatePicker(state = datePickerState)
                    }
                }
            }
        }
    }
}