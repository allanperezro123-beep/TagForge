package com.tagforge.app.feature.productivity

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tagforge.app.R
import com.tagforge.app.di.tagForgeViewModel
import com.tagforge.app.infrastructure.android.ContentResolverQrImageSource
import com.tagforge.app.infrastructure.android.QrImageExporter
import com.tagforge.app.infrastructure.android.SensitiveClipboard
import com.tagforge.app.infrastructure.android.ShareExporter
import kotlinx.coroutines.launch

/** BROWSABLE only for web links: the handlers of geo:, tel: and the like do not declare it, so adding it would find nobody. */
private fun viewIntent(uri: String): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
    if (uri.startsWith("http", ignoreCase = true)) addCategory(Intent.CATEGORY_BROWSABLE)
}

/**
 * Container for the QR screen. A picture comes from the system Photo Picker (no storage permission); the camera is asked for
 * only when the user taps Scan. [onOpenWrite] receives the QR text to prefill the Write wizard, which never arms a write.
 */
@Composable
fun QrRoute(payload: String?, onBack: () -> Unit, onOpenWrite: (String) -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val appContext = context.applicationContext
    val viewModel = tagForgeViewModel { container ->
        QrViewModel(
            payload = payload,
            images = ContentResolverQrImageSource(appContext.contentResolver, container.dispatchers.io),
            decodeDispatcher = container.dispatchers.default,
            templates = container.templateRepository,
            clock = container.clock,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val exporter = remember { QrImageExporter(appContext, kotlinx.coroutines.Dispatchers.IO) }
    var cameraDenied by rememberSaveable { mutableStateOf(false) }

    fun say(message: String) = scope.launch {
        snackbar.currentSnackbarData?.dismiss()
        snackbar.showSnackbar(message)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.onImagePicked(it.toString()) }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraDenied = !granted
        if (granted) viewModel.onScanStarted()
    }
    BackHandler(enabled = state.scanning) { viewModel.onScanClosed() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is QrEvent.OpenWrite -> onOpenWrite(event.text)
                is QrEvent.OpenUri -> try {
                    context.startActivity(viewIntent(event.uri))
                } catch (_: ActivityNotFoundException) {
                    say(resources.getString(R.string.qr_open_failed))
                }
                is QrEvent.Copy -> {
                    SensitiveClipboard.copy(context, resources.getString(R.string.qr_title), event.text)
                    say(resources.getString(R.string.qr_copied))
                }
                is QrEvent.TemplateSaved -> say(resources.getString(R.string.qr_template_saved, event.name))
            }
        }
    }

    fun withPng(action: suspend (ByteArray) -> Unit) {
        val matrix = state.generated ?: return
        scope.launch { action(exporter.render(matrix)) }
    }

    QrScreen(
        state = state,
        read = QrReadActions(
            onScan = {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    cameraDenied = false
                    viewModel.onScanStarted()
                } else {
                    cameraPermission.launch(Manifest.permission.CAMERA)
                }
            },
            onChooseImage = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onPasteChanged = viewModel::onPasteChanged,
            onPasteSubmit = viewModel::onPasteSubmit,
            onUse = viewModel::onUseResult,
            onOpen = viewModel::onOpenResult,
            onCopy = viewModel::onCopyResult,
            onSaveTemplate = viewModel::onSaveResultAsTemplate,
            onDismiss = viewModel::onDismissResult,
            onOpenAppSettings = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
            },
        ),
        create = QrCreateActions(
            onKind = viewModel::onCreateKind,
            onSpec = viewModel::onCreateSpec,
            onCreate = viewModel::onCreate,
            onShare = { withPng { png -> ShareExporter(context).sharePng(png) } },
            onSave = {
                withPng { png ->
                    val saved = exporter.saveToGallery(png)
                    say(resources.getString(if (saved) R.string.qr_saved else R.string.qr_save_failed))
                }
            },
        ),
        onScanned = viewModel::onScanned,
        onCloseScanner = viewModel::onScanClosed,
        cameraDenied = cameraDenied,
        snackbarHostState = snackbar,
        onBack = onBack,
    )
}
