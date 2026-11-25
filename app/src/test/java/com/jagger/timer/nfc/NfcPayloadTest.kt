// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.nfc

import com.jagger.timer.core.data.local.entity.TimerEntity
import com.jagger.timer.core.data.local.entity.TimerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NfcPayloadTest {
    private val id = "AAAAAAAAAAAAAAAAAAAAAA"

    @Test fun medicineUriWithoutNameCarriesOnlyTheId() {
        val uri = NfcPayload.medicineUri(id)
        assertEquals("jpill://tag/$id", uri)
        assertEquals(NfcPayload.Medicine(id, null), NfcPayload.parse(uri))
    }

    @Test fun medicineNameRoundTrips() {
        val uri = NfcPayload.medicineUri(id, "Amlodipine")
        assertEquals("jpill://tag/$id?name=Amlodipine", uri)
        assertEquals(NfcPayload.Medicine(id, "Amlodipine"), NfcPayload.parse(uri))
        assertEquals("Vitamin D3 (1000 IU)", (NfcPayload.parse(NfcPayload.medicineUri(id, "Vitamin D3 (1000 IU)")) as NfcPayload.Medicine).name)
    }

    @Test fun blankMedicineNameIsDropped() = assertEquals("jpill://tag/$id", NfcPayload.medicineUri(id, "   "))

    @Test fun medicineWithUnknownQueryStillParses() =
        assertEquals(NfcPayload.Medicine(id, null), NfcPayload.parse("jpill://tag/$id?other=1"))

    @Test fun timerRoundTripsNameAndLength() {
        val uri = NfcPayload.timerUri(id, "Washer", 45 * 60)
        assertEquals("jpill://timer/$id?label=Washer&secs=2700", uri)
        assertEquals(NfcPayload.Timer(id, "Washer", 2700), NfcPayload.parse(uri))
    }

    @Test fun timerLabelWithSpacesAccentsAndSymbolsSurvives() {
        val label = "Café & Tea (3 min)"
        assertEquals(label, (NfcPayload.parse(NfcPayload.timerUri(id, label, 180)) as NfcPayload.Timer).label)
    }

    @Test fun longLabelIsTrimmed() {
        val p = NfcPayload.parse(NfcPayload.timerUri(id, "x".repeat(200), 60)) as NfcPayload.Timer
        assertEquals(NfcPayload.MAX_LABEL, p.label.length)
    }

    @Test fun rejectsBadTimerData() {
        assertNull(NfcPayload.parse("jpill://timer/$id?label=X&secs=0"))
        assertNull(NfcPayload.parse("jpill://timer/$id?label=X&secs=-5"))
        assertNull(NfcPayload.parse("jpill://timer/$id?label=X&secs=9999999"))
        assertNull(NfcPayload.parse("jpill://timer/$id?label=X"))
        assertNull(NfcPayload.parse("jpill://timer/short?label=X&secs=60"))
        assertNull(NfcPayload.parse("https://example.com/timer/$id?label=X&secs=60"))
    }

    @Test fun timerScanDecisions() {
        val p = NfcPayload.Timer(id, "Washer", 2700)
        fun t(state: TimerState, end: Long? = null, rem: Long? = null) =
            TimerEntity(id = 1, label = "Washer", durationMillis = 2_700_000, state = state, endAtMillis = end, remainingMillis = rem, nfcTagId = id)
        assertTrue(TimerScanResolver.resolve(p, null, 0) is TimerScanDecision.CreateAndStart)
        assertTrue(TimerScanResolver.resolve(p, t(TimerState.FINISHED), 0) is TimerScanDecision.Start)
        assertTrue(TimerScanResolver.resolve(p, t(TimerState.RUNNING, end = 100_000), 200_000) is TimerScanDecision.Start) // overdue
        val running = TimerScanResolver.resolve(p, t(TimerState.RUNNING, end = 100_000), 40_000) as TimerScanDecision.AlreadyRunning
        assertEquals(60_000, running.remainingMillis)
        assertTrue(TimerScanResolver.resolve(p, t(TimerState.PAUSED, rem = 500_000), 0) is TimerScanDecision.Paused)
    }
}
