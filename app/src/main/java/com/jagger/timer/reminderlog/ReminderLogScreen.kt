// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.reminderlog

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jagger.timer.core.receiver.ReminderNotifier
import com.jagger.timer.core.util.AlarmScheduler
import com.jagger.timer.core.util.ScheduleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class ScheduleStatus(val pillName: String, val due: String, val armed: Boolean)

data class ReminderLogState(
    val delivery: String = "",
    val nextAlarm: String = "",
    val schedules: List<ScheduleStatus> = emptyList(),
    /** Newest first, so the latest events are at the top. */
    val log: String = ""
)

@HiltViewModel
class ReminderLogViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scheduleManager: ScheduleManager,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {
    private val _state = MutableStateFlow(ReminderLogState())
    val state: StateFlow<ReminderLogState> = _state

    fun refresh() {
        viewModelScope.launch {
            val upcoming = scheduleManager.getUpcomingDoses(48)
            val fmt = DateTimeFormatter.ofPattern("EEE HH:mm")
            val rows = upcoming.map { ScheduleStatus(it.pill.name, it.dueTime.format(fmt), alarmScheduler.isArmed(it.schedule.id)) }
            val log = withContext(Dispatchers.IO) { ReminderLog.read().lines().asReversed().joinToString("\n") }
            _state.value = ReminderLogState(
                delivery = ReminderNotifier.deliveryState(context),
                nextAlarm = alarmScheduler.nextAlarmClockMillis()?.let { ReminderLog.clock(it) } ?: "none",
                schedules = rows,
                log = log
            )
        }
    }

    fun rearm() {
        viewModelScope.launch {
            ReminderLog.log("REARM ALL", "reason=manual")
            scheduleManager.refreshAlarms()
            refresh()
        }
    }

    fun clear() { ReminderLog.clear(); refresh() }

    fun share() {
        val text = ReminderLog.read()
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "jpill-reminder-log.txt").apply { writeText(text) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share reminder log").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Settings > Developer tools > Reminder log: what is armed right now, and a trace of everything the reminder machinery did. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderLogScreen(onNavigateBack: () -> Unit, viewModel: ReminderLogViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reminder log") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Right now", style = MaterialTheme.typography.titleMedium)
            Text("Delivery: ${state.delivery}", style = MaterialTheme.typography.bodySmall)
            Text("Android's next alarm: ${state.nextAlarm}", style = MaterialTheme.typography.bodySmall)
            if (state.schedules.isEmpty()) Text("No upcoming scheduled doses in the next 48 hours.", style = MaterialTheme.typography.bodySmall)
            state.schedules.forEach {
                Text(
                    (if (it.armed) "armed   " else "NOT ARMED   ") + "${it.due}  ${it.pillName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it.armed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = viewModel::rearm, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Re-arm all") }
                OutlinedButton(onClick = viewModel::share, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Share") }
                OutlinedButton(onClick = viewModel::clear, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Clear") }
            }
            Text("Log, newest first", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            SelectionContainer {
                Text(
                    state.log.ifBlank { "Nothing logged yet." },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    lineHeight = 13.sp
                )
            }
        }
    }
}
