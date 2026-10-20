package com.tagforge.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
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
import com.tagforge.domain.location.SharedText
import com.tagforge.domain.location.SharedTextResult
import com.tagforge.domain.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var readerController: NfcReaderController

    /** The task runner's "Fix" button asks for the Permission Center. Consumed once the screen is open. */
    private val openPermissionCenter = MutableStateFlow(false)

    /** A launcher shortcut or widget button asked for a screen. Consumed once the navigation has happened. */
    private val pendingShortcut = MutableStateFlow<ShortcutTarget?>(null)

    /** Text another app shared to Tag Forge, already checked by [SharedText]. Consumed once the review is open. */
    private val pendingShare = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as TagForgeApplication).container
        openPermissionCenter.value = intent.getBooleanExtra(EXTRA_OPEN_PERMISSION_CENTER, false)
        resolveShortcut(intent)
        // Only a fresh launch: a rotation re-delivers the same intent and must not open the review a second time.
        if (savedInstanceState == null) resolveShare(intent)

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
            val shared by pendingShare.collectAsStateWithLifecycle()
            TagForgeTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                TagForgeApp(
                    openPermissionCenter = permissionCenterRequested,
                    onPermissionCenterOpened = { openPermissionCenter.value = false },
                    shortcut = shortcut,
                    onShortcutOpened = { pendingShortcut.value = null },
                    sharedText = shared,
                    onSharedTextOpened = { pendingShare.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_PERMISSION_CENTER, false)) openPermissionCenter.value = true
        resolveShortcut(intent)
        resolveShare(intent)
    }

    /**
     * "Share > Tag Forge". The sender is untrusted, so the text is capped and parsed by [SharedText]; what passes only opens the
     * Write review pre-filled. Nothing is armed here and nothing runs: the user must press Write on that screen.
     */
    private fun resolveShare(intent: Intent) {
        if (intent.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        when (SharedText.parse(text)) {
            is SharedTextResult.Record -> pendingShare.value = text
            SharedTextResult.TooLong -> Toast.makeText(this, R.string.share_text_too_long, Toast.LENGTH_LONG).show()
            SharedTextResult.Empty -> Toast.makeText(this, R.string.share_text_empty, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * The activity is exported, so a VIEW link may come from any app: it only arms a write when it carries the install's
     * nonce (see [ShortcutLinks.resolve]); otherwise the Write review opens and the user must press Write.
     */
    private fun resolveShortcut(intent: Intent) {
        if (intent.action != Intent.ACTION_VIEW) return
        val link = intent.dataString
        val store = (application as TagForgeApplication).container.shortcutNonceStore
        lifecycleScope.launch {
            ShortcutLinks.resolve(link, store.peek())?.let { pendingShortcut.value = it }
        }
    }

    companion object {
        const val EXTRA_OPEN_PERMISSION_CENTER = "com.tagforge.app.extra.OPEN_PERMISSION_CENTER"
    }
}
