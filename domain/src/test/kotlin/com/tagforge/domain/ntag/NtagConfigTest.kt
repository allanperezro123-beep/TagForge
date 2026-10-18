package com.tagforge.domain.ntag

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.fakes.hex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * CFG0 / CFG1 layout, datasheet Rev. 3.2 section 8.5.7: CFG0 = MIRROR, RFUI, MIRROR_PAGE, AUTH0 (byte 3);
 * CFG1 = ACCESS (PROT bit 7, CFGLCK bit 6, RFUI bit 5, NFC_CNT_EN bit 4, NFC_CNT_PWD_PROT bit 3, AUTHLIM bits 2-0), RFUI x3.
 */
class NtagConfigTest {

    private val factory = NtagConfig("04 00 00 FF".hex(), "00 00 00 00".hex())

    // ---- command builders ---------------------------------------------------------------------

    @Test
    fun pwdAuthCarriesTheFourPasswordBytes() {
        assertEquals("1B 12 34 56 78".hex(), NtagCommands.pwdAuth("12 34 56 78".hex()))
        assertEquals("1B FF FF FF FF".hex(), NtagCommands.pwdAuth("FF FF FF FF".hex()))
    }

    @Test
    fun readSigAndReadCntHaveTheirFixedArguments() {
        assertEquals("3C 00".hex(), NtagCommands.readSig())
        assertEquals("39 02".hex(), NtagCommands.readCnt())
    }

    @Test
    fun pwdAuthRejectsAPasswordThatIsNotFourBytes() {
        assertFailsWith<IllegalArgumentException> { NtagCommands.pwdAuth("12 34 56".hex()) }
        assertFailsWith<IllegalArgumentException> { NtagCommands.pwdAuth("12 34 56 78 9A".hex()) }
    }

    // ---- reading the layout -------------------------------------------------------------------

    @Test
    fun factoryConfigHasNoProtection() {
        assertEquals(0xFF, factory.auth0)
        assertFalse(factory.prot)
        assertFalse(factory.cfgLock)
        assertEquals(0, factory.authLimit)
        assertFalse(factory.isProtectionActive(NtagModel.NTAG215))
    }

    @Test
    fun accessBitsAreDecodedFromTheFirstCfg1Byte() {
        val config = NtagConfig("04 00 00 04".hex(), "D3 00 00 00".hex())   // 1101 0011
        assertTrue(config.prot)
        assertTrue(config.cfgLock)
        assertTrue(config.nfcCountEnabled)
        assertFalse(config.nfcCountPasswordProtected)
        assertEquals(3, config.authLimit)
        assertEquals(4, config.auth0)
    }

    @Test
    fun protectionIsActiveOnlyWhenAuth0IsInsideTheChip() {
        assertTrue(NtagConfig("04 00 00 04".hex(), Bytes.of(0, 0, 0, 0)).isProtectionActive(NtagModel.NTAG215))
        assertTrue(NtagConfig("04 00 00 86".hex(), Bytes.of(0, 0, 0, 0)).isProtectionActive(NtagModel.NTAG215))   // last page
        assertFalse(NtagConfig("04 00 00 87".hex(), Bytes.of(0, 0, 0, 0)).isProtectionActive(NtagModel.NTAG215))  // past the end
        assertTrue(NtagConfig("04 00 00 2C".hex(), Bytes.of(0, 0, 0, 0)).isProtectionActive(NtagModel.NTAG213))
        assertFalse(NtagConfig("04 00 00 2D".hex(), Bytes.of(0, 0, 0, 0)).isProtectionActive(NtagModel.NTAG213))
    }

    @Test
    fun parseTakesTheEightBytesOfAFastReadOfCfg0AndCfg1() {
        val parsed = NtagConfig.parse("04 00 00 FF 80 00 00 00".hex())
        assertEquals(NtagConfig("04 00 00 FF".hex(), "80 00 00 00".hex()), parsed)
        assertNull(NtagConfig.parse("04 00 00 FF".hex()))
    }

    // ---- read-modify-write --------------------------------------------------------------------

    @Test
    fun settingAuth0ChangesOnlyByteThreeOfCfg0() {
        val changed = NtagConfig("A4 12 34 FF".hex(), "00 00 00 00".hex()).withAuth0(0x04)
        assertEquals("A4 00 34 04".hex(), changed.cfg0, "unrelated MIRROR bits and MIRROR_PAGE survive; RFUI byte 1 is cleared")
    }

    @Test
    fun settingProtOnlyTouchesBitSevenOfAccess() {
        val on = NtagConfig("04 00 00 FF".hex(), "13 00 00 00".hex()).withProt(true)
        assertEquals("93 00 00 00".hex(), on.cfg1)
        assertEquals("13 00 00 00".hex(), on.withProt(false).cfg1)
    }

    @Test
    fun cfgLockAndAuthLimitUseTheirOwnBits() {
        assertEquals("40 00 00 00".hex(), factory.withCfgLock(true).cfg1)
        assertEquals("05 00 00 00".hex(), factory.withAuthLimit(5).cfg1)
        assertEquals("C5 00 00 00".hex(), factory.withProt(true).withCfgLock(true).withAuthLimit(5).cfg1)
        assertFailsWith<IllegalArgumentException> { factory.withAuthLimit(8) }
        assertFailsWith<IllegalArgumentException> { factory.withAuth0(256) }
    }

    @Test
    fun everyRfuiBitIsWrittenAsZero() {
        val dirty = NtagConfig("FF FF FF 10".hex(), "FF FF FF FF".hex())
        val clean = dirty.withRfuiCleared()
        assertEquals("F4 00 FF 10".hex(), clean.cfg0, "CFG0 byte 0 keeps MIRROR_CONF, MIRROR_BYTE and STRG_MOD_EN only")
        assertEquals("DF 00 00 00".hex(), clean.cfg1, "ACCESS bit 5 is RFUI and CFG1 bytes 1-3 are RFUI")
    }

    @Test
    fun everyMutatorReturnsRfuiClearedBytes() {
        val dirty = NtagConfig("FF FF 00 FF".hex(), "FF FF FF FF".hex())
        val updated = dirty.withAuth0(4).withProt(true)
        assertEquals(0, updated.cfg0[1])
        assertEquals(0, updated.cfg1[0] and 0x20)
        assertEquals("00 00 00".hex(), updated.cfg1.slice(1))
    }
}
