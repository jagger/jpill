// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.whatsnew

/** One released version's notes, taken from CHANGELOG.md. */
data class ReleaseNotes(val version: String, val code: Int, val date: String, val lines: List<NoteLine>)

/** A rendered-ready line of a release's notes. [indent] is 0 for top-level bullets, 1 for nested ones. */
sealed interface NoteLine {
    data class Heading(val text: String) : NoteLine
    data class Bullet(val text: String, val indent: Int) : NoteLine
    data class Paragraph(val text: String) : NoteLine
}

/** Pure parsing and selection for the "What's new" popup, so it is unit-tested. */
object WhatsNew {
    /** Installs from before this feature existed were at most this version (0.9.2). */
    const val LEGACY_VERSION_CODE = 902

    private val releaseHeader = Regex("""^## \[(\d+)\.(\d+)\.(\d+)]\s*(?:-\s*(.+))?$""")

    fun versionCode(major: Int, minor: Int, patch: Int) = major * 10_000 + minor * 100 + patch

    /** Parses Keep-a-Changelog text into releases, newest first as written. `[Unreleased]` is skipped. */
    fun parse(changelog: String): List<ReleaseNotes> {
        val out = mutableListOf<ReleaseNotes>()
        var header: MatchResult? = null
        val body = mutableListOf<String>()
        fun flush() {
            val h = header ?: return
            val (maj, min, pat) = h.groupValues.slice(1..3).map { it.toInt() }
            out += ReleaseNotes("$maj.$min.$pat", versionCode(maj, min, pat), h.groupValues[4].trim(), body.mapNotNull(::toLine))
        }
        for (raw in changelog.lines()) {
            if (raw.startsWith("## ")) {
                flush(); body.clear()
                header = releaseHeader.matchEntire(raw.trim())
            } else if (header != null && !raw.startsWith("[")) {
                body += raw
            }
        }
        flush()
        return out
    }

    private fun toLine(raw: String): NoteLine? {
        if (raw.isBlank()) return null
        val trimmed = raw.trimStart()
        return when {
            trimmed.startsWith("### ") -> NoteLine.Heading(trimmed.removePrefix("### ").trim())
            trimmed.startsWith("- ") -> NoteLine.Bullet(trimmed.removePrefix("- ").trim(), if (raw.length - trimmed.length >= 2) 1 else 0)
            else -> NoteLine.Paragraph(trimmed.trim())
        }
    }

    /**
     * What to show now. [lastSeen] is the stored version code (0 = none). A fresh install shows nothing; an install
     * that predates this feature is treated as coming from [LEGACY_VERSION_CODE]. Otherwise: every release newer than
     * what the user last saw, up to the running version, newest first.
     */
    fun pending(releases: List<ReleaseNotes>, lastSeen: Int, current: Int, isFreshInstall: Boolean): List<ReleaseNotes> {
        val from = when {
            lastSeen > 0 -> lastSeen
            isFreshInstall -> return emptyList()
            else -> LEGACY_VERSION_CODE
        }
        return releases.filter { it.code in (from + 1)..current && it.lines.isNotEmpty() }.sortedByDescending { it.code }
    }
}
