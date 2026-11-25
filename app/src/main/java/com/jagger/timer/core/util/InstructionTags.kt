package com.jagger.timer.core.util

/**
 * Food and timing notes shown as tags ("With food", "Stay upright 30 min", "No heavy machinery").
 * They are stored in the medicine's notes text, separated by ". ", so nothing in the database changes and notes
 * typed by hand (or from the catalog) show up as tags too. Pure, so it is unit-tested.
 */
object InstructionTags {
    val PRESETS = listOf(
        "With food", "Empty stomach", "Morning", "Evening", "At bedtime",
        "Stay upright 30 min", "Full glass of water", "No heavy machinery",
        "Avoid alcohol", "Avoid grapefruit", "Avoid sun exposure"
    )

    /** Splits notes on sentence breaks, semicolons, commas and new lines into trimmed, de-duplicated tags. */
    fun parse(text: String?): List<String> =
        text.orEmpty().split('.', ';', ',', '\n')
            .map { it.trim().replaceFirstChar { c -> c.uppercase() } }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }

    fun has(text: String?, tag: String): Boolean = parse(text).any { it.equals(tag, ignoreCase = true) }

    /** Adds [tag] if missing, removes it if present. */
    fun toggle(text: String?, tag: String): String {
        val tags = parse(text)
        val next = if (tags.any { it.equals(tag, ignoreCase = true) }) tags.filterNot { it.equals(tag, ignoreCase = true) } else tags + tag
        return next.joinToString(". ")
    }
}
