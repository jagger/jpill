// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.history.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jagger.timer.core.util.AdherenceCalc
import com.jagger.timer.core.util.DayAdherence
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * The last 30 days as a calendar grid of halftone blocks. Each block is a 4 x 4 dot matrix whose dots grow with the
 * share of scheduled doses taken that day, and merge into one solid block at 100%. An empty outline means doses were
 * scheduled but none were taken; a faint dot means nothing was scheduled. Tap a day for the numbers.
 */
@Composable
fun AdherenceHeatmap(days: List<DayAdherence>, modifier: Modifier = Modifier) {
    if (days.isEmpty()) return
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val picked = days.firstOrNull { it.date.toString() == selected }
    val overall = AdherenceCalc.overallPercent(days)

    val lead = days.first().date.dayOfWeek.value - 1 // Monday-first grid; blanks before the first day
    val rows = ceil((lead + days.size) / 7.0).toInt()

    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Last 30 days", style = MaterialTheme.typography.titleSmall)
        Text(
            picked?.let { AdherenceCalc.describe(it) }
                ?: (overall?.let { "$it% of scheduled doses taken. Tap a day for details." } ?: "No scheduled doses recorded yet."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val idx = r * 7 + c - lead
                    val day = days.getOrNull(idx)?.takeIf { idx >= 0 }
                    if (day == null) Box(Modifier.weight(1f).aspectRatio(1f))
                    else DayCell(
                        day, isToday = day.date == LocalDate.now(), isSelected = selected == day.date.toString(),
                        onClick = { selected = if (selected == day.date.toString()) null else day.date.toString() },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        Legend()
    }
}

@Composable
private fun DayCell(day: DayAdherence, isToday: Boolean, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = AdherenceCalc.describe(day) + if (isToday) ", today" else "" },
        contentAlignment = Alignment.Center
    ) {
        HalftoneBlock(
            fraction = day.fraction, hasData = day.hasData,
            dot = MaterialTheme.colorScheme.primary,
            none = MaterialTheme.colorScheme.error,
            faint = MaterialTheme.colorScheme.outlineVariant,
            outline = if (isSelected) MaterialTheme.colorScheme.primary else if (isToday) MaterialTheme.colorScheme.onSurfaceVariant else null,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/** One day's block: a 4 x 4 halftone dot matrix, solid at 100%. */
@Composable
private fun HalftoneBlock(
    fraction: Float, hasData: Boolean, dot: Color, none: Color, faint: Color, outline: Color?, modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val n = 4
        val pad = size.minDimension * 0.14f
        val area = size.minDimension - 2 * pad
        val step = area / n
        outline?.let {
            drawRoundRect(it, Offset(1f, 1f), Size(size.width - 2f, size.height - 2f), CornerRadius(10.dp.toPx()), style = Stroke(1.5.dp.toPx()))
        }
        when {
            !hasData -> drawCircle(faint, radius = step * 0.14f, center = center)
            fraction >= 0.999f ->
                drawRoundRect(dot, Offset(pad, pad), Size(area, area), CornerRadius(step * 0.4f))
            fraction <= 0f -> {
                drawRoundRect(none.copy(alpha = 0.7f), Offset(pad, pad), Size(area, area), CornerRadius(step * 0.4f), style = Stroke(1.5.dp.toPx()))
            }
            else -> {
                // Dot area grows with the share taken (radius ~ sqrt), so the block darkens evenly and the dots touch near 100%.
                val r = (step / 2f) * sqrt(fraction).coerceAtLeast(0.22f)
                for (row in 0 until n) for (col in 0 until n) {
                    drawCircle(dot, radius = r, center = Offset(pad + step * (col + 0.5f), pad + step * (row + 0.5f)))
                }
            }
        }
    }
}

@Composable
private fun Legend() {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("None", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { f ->
            HalftoneBlock(f, true, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.outlineVariant, null,
                Modifier.size(24.dp).semantics { contentDescription = "${(f * 100).toInt()} percent" })
        }
        Text("All taken", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
