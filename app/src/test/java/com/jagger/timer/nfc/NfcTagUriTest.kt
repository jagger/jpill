// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NfcTagUriTest {
    @Test fun newIdIsValidUrlSafeAnd22Chars() {
        val id = NfcTagUri.newId()
        assertEquals(22, id.length)
        assertTrue(NfcTagUri.isValidId(id))
    }

    @Test fun idsDiffer() = assertNotEquals(NfcTagUri.newId(), NfcTagUri.newId())

    @Test fun roundTrips() {
        val id = NfcTagUri.newId()
        assertEquals(id, NfcTagUri.parse(NfcTagUri.toUri(id)))
    }

    @Test fun rejectsOtherUrisAndSchemes() {
        assertNull(NfcTagUri.parse("https://example.com/tag/AAAAAAAAAAAAAAAAAAAAAA"))
        assertNull(NfcTagUri.parse("jpill://other/AAAAAAAAAAAAAAAAAAAAAA"))
        assertNull(NfcTagUri.parse("jpill://tag/short"))
        assertNull(NfcTagUri.parse("jpill://tag/AAAAAAAAAAAAAAAAAAAAAA/extra"))
        assertNull(NfcTagUri.parse(null))
    }

    @Test fun rejectsBadIds() {
        assertFalse(NfcTagUri.isValidId("has space......."))
        assertFalse(NfcTagUri.isValidId(null))
    }
}
