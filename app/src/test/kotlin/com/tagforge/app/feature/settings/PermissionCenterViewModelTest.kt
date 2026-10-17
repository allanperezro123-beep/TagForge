package com.tagforge.app.feature.settings

import com.tagforge.app.infrastructure.nfc.NfcAvailability
import com.tagforge.domain.task.DeviceFacts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** TK-09: every special permission with its live state, refreshed when the user comes back from a system screen. */
class PermissionCenterViewModelTest {

    private class MutableSource(var facts: DeviceFacts, var nfc: NfcAvailability = NfcAvailability.ENABLED) : PermissionStatusSource {
        override fun snapshot() = PermissionSnapshot(facts, nfc)
    }

    private fun facts(api: Int = 34, write: Boolean = false, policy: Boolean = false, notifications: Boolean = false) =
        DeviceFacts(api, write, policy, notifications)

    private fun PermissionCenterViewModel.state(kind: PermissionKind) = uiState.value.rows.single { it.kind == kind }.state

    @Test
    fun listsEverySpecialPermissionInOrder() {
        val vm = PermissionCenterViewModel(MutableSource(facts()))

        assertEquals(
            listOf(
                PermissionKind.WRITE_SETTINGS,
                PermissionKind.NOTIFICATION_POLICY,
                PermissionKind.NOTIFICATIONS,
                PermissionKind.NFC,
                PermissionKind.ALARMS,
            ),
            vm.uiState.value.rows.map { it.kind },
        )
    }

    @Test
    fun ungrantedPermissionsNeedAGrantAndGrantedOnesDoNot() {
        val needs = PermissionCenterViewModel(MutableSource(facts()))
        assertEquals(PermissionState.NEEDS_GRANT, needs.state(PermissionKind.WRITE_SETTINGS))
        assertEquals(PermissionState.NEEDS_GRANT, needs.state(PermissionKind.NOTIFICATION_POLICY))
        assertEquals(PermissionState.NEEDS_GRANT, needs.state(PermissionKind.NOTIFICATIONS))

        val granted = PermissionCenterViewModel(MutableSource(facts(write = true, policy = true, notifications = true)))
        assertEquals(PermissionState.GRANTED, granted.state(PermissionKind.WRITE_SETTINGS))
        assertEquals(PermissionState.GRANTED, granted.state(PermissionKind.NOTIFICATION_POLICY))
        assertEquals(PermissionState.GRANTED, granted.state(PermissionKind.NOTIFICATIONS))
    }

    @Test
    fun grantingWriteSettingsAndReturningUpdatesTheRowWithoutRestart() {
        val source = MutableSource(facts(write = false))
        val vm = PermissionCenterViewModel(source)
        assertEquals(PermissionState.NEEDS_GRANT, vm.state(PermissionKind.WRITE_SETTINGS))

        source.facts = facts(write = true)       // the user flipped the switch in the system screen
        vm.refresh()                             // ON_RESUME

        assertEquals(PermissionState.GRANTED, vm.state(PermissionKind.WRITE_SETTINGS))
        assertEquals(PermissionState.NEEDS_GRANT, vm.state(PermissionKind.NOTIFICATION_POLICY), "other rows are untouched")
    }

    @Test
    fun revokingAGrantIsNoticedToo() {
        val source = MutableSource(facts(policy = true))
        val vm = PermissionCenterViewModel(source)
        assertEquals(PermissionState.GRANTED, vm.state(PermissionKind.NOTIFICATION_POLICY))

        source.facts = facts(policy = false)
        vm.refresh()

        assertEquals(PermissionState.NEEDS_GRANT, vm.state(PermissionKind.NOTIFICATION_POLICY))
    }

    @Test
    fun notificationsAreNotAvailableBeforeAndroid13() {
        listOf(26, 30, 32).forEach {
            val vm = PermissionCenterViewModel(MutableSource(facts(api = it)))
            assertEquals(PermissionState.NOT_AVAILABLE, vm.state(PermissionKind.NOTIFICATIONS), "API $it")
        }
        assertEquals(PermissionState.NEEDS_GRANT, PermissionCenterViewModel(MutableSource(facts(api = 33))).state(PermissionKind.NOTIFICATIONS))
    }

    @Test
    fun nfcRowFollowsTheAdapter() {
        assertEquals(PermissionState.GRANTED, PermissionCenterViewModel(MutableSource(facts(), NfcAvailability.ENABLED)).state(PermissionKind.NFC))
        assertEquals(PermissionState.NEEDS_GRANT, PermissionCenterViewModel(MutableSource(facts(), NfcAvailability.DISABLED)).state(PermissionKind.NFC))
        assertEquals(PermissionState.NOT_AVAILABLE, PermissionCenterViewModel(MutableSource(facts(), NfcAvailability.UNSUPPORTED)).state(PermissionKind.NFC))
    }

    @Test
    fun alarmsAreAlwaysGrantedBecauseTheyAreANormalPermission() {
        assertEquals(PermissionState.GRANTED, PermissionCenterViewModel(MutableSource(facts(api = 26))).state(PermissionKind.ALARMS))
        assertEquals(PermissionState.GRANTED, PermissionCenterViewModel(MutableSource(facts(api = 36))).state(PermissionKind.ALARMS))
    }

    @Test
    fun onlyRowsThatCanBeGrantedOfferAGrantButton() {
        val vm = PermissionCenterViewModel(MutableSource(facts(api = 30, write = true), NfcAvailability.ENABLED))

        val rows = vm.uiState.value.rows.associateBy { it.kind }
        assertFalse(rows.getValue(PermissionKind.WRITE_SETTINGS).canGrant, "already granted")
        assertTrue(rows.getValue(PermissionKind.NOTIFICATION_POLICY).canGrant)
        assertFalse(rows.getValue(PermissionKind.NOTIFICATIONS).canGrant, "not available on API 30")
        assertFalse(rows.getValue(PermissionKind.ALARMS).canGrant)
    }

    @Test
    fun noRowExistsForThePermissionsTheAppPromisesNeverToAskFor() {
        val names = PermissionKind.entries.map { it.name }
        assertTrue(names.none { "QUERY_ALL" in it || "SMS" in it || "CALL" in it }, names.toString())
    }
}
