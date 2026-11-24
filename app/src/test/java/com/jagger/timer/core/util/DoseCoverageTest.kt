package com.jagger.timer.core.util

import com.jagger.timer.core.data.local.entity.DoseLogEntity
import com.jagger.timer.core.data.local.entity.DoseStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DoseCoverageTest {
    private val min = 60_000L
    private val due = 1_000_000_000L

    private fun log(offsetMin: Long, status: DoseStatus = DoseStatus.TAKEN, schedule: Long? = 1) =
        DoseLogEntity(pillId = 1, scheduleId = schedule, timestamp = due + offsetMin * min, status = status)

    @Test fun takenThirtyMinutesEarlyCoversDose() {
        assertNotNull(DoseCoverage.findCovering(listOf(log(-30)), 1, due, due - 29 * min))
    }

    @Test fun skippedCountsToo() {
        assertNotNull(DoseCoverage.findCovering(listOf(log(-5, DoseStatus.SKIPPED)), 1, due, due))
    }

    @Test fun tooEarlyDoesNotCover() {
        assertNull(DoseCoverage.findCovering(listOf(log(-180)), 1, due, due))
    }

    @Test fun otherScheduleOrMissedDoesNotCover() {
        assertNull(DoseCoverage.findCovering(listOf(log(-10, schedule = 2), log(-10, DoseStatus.MISSED)), 1, due, due))
    }

    @Test fun picksLatestLog() {
        val r = DoseCoverage.findCovering(listOf(log(-60), log(-10)), 1, due, due)
        assertEquals(due - 10 * min, r?.timestamp)
    }
}
