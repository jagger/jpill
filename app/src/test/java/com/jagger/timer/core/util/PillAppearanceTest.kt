package com.jagger.timer.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PillAppearanceTest {
    @Test fun unknownOrNullIconFallsBackToPill() {
        val pill = PillAppearance.icon("medication")
        assertSame(pill, PillAppearance.icon(null))
        assertSame(pill, PillAppearance.icon("no-such-icon"))
    }

    @Test fun everyStoredIconNameResolvesToItself() {
        PillAppearance.icons.forEach { (name, v) -> assertSame(v, PillAppearance.icon(name)) }
    }

    @Test fun presetUnitsAreOfferedAsIs() {
        assertEquals(PillAppearance.units, PillAppearance.unitOptions("mg"))
        assertEquals(PillAppearance.units, PillAppearance.unitOptions(""))
    }

    @Test fun catalogUnitOutsidePresetsStillShows() {
        val opts = PillAppearance.unitOptions("g")
        assertTrue("g" in opts)
        assertEquals("other", opts.last())
        assertEquals(PillAppearance.units.size + 1, opts.size)
    }

    @Test fun colorsAreDistinctAndOpaque() {
        assertEquals(PillAppearance.colors.size, PillAppearance.colors.toSet().size)
        assertTrue(PillAppearance.colors.all { (it ushr 24) == 0xFF })
    }
}
