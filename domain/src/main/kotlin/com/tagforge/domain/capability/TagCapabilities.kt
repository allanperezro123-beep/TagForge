package com.tagforge.domain.capability

import com.tagforge.domain.tag.TagError

/** What a user can ask of a tag. Every screen routes through [TagCapabilities] instead of testing tag families itself. */
enum class ToolOperation { READ_NDEF, DUMP, WRITE_NDEF, ERASE, FORMAT, CLONE }

/**
 * The ONE technology an operation talks to the tag over. An operation never mixes channels: switching technology in
 * the middle of an operation is what made an NTAG215 report "tag lost" on real phones.
 */
enum class AccessChannel { RAW_NFC_A, RAW_NFC_V, CLASSIC_KEYS, PLATFORM_NDEF }

/** Why an operation is greyed out. The UI maps each reason to a sentence (es + en). */
enum class UnsupportedReason {
    /** MIFARE Classic needs the NXP controller; the phone reported the card without that technology. */
    CLASSIC_NEEDS_NXP_CHIP,

    /** The family can be identified but nothing can be read or written without proprietary access. */
    INFO_ONLY,

    /** The platform exposes no NDEF structure for this tag. */
    NO_NDEF,

    /** Reading the memory needs authentication (ISO-DEP cards), which Tag Forge never performs. */
    NEEDS_AUTHENTICATION,

    /** The tag cannot be formatted for NDEF with the platform APIs. */
    NOT_FORMATABLE,
}

sealed interface Support {
    data class Available(val channel: AccessChannel) : Support
    data class Unavailable(val reason: UnsupportedReason) : Support
}

/** The operations one tag supports, with the channel each uses or the reason it is not available. */
class Capabilities(private val byOperation: Map<ToolOperation, Support>) {
    operator fun get(operation: ToolOperation): Support = byOperation.getValue(operation)

    fun isSupported(operation: ToolOperation): Boolean = get(operation) is Support.Available

    fun reasonFor(operation: ToolOperation): UnsupportedReason? = (get(operation) as? Support.Unavailable)?.reason

    /** True when the Read screen may also dump the raw pages: only when that stays on the channel that read the NDEF. */
    val readIncludesDump: Boolean
        get() = (get(ToolOperation.DUMP) as? Support.Available)?.channel == AccessChannel.RAW_NFC_A &&
            (get(ToolOperation.READ_NDEF) as? Support.Available)?.channel == AccessChannel.RAW_NFC_A
}

/**
 * The typed error an operation returns before touching the tag, or null when the tag supports it. Use cases call this
 * right after identifying, so an unsupported tag is refused with zero writes and a reason the UI can explain.
 */
fun Capabilities.refusal(operation: ToolOperation): TagError? = when (reasonFor(operation)) {
    null -> null
    UnsupportedReason.CLASSIC_NEEDS_NXP_CHIP -> TagError.Unsupported(TagError.Unsupported.MIFARE_CLASSIC)
    UnsupportedReason.INFO_ONLY -> TagError.Unsupported(TagError.Unsupported.INFO_ONLY)
    UnsupportedReason.NO_NDEF -> TagError.NotNdef
    UnsupportedReason.NEEDS_AUTHENTICATION -> TagError.Unsupported(TagError.Unsupported.NEEDS_AUTHENTICATION)
    UnsupportedReason.NOT_FORMATABLE -> TagError.Unsupported(TagError.Unsupported.FORMAT)
}
