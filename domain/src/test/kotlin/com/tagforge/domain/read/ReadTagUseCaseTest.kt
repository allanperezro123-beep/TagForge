package com.tagforge.domain.read

import com.tagforge.domain.common.Outcome
import com.tagforge.domain.fakes.FakeNtag21x
import com.tagforge.domain.fakes.FakeTagSession
import com.tagforge.domain.fakes.hex
import com.tagforge.domain.ndef.DefaultNdefCodec
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.NdefRecord
import com.tagforge.domain.ndef.ParsedRecord
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.scan.OperationKind
import com.tagforge.domain.scan.OperationPhase
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.TagFamily
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ReadTagUseCaseTest {

    private val useCase = ReadTagUseCase()
    private val phases = mutableListOf<OperationPhase>()

    private suspend fun read(session: FakeTagSession): Outcome<TagReadout, TagError> =
        useCase.execute(session) { phases += it }

    private suspend fun readOk(session: FakeTagSession): TagReadout {
        val result = read(session)
        assertIs<Outcome.Ok<TagReadout>>(result, "expected Ok but was $result")
        return result.value
    }

    @Test
    fun operationIsAReadAndReportsConnectingThenReading() = runTest {
        assertEquals(OperationKind.READ, useCase.kind)
        readOk(FakeTagSession.ntag(FakeNtag21x(NtagModel.NTAG213)))
        assertEquals(listOf(OperationPhase.CONNECTING, OperationPhase.READING), phases)
    }

    @Test
    fun identifiesNtag215FromGetVersion() = runTest {
        val readout = readOk(FakeTagSession.ntag(FakeNtag21x(NtagModel.NTAG215), identified = false))

        assertEquals(NtagModel.NTAG215, readout.info.ntag)
        assertEquals(TagFamily.NTAG21X, readout.info.family)
        assertEquals(504, readout.info.ntag?.userBytes)
        assertEquals(496, readout.info.ntag?.ccNdefSize)
    }

    @Test
    fun blankTagHasNoMessageAndNoNotice() = runTest {
        val readout = readOk(FakeTagSession.ntag(FakeNtag21x(NtagModel.NTAG216)))

        assertNull(readout.message)
        assertEquals(emptyList(), readout.parsed)
        assertNull(readout.rawNdef)
        assertNull(readout.notice)
    }

    @Test
    fun messageIsParsedAndRawBytesKept() = runTest {
        val message = NdefMessage(listOf(NdefRecord.text("hello", "en"), NdefRecord.uri("https://example.com")))
        val session = FakeTagSession.ntag(FakeNtag21x(NtagModel.NTAG213))
        session.writeNdef(message)

        val readout = readOk(session)

        assertEquals(message, readout.message)
        assertEquals(2, readout.parsed.size)
        assertEquals("hello", (readout.parsed[0] as ParsedRecord.Text).text)
        assertEquals("https://example.com", (readout.parsed[1] as ParsedRecord.Uri).uri)
        assertEquals(DefaultNdefCodec.encode(message), readout.rawNdef)
    }

    @Test
    fun getVersionNakShowsAnUnknownNfcATagWithoutError() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG213).apply { getVersionNaks = true }
        val readout = readOk(FakeTagSession.ntag(tag, identified = false))

        assertNull(readout.info.ntag)
        assertEquals(TagFamily.UNKNOWN, readout.info.family)
        assertNull(readout.notice)
    }

    @Test
    fun corruptNdefStillReturnsTheTagInfoWithANotice() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG215)
        tag.setPage(4, "03 03 D1 01".hex())
        tag.setPage(5, "05 00 00 00".hex())

        val readout = readOk(FakeTagSession.ntag(tag))

        assertEquals(NtagModel.NTAG215, readout.info.ntag)
        assertNull(readout.message)
        assertIs<ReadNotice.NdefUnreadable>(readout.notice)
    }

    @Test
    fun zeroedMemoryIsReportedAsNotNdef() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG215)
        tag.setPage(4, "00 00 00 00".hex())

        assertEquals(ReadNotice.NotNdef, readOk(FakeTagSession.ntag(tag)).notice)
    }

    @Test
    fun protectedNtagFailsWithAuthRequiredButSessionInfoKeepsTheChipIdentity() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG215)
        tag.setPage(NtagModel.NTAG215.cfg0, "00 00 00 04".hex())   // AUTH0 = 04
        tag.setPage(NtagModel.NTAG215.cfg1, "80 00 00 00".hex())   // PROT = 1
        val session = FakeTagSession.ntag(tag, identified = false)

        assertEquals(Outcome.Err(TagError.AuthRequired), read(session))

        assertEquals(NtagModel.NTAG215, session.info.ntag)
        assertEquals(tag.uid, session.info.uid)
    }

    @Test
    fun mifareClassicIsExplainedNotAnError() = runTest {
        val readout = readOk(FakeTagSession.withoutNtag())

        assertEquals(ReadNotice.UnsupportedFamily(TagFamily.MIFARE_CLASSIC), readout.notice)
        assertNull(readout.message)
    }

    @Test
    fun tagLostDuringReadIsATypedFailure() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG213).apply { failNext(TagError.TagLost) }
        assertEquals(Outcome.Err(TagError.TagLost), read(FakeTagSession.ntag(tag)))
    }

    @Test
    fun tagLostDuringIdentificationIsATypedFailure() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG213).apply { failNext(TagError.TagLost) }
        assertEquals(Outcome.Err(TagError.TagLost), read(FakeTagSession.ntag(tag, identified = false)))
    }
}
