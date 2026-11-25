package com.jagger.timer.widget

import com.jagger.timer.core.data.local.entity.DoseLogEntity
import com.jagger.timer.core.data.local.entity.PillEntity
import com.jagger.timer.core.util.CooldownCalculator
import com.jagger.timer.core.util.CooldownStatus
import com.jagger.timer.core.util.IngredientWarning
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One as-needed medicine as the widget draws it. */
data class WidgetRow(
    val pillId: Long,
    val name: String,
    val color: Int,
    /** 0..1 fill of the ring: how much of the wait has passed (1 = ready). */
    val progress: Float,
    val ready: Boolean,
    /** "Ready", "Ready at 9:41 PM" or "Daily max, ready at 9:41 PM". Absolute times never go stale between refreshes. */
    val caption: String,
    /** True when one tap can log the dose; otherwise the tap opens the app to confirm. */
    val takeDirectly: Boolean
)

/** Pure widget content builder, so what the widget says is unit-tested. */
object WidgetModel {
    private val timeFormat = DateTimeFormatter.ofPattern("h:mm a")

    fun build(
        pills: List<PillEntity>,
        takenLogs: Map<Long, List<DoseLogEntity>>,
        warnings: Map<Long, IngredientWarning>,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<WidgetRow> = pills.map { pill ->
        val logs = takenLogs[pill.id].orEmpty()
        val cd = CooldownCalculator.forPill(pill, logs, nowMillis)
        val ready = cd.status == CooldownStatus.READY
        val next = cd.nextDoseAtMillis
        val clock = next?.let { Instant.ofEpochMilli(it).atZone(zone).format(timeFormat) }
        val caption = when (cd.status) {
            CooldownStatus.READY -> "Ready"
            CooldownStatus.WAITING -> "Ready at $clock"
            CooldownStatus.AT_DAILY_MAX -> "Daily max, ready at $clock"
        }
        val last = cd.lastDoseMillis
        val progress = when {
            ready -> 1f
            last != null && next != null && next > last -> ((nowMillis - last).toFloat() / (next - last)).coerceIn(0f, 1f)
            else -> 0f
        }
        WidgetRow(
            pillId = pill.id,
            name = pill.name,
            color = pill.color,
            progress = progress,
            ready = ready,
            caption = caption,
            takeDirectly = ready && warnings[pill.id]?.wouldExceed != true
        )
    }.sortedWith(compareByDescending<WidgetRow> { it.ready }.thenByDescending { it.progress })
}
