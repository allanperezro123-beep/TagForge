package com.tagforge.app.feature.tools.security

import com.tagforge.app.infrastructure.nfc.NfcAvailability
import com.tagforge.domain.common.Bytes
import com.tagforge.domain.fakes.FakeNtag21x
import com.tagforge.domain.fakes.FakeTagSession
import com.tagforge.domain.fakes.hex
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.scan.ScanCoordinator
import com.tagforge.domain.security.NakMeaning
import com.tagforge.domain.security.RawReply
import com.tagforge.domain.security.SecurityFailure
import com.tagforge.domain.settings.AppSettings
import com.tagforge.domain.tag.TagError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** NS-08: the console is Advanced only, warns once per session, runs reads freely and guards writes to reserved pages. */
@OptIn(ExperimentalCoroutinesApi::class)
class RawConsoleViewModelTest {

    private val model = NtagModel.NTAG215
    private val availability = MutableStateFlow(NfcAvailability.ENABLED)
    private val acknowledged = MutableStateFlow(true)
    private val uid = "04 A1 B2 C3 D4 E5 F6".hex()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.coordinator() = ScanCoordinator(this, StandardTestDispatcher(testScheduler))

    private fun viewModel(coordinator: ScanCoordinator, advanced: Boolean = true) = RawConsoleViewModel(
        coordinator = coordinator,
        availability = availability,
        settings = MutableStateFlow(AppSettings(advancedMode = advanced)),
        acknowledged = acknowledged,
    )

    private fun blankTag(tagUid: Bytes = uid): FakeNtag21x = FakeNtag21x(model, tagUid).also { it.setPage(model.cfg0, "04 00 00 FF".hex()) }

    private fun TestScope.tap(coordinator: ScanCoordinator, tag: FakeNtag21x) {
        coordinator.onTagDiscovered(FakeTagSession.ntag(tag))
        advanceUntilIdle()
    }

    private fun RawConsoleViewModel.send(text: String) {
        onHexChange(text)
        onSend()
    }

    // ---- the gates ------------------------------------------------------------------------------------------------------

    @Test
    fun theWarningMustBeAcknowledgedOncePerSessionBeforeAnythingIsSent() = runTest {
        acknowledged.value = false
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val tag = blankTag()

        vm.send("60")
        tap(coordinator, tag)

        assertTrue(vm.uiState.value.needsAck)
        assertEquals(emptyList(), tag.commandLog, "nothing was armed")

        vm.onAcknowledge()
        assertFalse(vm.uiState.value.needsAck)
        assertTrue(acknowledged.value, "the acknowledgement outlives this screen for the rest of the session")
        assertFalse(viewModel(coordinator).uiState.value.needsAck)
    }

    @Test
    fun frameTextThatIsNotWholeBytesIsRefusedInline() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val tag = blankTag()

        for (bad in listOf("30 0", "3G 04", "", "   ")) {
            vm.send(bad)
            assertTrue(vm.uiState.value.hexError, "'$bad'")
        }
        tap(coordinator, tag)

