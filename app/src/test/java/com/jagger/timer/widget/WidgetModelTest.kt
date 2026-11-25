// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.widget

import com.jagger.timer.core.data.local.entity.DoseLogEntity
import com.jagger.timer.core.data.local.entity.DoseStatus
import com.jagger.timer.core.data.local.entity.PillEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class WidgetModelTest {
    private val hour = 3_600_000L
    private val now = 100 * hour

    private fun pill(id: Long, name: String, interval: Int? = 240) =
        PillEntity(id = id, name = name, dosage = "200", unit = "mg", color = 0xFF0000FF.toInt(),
            asNeeded = true, minIntervalMinutes = interval)

    private fun taken(pillId: Long, at: Long) =
        DoseLogEntity(pillId = pillId, timestamp = at, status = DoseStatus.TAKEN)

    @Test fun readyWhenNothingTaken() {
        val r = WidgetModel.build(listOf(pill(1, "A")), emptyMap(), emptyMap(), now, ZoneOffset.UTC).single()
        assertTrue(r.ready); assertTrue(r.takeDirectly); assertEquals("Ready", r.caption); assertEquals(1f, r.progress)
    }

    @Test fun waitingShowsAbsoluteTimeAndProgress() {
        val logs = mapOf(1L to listOf(taken(1, now - hour)))   // 4h interval, 1h elapsed
        val r = WidgetModel.build(listOf(pill(1, "A")), logs, emptyMap(), now, ZoneOffset.UTC).single()
        assertFalse(r.ready); assertFalse(r.takeDirectly)
        assertEquals(0.25f, r.progress, 0.001f)
        assertTrue(r.caption.startsWith("Ready at "))
    }

    @Test fun readyRowsSortFirst() {
        val logs = mapOf(1L to listOf(taken(1, now - hour)))
        val rows = WidgetModel.build(listOf(pill(1, "Waiting"), pill(2, "Ready")), logs, emptyMap(), now, ZoneOffset.UTC)
        assertEquals(listOf("Ready", "Waiting"), rows.map { it.name })
    }
}
