package com.tagforge.app.infrastructure.nfc

import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.Ndef
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import com.tagforge.domain.common.Bytes
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.tag.ClassicDetails
import com.tagforge.domain.tag.ClassicType
import com.tagforge.domain.tag.IsoDepDetails
import com.tagforge.domain.tag.NdefInfo
import com.tagforge.domain.tag.NfcBDetails
import com.tagforge.domain.tag.NfcFDetails
import com.tagforge.domain.tag.NfcVDetails
import com.tagforge.domain.tag.TechDetails
import com.tagforge.domain.tag.UltralightDetails
import com.tagforge.domain.tag.UltralightType
import com.tagforge.domain.tag.TagFamily
import com.tagforge.domain.tag.TagInfo
import com.tagforge.domain.tag.TagTech
import com.tagforge.domain.type2.Type2Chip

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

    private val CLASSIC_SAKS = setOf(0x08, 0x09, 0x18, 0x88, 0x98)

    fun techFor(className: String): TagTech? = TECH_BY_CLASS[className]

    fun manufacturerFor(uid: Bytes): String? = if (uid.isEmpty()) null else MANUFACTURERS[uid[0]]

    fun formatAtqa(atqa: Bytes): String = "0x" + atqa.toHex()

    fun formatSak(sak: Int): String = "0x%02X".format(sak)

    /**
     * SAK values of MIFARE Classic family cards without an ISO-DEP layer (Mini, 1K, 4K, Infineon 1K, Pro 4K). A phone
     * without the NXP controller reports such a card as plain NfcA, so this is how it is still recognised.
     */
    fun isMifareClassicSak(sak: Int): Boolean = sak in CLASSIC_SAKS

    fun familyFor(techs: Set<TagTech>, ntag: NtagModel?, sak: Int? = null, type2: Type2Chip? = null): TagFamily = when {
        ntag != null -> TagFamily.NTAG21X
        type2 != null -> type2.family
        TagTech.MIFARE_CLASSIC in techs -> TagFamily.MIFARE_CLASSIC
        TagTech.NFC_A in techs && TagTech.MIFARE_ULTRALIGHT !in techs && TagTech.ISO_DEP !in techs &&
            sak != null && isMifareClassicSak(sak) -> TagFamily.MIFARE_CLASSIC
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
            family = familyFor(techs, null, nfcA?.sak?.toInt()),
            ntag = null,
            atqa = nfcA?.atqa?.let(Bytes::copyOf),
            sak = nfcA?.sak?.toInt(),
            historicalBytes = IsoDep.get(tag)?.historicalBytes?.let(Bytes::copyOf),
            ndef = ndef?.let { NdefInfo(it.type, it.maxSize, it.isWritable, it.canMakeReadOnly()) },
            details = detailsFor(tag),
        )
    }

    /**
     * Per-technology facts the OS already holds from discovery (TT-05). None of these calls sends a command to the
     * tag, so they are safe on the NFC callback thread; each is guarded so one odd stack never loses the others.
     */
    fun detailsFor(tag: Tag): TechDetails = TechDetails(
        nfcB = guarded { NfcB.get(tag)?.let { NfcBDetails(Bytes.copyOf(it.applicationData ?: ByteArray(0)), Bytes.copyOf(it.protocolInfo ?: ByteArray(0))) } },
        nfcF = guarded { NfcF.get(tag)?.let { NfcFDetails(Bytes.copyOf(it.systemCode ?: ByteArray(0)), Bytes.copyOf(it.manufacturer ?: ByteArray(0))) } },
        nfcV = guarded { NfcV.get(tag)?.let { NfcVDetails(it.dsfId.toInt() and 0xFF, it.responseFlags.toInt() and 0xFF) } },
        isoDep = guarded {
            IsoDep.get(tag)?.let {
                IsoDepDetails(
                    hiLayerResponse = it.hiLayerResponse?.let(Bytes::copyOf),
                    extendedLengthSupported = it.isExtendedLengthApduSupported,
                    timeoutMs = it.timeout,
                )
            }
        },
        ultralight = guarded { MifareUltralight.get(tag)?.let { UltralightDetails(UltralightType.fromAndroid(it.type)) } },
        classic = guarded {
            MifareClassic.get(tag)?.let {
                ClassicDetails(ClassicType.fromAndroid(it.type), it.sectorCount, it.blockCount, it.size)
            }
        },
    )

    private inline fun <T> guarded(block: () -> T?): T? = try {
        block()
    } catch (_: Throwable) {
        null
    }
}
