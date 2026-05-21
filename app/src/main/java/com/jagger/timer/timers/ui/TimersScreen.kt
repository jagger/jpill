package com.jagger.timer.timers.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jagger.timer.core.data.local.entity.TimerEntity
import com.jagger.timer.core.data.local.entity.TimerType
import com.jagger.timer.timers.viewmodel.TimersViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimersScreen(
    onAddTimerClick: () -> Unit,
    viewModel: TimersViewModel = hiltViewModel()
) {
    val timers by viewModel.timers.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("General Timers", fontWeight = FontWeight.Bold) }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTimerClick) {
                Icon(Icons.Default.Add, contentDescription = "Add Timer")
            }
        }
    ) { innerPadding ->
        if (timers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(16.dp))
                    Text("No timers set.", style = MaterialTheme.typography.bodyLarge)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(timers) { timer ->
                    TimerCard(
                        timer = timer,
                        onToggle = { viewModel.toggleTimer(timer) },
                        onDelete = { viewModel.deleteTimer(timer) }
                    )
                }
            }
        }
    }
}

@Composable
fun TimerCard(
    timer: TimerEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val timerColor = Color(timer.color)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(timerColor.copy(alpha = 0.1f), MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (timer.type == TimerType.REPEATING) Icons.Default.Repeat else Icons.Default.Timer,
                    contentDescription = null,
                    tint = timerColor
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = timer.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (timer.type == TimerType.REPEATING) "Every ${formatSeconds(timer.durationSeconds)}" else formatSeconds(timer.durationSeconds),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
            }

            Switch(
                checked = timer.isActive,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(checkedThumbColor = timerColor, checkedTrackColor = timerColor.copy(alpha = 0.3f))
            )
        }
    }
}

fun formatSeconds(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%dh %dm %ds".format(h, m, s) else if (m > 0) "%dm %ds".format(m, s) else "%ds".format(s)
}
