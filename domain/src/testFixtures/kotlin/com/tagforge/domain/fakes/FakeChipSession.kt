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
import com.tagforge.domain.tag.TechDetails
import com.tagforge.domain.tag.UltralightDetails
import com.tagforge.domain.tag.UltralightType
import com.tagforge.domain.type2.Type2Chip
import com.tagforge.domain.type2.Type2Identifier
import com.tagforge.domain.type2.Type2Identity

/**
 * [TagSession] over a [FakeType2Chip]. The platform NDEF calls are deliberately useless here: every call is counted in
 * [platformCalls] and answers `Unsupported`, because on a real phone each one is a technology switch (NfcA -> Ndef).
 * A flow that must stay on raw NfcA asserts `platformCalls == 0`.
 */
class FakeChipSession(
    val tag: FakeType2Chip,
    /** What `MifareUltralight.getType()` reports: ULTRALIGHT for everything except the Ultralight C. */
    private val androidType: UltralightType = androidTypeOf(tag.chip),
) : TagSession {

    private var identified: Type2Chip? = null

    var platformCalls: Int = 0
        private set

    var closed: Boolean = false
        private set

    override val info: TagInfo
        get() {
            val chip = identified
            val ndef = tag.hasCapabilityContainer
            return TagInfo(
                uid = tag.uid,
                techs = setOf(TagTech.NFC_A, TagTech.MIFARE_ULTRALIGHT, if (ndef) TagTech.NDEF else TagTech.NDEF_FORMATABLE),
                family = chip?.family ?: TagFamily.ULTRALIGHT,
                atqa = Bytes.of(0x00, 0x44),
                sak = 0x00,
                ndef = if (ndef) NdefInfo("org.nfcforum.ndef.type2", tag.page(3)[2] * 8 - 2, isWritable = tag.page(3)[3] == 0, canMakeReadOnly = true) else null,
                details = TechDetails(ultralight = UltralightDetails(androidType)),
                type2 = chip,
            )
        }

    override suspend fun identify(): TagResult<TagInfo> {
        if (identified == null) {
            when (val result = Type2Identifier.identify(tag, androidType)) {
                is Outcome.Err -> return result
                is Outcome.Ok -> identified = (result.value as? Type2Identity.Other)?.chip
            }
        }
        return Outcome.Ok(info)
    }

    override fun transceiver(tech: TagTech): TagTransceiver? = if (tech == TagTech.NFC_A) tag else null

    override suspend fun readNdef(): TagResult<NdefMessage?> = platform()

    override suspend fun writeNdef(message: NdefMessage): TagResult<Unit> = platform()

    override suspend fun formatNdef(initial: NdefMessage?): TagResult<Unit> = platform()

    override suspend fun makeReadOnly(): TagResult<Unit> = platform()

    override suspend fun close() {
        closed = true
    }

    private fun <T> platform(): TagResult<T> {
        platformCalls++
        return Outcome.Err(TagError.Unsupported("platform-ndef"))
    }

    companion object {
        fun androidTypeOf(chip: Type2Chip): UltralightType =
            if (chip == Type2Chip.ULTRALIGHT_C) UltralightType.ULTRALIGHT_C else UltralightType.ULTRALIGHT
    }
}
