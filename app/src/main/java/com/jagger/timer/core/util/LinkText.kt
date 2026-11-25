// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.core.util

/** A piece of text that is either plain or a web link (shown as [text], opening [url]). */
sealed interface TextSegment {
    data class Plain(val text: String) : TextSegment
    data class Link(val text: String, val url: String) : TextSegment
}

/**
 * Finds web addresses in free text (a pharmacy field might hold "Walgreens www.walgreens.com/refill") so they can be
 * tappable. Only http and https links are ever produced: an address must start with http(s)://, www., or be a bare domain
 * ending in a well-known suffix, so text like "12.5 mg", "Dr.Smith" or "javascript:alert(1)" is never linked. Pure,
 * so it is unit-tested.
 */
object LinkText {
    private val suffixes = "com|org|net|edu|gov|io|us|co|health|care|pharmacy|app|info|me|ca|uk"
    private val pattern = Regex(
        """(?<![\w@./-])(https?://[^\s<>"]+|www\.[^\s<>"]+|(?:[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?\.)+(?:$suffixes)(?![\w-])(?:[/?#][^\s<>"]*)?)""",
        RegexOption.IGNORE_CASE
    )
    private const val TRAILING = ".,;:!?)]}'\""

    fun segments(text: String): List<TextSegment> {
        val out = mutableListOf<TextSegment>()
        var last = 0
        for (m in pattern.findAll(text)) {
            var raw = m.value
            // Punctuation right after a link belongs to the sentence, not the address.
            while (raw.isNotEmpty() && raw.last() in TRAILING) raw = raw.dropLast(1)
            if (raw.isEmpty()) continue
            val url = normalize(raw) ?: continue
            if (m.range.first > last) out += TextSegment.Plain(text.substring(last, m.range.first))
            out += TextSegment.Link(raw, url)
            last = m.range.first + raw.length
        }
        if (last < text.length) out += TextSegment.Plain(text.substring(last))
        return out.ifEmpty { listOf(TextSegment.Plain(text)) }
    }

    fun hasLink(text: String): Boolean = segments(text).any { it is TextSegment.Link }

    /** http(s) as given, otherwise https; anything with another scheme is rejected. */
    private fun normalize(raw: String): String? {
        val lower = raw.lowercase()
        return when {
            lower.startsWith("http://") || lower.startsWith("https://") -> raw
            "://" in raw.substringBefore('/') -> null
            else -> "https://$raw"
        }
    }
}
