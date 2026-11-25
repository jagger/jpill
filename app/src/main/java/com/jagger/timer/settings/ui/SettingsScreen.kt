package com.jagger.timer.settings.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jagger.timer.core.settings.AppSettings
import com.jagger.timer.settings.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onNavigateBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings = viewModel.settings
    val lockEnabled by settings.lockEnabledFlow.collectAsState()
    val timeout by settings.lockTimeoutFlow.collectAsState()
    val hideNames by settings.hideNamesFlow.collectAsState()
    val context = LocalContext.current
    var lockProblem by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            SwitchRow(
                title = "Lock the app",
                subtitle = "Ask for your fingerprint, face, or phone PIN when jPill opens.",
                checked = lockEnabled,
                onChange = { on ->
                    if (!on) {
                        settings.lockEnabled = false; lockProblem = null
                    } else if (BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) ==
                        BiometricManager.BIOMETRIC_SUCCESS
                    ) {
                        settings.lockEnabled = true; lockProblem = null
                    } else {
                        lockProblem = "Set up a screen lock or fingerprint in your phone's settings first."
                    }
                }
            )
            lockProblem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            if (lockEnabled) {
                var open by remember { mutableStateOf(false) }
                val label = AppSettings.TIMEOUT_CHOICES.firstOrNull { it.first == timeout }?.second ?: "After ${timeout}s"
                Column(Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 12.dp)) {
                    Text("Lock timing", style = MaterialTheme.typography.titleMedium)
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        AppSettings.TIMEOUT_CHOICES.forEach { (secs, text) ->
                            DropdownMenuItem(text = { Text(text) }, onClick = { settings.lockTimeoutSeconds = secs; open = false })
                        }
                    }
                }
            }

            SwitchRow(
                title = "Hide medicine names on the lock screen",
                subtitle = "Notifications say \"Medication reminder\" while the phone is locked. Taken and Skip still work.",
                checked = hideNames,
                onChange = { settings.hideNamesOnLockScreen = it }
            )

            Text("About your data", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp))
            Text(
                "The lock only guards the screen. Reminders and the Taken/Skip buttons keep working while jPill is locked. " +
                    "Your data is not encrypted on the phone beyond Android's own storage protection, because background " +
                    "reminders can't use a key that needs your fingerprint, and keys don't survive restoring a backup.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
