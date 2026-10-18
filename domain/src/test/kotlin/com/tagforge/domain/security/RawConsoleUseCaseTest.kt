package com.tagforge.domain.security

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.fakes.FakeNtag21x
import com.tagforge.domain.fakes.hex
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.ntag.Region
import com.tagforge.domain.tag.TagError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** NS-08: the console runs read frames freely, confirms user writes lightly and sends reserved writes only after NS-01. */
class RawConsoleUseCaseTest {

    private val model = NtagModel.NTAG215

    private fun authorize(tag: FakeNtag21x, frame: String, light: Boolean = false, plan: ConfirmedPlan? = null, authLimit: Int? = 0): RawCommand {
        val result = RawCommandGuard.authorize(frame.hex(), tag.model, authLimit, advanced = true, lightweightConfirmed = light, plan = plan)
        check(result is Outcome.Ok) { "authorize refused: $result" }
        return result.value
    }

    private suspend fun send(tag: FakeNtag21x, command: RawCommand) = RawConsoleUseCase(command).runOn(sessionOf(tag))

    // ---- safe reads (NS-08 "Safe read") ----------------------------------------------------------------------------

    @Test
    fun getVersionOnAnNtag215ShowsTheKnownResponseWithoutAnyConfirm() = runTest {
        val tag = specTag()

        val response = send(tag, authorize(tag, "60")).value()

        assertEquals(RawReply.Data("00 04 04 02 01 00 11 03".hex()), response.reply)
        assertEquals(emptyList(), tag.writeCommands)
    }

    @Test
    fun aReadReturnsSixteenBytesAndAFastReadTheRequestedRange() = runTest {
        val tag = specTag()

        val read = send(tag, authorize(tag, "30 03")).value().reply as RawReply.Data
        val fast = send(tag, authorize(tag, "3A 03 04")).value().reply as RawReply.Data

        assertEquals(16, read.bytes.size)
        assertEquals(8, fast.bytes.size)
        assertEquals("E1 10 3E 00".hex(), fast.bytes.slice(0, 4))
    }

    // ---- NAK decoding (NS-08) -----------------------------------------------------------------------------------------

    @Test
    fun aNakIsDecodedWithItsDatasheetMeaning() = runTest {
        val tag = specTag()

        val reply = send(tag, authorize(tag, "30 FF")).value().reply

        assertEquals(RawReply.Nak(0x0, NakMeaning.INVALID_ARGUMENT), reply)
        assertEquals(NakMeaning.PARITY_OR_CRC, RawConsoleUseCase.decode(Bytes.of(0x1)).let { (it as RawReply.Nak).meaning })
        assertEquals(NakMeaning.COUNTER_OVERFLOW, (RawConsoleUseCase.decode(Bytes.of(0x4)) as RawReply.Nak).meaning)
        assertEquals(NakMeaning.EEPROM_WRITE, (RawConsoleUseCase.decode(Bytes.of(0x5)) as RawReply.Nak).meaning)
        assertEquals(RawReply.Ack, RawConsoleUseCase.decode(Bytes.of(0x0A)))
        assertEquals(RawReply.Empty, RawConsoleUseCase.decode(Bytes.EMPTY))
    }

    // ---- user pages: lightweight confirm (NS-08 "Unprotected user page write") ---------------------------------------

    @Test
    fun aUserPageWriteProceedsOnceConfirmedAndNeverWithoutIt() = runTest {
        val tag = specTag()
        val refused = RawCommandGuard.authorize("A2 10 01 02 03 04".hex(), model, 0, advanced = true)
        assertEquals(Outcome.Err(RawRefusal.NeedsConfirmation), refused)
        assertEquals(emptyList(), tag.writeCommands, "nothing was sent")

        val response = send(tag, authorize(tag, "A2 10 01 02 03 04", light = true)).value()

        assertEquals(RawReply.Ack, response.reply)
        assertEquals("01 02 03 04".hex(), tag.page(16))
    }

    // ---- reserved pages: NS-01 (NS-08 "Config write guarded") -----------------------------------------------------------

    @Test
    fun aConfigWriteShowsADryRunAndSendsNothingUntilTheUidIsTyped() = runTest {
        val tag = specTag()
        val plan = RawConsoleUseCase.plan("A2 83 04 00 00 04".hex(), stateOf(tag)).value()

        val line = plan.dryRun().single()
        assertEquals(131, line.page)
        assertEquals(Region.CFG0, line.region)
        assertEquals("04 00 00 FF", line.oldHex)
        assertEquals("04 00 00 04", line.newHex)
        assertEquals(setOf(IrreversibleEffect.RAW_RESERVED_PAGE_WRITE), plan.effects)

        assertEquals(null, plan.confirm("0000"), "the wrong text keeps Confirm disabled")
        assertEquals(emptyList(), tag.writeCommands, "cancelling here means nothing was ever sent")
    }

