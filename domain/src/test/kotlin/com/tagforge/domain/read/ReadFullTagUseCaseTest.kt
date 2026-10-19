package com.tagforge.domain.read

import com.tagforge.domain.common.Outcome
import com.tagforge.domain.fakes.FakeNtag21x
import com.tagforge.domain.fakes.FakeTagSession
import com.tagforge.domain.fakes.hex
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.NdefRecord
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.scan.OperationKind
import com.tagforge.domain.scan.OperationPhase
import com.tagforge.domain.tag.TagError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** One tap gives the Read screen both the NDEF readout and the raw pages for the Hex tab. */
class ReadFullTagUseCaseTest {

    private val useCase = ReadFullTagUseCase()

    private suspend fun scan(session: FakeTagSession, phases: MutableList<OperationPhase> = mutableListOf()): TagScan {
        val result = useCase.execute(session) { phases += it }
        assertIs<Outcome.Ok<TagScan>>(result, "expected Ok but was $result")
        return result.value
    }

    @Test
    fun combinesTheReadoutAndTheMemoryDump() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG213)
        val session = FakeTagSession.ntag(tag)
        session.writeNdef(NdefMessage(listOf(NdefRecord.text("hi", "en"))))

        val result = scan(session)

        assertEquals(OperationKind.READ, useCase.kind)
        assertEquals(1, result.readout.parsed.size)
        assertEquals(45, result.dump?.pages?.size)
        assertEquals(tag.snapshot(), result.dump?.pages)
        assertNull(result.dumpError)
    }

    @Test
    fun reportsConnectingThenReadingOnlyOnce() = runTest {
        val phases = mutableListOf<OperationPhase>()
        scan(FakeTagSession.ntag(FakeNtag21x(NtagModel.NTAG215)), phases)
        assertEquals(listOf(OperationPhase.CONNECTING, OperationPhase.READING), phases.distinct())
    }

    @Test
    fun tagLostAfterTheReadoutKeepsTheReadoutAndExplainsTheMissingDump() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG213)
        val session = FakeTagSession.ntag(tag)
        // the NDEF read is 10 FAST_READs (page 3 plus the 36 user pages, 4 pages each); the dump starts afterwards
        val sentBeforeDump = 10
        tag.failNextAfter(sentBeforeDump, TagError.TagLost)

        val result = scan(session)

        assertNull(result.dump)
        assertEquals(TagError.TagLost, result.dumpError)
        assertEquals(session.info, result.readout.info)
    }

    @Test
    fun protectedTagFailsAsAuthRequiredBecauseTheReadoutDoes() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG215)
        tag.setPage(NtagModel.NTAG215.cfg0, "00 00 00 04".hex())
        tag.setPage(NtagModel.NTAG215.cfg1, "80 00 00 00".hex())

        assertEquals(Outcome.Err(TagError.AuthRequired), useCase.execute(FakeTagSession.ntag(tag)) {})
    }

    @Test
    fun readoutFailureIsPropagated() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG213).apply { failNext(TagError.TagLost) }
        assertEquals(Outcome.Err(TagError.TagLost), useCase.execute(FakeTagSession.ntag(tag)) {})
    }

    @Test
    fun tagsWithoutAType2ChipStillReturnTheReadout() = runTest {
        val result = scan(FakeTagSession.withoutNtag())

        assertNull(result.dump)
        assertEquals(TagError.Unsupported("NfcA"), result.dumpError)
        assertIs<ReadNotice.UnsupportedFamily>(result.readout.notice)
    }
}
