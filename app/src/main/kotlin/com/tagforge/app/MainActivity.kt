package com.tagforge.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tagforge.app.feature.productivity.ShortcutLinks
import com.tagforge.app.feature.productivity.ShortcutTarget
import com.tagforge.app.infrastructure.nfc.NfcAvailability
import com.tagforge.app.infrastructure.nfc.NfcReaderController
import com.tagforge.app.navigation.TagForgeApp
import com.tagforge.app.ui.theme.TagForgeTheme
import com.tagforge.domain.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var readerController: NfcReaderController

    /** The task runner's "Fix" button asks for the Permission Center. Consumed once the screen is open. */
    private val openPermissionCenter = MutableStateFlow(false)

    /** A launcher shortcut or widget button asked for a screen. Consumed once the navigation has happened. */
    private val pendingShortcut = MutableStateFlow<ShortcutTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as TagForgeApplication).container
        openPermissionCenter.value = intent.getBooleanExtra(EXTRA_OPEN_PERMISSION_CENTER, false)
        pendingShortcut.value = shortcutOf(intent)

        // Reader mode lives and dies with the resumed Activity (TS-01); no foreground dispatch.
        readerController = NfcReaderController(this, container.scanCoordinator, container.ndefCodec, container.dispatchers.io)
        lifecycle.addObserver(readerController)

        // NFC switched on while the app is in front: start reading without a restart (AS-02).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                container.nfcAvailabilityMonitor.availability.collect { availability ->
                    if (availability == NfcAvailability.ENABLED) readerController.enable() else readerController.disable()
                }
            }
        }

        setContent {
            val settings by container.settingsStore.settings.collectAsStateWithLifecycle(AppSettings())
            val permissionCenterRequested by openPermissionCenter.collectAsStateWithLifecycle()
            val shortcut by pendingShortcut.collectAsStateWithLifecycle()
            TagForgeTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                TagForgeApp(
                    openPermissionCenter = permissionCenterRequested,
                    onPermissionCenterOpened = { openPermissionCenter.value = false },
                    shortcut = shortcut,
                    onShortcutOpened = { pendingShortcut.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_PERMISSION_CENTER, false)) openPermissionCenter.value = true
        shortcutOf(intent)?.let { pendingShortcut.value = it }
    }

    private fun shortcutOf(intent: Intent): ShortcutTarget? =
        if (intent.action == Intent.ACTION_VIEW) ShortcutLinks.parse(intent.dataString) else null

    companion object {
        const val EXTRA_OPEN_PERMISSION_CENTER = "com.tagforge.app.extra.OPEN_PERMISSION_CENTER"
    }
}
