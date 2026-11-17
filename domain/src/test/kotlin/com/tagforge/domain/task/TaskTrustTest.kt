package com.tagforge.domain.task

import com.tagforge.domain.settings.AppSettings
import com.tagforge.domain.settings.TaskExecutionPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** UX-A2: tag content is untrusted, so skipping the confirmation sheet is opt-in and narrow. */
class TaskTrustTest {

    private val ringer = TaskAction.SetRinger(RingerMode.VIBRATE)
    private val secure = TaskAction.HttpRequest(url = "https://example.com/hook", method = HttpMethod.GET)
    private val insecure = TaskAction.HttpRequest(url = "http://example.com/hook", method = HttpMethod.GET)

    private val local = TaskAction.HttpRequest(url = "https://192.168.1.1/on", method = HttpMethod.POST)
    private val localPlain = TaskAction.HttpRequest(url = "http://router.local/on", method = HttpMethod.POST)

    private fun plan(vararg actions: TaskAction, name: String? = "Night", capability: Capability = Capability.Ready) =
        TaskPlanner.plan(TaskPayload(name, actions.toList())) { capability }

    private fun settings(policy: TaskExecutionPolicy, vararg trusted: TaskPlan) =
        trusted.fold(AppSettings(taskExecution = policy)) { s, p -> TaskTrust.trust(s, p.name, p.items.map { it.action }) }

    @Test
    fun everyConfirmationIsOnAndTasksAskByDefault() {
        val defaults = AppSettings()
        assertTrue(defaults.confirmOverwrite)
        assertTrue(defaults.confirmEraseFormat)
        assertEquals(TaskExecutionPolicy.ASK_ALWAYS, defaults.taskExecution)
        assertTrue(defaults.trustedTaskFingerprints.isEmpty())
        assertEquals(TaskExecutionPolicy.ASK_ALWAYS, TaskExecutionPolicy.fromCode("unknown"))
        assertEquals(TaskExecutionPolicy.NEVER_ASK, TaskExecutionPolicy.fromCode(TaskExecutionPolicy.NEVER_ASK.code))
    }

    @Test
    fun theFingerprintIsAStableSha256OfTheCompactEncoding() {
        val first = TaskFingerprint.of(plan(ringer, secure))
        assertEquals(first, TaskFingerprint.of("Night", listOf(ringer, secure)))
        assertEquals(64, first!!.length)
        assertTrue(first.all { it in "0123456789abcdef" })
    }

    @Test
    fun anyChangeToTheTaskChangesTheFingerprint() {
        val base = TaskFingerprint.of(plan(ringer, secure))
        assertNotEquals(base, TaskFingerprint.of(plan(ringer, TaskAction.HttpRequest(url = "https://example.com/other", method = HttpMethod.GET))))
        assertNotEquals(base, TaskFingerprint.of(plan(secure, ringer)))
        assertNotEquals(base, TaskFingerprint.of(plan(ringer, secure, name = "Day")))
        assertNotEquals(base, TaskFingerprint.of(plan(ringer)))
    }

    @Test
    fun askAlwaysNeverSkipsTheSheetEvenForATrustedTask() {
        val p = plan(ringer)
        assertNull(TaskTrust.autoConfirm(settings(TaskExecutionPolicy.ASK_ALWAYS, p), p))
    }

    @Test
    fun ownTasksSkipTheSheetOnlyForExactlyTheTaskCreatedHere() {
        val mine = plan(ringer, secure)
        val settings = settings(TaskExecutionPolicy.OWN_TASKS, mine)

        val confirmed = assertNotNull(TaskTrust.autoConfirm(settings, mine))
        assertEquals(ConfirmationSource.POLICY, confirmed.source)
        assertEquals(listOf(ringer, secure), confirmed.plan.items.map { it.action })

        val modified = plan(ringer, TaskAction.HttpRequest(url = "https://evil.example/x", method = HttpMethod.GET))
        assertNull(TaskTrust.autoConfirm(settings, modified), "a modified task is a foreign task")
        assertNull(TaskTrust.autoConfirm(settings, plan(TaskAction.OpenUrl("https://example.com"))), "a foreign tag asks")
    }

    @Test
    fun ownTasksAlwaysAskForPlainHttpBlockedActionsOrAMissingGrant() {
        val http = plan(insecure)
        assertNull(TaskTrust.autoConfirm(settings(TaskExecutionPolicy.OWN_TASKS, http), http))
        val blocked = plan(TaskAction.OpenUrl("javascript:alert(1)"))
        assertNull(TaskTrust.autoConfirm(settings(TaskExecutionPolicy.OWN_TASKS, blocked), blocked))
        val needsGrant = plan(ringer, capability = Capability.NeedsGrant(SpecialGrant.WRITE_SETTINGS))
        assertNull(TaskTrust.autoConfirm(settings(TaskExecutionPolicy.OWN_TASKS, needsGrant), needsGrant))
        assertNull(TaskTrust.autoConfirm(settings(TaskExecutionPolicy.OWN_TASKS, plan()), plan()), "nothing to run: show the sheet")
    }

