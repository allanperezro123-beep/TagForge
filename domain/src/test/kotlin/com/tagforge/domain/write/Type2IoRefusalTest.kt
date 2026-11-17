package com.tagforge.domain.write

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.fakes.FakeNtag21x
import com.tagforge.domain.fakes.hex
import com.tagforge.domain.ntag.NtagCommands
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.type2.Type2Chip
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * A refused first write is not always "read-only": on a tag protected for write (PROT = 0, page >= AUTH0) the lock bits and
 * the CC are clean and the password is the only explanation. A refusal later in the sequence stays a plain NAK (partial write).
 */
class Type2IoRefusalTest {

    private val model = NtagModel.NTAG215
    private val data = "01 02 03 04".hex()

    private fun tag(): FakeNtag21x = FakeNtag21x(model)

    private fun FakeNtag21x.writeProtectedFrom(page: Int) = setPage(model.cfg0, Bytes.of(0x00, 0x00, 0x00, page))

    private suspend fun execute(tag: FakeNtag21x, vararg pages: Int, withArea: Boolean = true): com.tagforge.domain.common.TagResult<Unit> {
        val area = if (withArea) (Type2Io.readCcAndUserArea(tag, model) as Outcome.Ok).value else null
        return Type2Io.execute(tag, pages.map { PageWrite(it, data) }, model, area)
    }

    @Test
    fun aRefusedFirstWriteOnAWriteProtectedTagIsAuthRequiredNotReadOnly() = runTest {
        val tag = tag().also { it.writeProtectedFrom(4) }

        assertEquals(Outcome.Err(TagError.AuthRequired), execute(tag, 5))
        assertEquals(0, tag.writeCount, "nothing changed")
    }

    @Test
    fun theSameHoldsWhenTheCallerDidNotPassTheAreaItAlreadyRead() = runTest {
        val tag = tag().also { it.writeProtectedFrom(4) }

        assertEquals(Outcome.Err(TagError.AuthRequired), execute(tag, 5, withArea = false))
    }

    @Test
    fun aCcThatSaysReadOnlyIsReadOnlyWithoutAnyProbeRead() = runTest {
        val tag = tag().also { it.writeProtectedFrom(4); it.setPage(3, "E1 10 3E 0F".hex()) }
        val area = (Type2Io.readCcAndUserArea(tag, model) as Outcome.Ok).value
        val readsBefore = tag.commandLog.size

        val result = Type2Io.execute(tag, listOf(PageWrite(5, data)), model, area)

        assertEquals(Outcome.Err(TagError.ReadOnly), result)
        assertEquals(readsBefore + 1, tag.commandLog.size, "only the refused write was sent: the CC the caller read was enough")
    }

    @Test
    fun aStaticLockBitOnTheTargetPageIsReadOnlyEvenWhenAPasswordIsAlsoSet() = runTest {
        val tag = tag().also { it.writeProtectedFrom(4); it.setPage(2, "00 00 20 00".hex()) }   // L5

        assertEquals(Outcome.Err(TagError.ReadOnly), execute(tag, 5))
    }

    @Test
    fun aDynamicLockBitOnTheTargetPageIsReadOnly() = runTest {
        val tag = tag().also { it.setPage(model.dynLock, "01 00 00 BD".hex()) }   // pages 16-31 locked, nothing else

        assertEquals(Outcome.Err(TagError.ReadOnly), execute(tag, 20))
    }

    @Test
    fun aLockBitOnAnotherPageDoesNotMakeItReadOnly() = runTest {
        val tag = tag().also { it.writeProtectedFrom(4); it.setPage(2, "00 00 40 00".hex()) }   // L6 only

        assertEquals(Outcome.Err(TagError.AuthRequired), execute(tag, 5))
    }

    @Test
    fun aRefusedProbeReadAlsoMeansAuthRequired() = runTest {
        val tag = tag().also { it.setPage(model.cfg1, "80 00 00 00".hex()); it.setPage(model.cfg0, "00 00 00 04".hex()) }   // PROT = 1: the dynamic lock page is unreadable

        assertEquals(Outcome.Err(TagError.AuthRequired), execute(tag, 5, withArea = false))
    }

    @Test
    fun aLinkErrorWhileProbingIsReportedAsIsNotGuessed() = runTest {
        val tag = tag().also { it.writeProtectedFrom(4) }
        val area = (Type2Io.readCcAndUserArea(tag, model) as Outcome.Ok).value
        tag.failNextAfter(1, TagError.TagLost)   // the refused write goes through, the probe read does not

        assertEquals(Outcome.Err(TagError.TagLost), Type2Io.execute(tag, listOf(PageWrite(5, data)), model, area))
    }

    @Test
    fun aRefusalOnALaterStepStaysAPlainNakBecauseThePartialWriteIsReal() = runTest {
        val tag = tag().also { it.setPage(2, "00 00 40 00".hex()) }   // L6: page 5 writes, page 6 is refused

        val result = execute(tag, 5, 6)

        assertEquals(Outcome.Err(TagError.Nak(0)), result)
        assertEquals(1, tag.writeCount, "page 5 did change")
    }

    @Test
    fun aChipWithoutPasswordsKeepsReadOnly() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG213).also { it.setPage(2, "00 00 20 00".hex()) }
        val result = Type2Io.execute(tag, listOf(PageWrite(5, data)), Type2Chip.ULTRALIGHT, null)

        assertEquals(Outcome.Err(TagError.ReadOnly), result)
    }

    @Test
    fun withoutALayoutTheOldBehaviourRemains() = runTest {
        val tag = tag().also { it.writeProtectedFrom(4) }

        assertIs<Outcome.Err<TagError>>(Type2Io.execute(tag, listOf(PageWrite(5, data))))
        assertEquals(Outcome.Err(TagError.ReadOnly), Type2Io.execute(tag, listOf(PageWrite(5, data))))
        assertEquals(NtagCommands.WRITE, tag.commandLog.last()[0])
    }
}
