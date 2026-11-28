// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.nfc

import android.Manifest
import android.app.KeyguardManager
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.jagger.timer.MainActivity
import com.jagger.timer.core.data.local.entity.PillEntity
import com.jagger.timer.core.settings.AppSettings
import com.jagger.timer.core.util.CooldownStatus
import com.jagger.timer.core.util.IngredientLogic
import com.jagger.timer.core.util.RxFormLogic
import com.jagger.timer.core.util.TimerLogic
import com.jagger.timer.ui.theme.JPillTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Opened by Android when a jPill NFC sticker is scanned. Shows a small confirm card (never the rest of the app, so
 * it works while the app lock is on). A dose is only logged after the user confirms; a timer tag is scan-and-go and starts at once.
 */
@AndroidEntryPoint
class NfcScanActivity : ComponentActivity() {
    private val viewModel: NfcScanViewModel by viewModels()
    @Inject lateinit var appSettings: AppSettings
    private val notificationPermission = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        if (savedInstanceState == null) viewModel.handle(intent?.dataString, appSettings.nfcBetaEnabled)
        setContent {
            JPillTheme {
                val ui by viewModel.ui.collectAsState()
                // The medicine name stays off a locked screen when the user chose to hide names there.
                val hideName = remember { appSettings.hideNamesOnLockScreen && (getSystemService(KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked }
                val none = remember { MutableInteractionSource() }
                Box(
                    Modifier.fillMaxSize().clickable(interactionSource = none, indication = null) { finish() },
                    contentAlignment = Alignment.Center
                ) {
                    Card(Modifier.padding(24.dp).fillMaxWidth().clickable(interactionSource = none, indication = null) { }) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ScanCard(ui, hideName, onClose = { finish() }, onAddNew = { id, name -> openAddMedicine(id, name) })
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.handle(intent.dataString, appSettings.nfcBetaEnabled)
    }

    private fun openAddMedicine(tagId: String, name: String?) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_PREFILL_TAG, tagId)
                .putExtra(MainActivity.EXTRA_PREFILL_NAME, name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finish()
    }

    @Composable
    private fun ScanCard(ui: ScanUi, hideName: Boolean, onClose: () -> Unit, onAddNew: (String, String?) -> Unit) {
        when (ui) {
            ScanUi.Loading -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
            is ScanUi.Invalid -> { Title(ui.message); CloseRow(onClose) }
            ScanUi.BetaOff -> {
                Title("Medicine tags are switched off")
                Body("Medicine tags are a beta feature. Turn them on in jPill under Settings, Developer tools, Beta features. Timer tags work without it.")
                CloseRow(onClose)
            }
            is ScanUi.Done -> {
                Title(ui.message)
                // A phone that has never opened jPill hasn't allowed notifications yet; without them the timer
                // would finish silently, so ask right here, then close.
                val needsPermission = ui.timerStarted && ContextCompat.checkSelfPermission(
                    this@NfcScanActivity, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
                if (needsPermission) Body("Allow notifications so jPill can tell you when it's done.")
                LaunchedEffect(ui) {
                    if (needsPermission) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else { delay(1200); finish() }
                }
            }
            is ScanUi.UnknownMedicine -> {
                Title("Tag not linked here")
                Body(
                    (if (ui.name != null && !hideName) "This tag is for ${ui.name}, but it" else "This tag") +
                        " isn't linked to a medicine in this jPill. You can add it as a new medicine and link the tag."
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onAddNew(ui.tagId, ui.name) }, Modifier.heightIn(min = 48.dp)) { Text("Add as new medicine") }
                    TextButton(onClick = onClose, Modifier.heightIn(min = 48.dp)) { Text("Close") }
                }
            }
            is ScanUi.Medicine -> MedicineCard(ui.decision, hideName, onClose)
            is ScanUi.Timer -> TimerCard(ui.decision, onClose)
        }
    }

    @Composable
    private fun MedicineCard(d: ScanDecision, hideName: Boolean, onClose: () -> Unit) {
        fun name(p: PillEntity) = if (hideName) "your medicine" else p.name
        val clock = DateTimeFormatter.ofPattern("h:mm a")
        fun at(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(clock)
        when (d) {
            ScanDecision.UnknownTag -> { Title("Tag not linked"); CloseRow(onClose) }
            is ScanDecision.Archived -> {
                Title("${name(d.pill)} is archived"); Body("Restore it in jPill to log doses."); CloseRow(onClose)
            }
            is ScanDecision.ScheduledDue -> {
                var chosen by remember { mutableStateOf(d.options.first()) }
                Title("Log ${name(d.pill)}?")
                Body(RxFormLogic.doseLabel(d.pill))
                if (d.options.size == 1) {
                    Body(if (chosen.late) "Was due at ${at(chosen.dueAtMillis)}" else "Due at ${at(chosen.dueAtMillis)}")
                } else {
                    Column(Modifier.selectableGroup()) {
                        d.options.forEach { o ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .selectable(selected = chosen == o, role = Role.RadioButton, onClick = { chosen = o }),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = chosen == o, onClick = null)
                                Text("${if (o.late) "Was due" else "Due"} at ${at(o.dueAtMillis)}", Modifier.padding(start = 12.dp))
                            }
                        }
                    }
                }
                ConfirmRow("Log dose", onClose) { viewModel.logDose(d.pill, chosen.scheduleId) {} }
            }
            is ScanDecision.ScheduledEarly -> {
                Title("Not due yet"); Body("${name(d.pill)} is next due at ${at(d.nextDueMillis)}. Log an early dose?")
                ConfirmRow("Log early dose", onClose) { viewModel.logDose(d.pill, d.scheduleId) {} }
            }
            is ScanDecision.AlreadyTaken -> {
                Title("Already logged")
                Body("${name(d.pill)} was logged at ${at(d.atMillis)}." + (d.nextDueMillis?.let { " Next dose at ${at(it)}." } ?: ""))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { viewModel.logDose(d.pill, d.scheduleId) {} }, Modifier.heightIn(min = 48.dp)) { Text("Log another anyway") }
                    TextButton(onClick = onClose, Modifier.heightIn(min = 48.dp)) { Text("Close") }
                }
            }
            is ScanDecision.AsNeededReady -> {
                Title("Log ${name(d.pill)}?"); Body(RxFormLogic.doseLabel(d.pill))
                ConfirmRow("Log dose", onClose) { viewModel.logDose(d.pill, null) {} }
            }
            is ScanDecision.AsNeededEarly -> {
                Title("Take it early?")
                val why = buildString {
                    d.sharedWarning?.let { append(IngredientLogic.message(it)).append(' ') }
                    when (d.status) {
                        CooldownStatus.WAITING -> append("The next dose isn't due until ${d.nextAtMillis?.let { at(it) } ?: "later"}. ")
                        CooldownStatus.AT_DAILY_MAX -> append("The daily maximum is reached; the next dose is at ${d.nextAtMillis?.let { at(it) } ?: "later"}. ")
                        CooldownStatus.READY -> {}
                    }
                    append("Check your label before taking more.")
                }
                Body("${name(d.pill)}: $why")
                ConfirmRow("Log it anyway", onClose) { viewModel.logDose(d.pill, null) {} }
            }
        }
    }

    @Composable
    private fun TimerCard(d: TimerScanDecision, onClose: () -> Unit) {
        when (d) {
            // New and idle timers start straight away (scan-and-go), so they never reach this card.
            is TimerScanDecision.CreateAndStart, is TimerScanDecision.Start -> Unit
            is TimerScanDecision.AlreadyRunning -> {
                Title("${d.timer.label.ifBlank { "Timer" }} is running")
                Body("${TimerLogic.format(d.remainingMillis)} left")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { viewModel.restartTimer(d.timer.id, d.timer.label) }, Modifier.heightIn(min = 48.dp)) { Text("Restart") }
                    TextButton(onClick = onClose, Modifier.heightIn(min = 48.dp)) { Text("Leave it") }
                }
            }
            is TimerScanDecision.Paused -> {
                Title("${d.timer.label.ifBlank { "Timer" }} is paused")
                Body("${TimerLogic.format(d.remainingMillis)} left")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.resumeTimer(d.timer.id, d.timer.label) }, Modifier.heightIn(min = 48.dp)) { Text("Resume") }
                    FilledTonalButton(onClick = { viewModel.restartTimer(d.timer.id, d.timer.label) }, Modifier.heightIn(min = 48.dp)) { Text("Restart") }
                    TextButton(onClick = onClose, Modifier.heightIn(min = 48.dp)) { Text("Close") }
                }
            }
        }
    }

    @Composable private fun Title(text: String) = Text(text, style = MaterialTheme.typography.titleLarge)
    @Composable private fun Body(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

    @Composable
    private fun CloseRow(onClose: () -> Unit) = TextButton(onClick = onClose, Modifier.heightIn(min = 48.dp)) { Text("Close") }

    @Composable
    private fun ConfirmRow(label: String, onClose: () -> Unit, onConfirm: () -> Unit) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onConfirm, Modifier.heightIn(min = 48.dp)) { Text(label) }
            TextButton(onClick = onClose, Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
        }
    }
}
