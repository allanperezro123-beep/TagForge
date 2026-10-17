package com.tagforge.app.feature.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tagforge.app.R
import com.tagforge.app.di.tagForgeViewModel
import com.tagforge.app.infrastructure.android.SystemIntents
import kotlinx.coroutines.launch

/**
 * Container: owns the ViewModel, refreshes it every time the screen resumes (so a grant made in a system screen shows
 * up on return), and turns Grant presses into the right system screen or runtime prompt.
 */
@Composable
fun PermissionCenterRoute(onBack: () -> Unit) {
    val viewModel = tagForgeViewModel { container ->
        PermissionCenterViewModel {
            PermissionSnapshot(container.deviceFacts.snapshot(), container.nfcAvailabilityMonitor.current())
        }
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var askedForNotifications by rememberSaveable { mutableStateOf(false) }

    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val notificationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refresh() }

    fun open(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.permissions_open_failed)) }
        }
    }

    PermissionCenterScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onGrant = { kind ->
            when (kind) {
                PermissionKind.WRITE_SETTINGS -> open(SystemIntents.manageWriteSettings(context.packageName))
                PermissionKind.NOTIFICATION_POLICY -> open(SystemIntents.notificationPolicyAccess())
                PermissionKind.NFC -> open(SystemIntents.nfcSettings())
                PermissionKind.ALARMS -> Unit
                PermissionKind.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // After a "don't ask again" the system shows nothing: send the user to the app's notification settings.
                    val blocked = askedForNotifications && activity != null &&
                        !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
                    if (blocked) {
                        open(SystemIntents.appNotificationSettings(context.packageName))
                    } else {
                        askedForNotifications = true
                        notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
        },
    )
}
