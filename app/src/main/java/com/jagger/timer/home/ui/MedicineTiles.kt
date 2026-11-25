package com.jagger.timer.home.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jagger.timer.core.data.local.entity.DoseStatus
import com.jagger.timer.core.data.local.entity.PillEntity
import com.jagger.timer.core.data.model.TimelineEntry
import com.jagger.timer.core.util.CooldownStatus
import com.jagger.timer.core.util.IngredientWarning
import com.jagger.timer.core.util.InstructionTags
import com.jagger.timer.core.util.RxFormLogic
import com.jagger.timer.core.util.SupplyStatus
import com.jagger.timer.core.util.formatCountdown
import com.jagger.timer.home.viewmodel.AsNeededItem
import com.jagger.timer.home.viewmodel.SupplyInfo
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val clock = DateTimeFormatter.ofPattern("h:mm a")

/**
 * A medicine on Home, one compact row: icon, name, a status line (dose time or wait countdown) and its action
 * buttons. Tapping the row opens the details: tags, doctor, Rx number, pharmacy, supply and the Edit button.
 */
@Composable
fun AsNeededTile(
    item: AsNeededItem,
    supply: SupplyInfo?,
    warning: IngredientWarning?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onTakeNow: () -> Unit,
    onEdit: () -> Unit
) {
    val pill = item.pill
    val cd = item.cooldown
    val now = item.nowMillis
    val ready = cd.status == CooldownStatus.READY
    val countdown = when (cd.status) {
        CooldownStatus.READY -> "Ready"
        CooldownStatus.WAITING -> "Next in ${formatCountdown(cd.millisUntilNext(now))}"
        CooldownStatus.AT_DAILY_MAX -> "Daily max, next in ${formatCountdown(cd.millisUntilNext(now))}"
    }
    val usage = pill.maxPer24h?.let { max ->
        if (pill.maxIsDoses) "${cd.dosesIn24h} of $max doses in the last 24 h"
        else "${RxFormLogic.formatCount(cd.amountIn24h)} of $max ${pill.unit} in the last 24 h"
    }
    TileShell(
        pill = pill,
        subline = "$countdown  ·  ${RxFormLogic.doseLabel(pill)}",
        sublineColor = when (cd.status) {
            CooldownStatus.READY -> MaterialTheme.colorScheme.primary
            CooldownStatus.WAITING -> MaterialTheme.colorScheme.onSurfaceVariant
            CooldownStatus.AT_DAILY_MAX -> MaterialTheme.colorScheme.error
        },
        needsAttention = warning != null || supply?.status.let { it == SupplyStatus.LOW || it == SupplyStatus.EMPTY },
        dimmed = false,
        expanded = expanded,
        onToggle = onToggle,
        actions = {
            val content: @Composable RowScope.() -> Unit = {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Take now", modifier = Modifier.padding(start = 6.dp), maxLines = 1)
            }
            if (ready) Button(onClick = onTakeNow, content = content)
            else FilledTonalButton(onClick = onTakeNow, content = content)
        },
        details = { Details(pill, supply, warning, extra = usage, onEdit = onEdit) }
    )
}

@Composable
fun TimelineTile(
    entry: TimelineEntry,
    supply: SupplyInfo?,
    warning: IngredientWarning?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onTaken: () -> Unit,
    onSkip: () -> Unit,
    onEdit: () -> Unit
) {
    val pill = entry.pill
    val handled = entry.isHandled
    val subline = if (handled) {
        val verb = if (entry.handledStatus == DoseStatus.SKIPPED) "Skipped" else "Taken"
        val at = Instant.ofEpochMilli(entry.handledAt ?: 0L).atZone(ZoneId.systemDefault()).format(clock)
        "$verb at $at  ·  ${RxFormLogic.doseLabel(pill)}"
    } else {
        "${entry.dueTime.format(clock)}  ·  ${RxFormLogic.doseLabel(pill)}"
    }
    TileShell(
        pill = pill,
        subline = subline,
        sublineColor = if (handled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        needsAttention = warning != null || supply?.status.let { it == SupplyStatus.LOW || it == SupplyStatus.EMPTY },
        dimmed = handled,
        expanded = expanded,
        onToggle = onToggle,
        actions = {
            FilledTonalIconButton(onClick = onSkip, enabled = !handled) {
                Icon(Icons.Default.SkipNext, contentDescription = "Skip ${pill.name}")
            }
            FilledIconButton(onClick = onTaken, enabled = !handled) {
                Icon(Icons.Default.Check, contentDescription = "Mark ${pill.name} taken")
            }
        },
        details = { Details(pill, supply, warning, extra = null, onEdit = onEdit) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TileShell(
    pill: PillEntity,
    subline: String,
    sublineColor: Color,
    needsAttention: Boolean,
    dimmed: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    details: @Composable () -> Unit
) {
    Card(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .then(if (dimmed) Modifier.alpha(0.55f) else Modifier)
            .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed, tap for details" },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PillBadge(pill)
                Column(modifier = Modifier.weight(1f).padding(end = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            pill.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (needsAttention) Icon(
                            Icons.Default.Warning,
                            contentDescription = "Needs attention, open for details",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 6.dp).size(16.dp)
                        )
                    }
                    Text(
                        subline,
                        style = MaterialTheme.typography.bodySmall,
                        color = sublineColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), content = actions)
            }
            AnimatedVisibility(visible = expanded) { details() }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Details(pill: PillEntity, supply: SupplyInfo?, warning: IngredientWarning?, extra: String?, onEdit: () -> Unit) {
    val tags = InstructionTags.parse(pill.instructions)
    Column(modifier = Modifier.padding(top = 6.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                tags.forEach { Tag(it) }
            }
        }
        extra?.let { InfoLine(null, it) }
        supply?.let { s ->
            val left = RxFormLogic.formatCount(s.supply)
            val days = s.daysLeft?.let { ", about ${Math.ceil(it).toInt()} days" }.orEmpty()
            when (s.status) {
                SupplyStatus.EMPTY -> InfoLine(null, "Out of pills. Time to refill", error = true)
                SupplyStatus.LOW -> InfoLine(null, "Low, refill soon. $left left$days", error = true)
                SupplyStatus.OK -> InfoLine("Supply", "$left left$days")
            }
        }
        pill.prescriber?.takeIf { it.isNotBlank() }?.let { InfoLine("Doctor", it) }
        pill.rxNumber?.takeIf { it.isNotBlank() }?.let { InfoLine("Rx number", it) }
        pill.pharmacy?.takeIf { it.isNotBlank() }?.let { InfoLine("Pharmacy", it) }
        IngredientBanner(warning)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Edit", modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun Tag(text: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun InfoLine(label: String?, value: String, error: Boolean = false) {
    Row {
        if (label != null) Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (label != null) FontWeight.Medium else FontWeight.Normal,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}
