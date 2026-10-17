package com.tagforge.domain.template

import com.tagforge.domain.ndef.spec.RecordSpec
import kotlin.time.Instant

/** A named, reusable list of records: opening one prefills the wizard with identical records (HT-02). */
data class Template(
    val id: Long = 0,
    val name: String,
    val records: List<RecordSpec>,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** Name rules: trimmed, not blank, at most [MAX_LENGTH] characters, unique ignoring case. */
object TemplateNames {

    const val MAX_LENGTH = 60

    /**
     * The name to actually use for [requested], or null when it is blank. A name already in [existing] (compared
     * ignoring case) gets " (2)", " (3)"... The template being renamed must NOT be part of [existing].
     */
    fun resolve(requested: String, existing: Collection<String>): String? {
        val base = requested.trim().take(MAX_LENGTH).trimEnd()
        if (base.isEmpty()) return null
        val taken = existing.map { it.trim().lowercase() }.toSet()
        if (base.lowercase() !in taken) return base

        var number = 2
        while (true) {
            val suffix = " ($number)"
            val candidate = base.take(MAX_LENGTH - suffix.length).trimEnd() + suffix
            if (candidate.lowercase() !in taken) return candidate
            number++
        }
    }
}