    @Test
    fun neverAskRunsAnyTaskWithoutWarningsAndStaysAPolicyConfirmation() {
        val foreign = plan(ringer, secure, TaskAction.OpenUrl("https://example.com"))
        val confirmed = assertNotNull(TaskTrust.autoConfirm(AppSettings(taskExecution = TaskExecutionPolicy.NEVER_ASK), foreign))
        assertEquals(ConfirmationSource.POLICY, confirmed.source)
        assertNull(TaskTrust.autoConfirm(AppSettings(taskExecution = TaskExecutionPolicy.NEVER_ASK), plan()))
    }

    @Test
    fun neverAskStillAsksForAForeignTaskThatCarriesAnyWarning() {
        val never = AppSettings(taskExecution = TaskExecutionPolicy.NEVER_ASK)
        assertNull(TaskTrust.autoConfirm(never, plan(ringer, insecure)), "plain HTTP from an unknown tag")
        assertNull(TaskTrust.autoConfirm(never, plan(ringer, local)), "a request into the local network from an unknown tag")
        assertNull(TaskTrust.autoConfirm(never, plan(localPlain)), "plain HTTP to a local address")
    }

    @Test
    fun neverAskRunsATrustedTaskEvenWhenItCarriesWarnings() {
        for (action in listOf(insecure, local, localPlain)) {
            val mine = plan(ringer, action)
            val confirmed = assertNotNull(TaskTrust.autoConfirm(settings(TaskExecutionPolicy.NEVER_ASK, mine), mine), action.toString())
            assertEquals(ConfirmationSource.POLICY, confirmed.source)
        }
    }

    @Test
    fun neverAskTrustIsTiedToTheExactTask() {
        val mine = plan(ringer, local)
        val settings = settings(TaskExecutionPolicy.NEVER_ASK, mine)
        assertNull(TaskTrust.autoConfirm(settings, plan(ringer, TaskAction.HttpRequest("https://192.168.1.2/on", HttpMethod.POST))))
    }

    @Test
    fun ownTasksRunATrustedTaskWhoseOnlyWarningIsTheLocalNetwork() {
        for (action in listOf(local, localPlain)) {
            val mine = plan(ringer, action)
            val settings = settings(TaskExecutionPolicy.OWN_TASKS, mine)

            val confirmed = assertNotNull(TaskTrust.autoConfirm(settings, mine), action.toString())
            assertEquals(ConfirmationSource.POLICY, confirmed.source)
            assertEquals(listOf(ringer, action), confirmed.plan.items.map { it.action })
        }
    }

    @Test
    fun ownTasksAskForALocalNetworkTaskThatWasNotCreatedHere() {
        val foreign = plan(ringer, local)
        assertNull(TaskTrust.autoConfirm(settings(TaskExecutionPolicy.OWN_TASKS), foreign))
    }

    @Test
    fun ownTasksNeverRunAPlanThatAlsoHasPlainHttpABlockedActionOrAMissingGrantEvenIfTrusted() {
        val mixed = plan(local, insecure)
        assertNull(TaskTrust.autoConfirm(settings(TaskExecutionPolicy.OWN_TASKS, mixed), mixed), "local + plain HTTP")

        val blocked = plan(local, TaskAction.OpenUrl("javascript:alert(1)"))
        assertNull(TaskTrust.autoConfirm(settings(TaskExecutionPolicy.OWN_TASKS, blocked), blocked), "local + blocked")

        val needsGrant = plan(local, capability = Capability.NeedsGrant(SpecialGrant.WRITE_SETTINGS))
        assertNull(TaskTrust.autoConfirm(settings(TaskExecutionPolicy.OWN_TASKS, needsGrant), needsGrant), "local + missing grant")
    }

    @Test
    fun askAlwaysAsksEvenForATrustedLocalNetworkTask() {
        val mine = plan(local)
        assertNull(TaskTrust.autoConfirm(settings(TaskExecutionPolicy.ASK_ALWAYS, mine), mine))
    }

    @Test
    fun theUserTapIsStillTheOnlyUserSourceAndEveryWarningStillNeedsItsSecondTap() {
        for (action in listOf(insecure, local, localPlain)) {
            val withWarning = plan(action)
            assertTrue(withWarning.requiresSecondTap, action.toString())
            assertNull(withWarning.confirm(acknowledgedInsecure = false), action.toString())
            assertEquals(ConfirmationSource.USER, withWarning.confirm(acknowledgedInsecure = true)!!.source)
        }
        val clean = plan(ringer, secure)
        assertEquals(false, clean.requiresSecondTap)
        assertEquals(ConfirmationSource.USER, clean.confirm(acknowledgedInsecure = false)!!.source)
    }

    @Test
    fun untrustingForgetsTheTaskAndTheTrustedSetStaysBounded() {
        val mine = plan(ringer)
        val trusted = settings(TaskExecutionPolicy.OWN_TASKS, mine)
        val fingerprint = TaskFingerprint.of(mine)!!
        assertTrue(fingerprint in trusted.trustedTaskFingerprints)

        assertNull(TaskTrust.autoConfirm(TaskTrust.untrust(trusted, fingerprint), mine))

        var many = AppSettings()
        repeat(600) { many = TaskTrust.trust(many, "t$it", listOf(ringer)) }
        assertEquals(500, many.trustedTaskFingerprints.size)
    }
}
