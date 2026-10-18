package com.tagforge.app.feature.tools.security

import com.tagforge.app.infrastructure.nfc.NfcAvailability
import com.tagforge.domain.common.Bytes
import com.tagforge.domain.fakes.FakeNtag21x
import com.tagforge.domain.fakes.FakeSecretStore
import com.tagforge.domain.fakes.FakeSettingsStore
import com.tagforge.domain.fakes.FakeTagSession
import com.tagforge.domain.fakes.FixedClock
import com.tagforge.domain.fakes.InMemoryHistoryRepository
import com.tagforge.domain.fakes.hex
import com.tagforge.domain.history.HistoryKind
import com.tagforge.domain.history.HistoryOutcome
import com.tagforge.domain.history.RecordHistoryUseCase
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.scan.ScanCoordinator
import com.tagforge.domain.security.LockScope
import com.tagforge.domain.security.SecurityFailure
import com.tagforge.domain.security.SecurityOperation
import com.tagforge.domain.settings.AppSettings
import com.tagforge.domain.tag.TagError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import kotlin.random.Random
import kotlin.random.asJavaRandom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * NS-01 for the user: dry-run, then the typed UID, then a second tap that executes. Cancel, back, a wrong text, another
 * tag or a lost tag never end in a half-written tag that is reported as done, and the password never reaches the history.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SecurityViewModelTest {

    private val model = NtagModel.NTAG215
    private val availability = MutableStateFlow(NfcAvailability.ENABLED)
    private val settingsStore = FakeSettingsStore()
    private val history = InMemoryHistoryRepository()
    private val secrets = FakeSecretStore()
    private val clock = FixedClock(Instant.fromEpochMilliseconds(9_000))
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

    private fun viewModel(coordinator: ScanCoordinator, advanced: Boolean = false) = SecurityViewModel(
        coordinator = coordinator,
        recordHistory = RecordHistoryUseCase(history, settingsStore, clock),
        availability = availability,
        settings = MutableStateFlow(AppSettings(advancedMode = advanced)),
        secretStore = secrets,
        random = Random(42),
    )

    private fun blankTag(tagUid: Bytes = uid): FakeNtag21x =
        FakeNtag21x(model, tagUid).also { it.setPage(model.cfg0, "04 00 00 FF".hex()) }

    private fun protectedTag(): FakeNtag21x = blankTag().also {
        it.setPage(model.pwd, "12 34 56 78".hex())
        it.setPage(model.pack, "AB CD 00 00".hex())
        it.setPage(model.cfg1, "80 00 00 00".hex())
        it.setPage(model.cfg0, "04 00 00 04".hex())
    }

    private fun TestScope.tap(coordinator: ScanCoordinator, tag: FakeNtag21x) {
        coordinator.onTagDiscovered(FakeTagSession.ntag(tag))
        advanceUntilIdle()
    }

    private fun SecurityViewModel.fillSetPassword(stored: Boolean = true, remember: Boolean = false) {
        onChoose(SecurityAction.SET_PASSWORD)
        onFormChange { it.copy(passwordHex = "12345678", packHex = "ABCD", storedAck = stored, rememberInKeystore = remember) }
    }

    private suspend fun everythingTheHistoryKept(): String = history.observe().first().joinToString { "${it.summary}|${it.uid}|${it.ndef}|${it.tagType}" }

    private val setPasswordFrames = listOf("A2 85 12 34 56 78", "A2 86 AB CD 00 00", "A2 84 80 00 00 00", "A2 83 04 00 00 04").map { it.hex() }

    // ---- the happy path ----------------------------------------------------------------------------------------------

    @Test
    fun readingFirstShowsTheExactDryRunAndTheSecondTapExecutesIt() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val tag = blankTag()

        vm.fillSetPassword()
        vm.onNext()
        assertEquals(SecuritySheet.Waiting(SecurityStage.READ), vm.uiState.value.sheet)
        tap(coordinator, tag)

        val state = vm.uiState.value
        assertEquals(SecurityStep.DRY_RUN, state.step)
        assertNull(state.sheet)
        assertEquals(setPasswordFrames, state.plan!!.commands)
        assertEquals(emptyList(), tag.writeCommands, "the dry-run writes nothing")

        vm.onTypedChange("e5f6")
        vm.onUnderstoodChange(true)
        assertTrue(vm.uiState.value.canConfirm)
        vm.onConfirm()
        assertEquals(SecuritySheet.Waiting(SecurityStage.EXECUTE), vm.uiState.value.sheet)
        tap(coordinator, tag)

        assertIs<SecuritySheet.Success>(vm.uiState.value.sheet)
        assertEquals(setPasswordFrames, tag.writeCommands)
        assertEquals(4, tag.page(model.cfg0)[3])
    }

    @Test
    fun theSuccessIsLoggedWithoutAnySecretAndTheFormIsWiped() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val tag = blankTag()
        vm.fillSetPassword()
        vm.onNext()
        tap(coordinator, tag)
        vm.onTypedChange("E5F6")
        vm.onUnderstoodChange(true)
        vm.onConfirm()
        tap(coordinator, tag)

        val entry = history.observe().first().single()
        assertEquals(HistoryKind.SECURITY, entry.kind)
        assertEquals(HistoryOutcome.SUCCESS, entry.outcome)
        assertEquals(uid, entry.uid)
        val kept = everythingTheHistoryKept()
        assertFalse(kept.contains("12345678"), kept)
        assertFalse(kept.contains("ABCD"), kept)
        assertEquals("", vm.uiState.value.form.passwordHex, "the password is wiped from memory after the run")
        assertNull(vm.uiState.value.plan)
    }

    // ---- gates before anything is armed ---------------------------------------------------------------------------------

    @Test
    fun theWriteStaysBlockedUntilThePasswordIsMarkedAsStored() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.fillSetPassword(stored = false)

        vm.onNext()
        tap(coordinator, blankTag())

        assertEquals(FormProblem.STORED_NOT_ACKNOWLEDGED, vm.uiState.value.problem)
        assertNull(vm.uiState.value.plan)
        assertNull(vm.uiState.value.sheet)
    }

    @Test
    fun aPasswordThatIsNotEightHexDigitsIsRefused() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.onChoose(SecurityAction.SET_PASSWORD)

        for (bad in listOf("", "1234567", "123456789", "1234567G", "FFFFFFFF")) {
            vm.onFormChange { it.copy(passwordHex = bad, packHex = "ABCD", storedAck = true) }
            vm.onNext()
            assertEquals(FormProblem.PASSWORD_INVALID, vm.uiState.value.problem, "'$bad'")
        }
        vm.onFormChange { it.copy(passwordHex = "12345678", packHex = "ABC") }
        vm.onNext()
        assertEquals(FormProblem.PACK_INVALID, vm.uiState.value.problem)
    }

    @Test
    fun aGeneratedPasswordFillsTheFormWithValidValues() = runTest {
        val vm = viewModel(coordinator())
        vm.onChoose(SecurityAction.SET_PASSWORD)

        vm.onGeneratePassword()

        assertEquals(8, vm.uiState.value.form.passwordHex.length)
        assertEquals(4, vm.uiState.value.form.packHex.length)
        assertFalse(vm.uiState.value.form.storedAck, "a fresh password has to be acknowledged again")
    }

    // ---- NS-01: cancel, back, wrong text, other tag ---------------------------------------------------------------------

    @Test
    fun aWrongTextOrASkippedAcknowledgementNeverArms() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val tag = blankTag()
        vm.fillSetPassword()
        vm.onNext()
        tap(coordinator, tag)

        vm.onTypedChange("E5F7")
        vm.onUnderstoodChange(true)
        assertFalse(vm.uiState.value.canConfirm)
        vm.onConfirm()
        vm.onTypedChange("E5F6")
        vm.onUnderstoodChange(false)
        assertFalse(vm.uiState.value.canConfirm, "irreversible effects also need the Understood tick")
        vm.onConfirm()
        tap(coordinator, tag)

        assertNull(vm.uiState.value.sheet)
        assertEquals(emptyList(), tag.writeCommands)
    }

    @Test
    fun backFromTheDryRunWritesNothingEvenIfTheTagIsTappedAfterwards() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val tag = blankTag()
        vm.fillSetPassword()
        vm.onNext()
        tap(coordinator, tag)
        vm.onTypedChange("E5F6")
        vm.onUnderstoodChange(true)

        assertTrue(vm.onBack(), "back belongs to the dry-run")
        assertEquals(SecurityStep.FORM, vm.uiState.value.step)
        assertNull(vm.uiState.value.plan)
        assertEquals("", vm.uiState.value.typed)
        tap(coordinator, tag)

        assertEquals(emptyList(), tag.writeCommands)
        assertTrue(vm.onBack())
        assertEquals(SecurityStep.CHOOSE, vm.uiState.value.step)
        assertFalse(vm.onBack(), "nothing left to close")
    }

    @Test
    fun dismissingTheExecuteSheetDisarmsAndLeavesTheTagUntouched() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val tag = blankTag()
        vm.fillSetPassword()
        vm.onNext()
        tap(coordinator, tag)
        vm.onTypedChange("E5F6")
        vm.onUnderstoodChange(true)
        vm.onConfirm()

        vm.onSheetDismissed()
        tap(coordinator, tag)

        assertEquals(emptyList(), tag.writeCommands)
        assertEquals(0, history.observe().first().size, "a cancelled run is not an attempt")
    }

    @Test
    fun anotherTagAtExecutionTimeIsNeverTouchedAndTheAttemptIsLogged() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val tag = blankTag()
        vm.fillSetPassword()
        vm.onNext()
        tap(coordinator, tag)
        vm.onTypedChange("E5F6")
        vm.onUnderstoodChange(true)
        vm.onConfirm()
        val other = blankTag("04 A1 B2 C3 D4 E5 F7".hex())

        tap(coordinator, other)

        val failure = assertIs<SecuritySheet.Failure>(vm.uiState.value.sheet)
        assertEquals(TagError.Security(SecurityFailure.UidMismatch), failure.error)
        assertEquals(emptyList(), other.commandLog, "not one frame reached the other tag")
        assertEquals(emptyList(), tag.writeCommands)
        assertEquals(HistoryOutcome.FAILED_OTHER, history.observe().first().single().outcome)
    }

    // ---- G4/G5: a lost tag is never a success ----------------------------------------------------------------------------

    @Test
    fun tagLostAfterThePasswordLeavesProtectionOffAndSaysSoAndRetryFinishesTheJob() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val tag = blankTag()
        vm.fillSetPassword()
        vm.onNext()
        tap(coordinator, tag)
        vm.onTypedChange("E5F6")
        vm.onUnderstoodChange(true)
        vm.onConfirm()
        tag.loseTagAfter(1)
        tap(coordinator, tag)

        val failure = assertIs<SecuritySheet.Failure>(vm.uiState.value.sheet)
        assertEquals(
            TagError.Security(SecurityFailure.Partial(SecurityOperation.SET_PASSWORD, TagError.TagLost, completedWrites = 1, totalWrites = 4)),
            failure.error,
        )
        assertTrue(failure.canRetry)
        assertEquals(0xFF, tag.page(model.cfg0)[3], "protection is NOT active")
        val entry = history.observe().first().single()
        assertEquals(HistoryOutcome.FAILED_TAGLOST, entry.outcome)
        assertTrue(entry.summary.contains("interrupted after 1 of 4 writes"), entry.summary)
        assertFalse(everythingTheHistoryKept().contains("12345678"))

        val repaired = blankTag().also { it.setPage(model.pwd, "12 34 56 78".hex()) }
        vm.onRetry()
        tap(coordinator, repaired)

        assertIs<SecuritySheet.Success>(vm.uiState.value.sheet)
        assertEquals(4, repaired.page(model.cfg0)[3])
    }

    // ---- opt-in Keystore ----------------------------------------------------------------------------------------------------

    @Test
    fun thePasswordIsKeptInTheKeystoreOnlyWhenOptedInAndOnlyAfterSuccess() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val tag = blankTag()
        vm.fillSetPassword(remember = true)
        vm.onNext()
        tap(coordinator, tag)
        vm.onTypedChange("E5F6")
        vm.onUnderstoodChange(true)
        vm.onConfirm()
        tag.loseTagAfter(1)
        tap(coordinator, tag)
        assertEquals(emptyList(), secrets.keys, "nothing is stored for a failed run")

        vm.onRetry()
        tap(coordinator, blankTag().also { it.setPage(model.pwd, "12 34 56 78".hex()) })

        assertEquals(listOf("04A1B2C3D4E5F6"), secrets.keys)
        assertEquals("12345678", secrets.load("04A1B2C3D4E5F6"))
    }

    @Test
    fun withoutOptInNothingIsStored() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val tag = blankTag()
        vm.fillSetPassword(remember = false)
        vm.onNext()
        tap(coordinator, tag)
        vm.onTypedChange("E5F6")
        vm.onUnderstoodChange(true)
        vm.onConfirm()
        tap(coordinator, tag)

        assertEquals(emptyList(), secrets.keys)
    }

    // ---- the other operations -----------------------------------------------------------------------------------------------

    @Test
    fun removingTheProtectionAuthenticatesFirstAndOpensTheTag() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val tag = protectedTag()
        vm.onChoose(SecurityAction.REMOVE_PASSWORD)
        tap(coordinator, tag)   // the attempt limit is read first (W2) ...
        vm.onLimitUnderstood()  // ... and, being unreadable behind the password, acknowledged
        tag.powerCycle()
        vm.onFormChange { it.copy(currentPasswordHex = "12345678") }
        vm.onNext()
        tap(coordinator, tag)
        tag.powerCycle()

        assertEquals("1B 12 34 56 78".hex(), vm.uiState.value.plan!!.commands.first())
        assertTrue(vm.uiState.value.plan!!.effects.isEmpty())
        vm.onTypedChange("E5F6")
        assertTrue(vm.uiState.value.canConfirm, "no irreversible effect, so the typed UID alone is enough")
        vm.onConfirm()
        tap(coordinator, tag)

        assertIs<SecuritySheet.Success>(vm.uiState.value.sheet)
        assertEquals(0xFF, tag.page(model.cfg0)[3])
    }

    @Test
    fun theConfigLockIsItsOwnPlanThatNeedsTheUnderstoodTickToo() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val tag = protectedTag()
        vm.onChoose(SecurityAction.CONFIG_LOCK)
        vm.onProbeLimit()
        tap(coordinator, tag)
        vm.onLimitUnderstood()
        tag.powerCycle()
        vm.onFormChange { it.copy(currentPasswordHex = "12345678", lockConfig = true, authLimit = 3) }
        vm.onNext()
        tap(coordinator, tag)
        tag.powerCycle()

        assertEquals("A2 84 C3 00 00 00".hex(), vm.uiState.value.plan!!.commands.last())
        vm.onTypedChange("E5F6")
        assertFalse(vm.uiState.value.canConfirm)
        vm.onUnderstoodChange(true)
        assertTrue(vm.uiState.value.canConfirm)
        vm.onConfirm()
        tap(coordinator, tag)

        assertIs<SecuritySheet.Success>(vm.uiState.value.sheet)
        assertEquals("C3 00 00 00".hex(), tag.page(model.cfg1))
    }

    @Test
    fun aConfigLockWithNothingSelectedIsRefusedBeforeArming() = runTest {
        val vm = viewModel(coordinator())
        vm.onChoose(SecurityAction.CONFIG_LOCK)

        vm.onNext()

        assertEquals(FormProblem.NOTHING_SELECTED, vm.uiState.value.problem)
        assertNull(vm.uiState.value.sheet)
    }

    @Test
    fun rawLockScopesNeedAdvancedModeAndTheMakeReadOnlyPathDoesNot() = runTest {
        val coordinator = coordinator()
        val off = viewModel(coordinator, advanced = false)
        off.onChoose(SecurityAction.LOCK)
        off.onFormChange { it.copy(lockScope = LockScope.FULL) }
        off.onNext()
        tap(coordinator, blankTag())
        assertEquals(SecurityFailure.AdvancedRequired, off.uiState.value.failure)
        assertEquals(SecurityStep.FORM, off.uiState.value.step)

        val plain = viewModel(coordinator, advanced = false)
        plain.onChoose(SecurityAction.LOCK)
        plain.onFormChange { it.copy(lockScope = LockScope.NDEF_READ_ONLY) }
        plain.onNext()
        tap(coordinator, blankTag())
        assertEquals(SecurityStep.DRY_RUN, plain.uiState.value.step)

        val on = viewModel(coordinator, advanced = true)
        on.onChoose(SecurityAction.LOCK)
        on.onFormChange { it.copy(lockScope = LockScope.FULL) }
        on.onNext()
        tap(coordinator, blankTag())
        assertEquals(SecurityStep.DRY_RUN, on.uiState.value.step)
        assertEquals(3, on.uiState.value.plan!!.commands.size)
    }

    // ---- unlock -----------------------------------------------------------------------------------------------------------------

    @Test
    fun unlockShowsTheAttemptLimitFirstAndNeedsItsAcknowledgementBeforeTheField() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val tag = protectedTag().also { it.setPage(model.cfg1, "03 00 00 00".hex()) }

        vm.onChoose(SecurityAction.UNLOCK)
        assertEquals(SecuritySheet.Waiting(SecurityStage.READ), vm.uiState.value.sheet)
        tap(coordinator, tag)

        assertEquals(3, vm.uiState.value.authLimit?.authLimit)
        assertFalse(vm.uiState.value.passwordFieldEnabled, "after 3 wrong attempts the tag locks for good: acknowledge first")
        vm.onLimitUnderstood()
        assertTrue(vm.uiState.value.passwordFieldEnabled)
    }

    @Test
    fun unlockSendsExactlyOneAuthenticationPerTapAndReportsAWrongPassword() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val tag = protectedTag().also { it.setPage(model.cfg1, "00 00 00 00".hex()) }
        vm.onChoose(SecurityAction.UNLOCK)
        tap(coordinator, tag)
        vm.onFormChange { it.copy(currentPasswordHex = "00000001") }

        vm.onNext()
        tap(coordinator, tag)

        val failure = assertIs<SecuritySheet.Failure>(vm.uiState.value.sheet)
        assertEquals(TagError.AuthFailed(null), failure.error)
        assertEquals(1, tag.commandLog.count { it[0] == 0x1B }, "no automatic retry")
    }

    @Test
    fun unlockWithTheRightPasswordSucceedsAndLeavesNothingInTheHistory() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val tag = protectedTag().also { it.setPage(model.cfg1, "00 00 00 00".hex()) }
        vm.onChoose(SecurityAction.UNLOCK)
        tap(coordinator, tag)
        vm.onFormChange { it.copy(currentPasswordHex = "12345678") }

        vm.onNext()
        tap(coordinator, tag)

        val success = assertIs<SecuritySheet.Success>(vm.uiState.value.sheet)
        assertEquals("AB CD".hex(), success.pack)
        assertEquals(0, history.observe().first().size)
    }

    // ---- W3: the generated secrets come from a CSPRNG ------------------------------------------------------------------

    private fun TestScope.productionDefaultViewModel() = SecurityViewModel(
        coordinator = coordinator(),
        recordHistory = RecordHistoryUseCase(history, settingsStore, clock),
        availability = availability,
        settings = MutableStateFlow(AppSettings()),
        secretStore = secrets,
    )

    @Test
    fun theDefaultRandomSourceIsACryptographicallySecureOne() = runTest {
        val random = productionDefaultViewModel().random

        assertTrue(random !== Random.Default, "kotlin Random.Default is not a CSPRNG")
        assertIs<java.security.SecureRandom>(random.asJavaRandom())
    }

    @Test
    fun generatedPasswordsFromTheDefaultSourceAreNeverRepeatedAcrossViewModels() = runTest {
        val generated = (1..8).map {
            productionDefaultViewModel().also { vm ->
                vm.onChoose(SecurityAction.SET_PASSWORD)
                vm.onGeneratePassword()
            }.uiState.value.form.passwordHex
        }

        assertTrue(generated.all { it.length == 8 }, "8 hex digits each: $generated")
        assertEquals(generated.size, generated.toSet().size, "an unseeded CSPRNG does not repeat: $generated")
    }

    // ---- W2: no password attempt before the attempt limit is known and acknowledged ----------------------------------------

    private fun SecurityViewModel.pwAuthFrames(tag: FakeNtag21x) = tag.commandLog.count { it[0] == 0x1B }

    @Test
    fun theUnlockFieldStaysDisabledUntilTheAttemptLimitHasBeenRead() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)

        vm.onChoose(SecurityAction.UNLOCK)

        assertEquals(null, vm.uiState.value.authLimit)
        assertFalse(vm.uiState.value.passwordFieldEnabled, "an unread limit is not a limit of 0")
    }

    @Test
    fun unlockCannotArmAnAuthenticationWhileTheProbeIsStillOutstanding() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val tag = protectedTag()
        vm.onChoose(SecurityAction.UNLOCK)
        vm.onSheetDismissed()   // walked away from the probe tap
        vm.onFormChange { it.copy(currentPasswordHex = "00000001") }

        vm.onNext()
        tap(coordinator, tag)

        assertNull(vm.uiState.value.sheet, "nothing was armed")
        assertEquals(0, vm.pwAuthFrames(tag), "not a single wrong guess reached the tag")
    }

    @Test
    fun anAttemptLimitThatCannotBeReadNeedsTheAcknowledgementLikeANonZeroOne() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val tag = protectedTag()   // PROT = 1: the configuration page is behind the password

        vm.onChoose(SecurityAction.UNLOCK)
        tap(coordinator, tag)

        assertEquals(com.tagforge.domain.security.AuthLimitInfo(null), vm.uiState.value.authLimit)
        assertFalse(vm.uiState.value.passwordFieldEnabled, "unknown limit: could be 15 attempts before a permanent lock")
        vm.onLimitUnderstood()
        assertTrue(vm.uiState.value.passwordFieldEnabled)
    }

    @Test
    fun aTypedCurrentPasswordIsNotSentInAnyFlowBeforeTheLimitCheck() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val tag = protectedTag()
        vm.fillSetPassword()
        vm.onFormChange { it.copy(currentPasswordHex = "00000001") }

        vm.onNext()
        tap(coordinator, tag)

        assertNull(vm.uiState.value.sheet)
        assertEquals(0, vm.pwAuthFrames(tag))
    }

    @Test
    fun afterTheLimitCheckAndTheAcknowledgementAnyFlowMayTryItsCurrentPassword() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        val tag = protectedTag()
        vm.fillSetPassword()

        vm.onProbeLimit()
        assertEquals(SecuritySheet.Waiting(SecurityStage.READ), vm.uiState.value.sheet)
        tap(coordinator, tag)
        assertFalse(vm.uiState.value.passwordFieldEnabled, "unreadable limit: acknowledge first")
        vm.onLimitUnderstood()
        assertTrue(vm.uiState.value.passwordFieldEnabled)
        tag.powerCycle()
        vm.onFormChange { it.copy(currentPasswordHex = "12345678") }
        vm.onNext()
        tap(coordinator, tag)

        assertEquals(SecurityStep.DRY_RUN, vm.uiState.value.step)
        assertEquals(1, vm.pwAuthFrames(tag), "exactly the one typed attempt")
    }

    @Test
    fun aFlowWithoutAPasswordNeverNeedsTheLimitCheck() = runTest {
        val coordinator = coordinator()
        val vm = viewModel(coordinator)
        vm.fillSetPassword()

        assertTrue(vm.uiState.value.canGoNext)
        vm.onNext()
        tap(coordinator, blankTag())

        assertEquals(SecurityStep.DRY_RUN, vm.uiState.value.step)
    }

    @Test
    fun acknowledgingBeforeTheLimitWasReadDoesNotEnableTheField() = runTest {
        val vm = viewModel(coordinator())
        vm.onChoose(SecurityAction.UNLOCK)

        vm.onLimitUnderstood()

        assertFalse(vm.uiState.value.passwordFieldEnabled)
    }
}
