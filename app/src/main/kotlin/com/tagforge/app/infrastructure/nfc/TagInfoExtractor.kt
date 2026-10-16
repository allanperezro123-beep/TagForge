package com.tagforge.app.infrastructure.nfc

import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import com.tagforge.domain.common.Bytes
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.tag.NdefInfo
import com.tagforge.domain.tag.TagFamily
import com.tagforge.domain.tag.TagInfo
import com.tagforge.domain.tag.TagTech

/**
 * Builds [TagInfo] from an Android [Tag]. The pure helpers are unit tested; [basicInfo] only calls APIs that
 * cause no RF activity, so it is safe to run on the NFC callback thread.
 */
object TagInfoExtractor {

    private val TECH_BY_CLASS = mapOf(
        "android.nfc.tech.NfcA" to TagTech.NFC_A,
        "android.nfc.tech.NfcB" to TagTech.NFC_B,
        "android.nfc.tech.NfcF" to TagTech.NFC_F,
        "android.nfc.tech.NfcV" to TagTech.NFC_V,
        "android.nfc.tech.IsoDep" to TagTech.ISO_DEP,
        "android.nfc.tech.Ndef" to TagTech.NDEF,
        "android.nfc.tech.NdefFormatable" to TagTech.NDEF_FORMATABLE,
        "android.nfc.tech.MifareClassic" to TagTech.MIFARE_CLASSIC,
        "android.nfc.tech.MifareUltralight" to TagTech.MIFARE_ULTRALIGHT,
    )

    /** ISO/IEC 7816-6 manufacturer codes (first UID byte) that Tag Forge names. */
    private val MANUFACTURERS = mapOf(
        0x02 to "STMicroelectronics",
        0x04 to "NXP",
        0x05 to "Infineon",
        0x07 to "Texas Instruments",
    )

    fun techFor(className: String): TagTech? = TECH_BY_CLASS[className]

    fun manufacturerFor(uid: Bytes): String? = if (uid.isEmpty()) null else MANUFACTURERS[uid[0]]

    fun formatAtqa(atqa: Bytes): String = "0x" + atqa.toHex()

    fun formatSak(sak: Int): String = "0x%02X".format(sak)

    fun familyFor(techs: Set<TagTech>, ntag: NtagModel?): TagFamily = when {
        ntag != null -> TagFamily.NTAG21X
        TagTech.MIFARE_CLASSIC in techs -> TagFamily.MIFARE_CLASSIC
        TagTech.MIFARE_ULTRALIGHT in techs -> TagFamily.ULTRALIGHT
        TagTech.ISO_DEP in techs -> TagFamily.ISO14443_4
        TagTech.NFC_V in techs -> TagFamily.ISO15693
        TagTech.NFC_F in techs -> TagFamily.FELICA
        else -> TagFamily.UNKNOWN
    }

    /** What the OS tells us for free; the chip model is completed later by `TagSession.identify()`. */
    fun basicInfo(tag: Tag): TagInfo {
        val techs = tag.techList.mapNotNull(::techFor).toSet()
        val nfcA = NfcA.get(tag)
        val ndef = Ndef.get(tag)
        return TagInfo(
            uid = Bytes.copyOf(tag.id ?: ByteArray(0)),
            techs = techs,
            family = familyFor(techs, null),
            ntag = null,
            atqa = nfcA?.atqa?.let(Bytes::copyOf),
            sak = nfcA?.sak?.toInt(),
            historicalBytes = IsoDep.get(tag)?.historicalBytes?.let(Bytes::copyOf),
            ndef = ndef?.let { NdefInfo(it.type, it.maxSize, it.isWritable, it.canMakeReadOnly()) },
        )
    }
}