    @Test
    fun theTypedConfirmationLetsTheFrameThroughAndTheTagProvesIt() = runTest {
        val tag = specTag()
        val plan = RawConsoleUseCase.plan("A2 83 04 00 00 04".hex(), stateOf(tag)).value()

        val command = authorize(tag, "A2 83 04 00 00 04", plan = plan.confirmed())
        val response = send(tag, command).value()

        assertEquals(RawReply.Ack, response.reply)
        assertEquals("04 00 00 04".hex(), tag.page(model.cfg0))
        assertEquals(listOf("A2 83 04 00 00 04".hex()), tag.writeCommands)
    }

    @Test
    fun aConfirmedFrameIsNeverSentToAnotherTag() = runTest {
        val tag = specTag()
        val command = authorize(tag, "A2 83 04 00 00 04", plan = RawConsoleUseCase.plan("A2 83 04 00 00 04".hex(), stateOf(tag)).value().confirmed())
        val other = specTag(uid = "04 A1 B2 C3 D4 E5 F7".hex())

        assertEquals(SecurityFailure.UidMismatch, RawConsoleUseCase(command).runOn(sessionOf(other)).securityFailure())
        assertTrue(other.commandLog.isEmpty())
    }

    @Test
    fun theEffectsNameWhatCannotBeUndone() = runTest {
        val state = stateOf(specTag())

        fun effects(frame: String) = RawConsoleUseCase.plan(frame.hex(), state).value().effects
        val raw = IrreversibleEffect.RAW_RESERVED_PAGE_WRITE

        assertEquals(setOf(raw, IrreversibleEffect.LOCK_BITS_NEVER_CLEARED), effects("A2 82 FF 00 00 00"))
        assertEquals(setOf(raw, IrreversibleEffect.LOCK_BITS_NEVER_CLEARED), effects("A2 02 00 00 F0 FF"))
        assertEquals(setOf(raw, IrreversibleEffect.TAG_READ_ONLY_FOREVER), effects("A2 03 00 00 00 0F"))
        assertEquals(setOf(raw), effects("A2 03 00 00 00 00"))
        assertEquals(setOf(raw, IrreversibleEffect.CONFIG_PAGES_LOCKED_FOREVER), effects("A2 84 40 00 00 00"))
        assertEquals(setOf(raw, IrreversibleEffect.AUTH_ATTEMPTS_LIMITED), effects("A2 84 03 00 00 00"))
        assertEquals(setOf(raw), effects("A2 85 12 34 56 78"))
    }

    @Test
    fun aFrameThatDoesNotNeedNs01HasNoPlan() = runTest {
        val state = stateOf(specTag())

        assertEquals(Outcome.Err(RawRefusal.NotReserved), RawConsoleUseCase.plan("A2 10 01 02 03 04".hex(), state))
        assertEquals(Outcome.Err(RawRefusal.NotReserved), RawConsoleUseCase.plan("60".hex(), state))
        assertEquals(Outcome.Err(RawRefusal.Invalid(InvalidReason.BAD_LENGTH)), RawConsoleUseCase.plan("A2 83".hex(), state))
    }

    @Test
    fun anAuthenticationOnATagWithAnAttemptLimitGoesThroughTheProtocolToo() = runTest {
        val tag = specTag()
        tag.setPage(model.pwd, "12 34 56 78".hex())
        tag.setPage(model.pack, "AB CD 00 00".hex())
        tag.setPage(model.cfg1, "03 00 00 00".hex())   // AUTHLIM 3, PROT 0: CFG1 readable
        tag.setPage(model.cfg0, "04 00 00 04".hex())
        val state = stateOf(tag)

        val plan = RawConsoleUseCase.plan("1B 12 34 56 78".hex(), state).value()
        assertEquals(listOf(PlanStep.Authenticate("12 34 56 78".hex())), plan.steps)
        assertEquals(setOf(IrreversibleEffect.RAW_RESERVED_PAGE_WRITE, IrreversibleEffect.AUTH_ATTEMPTS_LIMITED), plan.effects)

        val command = authorize(tag, "1B 12 34 56 78", plan = plan.confirmed(), authLimit = 3)
        assertEquals(RawReply.Ack, send(tag, command).value().reply)
        assertEquals(1, tag.commandLog.count { it[0] == 0x1B })
    }

    // ---- G4: a vanished tag ---------------------------------------------------------------------------------------

    @Test
    fun tagLostDuringACommandIsReportedAsTagLost() = runTest {
        val tag = specTag()
        tag.failNext(TagError.TagLost)

        assertEquals(Outcome.Err(TagError.TagLost), send(tag, authorize(tag, "60")))
    }

    @Test
    fun tagLostInTheMiddleOfAConfirmedWriteIsPartialNotSuccess() = runTest {
        val tag = specTag()
        val command = authorize(tag, "A2 83 04 00 00 04", plan = RawConsoleUseCase.plan("A2 83 04 00 00 04".hex(), stateOf(tag)).value().confirmed())
        tag.failNextAfter(1, TagError.TagLost)   // preflight read passes, the write is lost

        val failure = RawConsoleUseCase(command).runOn(sessionOf(tag)).securityFailure()

        assertEquals(SecurityFailure.Partial(SecurityOperation.RAW_WRITE, TagError.TagLost, completedWrites = 0, totalWrites = 1), failure)
    }
}
