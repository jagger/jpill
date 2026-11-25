// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.settings.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

object ProjectLinks {
    const val PROJECT = "https://github.com/jagger/jpill"
    const val ISSUES = "$PROJECT/issues"
    const val KOFI = "https://ko-fi.com/I0X62B9ETA"
}

/** Opens [url] in the browser; does nothing if the phone has no browser. */
internal fun openLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
    }
}

/** About jPill: version, link to the project, the GPL notice, donation link and the medical disclaimer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onNavigateBack: () -> Unit, onLicense: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text("jPill", style = MaterialTheme.typography.headlineSmall)
            if (version.isNotEmpty()) Text("Version $version", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "A private medication reminder and timer. Everything stays on your phone: no account, no ads, no network access.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp)
            )

            Heading("The project")
            Body("jPill is open source. Browse the code, read the changelog, suggest a feature or report a problem on GitHub.")
            Button(onClick = { openLink(context, ProjectLinks.PROJECT) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp)) {
                Text("View the project on GitHub")
            }
            OutlinedButton(onClick = { openLink(context, ProjectLinks.ISSUES) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp)) {
                Text("Report a problem or suggest something")
            }

            Heading("Support jPill")
            Body("jPill is free. If it helps you and you would like to say thanks, you can chip in on Ko-fi. It is entirely optional.")
            FilledTonalButton(onClick = { openLink(context, ProjectLinks.KOFI) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp)) {
                Text("Support jPill on Ko-fi")
            }

            Heading("License")
            Body(
                "jPill is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License " +
                    "as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.\n\n" +
                    "jPill is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of " +
                    "MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details."
            )
            OutlinedButton(onClick = onLicense, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp)) {
                Text("Read the full license")
            }

            Heading("Medical disclaimer")
            Body(
                "jPill is a personal organiser, not a medical device, and it does not give medical advice. Dose limits and the prescription " +
                    "catalog are informational and have not all been checked against current labels. Always follow your medicine's label and " +
                    "your prescriber or pharmacist."
            )

            Heading("Built with")
            Body(
                "AndroidX, Jetpack Compose, Room, Hilt, Navigation, Biometric and Glance (Apache License 2.0). " +
                    "Prescription names come from the openFDA National Drug Code directory (CC0, public domain)."
            )
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp, bottom = 4.dp))
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
