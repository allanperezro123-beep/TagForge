package com.tagforge.domain.write

import com.tagforge.domain.ndef.DefaultNdefCodec
import com.tagforge.domain.ndef.NdefRecord
import com.tagforge.domain.ndef.spec.FieldError
import com.tagforge.domain.ndef.spec.RecordSpec
import com.tagforge.domain.ndef.spec.SpecError
import com.tagforge.domain.ndef.spec.SpecField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** TW-01 (multi-record add/reorder/remove) and TW-02 (live budget, oversize blocks Write). */
class WriteDraftTest {

    private val a = RecordSpec.Text("a")
    private val b = RecordSpec.Uri("https://example.com")
    private val c = RecordSpec.Phone("+57300")

    private fun analyse(draft: WriteDraft, capacity: Int = 137, pending: RecordSpec? = null) =
        draft.analyse(capacity, appPackage = "com.tagforge.app", pending = pending)

    // ---- editing the list -----------------------------------------------------------------------------

    @Test
    fun addAppendsAndReplaceChangesInPlace() {
        val draft = WriteDraft().add(a).add(b)
        assertEquals(listOf(a, b), draft.records)
        assertEquals(listOf(a, c), draft.replace(1, c).records)
    }

    @Test
    fun removeDropsTheRecordAtTheIndex() {
        assertEquals(listOf(a, c), WriteDraft(listOf(a, b, c)).remove(1).records)
        assertEquals(listOf(b, c), WriteDraft(listOf(a, b, c)).remove(0).records)
    }

    @Test
    fun moveSwapsWithTheNeighbourAndStopsAtTheEnds() {
        val draft = WriteDraft(listOf(a, b, c))
        assertEquals(listOf(b, a, c), draft.move(0, +1).records)
        assertEquals(listOf(a, c, b), draft.move(2, -1).records)
        assertEquals(draft, draft.move(0, -1), "moving the first record up does nothing")
        assertEquals(draft, draft.move(2, +1), "moving the last record down does nothing")
    }

    @Test
    fun outOfRangeIndexesAreIgnoredInsteadOfThrowing() {
        val draft = WriteDraft(listOf(a))
        assertEquals(draft, draft.remove(5))
        assertEquals(draft, draft.replace(5, b))
        assertEquals(draft, draft.move(5, 1))
    }

    // ---- analysis: blocks ---------------------------------------------------------------------------------

    @Test
    fun anEmptyDraftIsBlockedBecauseThereIsNothingToWrite() {
        val analysis = analyse(WriteDraft())
        assertEquals(WriteBlock.NO_RECORDS, analysis.block)
        assertNull(analysis.message)
        assertEquals(0, analysis.budget.used)
    }

    @Test
    fun aValidDraftProducesTheMessageAndAFittingBudget() {
        val analysis = analyse(WriteDraft(listOf(b)))

        assertNull(analysis.block)
        assertEquals(listOf(NdefRecord.uri("https://example.com")), analysis.message?.records)
        assertEquals(16, analysis.budget.used)
        assertEquals(137, analysis.budget.capacity)
        assertEquals(3, analysis.budget.tlvOverhead)
        assertEquals(listOf(16), analysis.budget.perRecord)
    }

    @Test
    fun anInvalidRecordBlocksWritingAndIsReportedByIndex() {
        val analysis = analyse(WriteDraft(listOf(a, RecordSpec.Uri("")), includeAar = false))

        assertEquals(WriteBlock.INVALID_RECORD, analysis.block)
        assertEquals(mapOf(1 to listOf(FieldError(SpecField.URI, SpecError.REQUIRED))), analysis.invalid)
        assertNull(analysis.message)
    }

    @Test
    fun theBudgetStillCountsTheValidRecordsWhileAnotherIsIncomplete() {
        val analysis = analyse(WriteDraft(listOf(a, RecordSpec.Uri(""))))
        assertEquals(DefaultNdefCodec.encode(com.tagforge.domain.ndef.NdefMessage(listOf(NdefRecord.text("a")))).size, analysis.budget.used)
    }

    @Test
    fun oneByteOverTheCapacityBlocksAndSaysBy1() {
        val text = RecordSpec.Text("a".repeat(131))   // 138 encoded bytes
        val analysis = analyse(WriteDraft(listOf(text)), capacity = 137)

        assertEquals(WriteBlock.OVER_CAPACITY, analysis.block)
        assertEquals(138, analysis.budget.used)
        assertEquals(1, analysis.budget.overBy)
        assertNotNull(analysis.message, "the message is valid, it just does not fit this tag")
    }

    @Test
    fun exactlyTheCapacityStillFits() {
        val analysis = analyse(WriteDraft(listOf(RecordSpec.Text("a".repeat(130)))), capacity = 137)
        assertNull(analysis.block)
        assertEquals(137, analysis.budget.used)
    }

    @Test
    fun aBiggerTargetUnblocksTheSameDraft() {
        val draft = WriteDraft(listOf(RecordSpec.Text("a".repeat(131))))
        assertEquals(WriteBlock.OVER_CAPACITY, analyse(draft, capacity = 137).block)
        assertNull(analyse(draft, capacity = 492).block)
    }

    // ---- AAR and the record being edited ----------------------------------------------------------------------

    @Test
    fun theAarIsAppendedLastAndCountsThirtyFourBytes() {
        val without = analyse(WriteDraft(listOf(b)))
        val with = analyse(WriteDraft(listOf(b), includeAar = true))

        assertEquals(without.budget.used + 34, with.budget.used)
        assertEquals(NdefRecord.aar("com.tagforge.app"), with.message?.records?.last())
        assertEquals(2, with.budget.perRecord.size)
    }

    @Test
    fun aValidPendingRecordIsCountedAsIfAlreadyAdded() {
        val analysis = analyse(WriteDraft(listOf(a)), pending = b)
        assertEquals(DefaultNdefCodec.encodedSizeOf(a) + 16, analysis.budget.used)
    }

    @Test
    fun anInvalidPendingRecordIsIgnoredByTheBudget() {
        val base = analyse(WriteDraft(listOf(a)))
        val withBroken = analyse(WriteDraft(listOf(a)), pending = RecordSpec.Uri(""))
        assertEquals(base.budget.used, withBroken.budget.used)
        assertNull(withBroken.block, "an incomplete form is not an invalid saved record")
    }

    private fun DefaultNdefCodec.encodedSizeOf(spec: RecordSpec): Int =
        encode(com.tagforge.domain.ndef.NdefMessage(listOf(com.tagforge.domain.ndef.spec.RecordSpecEncoder.encode(spec)!!))).size
}
