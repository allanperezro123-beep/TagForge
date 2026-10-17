package com.tagforge.app

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
import com.tagforge.app.infrastructure.nfc.NfcAvailability
import com.tagforge.app.infrastructure.nfc.NfcReaderController
import com.tagforge.app.navigation.TagForgeApp
import com.tagforge.app.ui.theme.TagForgeTheme
import com.tagforge.domain.settings.AppSettings
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var readerController: NfcReaderController

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as TagForgeApplication).container

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
            TagForgeTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                TagForgeApp()
            }
        }
    }
}
