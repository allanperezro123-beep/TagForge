package com.tagforge.domain.exchange

import com.tagforge.domain.ndef.spec.RecordSpec
import com.tagforge.domain.ndef.spec.WifiAuth
import com.tagforge.domain.task.ActionCheck
import com.tagforge.domain.task.ActionValidator
import com.tagforge.domain.task.BlockReason
import com.tagforge.domain.task.TaskAction
import com.tagforge.domain.task.UrlHost

/** What a scanned QR code says, in terms a person understands. [openUri] is non-null only when an "Open" button may be offered. */
sealed interface QrContent {
    val raw: String
    val openUri: String?

    data class Link(override val raw: String, val host: String, val secure: Boolean) : QrContent {
        override val openUri: String get() = raw
    }

    data class Phone(override val raw: String) : QrContent {
        override val openUri: String get() = raw
    }

    data class Email(override val raw: String) : QrContent {
        override val openUri: String get() = raw
    }

    data class Sms(override val raw: String) : QrContent {
        override val openUri: String get() = raw
    }

    data class Location(override val raw: String) : QrContent {
        override val openUri: String get() = raw
    }

    /** The password is never part of this: the screen shows the network name only. */
    data class Wifi(override val raw: String, val ssid: String, val secured: Boolean) : QrContent {
        override val openUri: String? get() = null
    }

    data class Contact(override val raw: String, val name: String) : QrContent {
        override val openUri: String? get() = null
    }

    data class Plain(override val raw: String, val text: String) : QrContent {
        override val openUri: String? get() = null
    }

    /** A scheme or address a QR code must never be able to trigger: shown as text with a warning, no Open button. */
    data class Unsafe(override val raw: String, val scheme: String?) : QrContent {
        override val openUri: String? get() = null
    }
}

/** Reuses the task allowlist (`ActionValidator`) so a QR can open exactly what a task can, and nothing more. */
object QrContentClassifier {

    private val SCHEME = Regex("^([A-Za-z][A-Za-z0-9+.\\-]*):")

    /** Schemes that can start an app, read a file or run script: named in the warning, never offered an Open button. */
    private val RISKY_SCHEMES = setOf(
        "javascript", "vbscript", "data", "intent", "android-app", "file", "content", "market", "ftp", "sftp", "smb", "blob",
    )

    fun classify(raw: String): QrContent {
        val text = raw.trim()
        if (text.startsWith("BEGIN:VCARD", ignoreCase = true)) return contact(text)
        val scheme = SCHEME.find(text)?.groupValues?.get(1)?.lowercase() ?: return plain(text)
        return when {
            scheme == "wifi" -> wifi(text)
            scheme in ActionValidator.ALLOWED_SCHEMES -> allowed(text, scheme)
            scheme in RISKY_SCHEMES -> QrContent.Unsafe(text, scheme)
            else -> plain(text)
        }
    }

    private fun plain(text: String) = QrContent.Plain(text, text)

    private fun allowed(text: String, scheme: String): QrContent = when (val check = ActionValidator.check(TaskAction.OpenUrl(text))) {
        is ActionCheck.Blocked -> if (isHidingItsHost(text, check)) QrContent.Unsafe(text, scheme) else plain(text)
        else -> when (scheme) {
            "http", "https" -> QrContent.Link(text, UrlHost.of(text).orEmpty(), secure = scheme == "https")
            "tel" -> QrContent.Phone(text)
            "mailto" -> QrContent.Email(text)
            "sms" -> QrContent.Sms(text)
            else -> QrContent.Location(text)
        }
    }

    /** `https://paypal.com@evil.example`: the validator refuses it, and it deserves a warning rather than a shrug. */
    private fun isHidingItsHost(text: String, check: ActionCheck.Blocked): Boolean =
        check.reason == BlockReason.INVALID_URL && text.substringAfter("://", "").substringBefore('/').let { '@' in it || '\\' in it }

    private fun wifi(text: String): QrContent {
        val spec = QrPayloadMapper.fromQr(text) as? RecordSpec.Wifi ?: return plain(text)
        return QrContent.Wifi(text, spec.ssid, secured = spec.auth != WifiAuth.OPEN)
    }

    private fun contact(text: String): QrContent {
        val spec = QrPayloadMapper.fromQr(text) as? RecordSpec.Contact ?: return plain(text)
        return QrContent.Contact(text, spec.name)
    }
}
