// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.settings

import com.jagger.timer.settings.ui.toParagraphs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LicenseTextTest {
    @Test fun reflowsHardWrappedParagraphs() =
        assertEquals(listOf("one two three", "four"), toParagraphs("one\n   two\nthree\n\n\nfour\n"))

    /** LICENSE (repo root) and the bundled asset must be the same GPL text, so the app shows what the repo ships. */
    @Test fun bundledAssetMatchesRepoLicense() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "LICENSE").exists() }
        val repo = File(root, "LICENSE").readText()
        val asset = File(root, "app/src/main/assets/LICENSE.txt").readText()
        assertEquals(repo, asset)
        assertTrue(repo.contains("GNU GENERAL PUBLIC LICENSE") && repo.contains("Version 3, 29 June 2007"))
    }
}
