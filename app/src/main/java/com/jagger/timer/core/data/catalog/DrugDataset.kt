// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.core.data.catalog

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One row of the bundled drug-name dataset (built from the openFDA NDC directory, CC0; see
 * tools/build_drug_dataset.py). Names/strengths only: never doses, frequencies or limits.
 */
data class DrugRecord(
    val genericName: String,
    val brandNames: List<String>,
    val strengths: List<String>,
    /** Lower-case active ingredient names, for duplicate-ingredient checks. */
    val ingredients: List<String>
)

object DrugDatasetParser {
    /** Parses `generic;brands(|);strengths(|);ingredients(|)` rows (RFC-4180 style quoting). Bad rows are skipped. */
    fun parse(lines: Sequence<String>): List<DrugRecord> = lines.mapNotNull { parseLine(it) }.toList()

    internal fun splitFields(line: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { cur.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                c == ';' && !inQuotes -> { out.add(cur.toString()); cur.setLength(0) }
                else -> cur.append(c)
            }
            i++
        }
        out.add(cur.toString())
        return out
    }

    private fun parseLine(line: String): DrugRecord? {
        if (line.isBlank()) return null
        val f = splitFields(line)
        val generic = f.getOrNull(0)?.trim().orEmpty()
        if (generic.isEmpty()) return null
        fun list(i: Int) = f.getOrNull(i)?.split('|')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        return DrugRecord(generic, list(1), list(2), list(3))
    }
}

/** Loads `assets/drugs.csv` once, lazily, off the main thread. Missing asset = empty list (search still works). */
@Singleton
class DrugDatasetSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val mutex = Mutex()
    private var cache: List<DrugRecord>? = null

    suspend fun records(): List<DrugRecord> = cache ?: mutex.withLock {
        cache ?: withContext(Dispatchers.IO) {
            try {
                context.assets.open("drugs.csv").bufferedReader().useLines { DrugDatasetParser.parse(it) }
            } catch (e: Exception) {
                emptyList()
            }
        }.also { cache = it }
    }
}
