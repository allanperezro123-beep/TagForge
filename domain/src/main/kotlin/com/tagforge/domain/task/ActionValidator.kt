package com.tagforge.domain.task

/** Why an action is refused. The UI turns each into a sentence; the domain never returns text. */
enum class BlockReason {
    /** `intent:`, `file:`, `content:`, `javascript:` and every other scheme outside the allowlist. */
    DISALLOWED_SCHEME,
    INVALID_URL,
    INVALID_NUMBER,
    INVALID_PACKAGE,
    EMPTY_VALUE,
    OUT_OF_RANGE,
    TOO_LONG,
}

enum class ActionWarning {
    /** HTTP without TLS: needs an extra warning in the sheet and a second tap (TK-08). */
    INSECURE_HTTP,
}

/** Verdict on one action. [Blocked] actions are shown as blocked and never executed. */
sealed interface ActionCheck {
    data object Ok : ActionCheck
    data class Warn(val warning: ActionWarning) : ActionCheck
    data class Blocked(val reason: BlockReason) : ActionCheck
}

/**
 * Sanity rules for task actions. Tag content is untrusted: this is the gate between "decoded from a tag" and "shown
 * to the user as something that may run". It is also what the builder forms use for inline errors.
 */
object ActionValidator {

    /** The only URL schemes a task may open (TK-06). */
    val ALLOWED_SCHEMES: Set<String> = setOf("http", "https", "tel", "mailto", "geo", "sms")

    private val SCHEME = Regex("^([A-Za-z][A-Za-z0-9+.\\-]*):")
    private val HTTP_HOST = Regex("^https?://[^/?#]+", RegexOption.IGNORE_CASE)
    private val PHONE = Regex("^\\+?[0-9*#()\\-. ]{1,32}$")
    private val PACKAGE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

    private const val MAX_SSID_BYTES = 32
    private const val MIN_WPA_KEY = 8
    private const val MAX_WPA_KEY = 63

    fun check(action: TaskAction): ActionCheck = when (action) {
        is TaskAction.OpenUrl -> checkUrl(action.url, httpOnly = false)
        is TaskAction.HttpRequest -> checkUrl(action.url, httpOnly = true)
        is TaskAction.Dial -> checkNumber(action.number)
        is TaskAction.SmsCompose -> firstProblem(checkNumber(action.number), checkLength(action.body))
        is TaskAction.LaunchApp -> checkPackage(action.packageName)
        is TaskAction.Alarm -> firstProblem(
            range(action.hour, 0, MAX_HOUR),
            range(action.minute, 0, MAX_MINUTE),
            checkLength(action.label),
        )
        is TaskAction.Timer -> firstProblem(range(action.seconds, 1, U16_MAX), checkLength(action.label))
        is TaskAction.CalendarEvent -> firstProblem(required(action.title), range(action.durationMinutes, 1, U16_MAX))
        is TaskAction.SetVolume -> range(action.level, 0, MAX_PERCENT)
        is TaskAction.SetBrightness -> range(action.level, 0, MAX_BRIGHTNESS)
        is TaskAction.Speak -> firstProblem(required(action.text), checkLength(action.text))
        is TaskAction.Notify -> firstProblem(required(action.title), checkLength(action.title), checkLength(action.text))
        is TaskAction.WifiJoin -> checkWifi(action)
        is TaskAction.SetRinger,
        is TaskAction.WifiToggle,
        is TaskAction.BluetoothToggle,
        is TaskAction.OpenSettings,
        is TaskAction.Flashlight,
        is TaskAction.MediaKey,
        -> ActionCheck.Ok
    }

    private fun checkUrl(url: String, httpOnly: Boolean): ActionCheck {
        if (url.isBlank()) return blocked(BlockReason.EMPTY_VALUE)
        if (url.any { it <= ' ' || it == DELETE }) return blocked(BlockReason.INVALID_URL)
        if (url.toByteArray(Charsets.UTF_8).size > TaskLimits.MAX_STRING_BYTES) return blocked(BlockReason.TOO_LONG)

        val scheme = SCHEME.find(url)?.groupValues?.get(1)?.lowercase() ?: return blocked(BlockReason.INVALID_URL)
        val allowed = if (httpOnly) scheme == "http" || scheme == "https" else scheme in ALLOWED_SCHEMES
        if (!allowed) return blocked(BlockReason.DISALLOWED_SCHEME)

        if (scheme == "http" || scheme == "https") {
            if (!HTTP_HOST.containsMatchIn(url)) return blocked(BlockReason.INVALID_URL)
            if (scheme == "http" && httpOnly) return ActionCheck.Warn(ActionWarning.INSECURE_HTTP)
        }
        return ActionCheck.Ok
    }

    private fun checkNumber(number: String): ActionCheck {
        if (number.isBlank()) return blocked(BlockReason.EMPTY_VALUE)
        val dialable = PHONE.matches(number) && number.any { it.isDigit() || it == '*' || it == '#' }
        return if (dialable) ActionCheck.Ok else blocked(BlockReason.INVALID_NUMBER)
    }

    private fun checkPackage(name: String): ActionCheck = when {
        name.isBlank() -> blocked(BlockReason.EMPTY_VALUE)
        PACKAGE.matches(name) -> ActionCheck.Ok
        else -> blocked(BlockReason.INVALID_PACKAGE)
    }

    private fun checkWifi(action: TaskAction.WifiJoin): ActionCheck {
        if (action.ssid.isBlank()) return blocked(BlockReason.EMPTY_VALUE)
        if (action.ssid.toByteArray(Charsets.UTF_8).size > MAX_SSID_BYTES) return blocked(BlockReason.OUT_OF_RANGE)
        if (action.auth != JoinAuth.OPEN && action.key.length !in MIN_WPA_KEY..MAX_WPA_KEY) return blocked(BlockReason.OUT_OF_RANGE)
        return ActionCheck.Ok
    }

    private fun required(text: String): ActionCheck = if (text.isBlank()) blocked(BlockReason.EMPTY_VALUE) else ActionCheck.Ok

    private fun checkLength(text: String): ActionCheck =
        if (text.toByteArray(Charsets.UTF_8).size > TaskLimits.MAX_STRING_BYTES) blocked(BlockReason.TOO_LONG) else ActionCheck.Ok

    private fun range(value: Int, min: Int, max: Int): ActionCheck =
        if (value in min..max) ActionCheck.Ok else blocked(BlockReason.OUT_OF_RANGE)

    private fun firstProblem(vararg checks: ActionCheck): ActionCheck = checks.firstOrNull { it != ActionCheck.Ok } ?: ActionCheck.Ok

    private fun blocked(reason: BlockReason) = ActionCheck.Blocked(reason)

    private const val DELETE = '\u007F'
    private const val MAX_HOUR = 23
    private const val MAX_MINUTE = 59
    private const val MAX_PERCENT = 100
    private const val MAX_BRIGHTNESS = 255
    private const val U16_MAX = 65535
}
