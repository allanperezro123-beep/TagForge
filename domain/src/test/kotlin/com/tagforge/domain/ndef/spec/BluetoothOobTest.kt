package com.tagforge.domain.ndef.spec

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.fakes.hex
import com.tagforge.domain.ndef.DefaultNdefCodec
import com.tagforge.domain.ndef.NdefMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** NC-10: Bluetooth Secure Simple Pairing OOB record. */
class BluetoothOobTest {

    @Test
    fun macOnlyPayloadIsLengthPlusReversedMac() {
        val payload = BluetoothOob.encode("AA:BB:CC:DD:EE:FF", name = null)
        assertEquals("08 00 FF EE DD CC BB AA".hex(), payload)
    }

    @Test
    fun macOnlyRecordHeaderIsD22008() {
        val record = BluetoothOob.record("AA:BB:CC:DD:EE:FF", name = null)
        assertEquals("D2 20 08".hex(), DefaultNdefCodec.encode(NdefMessage(listOf(record))).slice(0, 3))
    }

    @Test
    fun nameAppendsCompleteLocalNameStructure() {
        val payload = BluetoothOob.encode("AA:BB:CC:DD:EE:FF", name = "X")
        assertEquals("0B 00 FF EE DD CC BB AA 02 09 58".hex(), payload)
    }

    @Test
    fun differentMacAndLongerNameTriangulate() {
        val payload = BluetoothOob.encode("01:02:03:04:05:06", name = "Speaker")
        // total = 2 + 6 + (1 + 1 + 7) = 17 = 0x11
        assertEquals("11 00 06 05 04 03 02 01 08 09 53 70 65 61 6B 65 72".hex(), payload)
    }

    @Test
    fun macAcceptsDashesAndLowercase() {
        assertEquals(BluetoothOob.encode("AA:BB:CC:DD:EE:FF", null), BluetoothOob.encode("aa-bb-cc-dd-ee-ff", null))
    }

    @Test
    fun parseRecoversMacAndName() {
        assertEquals(BluetoothAddress("AA:BB:CC:DD:EE:FF", "X"), BluetoothOob.parse("0B 00 FF EE DD CC BB AA 02 09 58".hex()))
        assertEquals(BluetoothAddress("AA:BB:CC:DD:EE:FF", null), BluetoothOob.parse("08 00 FF EE DD CC BB AA".hex()))
    }

    @Test
    fun parseSkipsOtherEirStructuresAndReadsShortenedName() {
        // class-of-device structure (04 0D ..) then shortened local name (type 08)
        val payload = "10 00 01 02 03 04 05 06 04 0D 00 04 24 02 08 4E".hex()
        assertEquals(BluetoothAddress("06:05:04:03:02:01", "N"), BluetoothOob.parse(payload))
    }

    @Test
    fun parseRejectsLengthMismatchAndShortInput() {
        assertNull(BluetoothOob.parse("09 00 FF EE DD CC BB AA".hex()))   // declares 9, has 8
        assertNull(BluetoothOob.parse("08 00 FF EE".hex()))
        assertNull(BluetoothOob.parse(Bytes.EMPTY))
    }

    @Test
    fun isValidMacChecksFormat() {
        assertEquals(true, BluetoothOob.isValidMac("AA:BB:CC:DD:EE:FF"))
        assertEquals(false, BluetoothOob.isValidMac("AA:BB:CC:DD:EE"))
        assertEquals(false, BluetoothOob.isValidMac("GG:BB:CC:DD:EE:FF"))
    }
}
