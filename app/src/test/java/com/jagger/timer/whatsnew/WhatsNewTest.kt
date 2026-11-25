// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.whatsnew

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WhatsNewTest {
    private val sample = """
# Changelog

## [Unreleased]

### Added
- not shipped yet

## [0.9.2] - 2026-11-25

### Fixed
- **Reminders** came back.
  - nested detail

## [0.9.1] - 2026-11-24

### Added
- Links.

## [0.9.0] - 2026-11-23

### Added
- Banner.

[Unreleased]: https://example.com/compare
[0.9.2]: https://example.com/x
""".trimIndent()

    private val releases = WhatsNew.parse(sample)

    @Test fun parse_skipsUnreleasedAndLinkRefs() {
        assertEquals(listOf("0.9.2", "0.9.1", "0.9.0"), releases.map { it.version })
        assertEquals(902, releases[0].code)
        assertEquals("2026-11-25", releases[0].date)
    }

    @Test fun parse_keepsHeadingsBulletsAndNesting() {
        val lines = releases[0].lines
        assertEquals(NoteLine.Heading("Fixed"), lines[0])
        assertEquals(NoteLine.Bullet("**Reminders** came back.", 0), lines[1])
        assertEquals(NoteLine.Bullet("nested detail", 1), lines[2])
        assertTrue(lines.none { it.toString().contains("example.com") })
    }

    @Test fun pending_showsEverythingSinceLastSeenUpToCurrent_newestFirst() {
        val p = WhatsNew.pending(releases, lastSeen = 900, current = 902, isFreshInstall = false)
        assertEquals(listOf("0.9.2", "0.9.1"), p.map { it.version })
    }

    @Test fun pending_nothingWhenUpToDate() =
        assertTrue(WhatsNew.pending(releases, lastSeen = 902, current = 902, isFreshInstall = false).isEmpty())

    @Test fun pending_freshInstallShowsNothing() =
        assertTrue(WhatsNew.pending(releases, lastSeen = 0, current = 902, isFreshInstall = true).isEmpty())

    @Test fun pending_preFeatureInstallIsTreatedAsComingFrom092() {
        val withNew = releases + ReleaseNotes("0.10.0", 1000, "d", listOf(NoteLine.Bullet("x", 0)))
        val p = WhatsNew.pending(withNew, lastSeen = 0, current = 1000, isFreshInstall = false)
        assertEquals(listOf("0.10.0"), p.map { it.version })
    }

    @Test fun pending_ignoresReleasesNewerThanTheRunningVersion() =
        assertTrue(WhatsNew.pending(releases, lastSeen = 900, current = 900, isFreshInstall = false).isEmpty())

    @Test fun realChangelogParsesEveryVersionWithNotes() {
        val text = listOf("../CHANGELOG.md", "CHANGELOG.md").map(::File).first { it.exists() }.readText()
        val all = WhatsNew.parse(text)
        assertTrue(all.size >= 10)
        assertTrue(all.all { it.lines.isNotEmpty() })
    }

    @Test fun markdown_boldAndCodeAndLinks() {
        assertEquals("a bold b c d", markdown("a **bold** b `c` [d](http://x)").text)
    }
}
