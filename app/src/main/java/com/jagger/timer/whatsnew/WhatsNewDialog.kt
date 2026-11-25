// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.whatsnew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/** Shown once after an update: what changed since the version the user last saw. Cleared with "Got it". */
@Composable
fun WhatsNewDialog(releases: List<ReleaseNotes>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = {}, // must be cleared explicitly, so it isn't lost by a stray tap
        title = { Text("What's new in jPill") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                releases.forEach { r ->
                    Text(
                        "Version ${r.version}" + if (r.date.isNotBlank()) "  ·  ${r.date}" else "",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    r.lines.forEach { line ->
                        when (line) {
                            is NoteLine.Heading -> Text(line.text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            is NoteLine.Bullet -> Text(
                                markdown("• " + line.text),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = (12 * line.indent).dp)
                            )
                            is NoteLine.Paragraph -> Text(markdown(line.text), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } }
    )
}

/** Minimal inline markdown: **bold** becomes bold, `code` and [text](url) lose their markup. */
internal fun markdown(text: String): AnnotatedString {
    val cleaned = text.replace(Regex("""\[([^\]]+)]\([^)]*\)"""), "$1").replace("`", "")
    return buildAnnotatedString {
        var i = 0
        while (i < cleaned.length) {
            val open = cleaned.indexOf("**", i)
            val close = if (open >= 0) cleaned.indexOf("**", open + 2) else -1
            if (open < 0 || close < 0) { append(cleaned.substring(i)); break }
            append(cleaned.substring(i, open))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(cleaned.substring(open + 2, close)) }
            i = close + 2
        }
    }
}
