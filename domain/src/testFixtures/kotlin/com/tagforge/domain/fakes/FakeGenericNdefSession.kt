package com.tagforge.domain.fakes

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ports.TagSession
import com.tagforge.domain.ports.TagTransceiver
import com.tagforge.domain.tag.NdefInfo
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.TagFamily
import com.tagforge.domain.tag.TagInfo
import com.tagforge.domain.tag.TagTech

/**
 * A tag that only speaks NDEF through the platform (no raw NTAG access): a Type 4 card, an unformatted tag that Android
 * can format, a MIFARE Classic with an NDEF application. Records every platform call so tests can prove what was
 * (or was not) sent.
 */
class FakeGenericNdefSession(
    message: NdefMessage? = null,
    private val writable: Boolean = true,
    private val maxSize: Int = 2048,
    val formatable: Boolean = false,
    family: TagFamily = TagFamily.ISO14443_4,
    techs: Set<TagTech> = setOf(TagTech.NFC_A, TagTech.ISO_DEP, TagTech.NDEF),
    uid: Bytes = Bytes.of(0x08, 0x11, 0x22, 0x33),
) : TagSession {

    var current: NdefMessage? = message
        private set
    var formatted: Boolean = false
        private set

    /** Messages passed to [writeNdef], in order. */
    val writes = mutableListOf<NdefMessage>()
    var formatCalls: Int = 0
        private set
    var closed: Boolean = false
        private set

    /** When set, the next platform call fails with this error (one shot). */
    var failNext: TagError? = null

    override val info: TagInfo = TagInfo(
        uid = uid,
        techs = techs,
        family = family,
        ndef = if (TagTech.NDEF in techs) NdefInfo("org.nfcforum.ndef.type4", maxSize, writable, canMakeReadOnly = false) else null,
    )

    override suspend fun identify(): TagResult<TagInfo> = Outcome.Ok(info)

    override fun transceiver(tech: TagTech): TagTransceiver? = null

    override suspend fun readNdef(): TagResult<NdefMessage?> {
        takeFailure()?.let { return Outcome.Err(it) }
        return Outcome.Ok(current)
    }

    override suspend fun writeNdef(message: NdefMessage): TagResult<Unit> {
        takeFailure()?.let { return Outcome.Err(it) }
        if (!writable) return Outcome.Err(TagError.ReadOnly)
        writes += message
        current = message.takeUnless { it == NdefMessage.BLANK || it.isEmpty }
        return Outcome.Ok(Unit)
    }

    override suspend fun formatNdef(initial: NdefMessage?): TagResult<Unit> {
        takeFailure()?.let { return Outcome.Err(it) }
        if (!formatable) return Outcome.Err(TagError.Unsupported("format"))
        if (!writable) return Outcome.Err(TagError.ReadOnly)
        formatCalls++
        formatted = true
        current = initial?.takeUnless { it == NdefMessage.BLANK || it.isEmpty }
        return Outcome.Ok(Unit)
    }

    override suspend fun makeReadOnly(): TagResult<Unit> = Outcome.Err(TagError.Unsupported("makeReadOnly"))

    override suspend fun close() {
        closed = true
    }

    private fun takeFailure(): TagError? = failNext.also { failNext = null }
}
