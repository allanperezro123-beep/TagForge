package com.tagforge.domain.security

import com.tagforge.domain.common.Outcome
import com.tagforge.domain.fakes.FakeTagSession
import com.tagforge.domain.fakes.hex
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.tag.TagError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/** NS-08 on the first tap: look at the tag, classify the frame, send it only when it is read class. */
class RawConsoleProbeUseCaseTest {

    private suspend fun probe(frame: String, advanced: Boolean = true, tag: com.tagforge.domain.fakes.FakeNtag21x = specTag()) =
        RawConsoleProbeUseCase(frame.hex(), advanced).runOn(sessionOf(tag))

    @Test
    fun aReadClassFrameIsSentRightAwayWithoutAnyConfirm() = runTest {
        val tag = specTag()

        val result = probe("60", tag = tag).value()

        val sent = assertIs<RawProbeResult.Sent>(result)
        assertEquals(RawReply.Data("00 04 04 02 01 00 11 03".hex()), sent.response.reply)
        assertEquals(emptyList(), tag.writeCommands)
        assertEquals(1, tag.commandLog.count { it[0] == 0x60 })
    }

    @Test
    fun theConsoleIsAdvancedOnlyAndNothingReachesTheTagOtherwise() = runTest {
        val tag = specTag()

        val result = probe("60", advanced = false, tag = tag).value()

        assertEquals(RawProbeResult.Refused(RawRefusal.AdvancedRequired), result)
        assertEquals(emptyList(), tag.commandLog)
    }

    @Test
    fun aReservedWriteIsNotSentAndComesBackWithTheTagState() = runTest {
        val tag = specTag()

        val result = probe("A2 83 04 00 00 04", tag = tag).value()

        val needs = assertIs<RawProbeResult.NeedsConfirmation>(result)
        assertEquals(RawClass.Reserved(131, ReservedReason.CFG0), needs.kind)
        assertNotNull(needs.state)
        assertEquals("04 00 00 FF".hex(), needs.state!!.config.cfg0)
        assertEquals(emptyList(), tag.writeCommands)
    }

    @Test
    fun aUserPageWriteAsksForTheLightweightConfirm() = runTest {
        val tag = specTag()

        val needs = assertIs<RawProbeResult.NeedsConfirmation>(probe("A2 10 01 02 03 04", tag = tag).value())

        assertEquals(RawClass.UserWrite, needs.kind)
        assertEquals(emptyList(), tag.writeCommands)
    }

    @Test
    fun anInvalidFrameIsRefusedWithoutBeingSent() = runTest {
        val tag = specTag()

        assertEquals(RawProbeResult.Refused(RawRefusal.Invalid(InvalidReason.BAD_LENGTH)), probe("A2 83", tag = tag).value())
        assertEquals(RawProbeResult.Refused(RawRefusal.Invalid(InvalidReason.PAGE_OUT_OF_RANGE)), probe("A2 FF 00 00 00 00", tag = tag).value())
        assertEquals(emptyList(), tag.writeCommands)
    }

    @Test
    fun anAuthenticationDependsOnTheAttemptLimitTheTagReports() = runTest {
        val open = specTag().also { it.setPage(NtagModel.NTAG215.pwd, "12 34 56 78".hex()) }
        val limited = specTag().also { it.setPage(NtagModel.NTAG215.cfg1, "03 00 00 00".hex()) }

        val free = assertIs<RawProbeResult.Sent>(probe("1B 12 34 56 78", tag = open).value())
        val guarded = assertIs<RawProbeResult.NeedsConfirmation>(probe("1B 12 34 56 78", tag = limited).value())

        assertIs<RawReply.Data>(free.response.reply)
        assertEquals(RawClass.Reserved(null, ReservedReason.AUTH_ATTEMPT_LIMITED), guarded.kind)
        assertEquals(0, limited.commandLog.count { it[0] == 0x1B }, "no attempt was spent")
    }

    @Test
    fun aTagWithoutARawChannelCannotBeProbed() = runTest {
        val classic = FakeTagSession.withoutNtag(techs = setOf(com.tagforge.domain.tag.TagTech.NFC_A))

        val result = RawConsoleProbeUseCase("60".hex(), advanced = true).runOn(classic)

        assertEquals(Outcome.Err(TagError.Unsupported("nfc-a")), result)
    }

    @Test
    fun tagLostWhileProbingIsTagLost() = runTest {
        val tag = specTag()
        tag.failNext(TagError.TagLost)

        assertEquals(Outcome.Err(TagError.TagLost), probe("60", tag = tag))
    }
}
