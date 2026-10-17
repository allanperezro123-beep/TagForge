package com.tagforge.app.feature.settings

import android.os.Build
import androidx.lifecycle.ViewModel
import com.tagforge.app.infrastructure.nfc.NfcAvailability
import com.tagforge.domain.task.DeviceFacts
import com.tagforge.domain.task.SpecialGrant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the Permission Center lists. QUERY_ALL_PACKAGES, SEND_SMS and CALL_PHONE are deliberately NOT here: the app
 * never asks for them (SMS and calls only open the messaging and dialer apps).
 */
enum class PermissionKind {
    WRITE_SETTINGS,
    NOTIFICATION_POLICY,
    NOTIFICATIONS,
    NFC,
    ALARMS,
    ;

    companion object {
        /** The row that explains and fixes [grant]. */
        fun of(grant: SpecialGrant): PermissionKind = when (grant) {
            SpecialGrant.WRITE_SETTINGS -> WRITE_SETTINGS
            SpecialGrant.NOTIFICATION_POLICY -> NOTIFICATION_POLICY
            SpecialGrant.POST_NOTIFICATIONS -> NOTIFICATIONS
        }
    }
}

enum class PermissionState {
    GRANTED,
    NEEDS_GRANT,

    /** Nothing to grant on this Android version or device (notifications before Android 13, a phone without NFC). */
    NOT_AVAILABLE,
}

data class PermissionRow(val kind: PermissionKind, val state: PermissionState) {
    /** The Grant button is shown only when the user can actually do something. */
    val canGrant: Boolean get() = state == PermissionState.NEEDS_GRANT
}

data class PermissionCenterUiState(val rows: List<PermissionRow> = emptyList()) {
    val needAttention: Int get() = rows.count { it.canGrant }
}

/** Live view of the device: the grants that feed the feasibility matrix plus the NFC adapter. */
data class PermissionSnapshot(val facts: DeviceFacts, val nfc: NfcAvailability)

fun interface PermissionStatusSource {
    fun snapshot(): PermissionSnapshot
}

/**
 * State holder for the Permission Center. Statuses live in the system, not here: [refresh] re-reads them, and the
 * screen calls it every time it is resumed so a grant made in a system screen shows up without a restart.
 */
class PermissionCenterViewModel(private val source: PermissionStatusSource) : ViewModel() {

    private val _uiState = MutableStateFlow(rowsOf(source.snapshot()))
    val uiState: StateFlow<PermissionCenterUiState> = _uiState.asStateFlow()

    fun refresh() {
        _uiState.value = rowsOf(source.snapshot())
    }

    private fun rowsOf(snapshot: PermissionSnapshot): PermissionCenterUiState {
        val facts = snapshot.facts
        return PermissionCenterUiState(
            listOf(
                PermissionRow(PermissionKind.WRITE_SETTINGS, granted(facts.writeSettingsGranted)),
                PermissionRow(PermissionKind.NOTIFICATION_POLICY, granted(facts.notificationPolicyGranted)),
                PermissionRow(
                    PermissionKind.NOTIFICATIONS,
                    if (facts.apiLevel < Build.VERSION_CODES.TIRAMISU) PermissionState.NOT_AVAILABLE else granted(facts.postNotificationsGranted),
                ),
                PermissionRow(
                    PermissionKind.NFC,
                    when (snapshot.nfc) {
                        NfcAvailability.UNSUPPORTED -> PermissionState.NOT_AVAILABLE
                        NfcAvailability.ENABLED -> PermissionState.GRANTED
                        NfcAvailability.DISABLED -> PermissionState.NEEDS_GRANT
                    },
                ),
                PermissionRow(PermissionKind.ALARMS, PermissionState.GRANTED),
            ),
        )
    }

    private fun granted(value: Boolean) = if (value) PermissionState.GRANTED else PermissionState.NEEDS_GRANT
}
