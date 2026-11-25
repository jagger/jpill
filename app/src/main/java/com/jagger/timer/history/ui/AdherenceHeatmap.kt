// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.history.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.sqrt

private val CELL = 16.dp
private val GAP = 2.dp

/**
 * The last 90 days in the style of GitHub's contribution graph: weeks run left to right as columns, weekdays top to
 * bottom (Sunday first), one small square per day. Each square is a 3 x 3 halftone dot matrix whose dots grow with the
 * share of scheduled doses taken that day and merge into one solid square at 100%. An outlined empty square means doses
 * were scheduled but none taken; a faint dot means none were scheduled. Tap a square for the numbers.
 */
@Composable
fun AdherenceHeatmap(days: List<DayAdherence>, modifier: Modifier = Modifier) {
    if (days.isEmpty()) return
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val picked = days.firstOrNull { it.date.toString() == selected }
    val overall = AdherenceCalc.overallPercent(days)
    val today = LocalDate.now()

    val lead = days.first().date.dayOfWeek.value % 7 // Sunday = 0, as in GitHub's graph
    val weeks = ceil((lead + days.size) / 7.0).toInt()

    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // weekday labels (Mon, Wed, Fri) like GitHub, then one column per week
        Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
            Column(verticalArrangement = Arrangement.spacedBy(GAP), horizontalAlignment = Alignment.End) {
                for (r in 0 until 7) {
                    Box(Modifier.height(CELL), contentAlignment = Alignment.CenterEnd) {
                        val label = when (r) { 1 -> "M"; 3 -> "W"; 5 -> "F"; else -> "" }
                        if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 3.dp))
                    }
                }
            }
            for (w in 0 until weeks) {
                Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
                    for (r in 0 until 7) {
                        val idx = w * 7 + r - lead
                        val day = days.getOrNull(idx)?.takeIf { idx >= 0 }
                        if (day == null) Box(Modifier.size(CELL))
                        else DayCell(
                            day, isToday = day.date == today, isSelected = selected == day.date.toString(),
                            onClick = { selected = if (selected == day.date.toString()) null else day.date.toString() }
                        )
                    }
                }
            }
        }

        // one line under the grid: what it shows (or the tapped day) and the legend
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                picked?.let { AdherenceCalc.describe(it) }
                    ?: ("Last ${days.size} days" + (overall?.let { "  ·  $it% taken" } ?: "")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Less", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { f ->
                    HalftoneBlock(f, true, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.outlineVariant, null,
                        Modifier.size(12.dp).semantics { contentDescription = "${(f * 100).toInt()} percent" })
                }
                Text("More", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DayCell(day: DayAdherence, isToday: Boolean, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(CELL)
            .clip(RoundedCornerShape(3.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = AdherenceCalc.describe(day) + if (isToday) ", today" else "" }
    ) {
        HalftoneBlock(
            fraction = day.fraction, hasData = day.hasData,
            dot = MaterialTheme.colorScheme.primary,
            none = MaterialTheme.colorScheme.error,
            faint = MaterialTheme.colorScheme.outlineVariant,
            outline = if (isSelected) MaterialTheme.colorScheme.onSurface else if (isToday) MaterialTheme.colorScheme.onSurfaceVariant else null,
            modifier = Modifier.size(CELL)
        )
    }
}

/** One day's square: a 3 x 3 halftone dot matrix, solid at 100%. */
@Composable
private fun HalftoneBlock(
    fraction: Float, hasData: Boolean, dot: Color, none: Color, faint: Color, outline: Color?, modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val n = 3
        val pad = size.minDimension * 0.08f
        val area = size.minDimension - 2 * pad
        val step = area / n
        val corner = CornerRadius(size.minDimension * 0.2f)
        when {
            !hasData -> drawCircle(faint, radius = step * 0.2f, center = center)
            fraction >= 0.999f -> drawRoundRect(dot, Offset(pad, pad), Size(area, area), corner)
            fraction <= 0f -> drawRoundRect(none.copy(alpha = 0.8f), Offset(pad + 0.5.dp.toPx(), pad + 0.5.dp.toPx()),
                Size(area - 1.dp.toPx(), area - 1.dp.toPx()), corner, style = Stroke(1.dp.toPx()))
            else -> {
                // dot area grows with the share taken (radius ~ sqrt), so the square darkens evenly and the dots touch near 100%
                val r = (step / 2f) * sqrt(fraction).coerceAtLeast(0.3f)
                for (row in 0 until n) for (col in 0 until n) {
                    drawCircle(dot, radius = r, center = Offset(pad + step * (col + 0.5f), pad + step * (row + 0.5f)))
                }
            }
        }
        outline?.let {
            drawRoundRect(it, Offset(0.5.dp.toPx(), 0.5.dp.toPx()), Size(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()), corner, style = Stroke(1.dp.toPx()))
        }
    }
}
