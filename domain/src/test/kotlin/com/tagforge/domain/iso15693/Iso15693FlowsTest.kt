package com.tagforge.domain.iso15693

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.fakes.FakeIso15693
import com.tagforge.domain.fakes.FakeNfcVSession
import com.tagforge.domain.fakes.hex
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.NdefRecord
import com.tagforge.domain.ntag.Region
import com.tagforge.domain.read.ReadFullTagUseCase
import com.tagforge.domain.tag.DetailKey
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.TagTech
import com.tagforge.domain.tag.TechValue
import com.tagforge.domain.tools.CloneTagUseCase
import com.tagforge.domain.tools.DumpMemoryUseCase
import com.tagforge.domain.tools.DumpReport
import com.tagforge.domain.tools.EraseTagUseCase
import com.tagforge.domain.tools.IdentifyTagUseCase
import com.tagforge.domain.write.WriteNdefUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** ISO 15693: the raw dump stays on NfcV, NDEF stays on the platform, and an operation never mixes the two. */
class Iso15693FlowsTest {

    private val hello = NdefMessage(listOf(NdefRecord.text("hello", "en")))

    private suspend fun dump(session: FakeNfcVSession): Outcome<DumpReport, TagError> = DumpMemoryUseCase().execute(session) {}

    @Test
    fun dumpsEveryBlockWithReadSingleBlockAndNeverTouchesThePlatform() = runTest {
        val tag = FakeIso15693(blockCount = 80, blockSize = 4, afi = 0x07)
        (0 until 80).forEach { tag.setBlock(it, Bytes.of(it, 0, 0, 0xFF)) }
        val session = FakeNfcVSession(tag)

        val report = (dump(session) as Outcome.Ok).value

        assertEquals(80, report.dump.pages.size)
        assertEquals(Bytes.of(79, 0, 0, 0xFF), report.dump.pages[79])
        assertNull(report.dump.unreadable)
        assertEquals(0, session.platformCalls)
        assertTrue(tag.otherCommands.isEmpty(), "no raw writes: ${tag.otherCommands}")
        val nfcV = report.info.details.nfcV
        assertEquals(80, nfcV?.blockCount)
        assertEquals(4, nfcV?.blockSize)
        assertEquals(0x07, nfcV?.afi)
    }

    @Test
    fun theCapabilityContainerBlockIsAnnotated() = runTest {
        val tag = FakeIso15693(blockCount = 4).also { it.setBlock(0, "E1 40 04 01".hex()) }
        val annotations = (dump(FakeNfcVSession(tag)) as Outcome.Ok).value.dump.annotations
        assertEquals(listOf(Region.CC, Region.USER, Region.USER, Region.USER), annotations.map { it.region })
    }

    @Test
    fun aTagWithoutGetSystemInfoIsProbedUntilTheBlocksRunOut() = runTest {
        val tag = FakeIso15693(blockCount = 12, supportsSystemInfo = false)
        val dump = (dump(FakeNfcVSession(tag)) as Outcome.Ok).value.dump
        assertEquals(12, dump.pages.size)
        assertNull(dump.unreadable)
    }

    @Test
    fun aSilentGetSystemInfoDropsTheLinkAndTheTagIsReconnectedBeforeReading() = runTest {
        val tag = FakeIso15693(blockCount = 6, dropsLinkOnSystemInfo = true)
        val dump = (dump(FakeNfcVSession(tag)) as Outcome.Ok).value.dump
        assertEquals(6, dump.pages.size)
        assertEquals(1, tag.reconnectCount)
    }

    @Test
    fun aTagBiggerThanTheSingleBlockAddressSpaceIsDumpedUpToThe256thBlock() = runTest {
        val tag = FakeIso15693(uid = FakeIso15693.ST_UID, blockCount = 300)
        val dump = (dump(FakeNfcVSession(tag)) as Outcome.Ok).value.dump
        assertEquals(256, dump.pages.size)
        assertTrue(tag.commandLog.none { it[1] == 0x30 }, "extended commands are not used")
    }

    @Test
    fun aLockedAreaEndsTheDumpWithAnUnreadableRange() = runTest {
        val dump = (dump(FakeNfcVSession(FakeIso15693(blockCount = 80, unreadableFrom = 40))) as Outcome.Ok).value.dump
        assertEquals(40, dump.pages.size)
        assertEquals(40 until 80, dump.unreadable)
    }

    @Test
    fun aTagThatRefusesTheFirstBlockIsProtected() = runTest {
        val result = dump(FakeNfcVSession(FakeIso15693(unreadableFrom = 0)))
        assertEquals(Outcome.Err(TagError.AuthRequired), result)
    }

    @Test
    fun aVanishedTagIsTagLost() = runTest {
        val tag = FakeIso15693().also { it.loseTag() }
        assertEquals(Outcome.Err(TagError.TagLost), dump(FakeNfcVSession(tag)))
    }

    @Test
    fun theReadScreenReadsNdefThroughThePlatformOnlyAndSkipsTheRawDump() = runTest {
        val tag = FakeIso15693()
        val session = FakeNfcVSession(tag, message = hello)
        val scan = (ReadFullTagUseCase().execute(session) {} as Outcome.Ok).value
        assertEquals(hello, scan.readout.message)
        assertNull(scan.dump)
        assertNull(scan.dumpError)
        assertEquals(0, tag.transceiveCount, "one technology per operation: no NfcV frame while reading NDEF")
    }

    @Test
    fun writeEraseAndCloneOfNdefStayOnThePlatform() = runTest {
        val source = FakeNfcVSession(FakeIso15693(), message = hello)
        val target = FakeNfcVSession(FakeIso15693(uid = FakeIso15693.ST_UID))
        val clone = CloneTagUseCase()
        val captured = (clone.readSource().execute(source) {} as Outcome.Ok).value
        assertIs<Outcome.Ok<*>>(clone.writeTarget(captured) { true }.execute(target) {})
        assertEquals(hello, target.message)

        assertIs<Outcome.Ok<*>>(EraseTagUseCase().execute(target) {})
        assertIs<Outcome.Ok<*>>(WriteNdefUseCase(hello, confirmOverwrite = { true }).execute(target) {})
        assertEquals(0, source.tag.transceiveCount + target.tag.transceiveCount)
    }

    @Test
    fun tagInfoCompletesTheDetailsOverNfcVAndNamesTheManufacturer() = runTest {
        val session = FakeNfcVSession(FakeIso15693(blockCount = 80, afi = 0x0A))
        val info = (IdentifyTagUseCase().execute(session) {} as Outcome.Ok).value
        val entries = info.details.sections(info).single { it.tech == TagTech.NFC_V }.entries.associate { it.key to it.value }
        assertEquals(TechValue.Text("80"), entries[DetailKey.BLOCK_COUNT])
        assertEquals(TechValue.Text("4"), entries[DetailKey.BLOCK_SIZE])
        assertEquals(TechValue.Text("0x0A"), entries[DetailKey.AFI])
        assertEquals(TechValue.Text("NXP"), entries[DetailKey.MANUFACTURER])
        assertEquals(0, session.platformCalls)
    }
}
