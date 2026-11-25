// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.settings.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** The full GNU GPL v3 text, bundled as an asset (the same file as LICENSE in the repository). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val paragraphs = remember {
        runCatching { context.assets.open("LICENSE.txt").bufferedReader().use { it.readText() } }
            .getOrDefault("The license text could not be loaded. It is available at https://www.gnu.org/licenses/gpl-3.0.txt")
            .let(::toParagraphs)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GNU General Public License v3") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp)) {
            itemsIndexed(paragraphs) { _, p ->
                Text(p, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 10.dp))
            }
        }
    }
}

/** The license file is hard-wrapped at about 70 columns; re-flow each blank-line-separated paragraph to the screen width. */
internal fun toParagraphs(text: String): List<String> =
    text.replace("\r", "").split(Regex("\n\\s*\n"))
        .map { block -> block.lines().joinToString(" ") { it.trim() }.replace(Regex("\\s+"), " ").trim() }
        .filter { it.isNotEmpty() }
