package com.tagforge.domain.security

import com.tagforge.domain.common.Outcome
import com.tagforge.domain.fakes.FakeNtag21x
import com.tagforge.domain.fakes.hex
import com.tagforge.domain.ntag.NtagCommands
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.tag.TagError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** NS-06: permanent locks. Platform `makeReadOnly` for everyone, raw lock bits only in Advanced mode, OR semantics, no false success. */
class LockTagUseCaseTest {

    private suspend fun plan(tag: FakeNtag21x, scope: LockScope, advanced: Boolean = true) =
        LockTagUseCase.plan(stateOf(tag), LockRequest(scope, advanced))

    private suspend fun lock(tag: FakeNtag21x, scope: LockScope) =
        LockTagUseCase(plan(tag, scope).value().confirmed()).runOn(sessionOf(tag))

    // ---- makeReadOnly (NS-06 "makeReadOnly") --------------------------------------------------------------------------

    @Test
    fun makeReadOnlyIsAvailableWithoutAdvancedModeAndTheRescanShowsItNotWritable() = runTest {
        val tag = specTag()
        val planned = plan(tag, LockScope.NDEF_READ_ONLY, advanced = false).value()

        assertEquals(listOf(PlanStep.MakeReadOnly), planned.steps)
        assertEquals(setOf(IrreversibleEffect.LOCK_BITS_NEVER_CLEARED, IrreversibleEffect.TAG_READ_ONLY_FOREVER), planned.effects)

        val session = sessionOf(tag)
        assertTrue(session.info.ndef!!.isWritable)
        LockTagUseCase(planned.confirmed()).runOn(session).value()
        assertFalse(sessionOf(tag).info.ndef!!.isWritable, "a re-scan reports isWritable=false")
    }

    @Test
    fun aTagThatCannotBeMadeReadOnlyIsRefusedBeforeAnything() = runTest {
        val tag = specTag()
        val state = stateOf(tag)
        val stuck = state.copy(info = state.info.copy(ndef = state.info.ndef!!.copy(canMakeReadOnly = false)))

        assertEquals(SecurityFailure.CannotMakeReadOnly, LockTagUseCase.plan(stuck, LockRequest(LockScope.NDEF_READ_ONLY, advanced = true)).failure())
    }

    // ---- raw locks are Advanced only ------------------------------------------------------------------------------------

    @Test
    fun everyRawScopeNeedsAdvancedMode() = runTest {
        val tag = specTag()

        for (scope in LockScope.entries.filter { it != LockScope.NDEF_READ_ONLY }) {
            assertEquals(SecurityFailure.AdvancedRequired, plan(tag, scope, advanced = false).failure(), "$scope")
        }
        assertEquals(emptyList(), tag.writeCommands)
    }

    // ---- exact frames ---------------------------------------------------------------------------------------------------

    @Test
    fun theRawScopesProduceTheSpecifiedFrames() = runTest {
        val tag = specTag()

        assertEquals(listOf("A2 03 00 00 00 0F"), plan(tag, LockScope.CC_ONLY).value().commands.map { it.toHex(" ") })
        assertEquals(listOf("A2 02 00 00 F0 FF"), plan(tag, LockScope.STATIC_PAGES).value().commands.map { it.toHex(" ") })
        assertEquals(listOf("A2 82 FF 00 00 00"), plan(tag, LockScope.DYNAMIC_PAGES).value().commands.map { it.toHex(" ") })
    }

    @Test
    fun dynamicFramesFollowTheChip() = runTest {
        assertEquals("A2 28 FF 0F 00 00".hex(), plan(specTag(NtagModel.NTAG213), LockScope.DYNAMIC_PAGES).value().commands.single())
        assertEquals("A2 E2 FF 3F 00 00".hex(), plan(specTag(NtagModel.NTAG216), LockScope.DYNAMIC_PAGES).value().commands.single())
    }

    @Test
    fun theFullLockWritesTheCapabilityContainerFirstAndTheStaticBytesLast() = runTest {
        val frames = plan(specTag(), LockScope.FULL).value().commands.map { it.toHex(" ") }

        assertEquals(listOf("A2 03 00 00 00 0F", "A2 82 FF 00 00 00", "A2 02 00 00 F8 FF"), frames)
    }

    // ---- OR semantics (NS-06 "OR semantics in the fake") ----------------------------------------------------------------

    @Test
    fun lockBitsOnlyEverAccumulate() = runTest {
        val tag = specTag()
        lock(tag, LockScope.STATIC_PAGES).value()

        assertEquals("F0 FF".hex(), tag.page(2).slice(2))
        assertTrue((4..15).all(tag::isPageLocked), "pages 4-15 are locked")

        tag.transceive(NtagCommands.write(2, "00 00 00 00".hex()))
        assertEquals("F0 FF".hex(), tag.page(2).slice(2), "writing zeros clears nothing")
    }

    @Test
    fun theFullLockMakesTheTagReadOnlyEverywhere() = runTest {
        val tag = specTag()
        lock(tag, LockScope.FULL).value()

        assertEquals("E1 10 3E 0F".hex(), tag.page(3))
        assertTrue((3..tag.model.userEnd).all(tag::isPageLocked), "CC, static and dynamic pages")
        assertEquals(Outcome.Ok("00".hex()), tag.transceive(NtagCommands.write(10, "01 02 03 04".hex())))
    }

    @Test
    fun anAlreadyLockedTagHasNothingToDo() = runTest {
        val tag = specTag()
        lock(tag, LockScope.FULL).value()

        assertEquals(SecurityFailure.NothingToChange, plan(tag, LockScope.FULL).failure())
        assertEquals(SecurityFailure.NothingToChange, plan(tag, LockScope.CC_ONLY).failure())
    }

    // ---- G5: never a false success ---------------------------------------------------------------------------------------

    @Test
    fun tagLostMidLockIsAPartialFailureNeverASuccess() = runTest {
        val tag = specTag()
        val planned = plan(tag, LockScope.FULL).value()
        tag.loseTagAfter(tag.writeCommands.size + 1)

        val failure = LockTagUseCase(planned.confirmed()).runOn(sessionOf(tag)).securityFailure()

        assertEquals(SecurityFailure.Partial(SecurityOperation.LOCK, TagError.TagLost, completedWrites = 1, totalWrites = 3), failure)
        assertEquals("E1 10 3E 0F".hex(), tag.page(3), "the first write landed: the tag is partially locked")
    }

    @Test
    fun aDifferentTagIsNeverLocked() = runTest {
        val tag = specTag()
        val confirmed = plan(tag, LockScope.FULL).value().confirmed()
        val other = specTag(uid = "04 A1 B2 C3 D4 E5 F7".hex())

        assertEquals(SecurityFailure.UidMismatch, LockTagUseCase(confirmed).runOn(sessionOf(other)).securityFailure())
        assertEquals(emptyList(), other.commandLog)
    }

    @Test
    fun theLockIsVerifiedByReadBack() = runTest {
        val tag = specTag()
        val planned = plan(tag, LockScope.STATIC_PAGES).value()

        assertEquals(listOf(Check.PageHasBits(2, "00 00 F0 FF".hex())), planned.checks)
    }
}
