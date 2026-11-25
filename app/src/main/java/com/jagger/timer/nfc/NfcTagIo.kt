// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.nfc

import android.app.Activity
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Handler
import android.os.Looper

sealed interface TagWriteResult {
    data object Written : TagWriteResult
    /** The tag already holds something that is not this jPill tag: ask before overwriting. */
    data class NeedsOverwrite(val what: String) : TagWriteResult
    data class Failed(val reason: String) : TagWriteResult
}

sealed interface TagReadResult {
    /** The `jpill://` URI on the tag, or null if it holds nothing we recognise. */
    data class Read(val uri: String?) : TagReadResult
    data class Failed(val reason: String) : TagReadResult
}

/**
 * Reads and writes NDEF URI tags using NFC reader mode while the user holds the phone to a sticker. Android only
 * lets the foreground [activity] do this. Results arrive on the main thread. Call [stop] when done or cancelled.
 */
class NfcTagIo(private val activity: Activity) {
    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)
    private val main = Handler(Looper.getMainLooper())

    val isSupported: Boolean get() = adapter != null
    val isEnabled: Boolean get() = adapter?.isEnabled == true

    private val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
        NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK

    private var pendingWrite: ((TagWriteResult) -> Unit)? = null
    private var pendingRead: ((TagReadResult) -> Unit)? = null

    /**
     * Waits for a tag and writes [uri] to it, then reports the result. If the tag already holds something else the
     * result is [TagWriteResult.NeedsOverwrite] and reader mode stays on; ask the user, then call again with
     * [overwrite] true (they hold the sticker to the phone once more).
     */
    fun write(uri: String, overwrite: Boolean, onResult: (TagWriteResult) -> Unit) {
        pendingWrite = onResult
        pendingRead = null
        adapter?.enableReaderMode(activity, { tag ->
            val r = doWrite(tag, uri, overwrite)
            main.post {
                pendingWrite?.invoke(r)
                if (r !is TagWriteResult.NeedsOverwrite) stop()
            }
        }, flags, null)
    }

    /** Waits for a tag and reports the jPill URI on it (or null if it has none). */
    fun read(onResult: (TagReadResult) -> Unit) {
        pendingRead = onResult
        pendingWrite = null
        adapter?.enableReaderMode(activity, { tag ->
            val r = doRead(tag)
            main.post { pendingRead?.invoke(r); stop() }
        }, flags, null)
    }

    fun stop() {
        runCatching { adapter?.disableReaderMode(activity) }
        pendingWrite = null
        pendingRead = null
    }

    private fun doRead(tag: Tag): TagReadResult = try {
        val ndef = Ndef.get(tag)
        if (ndef == null) TagReadResult.Read(null)
        else {
            ndef.connect()
            val msg = try { ndef.ndefMessage } finally { runCatching { ndef.close() } }
            TagReadResult.Read(msg?.records?.firstNotNullOfOrNull { uriOf(it) })
        }
    } catch (e: Exception) {
        TagReadResult.Failed("Couldn't read the tag. Hold the phone steady against it and try again.")
    }

    private fun doWrite(tag: Tag, uri: String, overwrite: Boolean): TagWriteResult {
        return try {
        val message = NdefMessage(NdefRecord.createUri(uri))
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            ndef.connect()
            try {
                if (!ndef.isWritable) return TagWriteResult.Failed("This tag is read-only (locked), so it can't be written.")
                if (message.byteArrayLength > ndef.maxSize) {
                    return TagWriteResult.Failed("This tag is too small. Use an NTAG213 or larger.")
                }
                val existing = ndef.ndefMessage
                val existingUri = existing?.records?.firstNotNullOfOrNull { uriOf(it) }
                val hasData = existing != null && existing.records.any { !(it.tnf == NdefRecord.TNF_EMPTY) }
                if (hasData && !overwrite && NfcPayload.parse(existingUri) == null) {
                    return TagWriteResult.NeedsOverwrite(existingUri ?: "other data")
                }
                if (hasData && !overwrite && NfcPayload.parse(existingUri) != null &&
                    NfcPayload.parse(existingUri)?.idOrNull() != NfcPayload.parse(uri)?.idOrNull()
                ) {
                    return TagWriteResult.NeedsOverwrite("a different jPill tag")
                }
                ndef.writeNdefMessage(message)
                val back = ndef.ndefMessage
                if (back == null || !back.toByteArray().contentEquals(message.toByteArray())) {
                    return TagWriteResult.Failed("The tag didn't keep what was written. Try again.")
                }
            } finally {
                runCatching { ndef.close() }
            }
            TagWriteResult.Written
        } else {
            val fmt = NdefFormatable.get(tag) ?: return TagWriteResult.Failed("This tag isn't an NFC data tag jPill can write to.")
            fmt.connect()
            try { fmt.format(message) } finally { runCatching { fmt.close() } }
            TagWriteResult.Written
        }
        } catch (e: Exception) {
            TagWriteResult.Failed("Writing failed. Hold the phone steady against the sticker and try again.")
        }
    }

    private fun uriOf(r: NdefRecord): String? = runCatching { r.toUri()?.toString() }.getOrNull()

    private fun NfcPayload.idOrNull(): String = when (this) {
        is NfcPayload.Medicine -> tagId
        is NfcPayload.Timer -> tagId
    }
}
