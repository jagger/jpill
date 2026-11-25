// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.timers.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jagger.timer.core.data.local.entity.TimerState
import com.jagger.timer.core.util.TimerLogic
import com.jagger.timer.nav.MainNavBar
import com.jagger.timer.nav.Routes
import com.jagger.timer.timers.viewmodel.TimerItem
import com.jagger.timer.timers.viewmodel.TimersViewModel

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TimersScreen(
    onNavigate: (String) -> Unit,
    viewModel: TimersViewModel = hiltViewModel()
) {
    val timers by viewModel.timers.collectAsState()
    val nfcEnabled by viewModel.nfcEnabled.collectAsState()
    var showNew by remember { mutableStateOf(false) }
    var nfcTimer by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Timers", fontWeight = FontWeight.Bold) }) },
        bottomBar = { MainNavBar(Routes.TIMERS, onNavigate) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showNew = true }) { Icon(Icons.Default.Add, contentDescription = "New timer") }
        }
    ) { inner ->
        if (timers.isEmpty()) {
            Column(Modifier.padding(inner).fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center) {
                Text("No timers yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Tap + to start a countdown. It keeps running if you close the app, and rings when it ends.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(Modifier.padding(inner).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                items(timers, key = { it.timer.id }) { item ->
                    TimerCard(item, viewModel::pause, viewModel::resume, viewModel::restart, viewModel::delete, onNfc = if (nfcEnabled) ({ nfcTimer = item.timer.id }) else null)
                }
            }
        }
    }

    timers.firstOrNull { it.timer.id == nfcTimer }?.timer?.let { t ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { nfcTimer = null },
            title = { Text(t.label.ifBlank { "Timer" }) },
            text = {
                com.jagger.timer.nfc.NfcTagPanel(
                    kind = com.jagger.timer.nfc.NfcTagKind.TIMER,
                    tagId = t.nfcTagId,
                    name = t.label,
                    timerSeconds = t.durationMillis / 1000,
                    findOwner = { viewModel.tagOwner(it, t.id) },
                    onTagChanged = { viewModel.setNfcTag(t.id, it) }
                )
            },
            confirmButton = { TextButton(onClick = { nfcTimer = null }) { Text("Done") } }
        )
    }

    if (showNew) NewTimerDialog(
        onDismiss = { showNew = false },
        onStart = { label, millis -> viewModel.start(label, millis); showNew = false }
    )
}

@Composable
private fun TimerCard(
    item: TimerItem,
    onPause: (Long) -> Unit, onResume: (Long) -> Unit, onRestart: (Long) -> Unit, onDelete: (Long) -> Unit,
    onNfc: (() -> Unit)?
) {
    val t = item.timer
    // A RUNNING timer past its end shows as done right away, before the alarm updates the row.
    val done = t.state == TimerState.FINISHED || TimerLogic.isDue(t, item.nowMillis)
    Card(Modifier.fillMaxWidth(), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t.label.ifBlank { "Timer" }, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (done) "Done" else TimerLogic.format(TimerLogic.remaining(t, item.nowMillis)),
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (done) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    when {
                        done -> "of ${TimerLogic.format(t.durationMillis)}"
                        t.state == TimerState.PAUSED -> "Paused, of ${TimerLogic.format(t.durationMillis)}"
                        else -> "of ${TimerLogic.format(t.durationMillis)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            when {
                done -> IconButton(onClick = { onRestart(t.id) }) { Icon(Icons.Default.Replay, contentDescription = "Restart") }
                t.state == TimerState.PAUSED -> IconButton(onClick = { onResume(t.id) }) { Icon(Icons.Default.PlayArrow, contentDescription = "Resume") }
                else -> IconButton(onClick = { onPause(t.id) }) { Icon(Icons.Default.Pause, contentDescription = "Pause") }
            }
            if (onNfc != null) IconButton(onClick = onNfc) {
                Icon(
                    Icons.Default.Nfc,
                    contentDescription = if (t.nfcTagId != null) "NFC tag linked, manage tag" else "Add NFC tag",
                    tint = if (t.nfcTagId != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { onDelete(t.id) }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewTimerDialog(onDismiss: () -> Unit, onStart: (String, Long) -> Unit) {
    var label by remember { mutableStateOf("") }
    var h by remember { mutableStateOf("") }
    var m by remember { mutableStateOf("") }
    var s by remember { mutableStateOf("") }
    val millis = ((h.toLongOrNull() ?: 0) * 3600 + (m.toLongOrNull() ?: 0) * 60 + (s.toLongOrNull() ?: 0)) * 1000
    fun setMinutes(min: Int) { h = ""; m = min.toString(); s = "" }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New timer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(label, { label = it }, label = { Text("Label (optional)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Triple("h", h, { v: String -> h = v }), Triple("min", m, { v: String -> m = v }),
                        Triple("sec", s, { v: String -> s = v })).forEach { (name, value, set) ->
                        OutlinedTextField(
                            value, { set(it.filter(Char::isDigit).take(3)) }, label = { Text(name) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 5, 10, 15, 30, 60).forEach { AssistChip(onClick = { setMinutes(it) }, label = { Text("$it min") }) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onStart(label, millis) }, enabled = millis > 0) { Text("Start") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
