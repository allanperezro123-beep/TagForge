package com.tagforge.domain.ndef

import com.tagforge.domain.ntag.NtagModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NdefBudgetTest {

    /**
     * Text record whose encoded size is exactly [total] bytes: header (4 short / 7 long) + 3 status/lang + text.
     * Sizes 258..259 cannot be hit exactly (short form tops out at 4 + 255 = 259, long starts at 7 + 256 = 263).
     */
    private fun textRecordOfSize(total: Int): NdefRecord {
        val shortPayload = total - 4
        val payload = if (shortPayload <= 255) shortPayload else total - 7
        return NdefRecord.text("a".repeat(payload - 3), "en")
    }

    @Test
    fun usedIsTheEncodedMessageSize() {
        val report = NdefBudget.budget(NdefMessage(listOf(NdefRecord.uri("https://example.com"))), capacity = 137)
        assertEquals(16, report.used)
        assertEquals(137, report.capacity)
        assertEquals(listOf(16), report.perRecord)
        assertTrue(report.fits)
        assertEquals(0, report.overBy)
    }

    @Test
    fun tlvOverheadIsReportedSeparately() {
        val small = NdefBudget.budget(NdefMessage(listOf(NdefRecord.uri("https://example.com"))), capacity = 137)
        assertEquals(3, small.tlvOverhead)   // tag + 1-byte length + terminator

        val large = NdefBudget.budget(NdefMessage(listOf(textRecordOfSize(300))), capacity = 400)
        assertEquals(300, large.used)
        assertEquals(5, large.tlvOverhead)   // tag + 3-byte length + terminator
    }

    @Test
    fun overheadSwitchesToThreeByteLengthAtTheBoundary() {
        assertEquals(3, NdefBudget.budget(NdefMessage(listOf(textRecordOfSize(254))), 500).tlvOverhead)
        assertEquals(5, NdefBudget.budget(NdefMessage(listOf(textRecordOfSize(255))), 500).tlvOverhead)
    }

    @Test
    fun perRecordSizesSumToTheMessageSize() {
        val report = NdefBudget.budget(
            NdefMessage(listOf(NdefRecord.text("hi", "en"), NdefRecord.uri("https://example.com"))),
            capacity = 137,
        )
        assertEquals(listOf(9, 16), report.perRecord)
        assertEquals(report.used, report.perRecord.sum())
    }

    @Test
    fun oneByteOverCapacityDoesNotFit() {
        val message = NdefMessage(listOf(textRecordOfSize(138)))
        val report = NdefBudget.budget(message, capacity = 137)
        assertEquals(138, report.used)
        assertFalse(report.fits)
        assertEquals(1, report.overBy)
    }

    @Test
    fun exactlyAtCapacityFits() {
        val report = NdefBudget.budget(NdefMessage(listOf(textRecordOfSize(137))), capacity = 137)
        assertTrue(report.fits)
        assertEquals(0, report.overBy)
    }

    @Test
    fun emptyMessageUsesNothingButStillNeedsTheTlv() {
        val report = NdefBudget.budget(NdefMessage.EMPTY, capacity = 137)
        assertEquals(0, report.used)
        assertEquals(emptyList(), report.perRecord)
        assertEquals(3, report.tlvOverhead)
        assertTrue(report.fits)
    }

    @Test
    fun usedFractionDrivesTheMeterThresholds() {
        val report = NdefBudget.budget(NdefMessage(listOf(textRecordOfSize(100))), capacity = 200)
        assertEquals(0.5f, report.usedFraction)
        assertEquals(1.5f, NdefBudget.budget(NdefMessage(listOf(textRecordOfSize(150))), capacity = 100).usedFraction)
    }

    @Test
    fun presetCapacitiesFollowTheReconciledFormula() {
        // CC size - Lock Control TLV (213 only) - NDEF TLV header (2 or 4) - terminator
        assertEquals(144 - 5 - 2 - 1, NdefBudget.presetCapacity(NtagModel.NTAG213))
        assertEquals(136, NdefBudget.presetCapacity(NtagModel.NTAG213))
        assertEquals(496 - 4 - 1, NdefBudget.presetCapacity(NtagModel.NTAG215))
        assertEquals(872 - 4 - 1, NdefBudget.presetCapacity(NtagModel.NTAG216))
    }

    @Test
    fun androidMaxSizeMatchesWhatTheOsReportsForEachChip() {
        // Ndef.getMaxSize(): CC size - Lock Control TLV (213 only) - NDEF TLV header (2 short, 4 long)
        assertEquals(137, NdefBudget.androidMaxSize(NtagModel.NTAG213))
        assertEquals(492, NdefBudget.androidMaxSize(NtagModel.NTAG215))
        assertEquals(868, NdefBudget.androidMaxSize(NtagModel.NTAG216))
    }

    @Test
    fun smallestChipPicksTheCheapestTagThatStillFits() {
        assertEquals(NtagModel.NTAG213, NdefBudget.smallestChipFor(137))
        assertEquals(NtagModel.NTAG215, NdefBudget.smallestChipFor(138))
        assertEquals(NtagModel.NTAG215, NdefBudget.smallestChipFor(492))
        assertEquals(NtagModel.NTAG216, NdefBudget.smallestChipFor(493))
        assertEquals(NtagModel.NTAG216, NdefBudget.smallestChipFor(868))
        assertEquals(null, NdefBudget.smallestChipFor(869))
    }
}
