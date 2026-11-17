package com.tagforge.domain.security

import com.tagforge.domain.fakes.hex
import com.tagforge.domain.ntag.NtagModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** NS-08: which raw frames run freely, which need a light confirm and which go through the typed NS-01 protocol. */
class RawCommandGuardTest {

    private val ntag215 = NtagModel.NTAG215

    private fun classify(frame: String, model: NtagModel? = ntag215, authLimit: Int? = 0) =
        RawCommandGuard.classify(frame.hex(), model, authLimit)

    // ---- read class ------------------------------------------------------------------------------------------------

    @Test
    fun readClassCommandsRunWithoutAnyConfirmation() {
        for (frame in listOf("30 04", "3A 04 07", "60", "39 02", "3C 00")) {
            assertEquals(RawClass.ReadClass, classify(frame), frame)
        }
    }

    @Test
    fun passwordAuthenticationIsReadClassOnlyWhenTheTagHasNoAttemptLimit() {
        assertEquals(RawClass.ReadClass, classify("1B 12 34 56 78", authLimit = 0))
        assertEquals(RawClass.Reserved(null, ReservedReason.AUTH_ATTEMPT_LIMITED), classify("1B 12 34 56 78", authLimit = 3))
        assertEquals(RawClass.Reserved(null, ReservedReason.AUTH_LIMIT_UNKNOWN), classify("1B 12 34 56 78", authLimit = null))
    }

    // ---- writes ----------------------------------------------------------------------------------------------------

    @Test
    fun theSpecExampleOfAConfigWriteIsReserved() {
        assertEquals(RawClass.Reserved(131, ReservedReason.CFG0), classify("A2 83 04 00 00 04"))
    }

    @Test
    fun aUserPageWriteOnlyNeedsALightweightConfirm() {
        assertEquals(RawClass.UserWrite, classify("A2 10 01 02 03 04"))
        assertEquals(RawClass.UserWrite, classify("A2 04 00 00 00 00"))
        assertEquals(RawClass.UserWrite, classify("A2 81 00 00 00 00"), "page 129 is the last user page of NTAG215")
    }

    @Test
    fun everyReservedPageOfEveryChipIsGuarded() {
        for (model in NtagModel.entries) {
            val expected = mapOf(
                2 to ReservedReason.STATIC_LOCK, 3 to ReservedReason.CC, model.dynLock to ReservedReason.DYNAMIC_LOCK,
                model.cfg0 to ReservedReason.CFG0, model.cfg1 to ReservedReason.CFG1,
                model.pwd to ReservedReason.PWD, model.pack to ReservedReason.PACK,
            )
            for ((page, reason) in expected) {
                val frame = "A2 %02X 00 00 00 00".format(page)
                assertEquals(RawClass.Reserved(page, reason), classify(frame, model), "${model.displayName} $frame")
            }
            assertEquals(RawClass.UserWrite, classify("A2 %02X 00 00 00 00".format(model.userEnd), model), "${model.displayName} last user page")
        }
    }

    @Test
    fun compatibilityWriteIsClassifiedByItsPageToo() {
        assertEquals(RawClass.Reserved(131, ReservedReason.CFG0), classify("A0 83"))
        assertEquals(RawClass.UserWrite, classify("A0 10"))
    }

    @Test
    fun withoutAKnownChipEveryWriteIsGuarded() {
        assertEquals(RawClass.Reserved(4, ReservedReason.CHIP_UNKNOWN), classify("A2 04 00 00 00 00", model = null))
        assertEquals(RawClass.ReadClass, classify("30 04", model = null))
    }

    @Test
    fun anOpcodeTheAppDoesNotKnowIsReservedBecauseItMayBeIrreversible() {
        val unknown = RawClass.Reserved(null, ReservedReason.UNKNOWN_OPCODE)
        for (frame in listOf("50 00", "00 11 22 33 44 55 66 77 88 99 AA BB CC DD EE FF", "FF", "A1 02", "C2 00 11")) {
            assertEquals(unknown, classify(frame), frame)
            assertEquals(unknown, classify(frame, model = null, authLimit = null), "$frame on an unidentified chip")
        }
    }

    @Test
    fun knownOpcodesAreNeverClassifiedAsUnknown() {
        val known = listOf("30 04", "3A 04 07", "60", "39 02", "3C 00", "A2 10 01 02 03 04", "A0 10", "1B 12 34 56 78")
        for (frame in known) {
            assertEquals(false, (classify(frame) as? RawClass.Reserved)?.reason == ReservedReason.UNKNOWN_OPCODE, frame)
        }
    }

    // ---- invalid input ---------------------------------------------------------------------------------------------

    @Test
    fun malformedFramesAreNeverSent() {
        assertEquals(RawClass.Invalid(InvalidReason.EMPTY), RawCommandGuard.classify(com.tagforge.domain.common.Bytes.EMPTY, ntag215, 0))
        assertEquals(RawClass.Invalid(InvalidReason.BAD_LENGTH), classify("A2 83 04"))
        assertEquals(RawClass.Invalid(InvalidReason.BAD_LENGTH), classify("30"))
        assertEquals(RawClass.Invalid(InvalidReason.BAD_LENGTH), classify("60 00"))
        assertEquals(RawClass.Invalid(InvalidReason.PAGE_OUT_OF_RANGE), classify("A2 FF 00 00 00 00"))
        assertEquals(RawClass.Invalid(InvalidReason.PAGE_OUT_OF_RANGE), classify("A2 87 00 00 00 00"), "NTAG215 ends at page 134")
    }

