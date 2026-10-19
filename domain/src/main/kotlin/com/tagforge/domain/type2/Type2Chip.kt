package com.tagforge.domain.type2

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.ntag.Region
import com.tagforge.domain.tag.TagFamily

/**
 * What the Type 2 NDEF machinery needs to know about a chip: where the user pages are, how big the NDEF area is and
 * whether the chip understands FAST_READ. Implemented by [com.tagforge.domain.ntag.NtagModel] and [Type2Chip].
 */
interface Type2Layout {
    val displayName: String
    val totalPages: Int
    val userStart: Int
    val userEnd: Int

    /** NDEF area size (bytes) an NFC Forum capability container advertises for this chip. */
    val ccNdefSize: Int

    /** Lock/Memory Control TLV the chip ships with in front of the NDEF TLV; erase/format MUST preserve it. */
    val factoryLockControlTlv: Bytes?

    /** False for MIFARE Ultralight and Ultralight C: they only know READ (4 pages per answer). */
    val supportsFastRead: Boolean

    /**
     * True when the capability container found on the tag is the authority for the NDEF size (chips whose CC is
     * written by whoever formats the tag). NTAG21x keep using the datasheet value.
     */
    val trustsCcSize: Boolean
}

/**
 * Type 2 chips other than NTAG213/215/216. Memory maps, GET_VERSION bytes and the absence of FAST_READ on
 * Ultralight/Ultralight C are VERIFIED against the NXP datasheets (MF0ICU1, MF0ICU2, MF0ULX1 Rev. 3.3, NTAG210/212
 * Rev. 3.0). The capability container values for the Ultralight family are the NFC Forum convention (language of
 * the NXP application notes), FROM MEMORY and not in those datasheets: that is why [trustsCcSize] is true, so a
 * CC found on the tag always wins over [ccNdefSize].
 *
 * [extraPages] names the pages after the user area (lock bytes, configuration, keys) for the annotated dump.
 */
enum class Type2Chip(
    override val displayName: String,
    override val totalPages: Int,
    override val userEnd: Int,
    private val ccBytes: Bytes,
    override val supportsFastRead: Boolean,
    val family: TagFamily,
    /** GET_VERSION product type (byte 2) and storage size (byte 6); null when the chip has no GET_VERSION. */
    val versionProductType: Int?,
    val versionSize: Int?,
    override val factoryLockControlTlv: Bytes?,
    val extraPages: Map<Int, Region>,
) : Type2Layout {
    ULTRALIGHT(
        "MIFARE Ultralight", 16, 15, Bytes.of(0xE1, 0x10, 0x06, 0x00), false, TagFamily.ULTRALIGHT, null, null, null,
        emptyMap(),
    ),
    ULTRALIGHT_C(
        "MIFARE Ultralight C", 48, 39, Bytes.of(0xE1, 0x10, 0x12, 0x00), false, TagFamily.ULTRALIGHT, null, null, null,
        mapOf(40 to Region.DYN_LOCK, 41 to Region.COUNTER, 42 to Region.AUTH_CONFIG, 43 to Region.AUTH_CONFIG) +
            (44..47).associateWith { Region.KEY },
    ),
    ULTRALIGHT_EV1_11(
        "MIFARE Ultralight EV1 (MF0UL11)", 20, 15, Bytes.of(0xE1, 0x10, 0x06, 0x00), true, TagFamily.ULTRALIGHT, 0x03, 0x0B, null,
        mapOf(16 to Region.CFG0, 17 to Region.CFG1, 18 to Region.PWD, 19 to Region.PACK),
    ),
    ULTRALIGHT_EV1_21(
        "MIFARE Ultralight EV1 (MF0UL21)", 41, 35, Bytes.of(0xE1, 0x10, 0x10, 0x00), true, TagFamily.ULTRALIGHT, 0x03, 0x0E, null,
        mapOf(36 to Region.DYN_LOCK, 37 to Region.CFG0, 38 to Region.CFG1, 39 to Region.PWD, 40 to Region.PACK),
    ),
    NTAG210(
        "NTAG210", 20, 15, Bytes.of(0xE1, 0x10, 0x06, 0x00), true, TagFamily.NTAG21X, 0x04, 0x0B, null,
        mapOf(16 to Region.CFG0, 17 to Region.CFG1, 18 to Region.PWD, 19 to Region.PACK),
    ),
    NTAG212(
        "NTAG212", 41, 35, Bytes.of(0xE1, 0x10, 0x10, 0x00), true, TagFamily.NTAG21X, 0x04, 0x0E,
        Bytes.of(0x01, 0x03, 0x90, 0x0A, 0x34),
        mapOf(36 to Region.DYN_LOCK, 37 to Region.CFG0, 38 to Region.CFG1, 39 to Region.PWD, 40 to Region.PACK),
    ),
    ;

    override val userStart: Int get() = 4
    override val ccNdefSize: Int get() = ccBytes[2] * 8
    override val trustsCcSize: Boolean get() = true

    /** Capability container written when the tag has none (page 3 still all zero): E1 10 xx 00. */
    val blankCapabilityContainer: Bytes get() = ccBytes

    /** Role of [page] on this chip; empty past the end of the memory. Page 3 is the CC (OTP on Ultralight). */
    fun regionsOf(page: Int): List<Region> = when {
        page < 0 || page >= totalPages -> emptyList()
        page < 2 -> listOf(Region.UID)
        page == 2 -> listOf(Region.UID, Region.STATIC_LOCK)
        page == 3 -> listOf(Region.CC)
        page in userStart..userEnd -> listOf(Region.USER)
        else -> extraPages[page]?.let(::listOf).orEmpty()
    }

    companion object {
        fun fromVersion(productType: Int, storageSize: Int): Type2Chip? =
            entries.firstOrNull { it.versionProductType == productType && it.versionSize == storageSize }
    }
}
