package com.tagforge.domain.tag

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.iso15693.Iso15693

/** MIFARE Ultralight family as `MifareUltralight.getType()` reports it (1 = Ultralight, 2 = Ultralight C). */
enum class UltralightType(val displayName: String?, val pages: Int?) {
    ULTRALIGHT("Ultralight", 16),
    ULTRALIGHT_C("Ultralight C", 48),
    UNKNOWN(null, null),
    ;

    companion object {
        fun fromAndroid(code: Int): UltralightType = when (code) {
            1 -> ULTRALIGHT
            2 -> ULTRALIGHT_C
            else -> UNKNOWN
        }
    }
}

/** MIFARE Classic family as `MifareClassic.getType()` reports it (0 = Classic, 1 = Plus, 2 = Pro). */
enum class ClassicType(val displayName: String?) {
    CLASSIC("Classic"),
    PLUS("Plus"),
    PRO("Pro"),
    UNKNOWN(null),
    ;

    companion object {
        fun fromAndroid(code: Int): ClassicType = when (code) {
            0 -> CLASSIC
            1 -> PLUS
            2 -> PRO
            else -> UNKNOWN
        }
    }
}

data class NfcBDetails(val applicationData: Bytes, val protocolInfo: Bytes)

data class NfcFDetails(val systemCode: Bytes, val manufacturer: Bytes)

/**
 * [afi], [blockCount] and [blockSize] come from ISO 15693 Get System Info (not from the OS) and stay null when the tag
 * does not answer it; [icReference] is the IC reference byte of that answer.
 */
data class NfcVDetails(
    val dsfId: Int,
    val responseFlags: Int,
    val afi: Int? = null,
    val blockCount: Int? = null,
    val blockSize: Int? = null,
    val icReference: Int? = null,
)

data class IsoDepDetails(val hiLayerResponse: Bytes?, val extendedLengthSupported: Boolean, val timeoutMs: Int?)

data class UltralightDetails(val type: UltralightType)

data class ClassicDetails(val type: ClassicType, val sectorCount: Int, val blockCount: Int, val sizeBytes: Int)

/** Which fact a [TechEntry] states. The UI maps each key to a label. */
enum class DetailKey {
    ATQA, SAK,
    APPLICATION_DATA, PROTOCOL_INFO,
    SYSTEM_CODE, MANUFACTURER,
    DSF_ID, RESPONSE_FLAGS, AFI, BLOCK_COUNT, BLOCK_SIZE,
    HISTORICAL_BYTES, HI_LAYER_RESPONSE, EXTENDED_LENGTH, TIMEOUT_MS,
    ULTRALIGHT_TYPE, PAGES,
    CLASSIC_TYPE, SECTORS, BLOCKS, SIZE_BYTES,
}

/** A value ready to show: text that needs no translation, a yes/no flag, or "not reported". */
sealed interface TechValue {
    data class Text(val text: String) : TechValue
    data class Flag(val on: Boolean) : TechValue
    data object Unknown : TechValue
}

data class TechEntry(val key: DetailKey, val value: TechValue)

data class TechSection(val tech: TagTech, val entries: List<TechEntry>)

/**
 * What the OS knows about each technology of a tag, gathered without sending anything to it (TT-05). NfcA's ATQA/SAK
 * and the IsoDep historical bytes live on [TagInfo] already. A null member means the tag lacks that technology.
 */
data class TechDetails(
    val nfcB: NfcBDetails? = null,
    val nfcF: NfcFDetails? = null,
    val nfcV: NfcVDetails? = null,
    val isoDep: IsoDepDetails? = null,
    val ultralight: UltralightDetails? = null,
    val classic: ClassicDetails? = null,
) {
    /** One section per technology that is present AND has something to say, in a fixed order. */
    fun sections(info: TagInfo): List<TechSection> = buildList {
        if (TagTech.NFC_A in info.techs && (info.atqa != null || info.sak != null)) {
            add(TechSection(TagTech.NFC_A, listOf(entry(DetailKey.ATQA, info.atqa?.toHex(" ")), entry(DetailKey.SAK, info.sak?.let(::hexByte)))))
        }
        if (TagTech.NFC_B in info.techs) nfcB?.let {
            add(TechSection(TagTech.NFC_B, listOf(entry(DetailKey.APPLICATION_DATA, it.applicationData.toHex(" ")), entry(DetailKey.PROTOCOL_INFO, it.protocolInfo.toHex(" ")))))
        }
        if (TagTech.NFC_F in info.techs) nfcF?.let {
            add(TechSection(TagTech.NFC_F, listOf(entry(DetailKey.SYSTEM_CODE, it.systemCode.toHex(" ")), entry(DetailKey.MANUFACTURER, it.manufacturer.toHex(" ")))))
        }
        if (TagTech.NFC_V in info.techs) nfcV?.let {
            add(
                TechSection(
                    TagTech.NFC_V,
                    buildList {
                        add(entry(DetailKey.DSF_ID, hexByte(it.dsfId)))
                        add(entry(DetailKey.RESPONSE_FLAGS, hexByte(it.responseFlags)))
                        it.afi?.let { afi -> add(entry(DetailKey.AFI, hexByte(afi))) }
                        it.blockCount?.let { n -> add(entry(DetailKey.BLOCK_COUNT, n.toString())) }
                        it.blockSize?.let { n -> add(entry(DetailKey.BLOCK_SIZE, n.toString())) }
                        Iso15693.manufacturerName(info.uid)?.let { name -> add(entry(DetailKey.MANUFACTURER, name)) }
                    },
                ),
            )
        }
        if (TagTech.ISO_DEP in info.techs) isoDep?.let {
            add(
                TechSection(
                    TagTech.ISO_DEP,
                    listOf(
                        entry(DetailKey.HISTORICAL_BYTES, info.historicalBytes?.takeUnless { b -> b.isEmpty() }?.toHex(" ")),
                        entry(DetailKey.HI_LAYER_RESPONSE, it.hiLayerResponse?.takeUnless { b -> b.isEmpty() }?.toHex(" ")),
                        TechEntry(DetailKey.EXTENDED_LENGTH, TechValue.Flag(it.extendedLengthSupported)),
                        entry(DetailKey.TIMEOUT_MS, it.timeoutMs?.toString()),
                    ),
                ),
            )
        }
        if (TagTech.MIFARE_ULTRALIGHT in info.techs) ultralight?.let {
            add(TechSection(TagTech.MIFARE_ULTRALIGHT, listOf(entry(DetailKey.ULTRALIGHT_TYPE, it.type.displayName), entry(DetailKey.PAGES, it.type.pages?.toString()))))
        }
        if (TagTech.MIFARE_CLASSIC in info.techs) classic?.let {
            add(
                TechSection(
                    TagTech.MIFARE_CLASSIC,
                    listOf(
                        entry(DetailKey.CLASSIC_TYPE, it.type.displayName),
                        entry(DetailKey.SECTORS, it.sectorCount.toString()),
                        entry(DetailKey.BLOCKS, it.blockCount.toString()),
                        entry(DetailKey.SIZE_BYTES, it.sizeBytes.toString()),
                    ),
                ),
            )
        }
    }

    private fun entry(key: DetailKey, text: String?) = TechEntry(key, text?.let(TechValue::Text) ?: TechValue.Unknown)

    private fun hexByte(value: Int) = "0x%02X".format(value)
}
