package com.divebridge.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.divebridge.dive.Dive
import com.divebridge.ssi.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiveReviewScreen(
    dive: Dive,
    initialParams: SsiDiveParams,
    onGenerate: (SsiDiveParams) -> Unit,
    onBack: () -> Unit,
) {
    var siteId by remember { mutableStateOf(initialParams.siteId ?: "") }
    var selectedWaterType by remember { mutableStateOf(initialParams.waterTypeId) }
    var selectedDiveSubType by remember { mutableStateOf(initialParams.diveSubTypeId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dive Review") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
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
            // Parsed dive data
            Text("Dive Data", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            DiveInfoCard(dive)

            Spacer(modifier = Modifier.height(24.dp))

            // User inputs
            Text("Dive Details", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = siteId,
                onValueChange = { siteId = it.filter { c -> c.isDigit() } },
                label = { Text("Dive site ID") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = { Text("SSI dive spot numeric ID") },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Water type dropdown
            DropdownSelector(
                label = "Water type",
                options = listOf(null to "Not set", WaterType.FRESH to "Fresh", WaterType.SALT to "Salt"),
                selected = selectedWaterType,
                onSelect = { selectedWaterType = it },
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Dive sub-type dropdown
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

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val params = SsiDiveParams(
                        siteId = siteId.ifEmpty { null },
                        waterTypeId = selectedWaterType,
                        diveSubTypeId = selectedDiveSubType,
                    )
                    onGenerate(params)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Generate QR Code")
            }
        }
    }
}

@Composable
private fun DiveInfoCard(dive: Dive) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            val dt = dive.dateTime
            InfoRow("Date", "%04d-%02d-%02d %02d:%02d".format(
                dt.year, dt.monthNumber, dt.dayOfMonth, dt.hour, dt.minute
            ))
            InfoRow("Max depth", "%.1f m".format(dive.maxDepthMeters))
            InfoRow("Bottom time", formatDiveTime(dive.diveTimeMinutes))
            InfoRow("Water temp", "${dive.minWaterTempCelsius.roundToInt()} - ${dive.maxWaterTempCelsius.roundToInt()} C")
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatDiveTime(minutes: Double): String {
    val totalSeconds = (minutes * 60).roundToInt()
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
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

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (value, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    },
                )
            }
        }
    }
}