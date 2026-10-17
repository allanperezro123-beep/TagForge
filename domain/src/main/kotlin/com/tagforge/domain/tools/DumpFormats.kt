package com.tagforge.domain.tools

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.ntag.MemoryDump

/** File formats for sharing a dump (TT-06). Pure: the app writes the result to a private cache file. */
object DumpFormats {

    /**
     * Comment header (`#`) followed by one `PP: HH HH HH HH |aaaa|` line per page. PWD and PACK are written as zeros:
     * that is all the chip ever gives, and a shared dump must not suggest a password was captured.
     */
    fun toText(report: DumpReport): String = buildString {
        val info = report.info
        val dump = report.dump
        appendLine("# Tag Forge memory dump")
        appendLine("# UID ${info.uid.toHex(":")}")
        appendLine("# Chip ${dump.model?.displayName ?: info.family.code}")
        dump.unreadable?.let { appendLine("# Unreadable pages ${it.first}-${it.last}") }
        for (page in dump.pages.indices) appendLine(MemoryDump.formatRow(page, dump.displayBytes(page)))
    }

    /** The pages back to back, exactly as read (hidden pages as the zeros the chip returned). */
    fun toBinary(dump: MemoryDump): Bytes = Bytes.concat(dump.pages.indices.map(dump::displayBytes))
}
