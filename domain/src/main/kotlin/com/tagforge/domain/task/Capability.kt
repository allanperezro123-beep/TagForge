package com.tagforge.domain.task

/** A permission only the user can grant from a system screen (or, for notifications, a runtime prompt). */
enum class SpecialGrant { WRITE_SETTINGS, NOTIFICATION_POLICY, POST_NOTIFICATIONS }

/** What Tag Forge does INSTEAD of the literal action, so a result can say honestly what happened. */
enum class FallbackReason {
    /** Android 10+ no longer lets apps flip Wi-Fi: the internet connectivity panel opens and the user taps. */
    CONNECTIVITY_PANEL,

    /** Android 11+: the system "add this network" dialog. */
    WIFI_ADD_NETWORK_SCREEN,

    /** Android 10: a network suggestion the user approves from a notification. */
    WIFI_SUGGESTION_APPROVAL,

    /** Bluetooth is never toggled silently: its settings open. */
    BLUETOOTH_SETTINGS,

    /** "Silent" needs Do Not Disturb access; without it the phone was set to vibrate. */
    VIBRATE_INSTEAD_OF_SILENT,
}

enum class UnavailableReason { NO_FLASHLIGHT }

/** Can an action run here, right now, and if not what happens instead (TK-07). */
sealed interface Capability {
    /** Runs as described. */
    data object Ready : Capability

    /** Needs [grant] first; [fallback] says what is done meanwhile, if anything. */
    data class NeedsGrant(val grant: SpecialGrant, val fallback: FallbackReason? = null) : Capability

    /** Cannot run directly on this Android version: [reason] is what opens instead and the user must finish. */
    data class FallbackOnly(val reason: FallbackReason) : Capability

    data class Unavailable(val reason: UnavailableReason) : Capability
}

enum class SkipReason {
    /** Failed validation (disallowed URL scheme, invalid number, ...). */
    BLOCKED,

    /** HttpRequest only runs while the runner is on screen (TK-08). */
    NOT_FOREGROUND,
}

enum class FailReason {
    NOT_INSTALLED,
    NO_HANDLER,
    NETWORK,
    TIMEOUT,
    HTTP_STATUS,
    SECURITY,
    UNAVAILABLE,
    UNEXPECTED,
}

/** What happened to one action. The summary shows one row per outcome. */
sealed interface ActionOutcome {
    /** Did what it says. [detail] is extra data worth showing, e.g. an HTTP status code. */
    data class Done(val detail: String? = null) : ActionOutcome

    /** Opened [what] instead: the user must finish the job ("needs your tap"), never reported as done. */
    data class OpenedFallback(val what: FallbackReason) : ActionOutcome

    /** Could not run until [grant] is given; [fallbackApplied] is what was done meanwhile, if anything. */
    data class PermissionNeeded(val grant: SpecialGrant, val fallbackApplied: FallbackReason? = null) : ActionOutcome

    data class Skipped(val why: SkipReason) : ActionOutcome

    data class Failed(val why: FailReason, val detail: String? = null) : ActionOutcome
}

/** What the device can do right now. A snapshot, so [FeasibilityMatrix] stays a pure function. */
data class DeviceFacts(
    val apiLevel: Int,
    val writeSettingsGranted: Boolean,
    val notificationPolicyGranted: Boolean,
    val postNotificationsGranted: Boolean,
    val hasFlashlight: Boolean = true,
)
