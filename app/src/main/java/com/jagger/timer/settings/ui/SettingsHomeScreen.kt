// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** The Settings hub: one row per group of options. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHomeScreen(onNavigateBack: () -> Unit, onAppearance: () -> Unit, onPrivacy: () -> Unit, onReliability: () -> Unit, onBackup: () -> Unit, onDeveloper: () -> Unit, onAbout: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SettingsRow(Icons.Default.Palette, "Appearance", "Light, dark or follow your phone", onAppearance)
            SettingsRow(Icons.Default.Lock, "Privacy and data", "App lock, lock-screen names, delete all data", onPrivacy)
            SettingsRow(Icons.Default.NotificationsActive, "Reminder reliability", "Check that notifications, battery and alarms let reminders arrive", onReliability)
            SettingsRow(Icons.Default.Backup, "Backup and restore", "Encrypted backup file you keep yourself", onBackup)
            SettingsRow(Icons.Default.Build, "Developer tools", "Reminder log and beta features", onDeveloper)
            SettingsRow(Icons.Default.Info, "About", "Version, project page, license, support jPill", onAbout)
        }
    }
}

@Composable
internal fun SettingsRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
