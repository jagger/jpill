// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Settings > Developer tools: things for troubleshooting and trying unfinished features, kept out of the main list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperToolsScreen(onNavigateBack: () -> Unit, onReminderLog: () -> Unit, onBeta: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Developer tools") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SettingsRow(Icons.Default.NotificationsActive, "Reminder log", "What is armed, what fired, and why a reminder may have been missed", onReminderLog)
            SettingsRow(Icons.Default.Science, "Beta features", "Try new features before they are finished (NFC medicine tags)", onBeta)
        }
    }
}
