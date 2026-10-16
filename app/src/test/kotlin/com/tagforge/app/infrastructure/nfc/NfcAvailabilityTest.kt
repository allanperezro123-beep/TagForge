package com.tagforge.app.infrastructure.nfc

import android.nfc.NfcAdapter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** AS-02 (availability states) and TS-01 (reader-mode flags). */
class NfcAvailabilityTest {

    @Test
    fun noAdapterMeansUnsupportedWhateverTheEnabledFlag() {
        assertEquals(NfcAvailability.UNSUPPORTED, NfcAvailabilityMapper.from(hasAdapter = false, isEnabled = false))
        assertEquals(NfcAvailability.UNSUPPORTED, NfcAvailabilityMapper.from(hasAdapter = false, isEnabled = true))
    }

    @Test
    fun adapterOffIsDisabledAndOnIsEnabled() {
        assertEquals(NfcAvailability.DISABLED, NfcAvailabilityMapper.from(hasAdapter = true, isEnabled = false))
        assertEquals(NfcAvailability.ENABLED, NfcAvailabilityMapper.from(hasAdapter = true, isEnabled = true))
    }

    @Test
    fun broadcastStatesMapToAvailability() {
        assertEquals(NfcAvailability.ENABLED, NfcAvailabilityMapper.fromAdapterState(NfcAdapter.STATE_ON))
        assertEquals(NfcAvailability.DISABLED, NfcAvailabilityMapper.fromAdapterState(NfcAdapter.STATE_OFF))
        assertEquals(NfcAvailability.DISABLED, NfcAvailabilityMapper.fromAdapterState(NfcAdapter.STATE_TURNING_ON))
        assertEquals(NfcAvailability.DISABLED, NfcAvailabilityMapper.fromAdapterState(NfcAdapter.STATE_TURNING_OFF))
    }

    @Test
    fun readerModeCoversAllFourTechnologiesAndSilencesThePlatform() {
        val flags = NfcReaderController.READER_FLAGS
        for (tech in listOf(NfcAdapter.FLAG_READER_NFC_A, NfcAdapter.FLAG_READER_NFC_B, NfcAdapter.FLAG_READER_NFC_F, NfcAdapter.FLAG_READER_NFC_V)) {
            assertTrue(flags and tech != 0, "missing technology flag $tech")
        }
        assertTrue(flags and NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS != 0)
    }

    @Test
    fun readerModeKeepsTheNdefCheckSoTheNdefTechIsPresent() {
        assertFalse(NfcReaderController.READER_FLAGS and NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK != 0)
    }
}
