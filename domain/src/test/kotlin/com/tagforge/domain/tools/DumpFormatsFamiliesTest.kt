package com.tagforge.domain.tools

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.fakes.FakeChipSession
import com.tagforge.domain.fakes.FakeClassicSession
import com.tagforge.domain.fakes.FakeIso15693
import com.tagforge.domain.fakes.FakeMifareClassic
import com.tagforge.domain.fakes.FakeNfcVSession
import com.tagforge.domain.fakes.FakeType2Chip
import com.tagforge.domain.ports.TagSession
import com.tagforge.domain.type2.Type2Chip
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The share/export formats for the families added in 1.1.0. */
class DumpFormatsFamiliesTest {

    private suspend fun report(session: TagSession): DumpReport = (DumpMemoryUseCase().execute(session) {} as Outcome.Ok).value

    @Test
    fun aClassicTextDumpMarksLockedBlocksInsteadOfInventingZeros() = runTest {
        val card = FakeMifareClassic().also { it.setKeys(1, Bytes.of(1, 2, 3, 4, 5, 6), Bytes.of(1, 2, 3, 4, 5, 6)) }
        val text = DumpFormats.toText(report(FakeClassicSession(card)))
        assertTrue(text.contains("# Locked blocks"), text)
        assertTrue(text.lines().first { it.startsWith("04:") }.contains("??"))
        assertTrue(text.lines().first { it.startsWith("00:") }.contains("1A 2B 3C 4D"))
        assertEquals(64 * 16, DumpFormats.toBinary(report(FakeClassicSession(card)).dump).size)
    }

    @Test
    fun theChipNameInTheHeaderComesFromTheTagInfoForEveryFamily() = runTest {
        val ultralight = DumpFormats.toText(report(FakeChipSession(FakeType2Chip(Type2Chip.ULTRALIGHT_C))))
        assertTrue(ultralight.contains("# Chip MIFARE Ultralight C"), ultralight)
        val icode = DumpFormats.toText(report(FakeNfcVSession(FakeIso15693())))
        assertTrue(icode.contains("# Chip iso15693"), icode)
    }

    @Test
    fun anIso15693UidIsExportedMostSignificantByteFirst() = runTest {
        val text = DumpFormats.toText(report(FakeNfcVSession(FakeIso15693())))
        assertTrue(text.contains("# UID E0:04:01:55:44:33:22:11"), text)
    }
}
