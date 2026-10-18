package com.tagforge.domain.exchange

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.ndef.NdefBudget
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.NdefRecord
import com.tagforge.domain.ndef.spec.RecordSpec
import com.tagforge.domain.ndef.spec.RecordSpecEncoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** PR-01: a `{n}` counter with start, step and zero padding; advances only after a verified write. */
class BatchPlanTest {

    private val url = listOf<RecordSpec>(RecordSpec.Uri("https://x.co/t/{n}"))
    private fun plan(
        records: List<RecordSpec> = url,
        start: Long = 7,
        step: Long = 1,
        pad: Int = 3,
        total: Int = 10,
        includeAar: Boolean = false,
    ) = BatchPlan(records, includeAar, BatchSettings(start, step, pad, total))

    private fun uid(n: Int) = Bytes.of(0x04, n, 0xB2, 0xC3)

    // ---- the counter -------------------------------------------------------------------------------------------------

    @Test
    fun labelsAreZeroPaddedAndAdvanceByTheStep() {
        val p = plan(start = 7, step = 1, pad = 3)
        assertEquals(listOf("007", "008", "009"), (0..2).map(p::label))
        assertEquals(listOf("010", "020", "030"), plan(start = 10, step = 10, pad = 3).let { q -> (0..2).map(q::label) })
    }

    @Test
    fun aCounterWiderThanThePaddingIsNeverTruncated() {
        assertEquals("1234", plan(start = 1234, pad = 3).label(0))
        assertEquals("5", plan(start = 5, pad = 1).label(0))
    }

    @Test
    fun theTokenIsReplacedInTextUrlAndMimePayloadsOnly() {
        val template = listOf(
            RecordSpec.Text("tag {n}", "en"),
            RecordSpec.Uri("https://x.co/t/{n}"),
            RecordSpec.Mime("application/x-test", "id={n}"),
            RecordSpec.Email(to = "a@b.co", subject = "Order {n}", body = "{n}"),
            RecordSpec.Contact(name = "Name {n}"),
            RecordSpec.Sms(number = "+573001234567", body = "sms {n}"),
        )

        val rendered = plan(records = template, start = 7).render(0)

        assertEquals(RecordSpec.Text("tag 007", "en"), rendered[0])
        assertEquals(RecordSpec.Uri("https://x.co/t/007"), rendered[1])
        assertEquals(RecordSpec.Mime("application/x-test", "id=007"), rendered[2])
        assertEquals(template[3], rendered[3])   // untouched
        assertEquals(template[4], rendered[4])
        assertEquals(template[5], rendered[5])
    }

    @Test
    fun everyOccurrenceIsReplacedAndATemplateWithoutTheTokenIsReportedAsConstant() {
        assertEquals(RecordSpec.Text("007-007"), plan(records = listOf(RecordSpec.Text("{n}-{n}"))).render(0).single())
        assertTrue(plan().usesCounter)
        assertFalse(plan(records = listOf(RecordSpec.Text("same everywhere"))).usesCounter)
    }

    // ---- size check over the whole range -----------------------------------------------------------------------------

    @Test
    fun theBudgetUsesTheLongestRenderingOfTheWholeRange() {
        val template = listOf<RecordSpec>(RecordSpec.Text("x{n}"))
        val fitsFirst = NdefBudget.budget(NdefMessage(listOf(RecordSpecEncoder.encode(RecordSpec.Text("x999"))!!)), capacity = 0).used
        // counters 999..1003: the first fits exactly, the last (4 digits) is one byte longer
        val p = plan(records = template, start = 999, pad = 1, total = 5)

        val analysis = p.analyse(capacity = fitsFirst, appPackage = "com.tagforge.app")

        assertEquals(fitsFirst + 1, analysis.budget!!.used)
        assertEquals(BatchBlock.OVER_CAPACITY, analysis.block)
        assertNull(p.analyse(capacity = fitsFirst + 1, appPackage = "com.tagforge.app").block)
    }

    @Test
    fun theAarCountsTowardsTheBudgetOnlyWhenIncluded() {
        val without = plan().analyse(capacity = 137, appPackage = "com.tagforge.app").budget!!.used
        val with = plan(includeAar = true).analyse(capacity = 137, appPackage = "com.tagforge.app").budget!!.used
        assertEquals(34, with - without)   // 3 + 15 + 16, the AAR for com.tagforge.app
    }

    @Test
    fun theMessageAtAnIndexEndsWithTheAarWhenRequested() {
        val message = plan(includeAar = true).messageAt(0, "com.tagforge.app")!!
        assertEquals(NdefRecord.aar("com.tagforge.app"), message.records.last())
        assertEquals(2, message.records.size)
    }

    // ---- blocks ------------------------------------------------------------------------------------------------------