    @Test
    fun hexInputIsValidatedBeforeAnythingElse() {
        assertEquals("30 04".hex(), RawCommandGuard.parseHex("30 04"))
        assertEquals("30 04".hex(), RawCommandGuard.parseHex("3004"))
        assertEquals("30 04".hex(), RawCommandGuard.parseHex(" 30:04 "))
        assertNull(RawCommandGuard.parseHex("30 0"))
        assertNull(RawCommandGuard.parseHex("3G 04"))
        assertNull(RawCommandGuard.parseHex(""))
        assertNull(RawCommandGuard.parseHex("   "))
    }

    // ---- authorize: nothing is sent without the right confirmation -----------------------------------------------

    @Test
    fun readClassFramesNeedNeitherConfirmationNorAdvancedModeBeyondTheConsoleItself() {
        val ok = RawCommandGuard.authorize("60".hex(), ntag215, 0, advanced = true)
        assertEquals("60".hex(), (ok as com.tagforge.domain.common.Outcome.Ok).value.frame)
    }

    @Test
    fun theConsoleItselfIsAdvancedOnly() {
        val refused = RawCommandGuard.authorize("60".hex(), ntag215, 0, advanced = false)
        assertEquals(com.tagforge.domain.common.Outcome.Err(RawRefusal.AdvancedRequired), refused)
    }

    private val probed = ProbedTag(SPEC_UID, ntag215)

    @Test
    fun aUserPageWriteWaitsForTheLightweightConfirm() {
        val frame = "A2 10 01 02 03 04".hex()

        assertEquals(
            com.tagforge.domain.common.Outcome.Err(RawRefusal.NeedsConfirmation),
            RawCommandGuard.authorize(frame, ntag215, 0, advanced = true),
        )
        val ok = RawCommandGuard.authorize(frame, ntag215, 0, advanced = true, lightweightFor = probed)
        val command = (ok as com.tagforge.domain.common.Outcome.Ok).value
        assertEquals(frame, command.frame)
        assertEquals(probed, command.boundTo, "the confirm is bound to the probed tag")
    }

    @Test
    fun aReservedWriteIsRefusedWithoutTheTypedConfirmationAndAMismatchedPlanDoesNotCount() {
        val frame = "A2 83 04 00 00 04".hex()
        val refused = RawCommandGuard.authorize(frame, ntag215, 0, advanced = true, lightweightFor = probed)
        assertEquals(com.tagforge.domain.common.Outcome.Err(RawRefusal.NeedsTypedConfirmation), refused)

        val other = SecurityPlan(
            SecurityOperation.RAW_WRITE, SPEC_UID, ntag215,
            listOf(PlanStep.WritePage(131, null, "04 00 00 05".hex())), setOf(IrreversibleEffect.RAW_RESERVED_PAGE_WRITE),
        ).confirmed()
        val mismatch = RawCommandGuard.authorize(frame, ntag215, 0, advanced = true, plan = other)
        assertEquals(com.tagforge.domain.common.Outcome.Err(RawRefusal.NeedsTypedConfirmation), mismatch)
    }

    @Test
    fun anUnknownOpcodeIsRefusedWithTheLightweightConfirmAndNeedsTheTypedPlanForThatExactFrame() {
        val frame = "50 00".hex()

        val light = RawCommandGuard.authorize(frame, ntag215, 0, advanced = true, lightweightFor = probed)
        assertEquals(com.tagforge.domain.common.Outcome.Err(RawRefusal.NeedsTypedConfirmation), light)
        assertEquals(
            com.tagforge.domain.common.Outcome.Err(RawRefusal.NeedsTypedConfirmation),
            RawCommandGuard.authorize(frame, ntag215, 0, advanced = true),
        )

        val other = SecurityPlan(SecurityOperation.RAW_WRITE, SPEC_UID, ntag215, listOf(PlanStep.Raw("50 01".hex())), setOf(IrreversibleEffect.RAW_UNKNOWN_COMMAND)).confirmed()
        assertEquals(
            com.tagforge.domain.common.Outcome.Err(RawRefusal.NeedsTypedConfirmation),
            RawCommandGuard.authorize(frame, ntag215, 0, advanced = true, plan = other),
            "a plan for another frame does not count",
        )

        val plan = SecurityPlan(SecurityOperation.RAW_WRITE, SPEC_UID, ntag215, listOf(PlanStep.Raw(frame)), setOf(IrreversibleEffect.RAW_UNKNOWN_COMMAND)).confirmed()
        val ok = RawCommandGuard.authorize(frame, ntag215, 0, advanced = true, plan = plan)
        assertEquals(frame, (ok as com.tagforge.domain.common.Outcome.Ok).value.frame)
    }

    @Test
    fun invalidFramesAreRefusedWhateverElseIsGiven() {
        val refused = RawCommandGuard.authorize("A2 83".hex(), ntag215, 0, advanced = true, lightweightFor = probed)
        assertEquals(com.tagforge.domain.common.Outcome.Err(RawRefusal.Invalid(InvalidReason.BAD_LENGTH)), refused)
    }
}
