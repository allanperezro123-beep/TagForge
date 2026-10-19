package com.tagforge.domain.fakes

import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ports.ClassicChannel
import com.tagforge.domain.ports.TagSession
import com.tagforge.domain.ports.TagTransceiver
import com.tagforge.domain.tag.ClassicDetails
import com.tagforge.domain.tag.ClassicType
import com.tagforge.domain.tag.NdefInfo
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.TagFamily
import com.tagforge.domain.tag.TagInfo
import com.tagforge.domain.tag.TagTech
import com.tagforge.domain.tag.TechDetails

/**
 * [TagSession] of a MIFARE Classic card on a phone WITH the NXP controller: the `MifareClassic` technology is the
 * [FakeMifareClassic] block channel, the platform `Ndef` technology (when the card is NFC Forum formatted) is an
 * in-memory message. [platformCalls] counts every platform call so a test can prove an operation used one technology.
 */
class FakeClassicSession(
    val card: FakeMifareClassic = FakeMifareClassic(),
    var message: NdefMessage? = null,
    private val hasNdef: Boolean = false,
) : TagSession {

    var platformCalls: Int = 0
        private set

    override val info: TagInfo = TagInfo(
        uid = card.uid,
        techs = setOf(TagTech.NFC_A, TagTech.MIFARE_CLASSIC) + if (hasNdef) setOf(TagTech.NDEF) else setOf(TagTech.NDEF_FORMATABLE),
        family = TagFamily.MIFARE_CLASSIC,
        sak = 0x08,
        ndef = if (hasNdef) NdefInfo("android.ndef.mifareclassic", 716, isWritable = true, canMakeReadOnly = true) else null,
        details = TechDetails(
            classic = ClassicDetails(ClassicType.CLASSIC, card.layout.sectorCount, card.layout.blockCount, card.layout.sizeBytes),
        ),
    )

    override suspend fun identify(): TagResult<TagInfo> = Outcome.Ok(info)

    override fun transceiver(tech: TagTech): TagTransceiver? = null

    override fun classic(): ClassicChannel = card

    override suspend fun readNdef(): TagResult<NdefMessage?> {
        platformCalls++
        return if (hasNdef) Outcome.Ok(message) else Outcome.Err(TagError.NotNdef)
    }

    override suspend fun writeNdef(message: NdefMessage): TagResult<Unit> {
        platformCalls++
        this.message = message.takeUnless { it == NdefMessage.BLANK || it.isEmpty }
        return Outcome.Ok(Unit)
    }

    override suspend fun formatNdef(initial: NdefMessage?): TagResult<Unit> {
        platformCalls++
        return Outcome.Ok(Unit)
    }

    override suspend fun makeReadOnly(): TagResult<Unit> = Outcome.Err(TagError.Unsupported("makeReadOnly"))

    override suspend fun close() = Unit
}