    @Test
    fun badInputsAreNamedNotCrashed() {
        fun block(p: BatchPlan) = p.analyse(137, "com.tagforge.app").block
        assertEquals(BatchBlock.NO_RECORDS, block(plan(records = emptyList())))
        assertEquals(BatchBlock.INVALID_RECORD, block(plan(records = listOf(RecordSpec.Uri("not a url {n}")))))
        assertEquals(BatchBlock.BAD_COUNT, block(plan(total = 0)))
        assertEquals(BatchBlock.BAD_COUNT, block(plan(total = BatchSettings.MAX_TOTAL + 1)))
        assertEquals(BatchBlock.BAD_STEP, block(plan(step = 0)))
        assertEquals(BatchBlock.BAD_PAD, block(plan(pad = 0)))
        assertEquals(BatchBlock.BAD_START, block(plan(start = -1)))
        assertEquals(BatchBlock.COUNTER_OVERFLOW, block(plan(start = BatchSettings.MAX_COUNTER - 2, step = 5, total = 10)))
        assertNull(block(plan()))
    }

    // ---- running a batch ---------------------------------------------------------------------------------------------

    @Test
    fun twoVerifiedWritesLeaveTheNextValueAtNine() {
        val run = BatchRun(plan(start = 7, pad = 3)).recordSuccess(uid(1)).recordSuccess(uid(2))

        assertEquals("009", run.nextLabel)
        assertEquals(9L, run.nextCounter)
        assertEquals(RecordSpec.Uri("https://x.co/t/009"), run.nextRecords().single())
    }

    @Test
    fun aFailedAttemptDoesNotAdvanceTheCounterSoTheSameNumberIsRetried() {
        val afterTwo = BatchRun(plan(start = 7, pad = 3)).recordSuccess(uid(1)).recordSuccess(uid(2))

        val afterLoss = afterTwo.recordFailure()

        assertEquals("009", afterLoss.nextLabel)
        assertEquals(1, afterLoss.summary.failed)
        assertEquals(2, afterLoss.summary.ok)
        assertEquals("009", afterLoss.recordFailure().nextLabel)
        assertEquals("010", afterLoss.recordSuccess(uid(3)).nextLabel)
    }

    @Test
    fun aTagAlreadyWrittenInThisBatchNeedsConfirmation() {
        val run = BatchRun(plan()).recordSuccess(uid(1))

        assertTrue(run.needsOverwriteConfirmation(uid(1)))
        assertFalse(run.needsOverwriteConfirmation(uid(2)))
        assertFalse(BatchRun(plan()).needsOverwriteConfirmation(uid(1)))
    }

    @Test
    fun aFailedAttemptDoesNotMarkTheTagAsWritten() {
        assertFalse(BatchRun(plan()).recordFailure().needsOverwriteConfirmation(uid(1)))
    }

    @Test
    fun stoppingAfterFourOfTenSummarisesFourOkZeroFailedSixRemaining() {
        var run = BatchRun(plan(total = 10))
        repeat(4) { run = run.recordSuccess(uid(it)) }

        assertEquals(BatchSummary(ok = 4, failed = 0, remaining = 6), run.summary)
        assertFalse(run.isComplete)
    }

    @Test
    fun theBatchCompletesAfterTheLastVerifiedWriteAndHasNothingLeftToWrite() {
        var run = BatchRun(plan(total = 3))
        repeat(3) { run = run.recordSuccess(uid(it)) }

        assertTrue(run.isComplete)
        assertEquals(0, run.summary.remaining)
        assertNull(run.nextRecords().firstOrNull())
        assertEquals(run, run.recordSuccess(uid(9)))   // a stray success past the end changes nothing
    }

    @Test
    fun skippingGivesUpOnANumberAndMovesToTheNextOneWithoutCountingItAsWritten() {
        val run = BatchRun(plan(start = 7, pad = 3, total = 4)).recordSuccess(uid(1)).recordFailure().recordSkip()

        assertEquals("009", run.nextLabel)   // 008 was skipped
        assertEquals(BatchSummary(ok = 1, failed = 1, remaining = 2, skipped = 1), run.summary)
        assertEquals(RecordSpec.Uri("https://x.co/t/009"), run.nextRecords().single())
    }

    @Test
    fun theNextMessageCarriesTheNextNumberAndTheAarAndIsNullWhenDone() {
        val run = BatchRun(plan(start = 7, pad = 3, total = 2, includeAar = true)).recordSuccess(uid(1))

        val message = run.nextMessage("com.tagforge.app")!!

        assertEquals(RecordSpecEncoder.encode(RecordSpec.Uri("https://x.co/t/008")), message.records.first())
        assertEquals(NdefRecord.aar("com.tagforge.app"), message.records.last())
        assertNull(run.recordSuccess(uid(2)).nextMessage("com.tagforge.app"))
    }

    @Test
    fun theBatchEndsWhenEveryNumberWasWrittenOrSkipped() {
        var run = BatchRun(plan(total = 3)).recordSuccess(uid(1)).recordSkip().recordSkip()

        assertTrue(run.isComplete)
        assertEquals(0, run.summary.remaining)
        assertEquals(1, run.summary.ok)
        assertEquals(run, run.recordSkip())   // nothing left to skip
        run = run.recordFailure()
        assertTrue(run.isComplete)
    }

    @Test
    fun aRunCanResumeAtAStoredCounterWithoutTheEarlierProgress() {
        val run = BatchRun(plan(start = 42, pad = 4, total = 5))
        assertEquals("0042", run.nextLabel)
        assertNotNull(run.plan.messageAt(0, "x.y"))
    }
}
