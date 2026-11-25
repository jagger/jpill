package com.jagger.timer.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstructionTagsTest {
    @Test fun parsesSentencesAndCommas() =
        assertEquals(listOf("With food", "Morning", "No heavy machinery"), InstructionTags.parse("With food. Morning, no heavy machinery"))

    @Test fun parseIgnoresBlanksAndDuplicates() =
        assertEquals(listOf("With food"), InstructionTags.parse(" with food;; With Food. "))

    @Test fun nullAndEmptyHaveNoTags() { assertTrue(InstructionTags.parse(null).isEmpty()); assertTrue(InstructionTags.parse("").isEmpty()) }

    @Test fun toggleAddsThenRemoves() {
        val added = InstructionTags.toggle("With food", "Morning")
        assertEquals("With food. Morning", added)
        assertEquals("With food", InstructionTags.toggle(added, "morning"))
    }

    @Test fun hasIsCaseInsensitive() { assertTrue(InstructionTags.has("Stay upright 30 min", "stay upright 30 MIN")); assertFalse(InstructionTags.has("", "x")) }
}
