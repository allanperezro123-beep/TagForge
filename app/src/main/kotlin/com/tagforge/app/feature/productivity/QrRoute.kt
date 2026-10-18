package com.tagforge.app.feature.productivity

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tagforge.app.di.tagForgeViewModel
import com.tagforge.app.infrastructure.android.ContentResolverQrImageSource

/**
 * Container for the QR screen. The image comes from the system Photo Picker, which returns just the chosen picture
 * without any permission; [onOpenWrite] receives the QR text to prefill the Write wizard with.
 */
@Composable
fun QrRoute(payload: String?, onBack: () -> Unit, onOpenWrite: (String) -> Unit) {
    val context = LocalContext.current.applicationContext
    val viewModel = tagForgeViewModel { container ->
        QrViewModel(
            payload = payload,
            images = ContentResolverQrImageSource(context.contentResolver, container.dispatchers.io),
            decodeDispatcher = container.dispatchers.default,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.onImagePicked(it.toString()) }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is QrEvent.OpenWrite -> onOpenWrite(event.text)
            }
        }
    }
    QrScreen(
        state = state,
        actions = QrReadActions(
            onChooseImage = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onPasteChanged = viewModel::onPasteChanged,
            onPasteSubmit = viewModel::onPasteSubmit,
            onUse = viewModel::onUseResult,
            onDismiss = viewModel::onDismissResult,
        ),
        onBack = onBack,
    )
}
