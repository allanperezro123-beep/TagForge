package com.tagforge.domain.tag

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.iso15693.Iso15693
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.type2.Type2Chip
import com.tagforge.domain.type2.Type2Layout

enum class TagTech {
    NFC_A, NFC_B, NFC_F, NFC_V, ISO_DEP, NDEF, NDEF_FORMATABLE, MIFARE_CLASSIC, MIFARE_ULTRALIGHT,
}

/** Stable [code] values are persisted (history) and must never change. */
enum class TagFamily(val code: String) {
    NTAG21X("ntag"),
    NTAG_I2C("ntag-i2c"),
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
    /** Per-technology facts from the OS (TT-05); empty for tags built without a platform tag. */
    val details: TechDetails = TechDetails(),
    /** A Type 2 chip that is not an NTAG213/215/216 (Ultralight family, NTAG210/212); null otherwise. */
    val type2: Type2Chip? = null,
) {
    /** The page layout of whichever Type 2 chip this is (NTAG21x or [type2]); null for anything else. */
    val layout: Type2Layout? get() = ntag ?: type2

    /** The UID as people write it: ISO 15693 UIDs arrive least significant byte first from Android and are shown reversed. */
    val displayUid: Bytes get() = if (family == TagFamily.ISO15693 && uid.size == Iso15693.UID_SIZE) Iso15693.displayUid(uid) else uid

    /** "NTAG215", "MIFARE Ultralight C", ... when the chip is known, else null. */
    val chipName: String? get() = layout?.displayName
}
