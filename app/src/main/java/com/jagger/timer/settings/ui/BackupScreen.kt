// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.settings.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jagger.timer.backup.BackupCrypto
import com.jagger.timer.backup.BackupUi
import com.jagger.timer.backup.BackupViewModel

/** Settings > Backup and restore: an encrypted file the user keeps themselves. Nothing is uploaded by jPill. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(onNavigateBack: () -> Unit, viewModel: BackupViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsState()
    // Passphrases are held only in memory (never saved with the screen state).
    var askExportPassphrase by remember { mutableStateOf(false) }
    var exportPassphrase by remember { mutableStateOf<CharArray?>(null) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }

    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val pass = exportPassphrase
        exportPassphrase = null
        if (uri != null && pass != null) viewModel.export(uri, pass)
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoreUri = uri }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup and restore") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(
                "jPill never uploads your data, and Android's cloud backup is turned off for it. To keep a copy, make an " +
                    "encrypted backup file and store it wherever you like (a cloud drive is fine: the file is unreadable without your passphrase).",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "The passphrase is the only key. If you forget it, nobody, including the developer, can open the backup. " +
                    "Use at least 8 characters; a short PIN is easy to guess if someone gets the file.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
            Button(
                onClick = { askExportPassphrase = true }, enabled = ui !is BackupUi.Working,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 20.dp)
            ) { Text("Create encrypted backup") }
            OutlinedButton(
                onClick = { pickFile.launch(arrayOf("*/*")) }, enabled = ui !is BackupUi.Working,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp)
            ) { Text("Restore from backup") }
            if (ui is BackupUi.Working) CircularProgressIndicator(Modifier.padding(top = 24.dp))
        }
    }

    if (askExportPassphrase) NewPassphraseDialog(
        onDismiss = { askExportPassphrase = false },
        onConfirm = { pass ->
            askExportPassphrase = false
            exportPassphrase = pass
            createFile.launch(viewModel.suggestedName())
        }
    )
    restoreUri?.let { uri ->
        RestorePassphraseDialog(
            onDismiss = { restoreUri = null },
            onConfirm = { pass -> restoreUri = null; viewModel.restore(uri, pass) }
        )
    }
    when (val s = ui) {
        is BackupUi.Message -> AlertDialog(
            onDismissRequest = viewModel::dismissMessage,
            title = { Text(if (s.isError) "That didn't work" else "Done") },
            text = { Text(s.text) },
            confirmButton = { TextButton(onClick = viewModel::dismissMessage) { Text("OK") } }
        )
        BackupUi.RestoredRestartNeeded -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Backup restored") },
            text = { Text("jPill needs to close to load your restored data. Open it again afterwards; your reminders are re-armed automatically.") },
            confirmButton = { TextButton(onClick = { android.os.Process.killProcess(android.os.Process.myPid()) }) { Text("Close jPill") } }
        )
        else -> {}
    }
}

private val passwordKeyboard = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false)

@Composable
private fun NewPassphraseDialog(onDismiss: () -> Unit, onConfirm: (CharArray) -> Unit) {
    var pass by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val long = pass.length >= BackupCrypto.MIN_PASSPHRASE
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a passphrase") },
        text = {
            Column {
                Text("You will need it to restore this backup. It can't be recovered if you forget it.")
                OutlinedTextField(
                    pass, { pass = it }, singleLine = true, label = { Text("Passphrase (8+ characters)") },
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = passwordKeyboard,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
                OutlinedTextField(
                    again, { again = it }, singleLine = true, label = { Text("Repeat passphrase") },
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = passwordKeyboard,
                    isError = again.isNotEmpty() && again != pass,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(pass.toCharArray()) }, enabled = long && pass == again) { Text("Choose location") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun RestorePassphraseDialog(onDismiss: () -> Unit, onConfirm: (CharArray) -> Unit) {
    var pass by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore backup?") },
        text = {
            Column {
                Text("This replaces ALL medicines, dose history and timers on this phone with the ones in the backup. A copy of your current data is kept in case something goes wrong.")
                OutlinedTextField(
                    pass, { pass = it }, singleLine = true, label = { Text("Backup passphrase") },
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = passwordKeyboard,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(pass.toCharArray()) }, enabled = pass.isNotEmpty(),
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Replace my data") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
