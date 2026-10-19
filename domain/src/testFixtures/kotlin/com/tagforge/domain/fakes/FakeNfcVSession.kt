package com.tagforge.domain.fakes

import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ports.TagSession
import com.tagforge.domain.ports.TagTransceiver
import com.tagforge.domain.tag.NdefInfo
import com.tagforge.domain.tag.NfcVDetails
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.TagFamily
import com.tagforge.domain.tag.TagInfo
import com.tagforge.domain.tag.TagTech
import com.tagforge.domain.tag.TechDetails

/**
 * [TagSession] for an ISO 15693 tag: the raw NfcV channel is the [FakeIso15693], the platform `Ndef` technology is an
 * in-memory message. Both are counted ([platformCalls], and `tag.transceiveCount`) so a test can prove that one
 * operation used ONE technology. `identify()` never touches the tag: on the real adapter it is OS data only.
 */
class FakeNfcVSession(
    val tag: FakeIso15693 = FakeIso15693(),
    var message: NdefMessage? = null,
    private val hasNdef: Boolean = true,
    private val writable: Boolean = true,
    private val maxSize: Int = 300,
) : TagSession {

    var platformCalls: Int = 0
        private set
    var writes: Int = 0
        private set

    override val info: TagInfo = TagInfo(
        uid = tag.uid,
        techs = setOf(TagTech.NFC_V) + if (hasNdef) setOf(TagTech.NDEF) else emptySet(),
        family = TagFamily.ISO15693,
        ndef = if (hasNdef) NdefInfo("org.nfcforum.ndef.type5", maxSize, writable, canMakeReadOnly = true) else null,
        details = TechDetails(nfcV = NfcVDetails(dsfId = tag.dsfId, responseFlags = 0x00)),
    )

    override suspend fun identify(): TagResult<TagInfo> = Outcome.Ok(info)

    override fun transceiver(tech: TagTech): TagTransceiver? = if (tech == TagTech.NFC_V) tag else null

    override suspend fun readNdef(): TagResult<NdefMessage?> {
        platformCalls++
        return if (hasNdef) Outcome.Ok(message) else Outcome.Err(TagError.NotNdef)
    }

    override suspend fun writeNdef(message: NdefMessage): TagResult<Unit> {
        platformCalls++
        if (!hasNdef) return Outcome.Err(TagError.NotNdef)
        if (!writable) return Outcome.Err(TagError.ReadOnly)
        writes++
        this.message = message.takeUnless { it == NdefMessage.BLANK || it.isEmpty }
        return Outcome.Ok(Unit)
    }

    override suspend fun formatNdef(initial: NdefMessage?): TagResult<Unit> {
        platformCalls++
        return Outcome.Err(TagError.Unsupported("format"))
    }

    override suspend fun makeReadOnly(): TagResult<Unit> = Outcome.Err(TagError.Unsupported("makeReadOnly"))

    override suspend fun close() = Unit
}
