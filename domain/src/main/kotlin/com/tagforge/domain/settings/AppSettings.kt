package com.tagforge.domain.settings

import com.tagforge.domain.ntag.NtagModel

/** Persisted by stable [code]; never rename a code. */
enum class ThemeMode(val code: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromCode(code: String?): ThemeMode = entries.firstOrNull { it.code == code } ?: SYSTEM
    }
}

/**
 * What a scanned task tag does. Tag content is untrusted input, so [ASK_ALWAYS] is the default. [NEVER_ASK] exists because
 * the user may choose it knowingly (it is gated by a risk warning in Settings); it never applies to irreversible tag
 * security operations, which always need the typed confirmation.
 */
enum class TaskExecutionPolicy(val code: String) {
    ASK_ALWAYS("ask"),

    /** Runs without asking only tasks whose exact content was created on this phone ([AppSettings.trustedTaskFingerprints]). */
    OWN_TASKS("own"),
    NEVER_ASK("never");

    companion object {
        fun fromCode(code: String?): TaskExecutionPolicy = entries.firstOrNull { it.code == code } ?: ASK_ALWAYS
    }
}

/** New fields MUST carry a default so existing stores keep working. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Material You wallpaper colours. Off by default: the Tag Forge brand palette is the look; this is an opt-in. */
    val dynamicColor: Boolean = false,
    val hapticsEnabled: Boolean = true,
    /** Keep a history of reads and writes (HT-01). Turning it off stops new entries; existing ones stay until cleared. */
    val saveHistory: Boolean = true,
    /** Gates the raw console and raw config writes (Phase 6). Off by default. */
    val advancedMode: Boolean = false,
    /** The chip the write wizard measures its size meter against when no tag has been scanned. */
    val lastTargetModel: NtagModel = NtagModel.NTAG213,
    /** The number the next tag of a batch write gets. Advances only after a verified write; survives process death (PR-01). */
    val batchNextCounter: Long = 1L,
    /** Ask before a write replaces what is on the tag (write wizard, task builder, clone). */
    val confirmOverwrite: Boolean = true,
    /** Ask before the Erase and Format tools arm. */
    val confirmEraseFormat: Boolean = true,
    val taskExecution: TaskExecutionPolicy = TaskExecutionPolicy.ASK_ALWAYS,
    /** SHA-256 fingerprints of the compact encoding of tasks created on this phone (see `TaskTrust`). */
    val trustedTaskFingerprints: Set<String> = emptySet(),
)
