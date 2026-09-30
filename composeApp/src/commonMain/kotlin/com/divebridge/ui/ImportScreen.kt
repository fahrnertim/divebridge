package com.divebridge.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.divebridge.dive.Dive
import com.divebridge.ssi.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImportScreen(
    dive: Dive,
    initialParams: SsiDiveParams,
    recentSiteIds: List<String>,
    onSave: (SsiDiveParams) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var siteId by remember { mutableStateOf(initialParams.siteId ?: "") }
    var selectedDiveSubType by remember { mutableStateOf(initialParams.diveSubTypeId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review Dive") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        val params = SsiDiveParams(
                            diveType = dive.sport.toSsiDiveType(),
                            siteId = siteId.ifEmpty { null },
                            diveSubTypeId = selectedDiveSubType,
                        )
                        onSave(params)
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
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Log Details", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = siteId,
                onValueChange = { siteId = it.filter { c -> c.isDigit() } },
                label = { Text("Dive site ID") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = { Text("SSI dive spot numeric ID (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )

            if (recentSiteIds.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Recent sites", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    recentSiteIds.forEach { id ->
                        AssistChip(onClick = { siteId = id }, label = { Text(id) })
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            DropdownSelector(
                label = "Dive type",
                options = listOf(
                    null to "Not set",
                    DiveSubType.FUN_DIVE to "Fun dive",
                    DiveSubType.EDUCATION to "Education",
                    DiveSubType.SCIENTIFIC to "Scientific",
                    DiveSubType.WORK to "Work",
                ),
                selected = selectedDiveSubType,
                onSelect = { selectedDiveSubType = it },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSelector(
    label: String,
    options: List<Pair<Int?, String>>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second ?: "Not set"

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { onSelect(value); expanded = false },
                )
            }
        }
    }
}