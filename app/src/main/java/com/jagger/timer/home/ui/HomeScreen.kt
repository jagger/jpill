// SPDX-License-Identifier: GPL-3.0-or-later
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Warning
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
import com.jagger.timer.core.util.IngredientLogic
import com.jagger.timer.core.util.IngredientWarning
import com.jagger.timer.core.util.formatCountdown
import com.jagger.timer.home.viewmodel.AsNeededItem
import com.jagger.timer.home.viewmodel.SupplyInfo
import com.jagger.timer.core.util.RxFormLogic
import com.jagger.timer.core.util.SupplyStatus
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
    onNavigate: (String) -> Unit = {},
    onArchivedClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onReliabilityClick: () -> Unit = {},
    confirmPillId: Long? = null,
    onConfirmHandled: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val pills by viewModel.pills.collectAsState()
    val timeline by viewModel.timeline.collectAsState()
    val asNeeded by viewModel.asNeeded.collectAsState()
    val supply by viewModel.supply.collectAsState()
    val archivedCount by viewModel.archivedCount.collectAsState()
    val warnings by viewModel.ingredientWarnings.collectAsState()
    var confirmEarly by remember { mutableStateOf<AsNeededItem?>(null) }
    val reliability: com.jagger.timer.reliability.ReliabilityViewModel = hiltViewModel()

    // A widget tap on a medicine that isn't ready asks for the same confirmation as the Take now button.
    androidx.compose.runtime.LaunchedEffect(confirmPillId, asNeeded) {
        val item = asNeeded.firstOrNull { it.pill.id == confirmPillId }
        if (confirmPillId != null && item != null) {
            if (item.cooldown.status == CooldownStatus.READY && warnings[item.pill.id]?.wouldExceed != true) {
                viewModel.takeNow(item.pill)
            } else {
                confirmEarly = item
            }
            onConfirmHandled()
        }
    }

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
                            text = { Text(if (archivedCount > 0) "Archived ($archivedCount)" else "Archived") },
                            leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) },
                            onClick = { menuOpen = false; onArchivedClick() }
                        )
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            onClick = { menuOpen = false; onSettingsClick() }
                        )
                    }
                }
            )
        },
        bottomBar = { com.jagger.timer.nav.MainNavBar(com.jagger.timer.nav.Routes.HOME, onNavigate) },
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
                    warnings = warnings,
                    entries = timeline,
                    onTakeNow = { item ->
                        if (item.cooldown.status == CooldownStatus.READY && warnings[item.pill.id]?.wouldExceed != true) viewModel.takeNow(item.pill)
                        else confirmEarly = item
                    },
                    onTaken = { viewModel.markAsTaken(it) },
                    onSkip = { viewModel.markAsSkipped(it) },
                    onEdit = onEditPillClick,
                    topContent = { com.jagger.timer.reliability.ReliabilityBanner(onFix = onReliabilityClick, viewModel = reliability) }
                )
            }
        }
    }

    confirmEarly?.let { item ->
        val cd = item.cooldown
        val shared = warnings[item.pill.id]?.takeIf { it.wouldExceed }
        val reason = when {
            cd.status == CooldownStatus.READY && shared != null ->
                "Another medicine you take has the same ingredient. ${IngredientLogic.message(shared)} " +
                    "This dose would go over the shared limit."
            cd.status == CooldownStatus.AT_DAILY_MAX -> "You've reached the 24-hour maximum for ${item.pill.name}."
            else -> "It's been less than the recommended wait since your last dose of ${item.pill.name}."
        }
        AlertDialog(
            onDismissRequest = { confirmEarly = null },
            title = { Text("Take it early?") },
            text = {
                Text(
                    reason + (if (cd.status == CooldownStatus.READY) " " else
                        " Next dose is due in ${formatCountdown(cd.millisUntilNext(item.nowMillis))}. ") +
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
    warnings: Map<Long, IngredientWarning> = emptyMap(),
    onTakeNow: (AsNeededItem) -> Unit,
    onTaken: (TimelineEntry) -> Unit,
    onSkip: (TimelineEntry) -> Unit,
    onEdit: (Long) -> Unit,
    topContent: @Composable () -> Unit = {}
) {
    // One tile open at a time; kept across rotation.
    var expandedKey by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    fun toggle(key: String) { expandedKey = if (expandedKey == key) null else key }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp) // Space for FAB
    ) {
        item(key = "reliability_banner") { topContent() }
        if (asNeeded.isNotEmpty()) {
            item(key = "as_needed_header") { SectionHeader("As Needed") }
            items(asNeeded, key = { "as_needed_${it.pill.id}" }) { item ->
                AsNeededTile(
                    item = item,
                    supply = supply[item.pill.id],
                    warning = warnings[item.pill.id],
                    expanded = expandedKey == "n${item.pill.id}",
                    onToggle = { toggle("n${item.pill.id}") },
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
            items(entries, key = { "t${it.pill.id}_${it.schedule.id}" }) { entry ->
                val key = "t${entry.pill.id}_${entry.schedule.id}"
                TimelineTile(
                    entry = entry,
                    supply = supply[entry.pill.id],
                    warning = warnings[entry.pill.id],
                    expanded = expandedKey == key,
                    onToggle = { toggle(key) },
                    onTaken = { onTaken(entry) },
                    onSkip = { onSkip(entry) },
                    onEdit = { onEdit(entry.pill.id) }
                )
            }
        }
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
internal fun PillBadge(pill: PillEntity) {
    val c = androidx.compose.ui.graphics.Color(pill.color)
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .padding(end = 10.dp)
            .size(36.dp)
            .androidx_background(c.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        // Dark purples vanish on a dark surface, so lift the icon toward white there.
        val tint = if (androidx.compose.foundation.isSystemInDarkTheme())
            androidx.compose.ui.graphics.lerp(c, androidx.compose.ui.graphics.Color.White, 0.45f)
        else androidx.compose.ui.graphics.lerp(c, androidx.compose.ui.graphics.Color.Black, 0.3f)
        Icon(com.jagger.timer.core.util.PillAppearance.icon(pill.icon), contentDescription = null, tint = tint)
    }
}

private fun Modifier.androidx_background(color: androidx.compose.ui.graphics.Color): Modifier =
    this.then(Modifier.background(color, androidx.compose.foundation.shape.CircleShape))

/** Heads-up that this medicine shares an active ingredient with another. Red once the shared 24h limit is reached or would be passed. */
@Composable
internal fun IngredientBanner(warning: IngredientWarning?) {
    if (warning == null) return
    val bad = warning.atOrOver || warning.wouldExceed
    androidx.compose.material3.Surface(
        color = if (bad) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.padding(top = 6.dp)
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Warning, contentDescription = "Shared ingredient",
                tint = if (bad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp)
            )
            Text(
                IngredientLogic.message(warning),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}
