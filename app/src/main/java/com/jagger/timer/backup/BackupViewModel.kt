// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

sealed interface BackupUi {
    data object Idle : BackupUi
    data object Working : BackupUi
    data class Message(val text: String, val isError: Boolean) : BackupUi
    /** Data was replaced; the process must restart before the app is usable again. */
    data object RestoredRestartNeeded : BackupUi
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val manager: BackupManager
) : ViewModel() {
    private val _ui = MutableStateFlow<BackupUi>(BackupUi.Idle)
    val ui: StateFlow<BackupUi> = _ui.asStateFlow()

    fun suggestedName(): String = "jpill-backup-${LocalDate.now()}.jpillbackup"

    fun export(uri: Uri, passphrase: CharArray) {
        _ui.value = BackupUi.Working
        viewModelScope.launch {
            _ui.value = try {
                val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("Couldn't open the file.")
                out.use { manager.export(it, passphrase) }
                BackupUi.Message("Backup saved. Keep the passphrase safe: without it the file can't be opened.", false)
            } catch (e: Exception) {
                BackupUi.Message("Couldn't save the backup: ${e.message ?: "unknown error"}", true)
            }
        }
    }

    fun restore(uri: Uri, passphrase: CharArray) {
        _ui.value = BackupUi.Working
        viewModelScope.launch {
            _ui.value = try {
                val input = context.contentResolver.openInputStream(uri) ?: error("Couldn't open the file.")
                input.use { manager.restore(it, passphrase) }
                BackupUi.RestoredRestartNeeded
            } catch (e: BackupException) {
                BackupUi.Message(e.message ?: "That backup can't be used.", true)
            } catch (e: Exception) {
                BackupUi.Message("Couldn't restore: ${e.message ?: "unknown error"}. Your current data was not changed.", true)
            }
        }
    }

    fun dismissMessage() { _ui.value = BackupUi.Idle }
}
