package com.tagforge.domain.tag

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.ntag.NtagModel

enum class TagTech {
    NFC_A, NFC_B, NFC_F, NFC_V, ISO_DEP, NDEF, NDEF_FORMATABLE, MIFARE_CLASSIC, MIFARE_ULTRALIGHT,
}

/** Stable [code] values are persisted (history) and must never change. */
enum class TagFamily(val code: String) {
    NTAG21X("ntag"),
    ULTRALIGHT("ul"),
    MIFARE_CLASSIC("mfc"),
    DESFIRE("desfire"),
    ISO15693("iso15693"),
    FELICA("felica"),
    ISO14443_4("iso4"),
    UNKNOWN("unknown"),
}

/** What Android's `Ndef` tech reports; [maxSize] is the NDEF message capacity (NTAG213 = 137). */
data class NdefInfo(
    val type: String,
    val maxSize: Int,
    val isWritable: Boolean,
    val canMakeReadOnly: Boolean,
)

data class TagInfo(
    val uid: Bytes,
    val techs: Set<TagTech>,
    val family: TagFamily,
    val ntag: NtagModel? = null,
    val atqa: Bytes? = null,
    val sak: Int? = null,
    val historicalBytes: Bytes? = null,
    val ndef: NdefInfo? = null,
)
