// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.reliability

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** Re-checks the phone's settings every time the screen comes back to the front, e.g. after the user fixed something. */
@Composable
fun RefreshOnResume(viewModel: ReliabilityViewModel) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReliabilityScreen(onNavigateBack: () -> Unit, viewModel: ReliabilityViewModel = hiltViewModel()) {
    RefreshOnResume(viewModel)
    val health by viewModel.health.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reminder reliability") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                if (health.allGood) "Everything jPill can check is set up, so reminders should arrive on time."
                else "Phones often delay or hide reminders to save battery. Fix the items below so yours arrive on time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            CheckRow(
                ok = health.notificationsAllowed, title = "Notifications",
                okText = "jPill can show reminders.",
                badText = "Notifications are off for jPill, so reminders can't appear at all.",
                action = "Open settings", onFix = { viewModel.fix(ReminderIssue.NOTIFICATIONS) }
            )
            CheckRow(
                ok = health.batteryUnrestricted, title = "Battery use",
                okText = "Unrestricted: Android won't delay reminders to save power.",
                badText = "Android may delay reminders to save battery. Allow jPill to run unrestricted.",
                action = "Allow", onFix = { viewModel.fix(ReminderIssue.BATTERY) }
            )
            CheckRow(
                ok = health.fullScreenAllowed, title = "Full-screen reminders",
                okText = "Urgent reminders can open over the lock screen.",
                badText = "Reminders will only appear as a notification, not over the lock screen.",
                action = "Allow", onFix = { viewModel.fix(ReminderIssue.FULL_SCREEN) }
            )
            if (!health.exactAlarmsAllowed) CheckRow(
                ok = false, title = "Exact alarms", okText = "",
                badText = "Android isn't letting jPill schedule exact alarms, so reminders can be late.",
                action = "Allow", onFix = { viewModel.fix(ReminderIssue.EXACT_ALARMS) }
            )
            if (viewModel.isSamsung) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Samsung phones", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Samsung also has \"Sleeping apps\" and \"Deep sleeping apps\" lists that can stop alarms. Android doesn't let jPill " +
                                "check or change them, so look yourself: Settings > Battery > Background usage limits, and make sure jPill is not on either list.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedButton(onClick = { viewModel.openAppSettings() }, Modifier.heightIn(min = 48.dp)) { Text("Open jPill's settings") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckRow(ok: Boolean, title: String, okText: String, badText: String, action: String, onFix: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                if (ok) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = if (ok) "OK" else "Needs attention",
                tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(if (ok) okText else badText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!ok) Button(onClick = onFix, Modifier.padding(top = 6.dp).heightIn(min = 48.dp)) { Text(action) }
            }
        }
    }
}
