package com.tagforge.domain.tools

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.fakes.FakeNtag21x
import com.tagforge.domain.fakes.FakeTagSession
import com.tagforge.domain.fakes.hex
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.NdefRecord
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.ntag.Region
import com.tagforge.domain.scan.OperationKind
import com.tagforge.domain.scan.OperationPhase
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.TagFamily
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** TT-03 annotated dump and TT-06 export formats. */
class DumpMemoryUseCaseTest {

    private fun dump(session: FakeTagSession): TagResult<DumpReport> =
        runBlocking { DumpMemoryUseCase().execute(session) {} }

    private fun seeded(model: NtagModel): FakeNtag21x {
        val tag = FakeNtag21x(model)
        runBlocking { FakeTagSession.ntag(tag).writeNdef(NdefMessage(listOf(NdefRecord.text("hi", "en")))) }
        return tag
    }

    @Test
    fun aFullDumpOfEveryModelMatchesTheTagPageForPageAndIsAnnotated() {
        for (model in NtagModel.entries) {
            val tag = seeded(model)

            val result = dump(FakeTagSession.ntag(tag))

            assertIs<Outcome.Ok<DumpReport>>(result, "$model")
            assertEquals(tag.snapshot(), result.value.dump.pages, "$model")
            assertEquals(model.totalPages, result.value.dump.annotations.size, "$model")
            assertEquals(Region.PACK, result.value.dump.annotations.last().region, "$model")
        }
    }

    @Test
    fun aProtectedTagGivesTheReadablePagesAndTheUnreadableRange() {
        val tag = FakeNtag21x(NtagModel.NTAG213)
        tag.setPage(NtagModel.NTAG213.cfg0, "00 00 00 04".hex())
        tag.setPage(NtagModel.NTAG213.cfg1, "80 00 00 00".hex())

        val result = dump(FakeTagSession.ntag(tag))

        assertIs<Outcome.Ok<DumpReport>>(result)
        assertEquals(4, result.value.dump.pages.size)
        assertEquals(4..44, result.value.dump.unreadable)
    }

    @Test
    fun mifareClassicIsExplainedNotDumped() {
        val result = dump(FakeTagSession.withoutNtag(TagFamily.MIFARE_CLASSIC))

        assertEquals(Outcome.Err(TagError.Unsupported(TagError.Unsupported.MIFARE_CLASSIC)), result)
    }

    @Test
    fun theKindIsMemoryDumpAndPhasesAreReported() {
        val phases = mutableListOf<OperationPhase>()
        runBlocking { DumpMemoryUseCase().execute(FakeTagSession.ntag(seeded(NtagModel.NTAG213))) { phases += it } }

        assertEquals(OperationKind.MEMORY_DUMP, DumpMemoryUseCase().kind)
        assertEquals(OperationPhase.CONNECTING, phases.first())
        assertTrue(OperationPhase.READING in phases)
    }

    // ---- exporters (TT-06) --------------------------------------------------------------------------------------------

    @Test
    fun textExportIsOneHexLinePerPageAfterACommentHeader() {
        val tag = seeded(NtagModel.NTAG213)
        val report = (dump(FakeTagSession.ntag(tag)) as Outcome.Ok).value

        val lines = DumpFormats.toText(report).lines().filter { it.isNotEmpty() }
        val header = lines.takeWhile { it.startsWith("#") }
        val rows = lines.drop(header.size)

        assertEquals(45, rows.size)
        assertTrue(header.any { it.contains("04:A2:3B:1C:5D:6E:80") }, "the UID is in the header")
        assertTrue(header.any { it.contains("NTAG213") })
        assertEquals("04: 01 03 A0 0C |....|", rows[4])
    }

    @Test
    fun textExportShowsPwdAndPackAsZerosEvenIfTheDumpHoldsSomethingElse() {
        val model = NtagModel.NTAG213
        val pages = (0 until model.totalPages).map { "11 22 33 44".hex() }
        val report = DumpReport(FakeTagSession.ntag(FakeNtag21x(model)).info, com.tagforge.domain.ntag.MemoryDump(model, pages, null))

        val rows = DumpFormats.toText(report).lines().filter { it.matches(Regex("^[0-9A-F]{2}: .*")) }

        assertEquals("2B: 00 00 00 00 |....|", rows[43])
        assertEquals("2C: 00 00 00 00 |....|", rows[44])
        assertEquals("2A: 11 22 33 44 |.\"3D|", rows[42])
    }

    @Test
    fun binaryExportIsThePagesBackToBack() {
        val tag = seeded(NtagModel.NTAG215)
        val report = (dump(FakeTagSession.ntag(tag)) as Outcome.Ok).value

        val bin = DumpFormats.toBinary(report.dump)

        assertEquals(135 * 4, bin.size)
        assertEquals(Bytes.concat(tag.snapshot()), bin)
    }

    @Test
    fun aPartialDumpExportsOnlyWhatWasRead() {
        val report = DumpReport(
            FakeTagSession.ntag(FakeNtag21x(NtagModel.NTAG213)).info,
            com.tagforge.domain.ntag.MemoryDump(NtagModel.NTAG213, listOf("04 A2 3B 1C".hex(), "5D 6E 80 00".hex()), 2..44),
        )

        assertEquals(8, DumpFormats.toBinary(report.dump).size)
        assertEquals(2, DumpFormats.toText(report).lines().count { it.matches(Regex("^[0-9A-F]{2}: .*")) })
        assertTrue(DumpFormats.toText(report).contains("# Unreadable pages 2-44"))
    }
}
