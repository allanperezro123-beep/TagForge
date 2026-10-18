package com.tagforge.domain.ntag

import com.tagforge.domain.common.Bytes

/**
 * The two configuration pages of an NTAG21x (datasheet Rev. 3.2, section 8.5.7), as values.
 *
 * CFG0 = MIRROR, RFUI, MIRROR_PAGE, AUTH0 (byte 3). CFG1 = ACCESS (byte 0), RFUI x3.
 * ACCESS: PROT bit 7 (1 = read AND write protected, 0 = write protected only), CFGLCK bit 6, RFUI bit 5,
 * NFC_CNT_EN bit 4, NFC_CNT_PWD_PROT bit 3, AUTHLIM bits 2-0.
 *
 * Every modifier is read-modify-write: it keeps every unrelated bit and writes every RFUI bit as 0, as the datasheet demands.
 */
data class NtagConfig(val cfg0: Bytes, val cfg1: Bytes) {

    init {
        require(cfg0.size == NtagCommands.PAGE_SIZE && cfg1.size == NtagCommands.PAGE_SIZE) { "Config pages are 4 bytes each" }
    }

    val auth0: Int get() = cfg0[AUTH0_INDEX]
    val access: Int get() = cfg1[ACCESS_INDEX]
    val prot: Boolean get() = access and PROT != 0
    val cfgLock: Boolean get() = access and CFGLCK != 0
    val nfcCountEnabled: Boolean get() = access and NFC_CNT_EN != 0
    val nfcCountPasswordProtected: Boolean get() = access and NFC_CNT_PWD_PROT != 0

    /** 0 = unlimited attempts; 1..7 = failed attempts after which the protected area locks for good. */
    val authLimit: Int get() = access and AUTHLIM_MASK

    /** AUTH0 at or below the last page protects memory; anything above (the default FFh) disables the password. */
    fun isProtectionActive(model: NtagModel): Boolean = auth0 <= model.lastPage

    fun withAuth0(page: Int): NtagConfig {
        require(page in 0..0xFF) { "AUTH0 is one byte but got $page" }
        return copy(cfg0 = replace(cfg0, AUTH0_INDEX, page)).withRfuiCleared()
    }

    fun withProt(enabled: Boolean): NtagConfig = withAccess(if (enabled) access or PROT else access and PROT.inv())

    fun withCfgLock(enabled: Boolean): NtagConfig = withAccess(if (enabled) access or CFGLCK else access and CFGLCK.inv())

    fun withAuthLimit(limit: Int): NtagConfig {
        require(limit in 0..AUTHLIM_MASK) { "AUTHLIM is 3 bits (0..7) but got $limit" }
        return withAccess((access and AUTHLIM_MASK.inv()) or limit)
    }

    /** Clears every reserved bit: CFG0 bytes 0 (bits 3, 1, 0) and 1, ACCESS bit 5, and CFG1 bytes 1-3. */
    fun withRfuiCleared(): NtagConfig {
        val cleanCfg0 = Bytes.of(cfg0[0] and CFG0_BYTE0_USED, 0, cfg0[2], cfg0[3])
        val cleanCfg1 = Bytes.of(cfg1[0] and RFUI_ACCESS.inv(), 0, 0, 0)
        return NtagConfig(cleanCfg0, cleanCfg1)
    }

    private fun withAccess(value: Int): NtagConfig = copy(cfg1 = replace(cfg1, ACCESS_INDEX, value)).withRfuiCleared()

    private fun replace(page: Bytes, index: Int, value: Int): Bytes =
        Bytes.of(*IntArray(NtagCommands.PAGE_SIZE) { if (it == index) value else page[it] })

    companion object {
        private const val AUTH0_INDEX = 3
        private const val ACCESS_INDEX = 0
        const val PROT = 0x80
        const val CFGLCK = 0x40
        private const val RFUI_ACCESS = 0x20
        private const val NFC_CNT_EN = 0x10
        private const val NFC_CNT_PWD_PROT = 0x08
        private const val AUTHLIM_MASK = 0x07
        private const val CFG0_BYTE0_USED = 0xF4

        /** [response] is the 8 bytes of FAST_READ cfg0..cfg1; null when it has another size. */
        fun parse(response: Bytes): NtagConfig? =
            if (response.size == 2 * NtagCommands.PAGE_SIZE) NtagConfig(response.slice(0, 4), response.slice(4)) else null
    }
}
