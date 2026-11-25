// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jagger.timer.settings.viewmodel.SettingsViewModel

/** Features that work but have not had enough real-world testing yet. Each is off until you switch it on. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BetaScreen(onNavigateBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val nfc by viewModel.settings.nfcBetaFlow.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Beta features") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "These features are still being tested with real phones and tags. They are off by default. " +
                    "Keep checking your History for anything that matters.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                Modifier.fillMaxWidth().heightIn(min = 72.dp)
                    .toggleable(value = nfc, role = Role.Switch, onValueChange = { viewModel.settings.nfcBetaEnabled = it }),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text("NFC tags", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Stick a tag on a medicine lid and tap your phone to confirm a dose, or on a washer to start its timer. " +
                            "Adds an NFC section to each medicine and an NFC button on timers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = nfc, onCheckedChange = null)
            }
        }
    }
}
