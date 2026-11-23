package com.jagger.timer.add_pill.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jagger.timer.add_pill.viewmodel.PillFormViewModel
import com.jagger.timer.core.data.catalog.OtcCatalog
import com.jagger.timer.core.data.local.entity.ScheduleType
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PillFormScreen(
    onNavigateBack: () -> Unit,
    viewModel: PillFormViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showTimePicker by remember { mutableStateOf(false) }
    // As-needed pills with an amount cap need a numeric dose so the daily total is right.
    val dosageInvalid = uiState.asNeeded && uiState.maxPer24h != null && !uiState.maxIsDoses &&
        uiState.dosage.trim().toDoubleOrNull() == null

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onNavigateBack()
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            onDismissRequest = { showTimePicker = false },
            onConfirm = { hour, minute ->
                viewModel.updateTime(hour, minute)
                showTimePicker = false
            },
            initialHour = uiState.timeMinutes / 60,
            initialMinute = uiState.timeMinutes % 60
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.pillId == null) "Add New Pill" else "Edit Pill") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Common medicines picker (new pills only)
            if (uiState.pillId == null) {
                CommonMedicinePicker(onPick = { viewModel.applyCatalogMed(it) })
            }

            // Pill Details Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pill Details", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = { viewModel.updateName(it) },
                    label = { Text("Pill Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.dosage,
                        onValueChange = { viewModel.updateDosage(it) },
                        label = { Text("Dosage") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        isError = dosageInvalid,
                        supportingText = if (dosageInvalid) {{ Text("Use a number, e.g. 200") }} else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = uiState.unit,
                        onValueChange = { },
                        label = { Text("Unit") },
                        modifier = Modifier.weight(1f),
                        readOnly = true,
                        singleLine = true
                    )
                }
            }

            // As-needed toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Take as needed", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "No fixed schedule. Shows a countdown until your next dose is OK.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = uiState.asNeeded, onCheckedChange = { viewModel.updateAsNeeded(it) })
            }

            if (uiState.asNeeded) {
                DoseLimitsSection(
                    minIntervalMinutes = uiState.minIntervalMinutes,
                    maxPer24h = uiState.maxPer24h,
                    maxIsDoses = uiState.maxIsDoses,
                    unit = uiState.unit,
                    resetKey = "${uiState.pillId}-${uiState.catalogId}-${uiState.isLoading}",
                    onMinInterval = { viewModel.updateMinInterval(it) },
                    onMax = { max, isDoses -> viewModel.updateMaxPer24h(max, isDoses) }
                )
                Text(
                    OtcCatalog.DISCLAIMER,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Scheduling Section
            if (!uiState.asNeeded) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Schedule", style = MaterialTheme.typography.titleMedium)
                
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val types = listOf(
                        ScheduleType.FIXED_TIME to "Fixed",
                        ScheduleType.TIME_SLOT to "Slot",
                        ScheduleType.INTERVAL to "Interval"
                    )
                    types.forEachIndexed { index, (type, label) ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = types.size),
                            onClick = { viewModel.updateScheduleType(type) },
                            selected = uiState.scheduleType == type
                        ) {
                            Text(label)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                when (uiState.scheduleType) {
                    ScheduleType.FIXED_TIME -> {
                        OutlinedCard(
                            onClick = { showTimePicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Schedule, contentDescription = null)
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text("Reminder Time", style = MaterialTheme.typography.labelMedium)
                                    Text(
                                        text = formatTime(uiState.timeMinutes),
                                        style = MaterialTheme.typography.headlineSmall
                                    )
                                }
                            }
                        }
                    }
                    ScheduleType.TIME_SLOT -> {
                        val slots = listOf("Morning", "Afternoon", "Evening", "Night")
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(
                                onClick = { expanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Text(uiState.timeSlotName)
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                slots.forEach { slot ->
                                    DropdownMenuItem(
                                        text = { Text(slot) },
                                        onClick = {
                                            viewModel.updateTimeSlot(slot)
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    ScheduleType.INTERVAL -> {
                        OutlinedTextField(
                            value = if (uiState.intervalMinutes == 0) "" else "${uiState.intervalMinutes / 60}",
                            onValueChange = { 
                                val h = it.toIntOrNull() ?: 0
                                viewModel.updateInterval(h)
                            },
                            label = { Text("Repeat every (hours)") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            )
                        )
                    }
                }
            }

            Button(
                onClick = { viewModel.savePill() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = uiState.name.isNotBlank() && !uiState.isSaving && !uiState.isLoading && !dosageInvalid
            ) {
                if (uiState.isSaving || uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(if (uiState.pillId == null) "Save Medication" else "Update Medication")
                }
            }
        }
    }
}

@Composable
private fun CommonMedicinePicker(onPick: (com.jagger.timer.core.data.catalog.OtcMed) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { if (query.isBlank()) emptyList() else OtcCatalog.search(query) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Common Medicines", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search by name or brand (e.g. Advil)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        if (query.isNotBlank() && results.isEmpty()) {
            Text(
                "No match. Enter it manually below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        results.forEach { med ->
            OutlinedCard(
                onClick = { onPick(med); query = "" },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(med.genericName, style = MaterialTheme.typography.titleSmall)
                    if (med.brandNames.isNotEmpty()) {
                        Text(
                            med.brandNames.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "${med.defaultDose} ${med.unit}, wait ${formatHours(med.minIntervalMinutes)}, " +
                            "max ${med.maxPer24h}${if (med.maxIsDoses) " doses" else " ${med.unit}"}/24h",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun DoseLimitsSection(
    minIntervalMinutes: Int?,
    maxPer24h: Int?,
    maxIsDoses: Boolean,
    unit: String,
    resetKey: String,
    onMinInterval: (Int?) -> Unit,
    onMax: (Int?, Boolean) -> Unit
) {
    // Local text so edits aren't clobbered; reset when a medicine is picked/loaded.
    var hoursText by remember(resetKey) {
        mutableStateOf(minIntervalMinutes?.let { hoursToText(it) } ?: "")
    }
    var maxText by remember(resetKey) { mutableStateOf(maxPer24h?.toString() ?: "") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Dose Limits", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = hoursText,
            onValueChange = { text ->
                // Accept digits and one decimal separator ("." or ","); e.g. 4, 4.5, 0.02
                val cleaned = text.replace(',', '.').filter { it.isDigit() || it == '.' }
                val firstDot = cleaned.indexOf('.')
                val normalized = if (firstDot < 0) cleaned
                    else cleaned.substring(0, firstDot + 1) + cleaned.substring(firstDot + 1).replace(".", "")
                hoursText = normalized.take(8)
                onMinInterval(
                    hoursText.toDoubleOrNull()?.let { h -> Math.round(h * 60).toInt() }?.takeIf { m -> m > 0 }
                )
            },
            label = { Text("Minimum wait between doses (hours)") },
            supportingText = {
                minIntervalMinutes?.let { Text("= ${formatHours(it)}") }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        OutlinedTextField(
            value = maxText,
            onValueChange = {
                maxText = it
                onMax(it.toIntOrNull()?.takeIf { v -> v > 0 }, maxIsDoses)
            },
            label = { Text("Maximum per 24 hours") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !maxIsDoses,
                onClick = { onMax(maxPer24h, false) },
                label = { Text("Total $unit") }
            )
            FilterChip(
                selected = maxIsDoses,
                onClick = { onMax(maxPer24h, true) },
                label = { Text("Number of doses") }
            )
        }
    }
}

/** Minutes as an hours string for the text field: 240 -> "4", 270 -> "4.5", 1 -> "0.0167". */
private fun hoursToText(minutes: Int): String {
    val h = minutes / 60.0
    return if (minutes % 60 == 0) (minutes / 60).toString() else "%.2f".format(java.util.Locale.US, h).trimEnd('0').trimEnd('.')
}

private fun formatHours(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
    initialHour: Int,
    initialMinute: Int
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = false
    )

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = {
                onConfirm(timePickerState.hour, timePickerState.minute)
            }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        },
        text = {
            TimePicker(state = timePickerState)
        }
    )
}

fun formatTime(totalMinutes: Int): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val ampm = if (hours >= 12) "PM" else "AM"
    val displayHours = when {
        hours == 0 -> 12
        hours > 12 -> hours - 12
        else -> hours
    }
    return "%d:%02d %s".format(displayHours, minutes, ampm)
}
