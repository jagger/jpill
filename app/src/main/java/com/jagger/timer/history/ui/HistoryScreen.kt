// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.history.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jagger.timer.core.data.local.dao.DoseHistoryRow
import com.jagger.timer.core.data.local.entity.DoseStatus
import com.jagger.timer.core.util.HistoryFormatter
import com.jagger.timer.history.HistoryShare
import com.jagger.timer.history.viewmodel.HistoryViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(
    onNavigate: (String) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val rows by viewModel.rows.collectAsState()
    val adherence by viewModel.adherence.collectAsState()
    val pills by viewModel.pills.collectAsState()
    val pillFilter by viewModel.pillFilter.collectAsState()
    val statusFilter by viewModel.statusFilter.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<DoseHistoryRow?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val zone = ZoneId.systemDefault()
    val groups = remember(rows) { HistoryFormatter.groupByDay(rows, zone, LocalDate.now(zone)) }
    val selectedPill = pills.firstOrNull { it.id == pillFilter }

    Scaffold(
        bottomBar = { com.jagger.timer.nav.MainNavBar(com.jagger.timer.nav.Routes.HISTORY, onNavigate) },
        topBar = {
            TopAppBar(
                title = { Text("History") },
                actions = {
                    IconButton(
                        enabled = rows.isNotEmpty(),
                        onClick = { scope.launch { HistoryShare.share(context, viewModel.exportCsv()) } }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share history as CSV")
                    }
                }
            )
        }
    ) { innerPadding ->
        editing?.let { row ->
            HistoryEditDialog(
                row = row,
                zone = zone,
                onSave = { status, ts, amount -> viewModel.editLog(row, status, ts, amount); editing = null },
                onDelete = { viewModel.deleteLog(row); editing = null },
                onDismiss = { editing = null }
            )
        }
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column {
                    OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            selectedPill?.let { it.name + if (it.isArchived) " (archived)" else "" } ?: "All medications"
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("All medications") },
                            onClick = { viewModel.setPillFilter(HistoryViewModel.ALL_PILLS); menuOpen = false }
                        )
                        pills.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.name + if (p.isArchived) " (archived)" else "") },
                                onClick = { viewModel.setPillFilter(p.id); menuOpen = false }
                            )
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = statusFilter == null, onClick = { viewModel.setStatusFilter(null) }, label = { Text("All") })
                    DoseStatus.values().forEach { s ->
                        FilterChip(
                            selected = statusFilter == s,
                            onClick = { viewModel.setStatusFilter(if (statusFilter == s) null else s) },
                            label = { Text(HistoryFormatter.statusLabel(s)) }
                        )
                    }
                }
            }

            if (groups.isEmpty()) {
                Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                    Text("No doses logged yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Doses you take, skip or miss will be listed here, including from archived medications.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    item(key = "heatmap") { AdherenceHeatmap(adherence) }
                    groups.forEach { group ->
                        item(key = "h_" + group.label) {
                            Text(
                                group.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
                            )
                        }
                        items(group.rows.size, key = { "r_" + group.rows[it].logId }) { i ->
                            HistoryRow(group.rows[i], zone, onLongClick = { editing = group.rows[i] })
                            if (i < group.rows.size - 1) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(row: DoseHistoryRow, zone: ZoneId, onLongClick: () -> Unit) {
    val statusColor: Color = when (row.status) {
        DoseStatus.TAKEN -> MaterialTheme.colorScheme.primary
        DoseStatus.SKIPPED -> MaterialTheme.colorScheme.onSurfaceVariant
        DoseStatus.MISSED -> MaterialTheme.colorScheme.error
    }
    Row(
        modifier = Modifier.fillMaxWidth()
            .combinedClickable(onLongClickLabel = "Edit or delete", onLongClick = onLongClick, onClick = {})
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(row.pillName + if (row.isArchived) " (archived)" else "", style = MaterialTheme.typography.bodyLarge)
            Text(
                HistoryFormatter.timeLabel(row.timestamp, zone),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(HistoryFormatter.statusLabel(row.status), color = statusColor, fontWeight = FontWeight.SemiBold)
            HistoryFormatter.amountLabel(row)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
