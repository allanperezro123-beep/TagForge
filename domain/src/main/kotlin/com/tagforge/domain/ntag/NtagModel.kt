package com.tagforge.domain.ntag

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.type2.Type2Layout

/**
 * NXP NTAG213/215/216 memory map, verified against datasheet Rev. 3.2 (doc 265332).
 *
 * Note [ccNdefSize] (CC byte 2 * 8) is the NDEF capacity advertised to readers and is SMALLER than
 * [userBytes] for NTAG215/216; budgets must use the CC value, never raw user memory.
 */
enum class NtagModel(
    override val displayName: String,
    /** Third byte of the GET_VERSION response (storage size code). */
    val versionSize: Int,
    override val totalPages: Int,
    override val userStart: Int,
    override val userEnd: Int,
    val dynLock: Int,
    val cfg0: Int,
    val cfg1: Int,
    val pwd: Int,
    val pack: Int,
    /** Capability Container page 03 (E1 10 xx 00). */
    val capabilityContainer: Bytes,
    /** Pages covered by one dynamic lock bit. */
    val dynamicLockGranularityPages: Int,
    /** NTAG213 ships with a Lock Control TLV in pages 04-05; erase/format MUST preserve it. */
    override val factoryLockControlTlv: Bytes?,
) : Type2Layout {
    NTAG213(
        displayName = "NTAG213", versionSize = 0x0F, totalPages = 45, userStart = 4, userEnd = 39,
        dynLock = 40, cfg0 = 41, cfg1 = 42, pwd = 43, pack = 44,
        capabilityContainer = Bytes.of(0xE1, 0x10, 0x12, 0x00),
        dynamicLockGranularityPages = 2,
        factoryLockControlTlv = Bytes.of(0x01, 0x03, 0xA0, 0x0C, 0x34),
    ),
    NTAG215(
        displayName = "NTAG215", versionSize = 0x11, totalPages = 135, userStart = 4, userEnd = 129,
        dynLock = 130, cfg0 = 131, cfg1 = 132, pwd = 133, pack = 134,
        capabilityContainer = Bytes.of(0xE1, 0x10, 0x3E, 0x00),
        dynamicLockGranularityPages = 16,
        factoryLockControlTlv = null,
    ),
    NTAG216(
        displayName = "NTAG216", versionSize = 0x13, totalPages = 231, userStart = 4, userEnd = 225,
        dynLock = 226, cfg0 = 227, cfg1 = 228, pwd = 229, pack = 230,
        capabilityContainer = Bytes.of(0xE1, 0x10, 0x6D, 0x00),
        dynamicLockGranularityPages = 16,
        factoryLockControlTlv = null,
    );

    val lastPage: Int get() = totalPages - 1

    /** User memory in bytes (144 / 504 / 888): page maps and dumps only, NOT the NDEF budget. */
    val userBytes: Int get() = (userEnd - userStart + 1) * 4

    /** NDEF area size advertised in the CC (144 / 496 / 872). */
    override val ccNdefSize: Int get() = capabilityContainer[2] * 8

    override val supportsFastRead: Boolean get() = true

    override val trustsCcSize: Boolean get() = false

    /** Bytes of user memory at pages 04-05 as shipped from the factory. */
    val factoryNdefArea: Bytes
        get() = factoryLockControlTlv?.let { it + EMPTY_NDEF_TLV } ?: (EMPTY_NDEF_TLV + Bytes.of(0x00))

    companion object {
        /** NDEF TLV with a zero-length message followed by the terminator. */
        val EMPTY_NDEF_TLV: Bytes = Bytes.of(0x03, 0x00, 0xFE)

        fun fromVersionSize(code: Int): NtagModel? = entries.firstOrNull { it.versionSize == code }
    }
}