        assertNull(vm.uiState.value.sheet)
        assertEquals(emptyList(), tag.commandLog)
        vm.onHexChange("30 04")
        assertFalse(vm.uiState.value.hexError, "typing clears the error")
    }

    @Test
    fun withAdvancedOffNothingCanBeSent() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator, advanced = false)
        val tag = blankTag()

        vm.send("60")
        tap(coordinator, tag)

        assertFalse(vm.uiState.value.canSend)
        assertNull(vm.uiState.value.sheet)
        assertEquals(emptyList(), tag.commandLog)
    }

    // ---- NS-08 "Safe read" / NAK decoding -------------------------------------------------------------------------------

    @Test
    fun aSafeReadShowsTheResponseWithoutAnyConfirm() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val tag = blankTag()

        vm.send("60")
        assertEquals(SecuritySheet.Waiting(SecurityStage.READ), vm.uiState.value.sheet)
        tap(coordinator, tag)

        val entry = vm.uiState.value.log.single()
        assertEquals("60", entry.frameHex)
        assertEquals(ConsoleOutcome.Reply(RawReply.Data("00 04 04 02 01 00 11 03".hex())), entry.outcome)
        assertNull(vm.uiState.value.sheet)
        assertNull(vm.uiState.value.pending)
    }

    @Test
    fun aNakIsLoggedWithItsDatasheetMeaning() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)

        vm.send("30 FF")
        tap(coordinator, blankTag())

        assertEquals(ConsoleOutcome.Reply(RawReply.Nak(0, NakMeaning.INVALID_ARGUMENT)), vm.uiState.value.log.single().outcome)
    }

    // ---- NS-08 "Config write guarded" -----------------------------------------------------------------------------------

    @Test
    fun aConfigWriteShowsTheDryRunAndSendsNothingUntilTheTypedUidMatches() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val tag = blankTag()

        vm.send("A2 83 04 00 00 04")
        tap(coordinator, tag)

        val pending = assertIs<ConsolePending.Typed>(vm.uiState.value.pending)
        assertEquals(131, pending.plan.dryRun().single().page)
        assertEquals(emptyList(), tag.writeCommands)

        vm.onTypedChange("0000")
        assertFalse(vm.uiState.value.canConfirmTyped)
        vm.onTypedConfirm()
        tap(coordinator, tag)
        assertEquals(emptyList(), tag.writeCommands, "a wrong text arms nothing")

        vm.onTypedChange("e5f6")
        assertTrue(vm.uiState.value.canConfirmTyped)
        vm.onTypedConfirm()
        assertEquals(SecuritySheet.Waiting(SecurityStage.EXECUTE), vm.uiState.value.sheet)
        tap(coordinator, tag)

        assertEquals(listOf("A2 83 04 00 00 04".hex()), tag.writeCommands)
        assertEquals(4, tag.page(model.cfg0)[3])
        assertEquals(ConsoleOutcome.Reply(RawReply.Ack), vm.uiState.value.log.last().outcome)
        assertNull(vm.uiState.value.pending)
    }

    @Test
    fun cancellingTheGuardedWriteSendsNothingEvenIfTheTagIsTappedAfterwards() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val tag = blankTag()
        vm.send("A2 83 04 00 00 04")
        tap(coordinator, tag)

        vm.onCancelPending()
        tap(coordinator, tag)

        assertNull(vm.uiState.value.pending)
        assertEquals(emptyList(), tag.writeCommands)
    }

    @Test
    fun aGuardedWriteIsNeverSentToAnotherTag() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val tag = blankTag()
        vm.send("A2 83 04 00 00 04")
        tap(coordinator, tag)
        vm.onTypedChange("E5F6")
        vm.onTypedConfirm()
        val other = blankTag("04 A1 B2 C3 D4 E5 F7".hex())

        tap(coordinator, other)

        val outcome = vm.uiState.value.log.last().outcome
        assertEquals(ConsoleOutcome.Failed(TagError.Security(SecurityFailure.UidMismatch)), outcome)
        assertEquals(emptyList(), other.commandLog)
    }

    // ---- NS-08 "Unprotected user page write" -------------------------------------------------------------------------------

    @Test
    fun aUserPageWriteNeedsOnlyTheLightweightConfirm() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val tag = blankTag()

        vm.send("A2 10 01 02 03 04")
        tap(coordinator, tag)
        assertIs<ConsolePending.Light>(vm.uiState.value.pending)
        assertEquals(emptyList(), tag.writeCommands)

        vm.onLightConfirm()
        tap(coordinator, tag)

        assertEquals("01 02 03 04".hex(), tag.page(16))
        assertEquals(ConsoleOutcome.Reply(RawReply.Ack), vm.uiState.value.log.last().outcome)
    }

    @Test
    fun decliningTheLightweightConfirmWritesNothing() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val tag = blankTag()
        vm.send("A2 10 01 02 03 04")
        tap(coordinator, tag)

        vm.onCancelPending()
        tap(coordinator, tag)

        assertEquals(emptyList(), tag.writeCommands)
    }

    // ---- NS-08 "Console TagLost" ---------------------------------------------------------------------------------------------

    @Test
    fun aVanishedTagIsShownAsTagLostAndTheConsoleIsReadyAgain() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val tag = blankTag().also { it.failNext(TagError.TagLost) }

        vm.send("60")
        tap(coordinator, tag)

        assertEquals(ConsoleOutcome.TagLost, vm.uiState.value.log.single().outcome)
        assertNull(vm.uiState.value.sheet)
        vm.send("60")
        tap(coordinator, blankTag())
        assertEquals(2, vm.uiState.value.log.size, "the console accepts the next frame")
    }

    @Test
    fun dismissingTheWaitingSheetDisarmsSoNothingIsSent() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val tag = blankTag()
        vm.send("60")

        vm.onSheetDismissed()
        tap(coordinator, tag)

        assertEquals(emptyList(), tag.commandLog)
        assertEquals(0, vm.uiState.value.log.size)
    }
}
