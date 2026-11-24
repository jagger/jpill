package com.jagger.timer.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxHeight
import com.jagger.timer.core.data.catalog.OtcCatalog
import com.jagger.timer.core.util.CooldownStatus
import com.jagger.timer.core.util.formatCountdown
import com.jagger.timer.home.viewmodel.AsNeededItem
import com.jagger.timer.home.viewmodel.SupplyInfo
import com.jagger.timer.core.util.RxFormLogic
import com.jagger.timer.core.util.SupplyStatus
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jagger.timer.core.data.local.entity.PillEntity
import com.jagger.timer.core.data.model.TimelineEntry
import com.jagger.timer.home.viewmodel.HomeViewModel
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddPillClick: () -> Unit,
    onEditPillClick: (Long) -> Unit,
    onHistoryClick: () -> Unit = {},
    onArchivedClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val pills by viewModel.pills.collectAsState()
    val timeline by viewModel.timeline.collectAsState()
    val asNeeded by viewModel.asNeeded.collectAsState()
    val supply by viewModel.supply.collectAsState()
    val archivedCount by viewModel.archivedCount.collectAsState()
    var confirmEarly by remember { mutableStateOf<AsNeededItem?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("jPill", fontWeight = FontWeight.Bold) },
                actions = {
                    var menuOpen by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("History") },
                            leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                            onClick = { menuOpen = false; onHistoryClick() }
                        )
                        DropdownMenuItem(
                            text = { Text(if (archivedCount > 0) "Archived ($archivedCount)" else "Archived") },
                            leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) },
                            onClick = { menuOpen = false; onArchivedClick() }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddPillClick) {
                Icon(Icons.Default.Add, contentDescription = "Add Pill")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if (timeline.isEmpty() && pills.isEmpty()) {
                EmptyState(onAddPillClick)
            } else {
                HomeContent(
                    asNeeded = asNeeded,
                    hasScheduledPills = pills.size > asNeeded.size,
                    supply = supply,
                    entries = timeline,
                    onTakeNow = { item ->
                        if (item.cooldown.status == CooldownStatus.READY) viewModel.takeNow(item.pill)
                        else confirmEarly = item
                    },
                    onTaken = { viewModel.markAsTaken(it) },
                    onSkip = { viewModel.markAsSkipped(it) },
                    onEdit = onEditPillClick
                )
            }
        }
    }

    confirmEarly?.let { item ->
        val cd = item.cooldown
        val reason = when (cd.status) {
            CooldownStatus.AT_DAILY_MAX -> "You've reached the 24-hour maximum for ${item.pill.name}."
            else -> "It's been less than the recommended wait since your last dose of ${item.pill.name}."
        }
        AlertDialog(
            onDismissRequest = { confirmEarly = null },
            title = { Text("Take it early?") },
            text = {
                Text(
                    "$reason Next dose is due in ${formatCountdown(cd.millisUntilNext(item.nowMillis))}. " +
                        "Check your label or ask a pharmacist before taking more."
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.takeNow(item.pill); confirmEarly = null }) {
                    Text("Log it anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmEarly = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun HomeContent(
    asNeeded: List<AsNeededItem>,
    hasScheduledPills: Boolean,
    supply: Map<Long, SupplyInfo>,
    entries: List<TimelineEntry>,
    onTakeNow: (AsNeededItem) -> Unit,
    onTaken: (TimelineEntry) -> Unit,
    onSkip: (TimelineEntry) -> Unit,
    onEdit: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp) // Space for FAB
    ) {
        if (asNeeded.isNotEmpty()) {
            item(key = "as_needed_header") { SectionHeader("As Needed") }
            items(asNeeded, key = { "as_needed_${it.pill.id}" }) { item ->
                AsNeededCard(
                    item = item,
                    supply = supply[item.pill.id],
                    onTakeNow = { onTakeNow(item) },
                    onEdit = { onEdit(item.pill.id) }
                )
            }
            item(key = "as_needed_disclaimer") {
                Text(
                    text = OtcCatalog.DISCLAIMER,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }

        if (entries.isNotEmpty() || hasScheduledPills) {
            item(key = "upcoming_header") { SectionHeader("Upcoming Doses") }
        }
        if (entries.isEmpty()) {
            if (hasScheduledPills) {
                item(key = "caught_up") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(
                            text = "All caught up! No doses scheduled for the next 24 hours.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        } else {
            items(entries) { entry ->
                TimelineCard(
                    entry = entry,
                    supply = supply[entry.pill.id],
                    onTaken = { onTaken(entry) },
                    onSkip = { onSkip(entry) },
                    onEdit = { onEdit(entry.pill.id) }
                )
            }
        }
    }
}

/** Food/timing note and supply line for prescriptions. Shows nothing for pills without either. */
@Composable
private fun RxInfoLines(instructions: String?, supply: SupplyInfo?) {
    if (!instructions.isNullOrBlank()) {
        Text(
            text = instructions,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (supply != null) {
        val left = RxFormLogic.formatCount(supply.supply)
        val days = supply.daysLeft?.let { "about ${Math.ceil(it).toInt()} days" }
        val (text, color) = when (supply.status) {
            SupplyStatus.EMPTY -> "Out of pills. Time to refill" to MaterialTheme.colorScheme.error
            SupplyStatus.LOW -> "Low: refill soon. $left left" + (days?.let { ", $it" } ?: "") to MaterialTheme.colorScheme.error
            SupplyStatus.OK -> "$left left" + (days?.let { ", $it" } ?: "") to MaterialTheme.colorScheme.onSurfaceVariant
        }
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun AsNeededCard(
    item: AsNeededItem,
    supply: SupplyInfo?,
    onTakeNow: () -> Unit,
    onEdit: () -> Unit
) {
    val pill = item.pill
    val cd = item.cooldown
    val now = item.nowMillis
    val ready = cd.status == CooldownStatus.READY

    val statusText = when (cd.status) {
        CooldownStatus.READY -> "Ready"
        CooldownStatus.WAITING -> "Next dose in ${formatCountdown(cd.millisUntilNext(now))}"
        CooldownStatus.AT_DAILY_MAX -> "Daily max reached. Next in ${formatCountdown(cd.millisUntilNext(now))}"
    }
    val usage = pill.maxPer24h?.let { max ->
        if (pill.maxIsDoses) "${cd.dosesIn24h} of $max doses in last 24h"
        else "${cd.amountIn24h.toInt()} of $max ${pill.unit} in last 24h"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PillBadge(pill)
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = pill.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = RxFormLogic.doseLabel(pill),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    RxInfoLines(pill.instructions, supply)
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Pill",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = when (cd.status) {
                    CooldownStatus.READY -> MaterialTheme.colorScheme.primary
                    CooldownStatus.WAITING -> MaterialTheme.colorScheme.onSurface
                    CooldownStatus.AT_DAILY_MAX -> MaterialTheme.colorScheme.error
                }
            )
            usage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            val buttonContent: @Composable () -> Unit = {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Take now")
            }
            if (ready) {
                Button(onClick = onTakeNow, modifier = Modifier.fillMaxWidth(), content = { buttonContent() })
            } else {
                FilledTonalButton(onClick = onTakeNow, modifier = Modifier.fillMaxWidth(), content = { buttonContent() })
            }
        }
    }
}

@Composable
fun TimelineCard(
    entry: TimelineEntry,
    supply: SupplyInfo?,
    onTaken: () -> Unit,
    onSkip: () -> Unit,
    onEdit: () -> Unit
) {
    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PillBadge(entry.pill)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.dueTime.format(timeFormatter),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = entry.pill.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = RxFormLogic.doseLabel(entry.pill),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    RxInfoLines(entry.pill.instructions, supply)
                }
                
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Pill",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onSkip,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Skip")
                }
                Button(
                    onClick = onTaken,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Taken")
                }
            }
        }
    }
}

@Composable
fun EmptyState(onAddPillClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Medication,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Your medicine cabinet is empty",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Add your first pill to start tracking",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onAddPillClick) {
            Text("Add Medication")
        }
    }
}


@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    HomeScreen(onAddPillClick = {}, onEditPillClick = {})
}

/** The pill's chosen icon on a tint of its chosen color. */
@Composable
private fun PillBadge(pill: PillEntity) {
    val c = androidx.compose.ui.graphics.Color(pill.color)
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .padding(end = 12.dp)
            .size(44.dp)
            .androidx_background(c.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(com.jagger.timer.core.util.PillAppearance.icon(pill.icon), contentDescription = null, tint = c)
    }
}

private fun Modifier.androidx_background(color: androidx.compose.ui.graphics.Color): Modifier =
    this.then(Modifier.background(color, androidx.compose.foundation.shape.CircleShape))
