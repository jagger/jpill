// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.history

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate

/** Hands the History CSV to Android's standard share sheet. The file lives in the cache and is replaced on each export. */
object HistoryShare {
    fun share(context: Context, csv: String, today: LocalDate = LocalDate.now()) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "jpill-history-$today.csv").apply { writeText(csv, Charsets.UTF_8) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "jPill history $today")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share history").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
