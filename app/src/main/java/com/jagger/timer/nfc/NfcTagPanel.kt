// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.nfc

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

enum class NfcTagKind { MEDICINE, TIMER }

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

/**
 * Write, re-write, link or remove the NFC sticker for one medicine or timer. A medicine tag holds a random id and
 * (switchable) the medicine's name; a timer tag also holds its length. Re-writing a new sticker reuses the same id,
 * so a lost sticker can be replaced without breaking the link.
 *
 * @param tagId the id currently linked (null = none); [onTagChanged] reports a new or cleared id
 * @param name medicine name or timer label written on the tag
 * @param timerSeconds length written on a timer tag
 * @param findOwner name of another medicine or timer that already owns a scanned tag id, or null
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NfcTagPanel(
    kind: NfcTagKind,
    tagId: String?,
    name: String,
    timerSeconds: Long? = null,
    findOwner: suspend (String) -> String?,
    onTagChanged: (String?) -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val io = remember(activity) { activity?.let { NfcTagIo(it) } }
    val scope = rememberCoroutineScope()
    DisposableEffect(io) { onDispose { io?.stop() } }

    if (io == null || !io.isSupported) {
        Text("This phone has no NFC, so tags can't be used.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    var includeName by remember { mutableStateOf(true) }
    var waiting by remember { mutableStateOf<String?>(null) }       // text shown while waiting for a tag
    var overwriteWhat by remember { mutableStateOf<String?>(null) } // set when the tag already holds something
    var pendingUri by remember { mutableStateOf<String?>(null) }
    var pendingId by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }

    fun fail(msg: String) { waiting = null; status = msg; error = true }

    fun startWrite(id: String, uri: String, overwrite: Boolean) {
        pendingId = id
        pendingUri = uri
        status = null
        waiting = "Hold the sticker to the back of your phone to write it."
        io.write(uri, overwrite) { r ->
            when (r) {
                TagWriteResult.Written -> {
                    waiting = null; error = false
                    status = "Tag written. Stick it on and scan to try it."
                    onTagChanged(id)
                }
                is TagWriteResult.NeedsOverwrite -> { waiting = null; overwriteWhat = r.what }
                is TagWriteResult.Failed -> fail(r.reason)
            }
        }
    }

    fun write() {
        val id = tagId ?: NfcTagUri.newId()
        val uri = when (kind) {
            NfcTagKind.MEDICINE -> NfcPayload.medicineUri(id, name.takeIf { includeName })
            NfcTagKind.TIMER -> NfcPayload.timerUri(id, name.ifBlank { "Timer" }, (timerSeconds ?: 60).coerceAtLeast(1))
        }
        startWrite(id, uri, overwrite = false)
    }

    fun link() {
        status = null
        waiting = "Hold the sticker to the back of your phone to link it."
        io.read { r ->
            waiting = null
            when (r) {
                is TagReadResult.Failed -> fail(r.reason)
                is TagReadResult.Read -> when (val p = NfcPayload.parse(r.uri)) {
                    is NfcPayload.Medicine -> scope.launch {
                        val owner = findOwner(p.tagId)
                        if (owner != null && p.tagId != tagId) fail("That tag already belongs to $owner.")
                        else { error = false; status = "Tag linked."; onTagChanged(p.tagId) }
                    }
                    is NfcPayload.Timer -> fail("That is a timer tag. Link it from the Timers screen.")
                    null -> fail("That tag isn't a jPill tag yet. Use Write tag to set it up.")
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("NFC tag", style = MaterialTheme.typography.titleSmall)
        Text(
            when (kind) {
                NfcTagKind.MEDICINE -> "Stick a tag on the lid and tap your phone to it to log a dose."
                NfcTagKind.TIMER -> "Stick a tag where you need it and tap your phone to it to start this timer."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            if (tagId != null) "Tag linked" else "No tag linked",
            style = MaterialTheme.typography.bodyMedium,
            color = if (tagId != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (kind == NfcTagKind.MEDICINE) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = includeName, role = Role.Switch, onValueChange = { includeName = it }),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Write the medicine's name on the tag")
                    Text("Anyone who scans the sticker can read it. Never a dose.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = includeName, onCheckedChange = null)
            }
        }

        if (!io.isEnabled) {
            Text("NFC is switched off on this phone.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                modifier = Modifier.heightIn(min = 48.dp)) { Text("Turn on NFC") }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { write() }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (tagId != null) "Rewrite tag" else "Write tag")
                }
                if (kind == NfcTagKind.MEDICINE) {
                    OutlinedButton(onClick = { link() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Link existing tag") }
                }
                if (tagId != null) {
                    TextButton(onClick = { error = false; status = "Tag removed. The sticker itself still holds its data."; onTagChanged(null) },
                        modifier = Modifier.heightIn(min = 48.dp)) { Text("Remove tag") }
                }
            }
        }
        status?.let {
            Text(it, style = MaterialTheme.typography.bodySmall,
                color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    waiting?.let { text ->
        AlertDialog(
            onDismissRequest = { io.stop(); waiting = null },
            title = { Text("Hold the sticker to your phone") },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { io.stop(); waiting = null }) { Text("Cancel") } }
        )
    }

    overwriteWhat?.let { what ->
        AlertDialog(
            onDismissRequest = { io.stop(); overwriteWhat = null },
            title = { Text("Overwrite this tag?") },
            text = { Text("It already contains $what. Writing replaces it. Hold the same sticker to the phone again after tapping Overwrite.") },
            confirmButton = {
                TextButton(onClick = {
                    overwriteWhat = null
                    val id = pendingId; val uri = pendingUri
                    if (id != null && uri != null) startWrite(id, uri, overwrite = true)
                }) { Text("Overwrite") }
            },
            dismissButton = { TextButton(onClick = { io.stop(); overwriteWhat = null }) { Text("Cancel") } }
        )
    }
}
