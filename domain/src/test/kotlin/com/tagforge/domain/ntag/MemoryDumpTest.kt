package com.tagforge.domain.ntag

import com.tagforge.domain.common.Outcome
import com.tagforge.domain.fakes.FakeNtag21x
import com.tagforge.domain.fakes.FakeTagSession
import com.tagforge.domain.fakes.hex
import com.tagforge.domain.read.ReadMemoryUseCase
import com.tagforge.domain.scan.OperationKind
import com.tagforge.domain.scan.OperationPhase
import com.tagforge.domain.tag.TagError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class MemoryDumpTest {

    private val useCase = ReadMemoryUseCase()

    private suspend fun dump(session: FakeTagSession, phases: MutableList<OperationPhase> = mutableListOf()): MemoryDump {
        val result = useCase.execute(session) { phases += it }
        assertIs<Outcome.Ok<MemoryDump>>(result, "expected Ok but was $result")
        return result.value
    }

    // ---- row format ---------------------------------------------------------------------------

    @Test
    fun rowShowsHexPageNumberHexBytesAndAscii() {
        assertEquals("00: 04 A2 3B 99 |..;.|", MemoryDump.formatRow(0, "04 A2 3B 99".hex()))
        assertEquals("04: 68 69 21 7E |hi!~|", MemoryDump.formatRow(4, "68 69 21 7E".hex()))
    }

    @Test
    fun rowUsesHexPageNumbersAndDotsForNonPrintable() {
        assertEquals("2C: 20 7F 80 1F | ...|", MemoryDump.formatRow(0x2C, "20 7F 80 1F".hex()))
        assertEquals("E6: 00 00 00 00 |....|", MemoryDump.formatRow(230, "00 00 00 00".hex()))
    }

    // ---- reading ------------------------------------------------------------------------------

    @Test
    fun ntag213DumpHasFortyFiveRowsMatchingTheTag() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG213)
        val phases = mutableListOf<OperationPhase>()
        val dump = dump(FakeTagSession.ntag(tag), phases)

        assertEquals(OperationKind.MEMORY_DUMP, useCase.kind)
        assertEquals(NtagModel.NTAG213, dump.model)
        assertEquals(45, dump.pages.size)
        assertEquals(45, dump.rows.size)
        assertEquals(tag.snapshot(), dump.pages)
        assertNull(dump.unreadable)
        assertEquals("00: 04 A2 3B 15 |..;.|", dump.rows.first())
        assertEquals("04: 01 03 A0 0C |....|", dump.rows[4])
        assertEquals(listOf(OperationPhase.CONNECTING, OperationPhase.READING), phases)
    }

    @Test
    fun ntag216DumpHasAllTwoHundredThirtyOnePages() = runTest {
        val dump = dump(FakeTagSession.ntag(FakeNtag21x(NtagModel.NTAG216)))
        assertEquals(231, dump.pages.size)
        assertEquals("E6:", dump.rows.last().take(3))
    }

    @Test
    fun protectedTagKeepsReadablePagesAndReportsTheUnreadableRange() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG215)
        tag.setPage(NtagModel.NTAG215.cfg0, "00 00 00 04".hex())   // AUTH0 = 4
        tag.setPage(NtagModel.NTAG215.cfg1, "80 00 00 00".hex())   // PROT

        val dump = dump(FakeTagSession.ntag(tag))

        assertEquals(4, dump.pages.size)
        assertEquals(4..134, dump.unreadable)
        assertEquals(tag.snapshot().take(4), dump.pages)
    }

    @Test
    fun partiallyProtectedTagCutsAtTheFirstBlockedChunk() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG215)
        tag.setPage(NtagModel.NTAG215.cfg0, "00 00 00 10".hex())   // AUTH0 = page 16
        tag.setPage(NtagModel.NTAG215.cfg1, "80 00 00 00".hex())

        val dump = dump(FakeTagSession.ntag(tag))

        assertEquals(16, dump.pages.size)
        assertEquals(16..134, dump.unreadable)
    }

    @Test
    fun unidentifiedTagIsDumpedWithPlainReadForSixteenPages() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG213).apply { getVersionNaks = true }

        val dump = dump(FakeTagSession.ntag(tag, identified = false))

        assertNull(dump.model)
        assertEquals(16, dump.pages.size)
        assertEquals(tag.snapshot().take(16), dump.pages)
    }

    @Test
    fun tagLostWhileDumpingIsATypedFailure() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG215).apply { failNext(TagError.TagLost) }
        assertEquals(Outcome.Err(TagError.TagLost), useCase.execute(FakeTagSession.ntag(tag)) {})
    }

    @Test
    fun tagsWithoutANfcAChannelAreUnsupported() = runTest {
        val result = useCase.execute(FakeTagSession.withoutNtag()) {}
        assertEquals(Outcome.Err(TagError.Unsupported("NfcA")), result)
    }
}
