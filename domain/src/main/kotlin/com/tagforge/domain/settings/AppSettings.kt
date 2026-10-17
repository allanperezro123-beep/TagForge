package com.tagforge.domain.settings

/** Persisted by stable [code]; never rename a code. */
enum class ThemeMode(val code: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromCode(code: String?): ThemeMode = entries.firstOrNull { it.code == code } ?: SYSTEM
    }
}

/** New fields MUST carry a default so existing stores keep working. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val hapticsEnabled: Boolean = true,
    /** Keep a history of reads and writes (HT-01). Turning it off stops new entries; existing ones stay until cleared. */
    val saveHistory: Boolean = true,
    /** Gates the raw console and raw config writes (Phase 6). Off by default. */
    val advancedMode: Boolean = false,
)
