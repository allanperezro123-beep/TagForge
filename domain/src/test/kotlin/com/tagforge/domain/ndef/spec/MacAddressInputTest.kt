package com.tagforge.domain.ndef.spec

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MacAddressInputTest {

    @Test
    fun parseAcceptsTheFormsPeoplePasteFromSettingsScreensAndLabels() {
        val expected = "AA:BB:CC:DD:EE:FF"

        assertEquals(expected, MacAddressInput.parse("AABBCCDDEEFF"))
        assertEquals(expected, MacAddressInput.parse("aa:bb:cc:dd:ee:ff"))
        assertEquals(expected, MacAddressInput.parse("aa-bb-cc-dd-ee-ff"))
        assertEquals(expected, MacAddressInput.parse("  AA BB CC DD EE FF "))
        assertEquals(expected, MacAddressInput.parse("aabb.ccdd.eeff"))
    }

    @Test
    fun parseRejectsAnythingThatIsNotExactlySixBytes() {
        assertNull(MacAddressInput.parse(""))
        assertNull(MacAddressInput.parse("AABBCCDDEE"))
        assertNull(MacAddressInput.parse("AABBCCDDEEFF00"))
        assertNull(MacAddressInput.parse("AABBCCDDEEFG"))
        assertNull(MacAddressInput.parse("not an address"))
    }

    @Test
    fun theCanonicalFormPassesTheRecordValidator() {
        val mac = MacAddressInput.parse("001a7dda7113")!!

        assertEquals(emptyList(), RecordSpecValidator.validate(RecordSpec.Bluetooth(mac = mac, name = "Speaker")))
    }

    @Test
    fun formatGroupsDigitsInPairsWhileTheUserTypes() {
        assertEquals("AA:BB:CC", MacAddressInput.format("aabbcc"))
        assertEquals("AA:BB:C", MacAddressInput.format("aa:bb:c"))
        assertEquals("AA", MacAddressInput.format("aa"))
        assertEquals("", MacAddressInput.format(""))
    }

    @Test
    fun formatDropsNonHexCharactersAndStopsAtSixBytes() {
        assertEquals("12", MacAddressInput.format("zz12"))
        assertEquals("AA:BB:CC:DD:EE:FF", MacAddressInput.format("AABBCCDDEEFF99"))
    }
}
