package com.tagforge.app.feature.productivity

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tagforge.app.di.tagForgeViewModel
import com.tagforge.app.infrastructure.android.ContentResolverDocumentIo
import com.tagforge.domain.exchange.DataExchange

/**
 * Container: owns the ViewModel and the three Storage Access Framework launchers. The system picker returns a URI
 * to exactly the document the user chose, so no storage permission is needed (or declared).
 */
@Composable
fun ImportExportRoute(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val viewModel = tagForgeViewModel { container ->
        ImportExportViewModel(
            exchange = DataExchange(container.exchangeStore, container.clock),
            documents = ContentResolverDocumentIo(context.contentResolver, container.dispatchers.io),
            clock = container.clock,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val createJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.JSON.mimeType)) { uri: Uri? ->
        uri?.let { viewModel.onExport(ExportFormat.JSON, it.toString()) }
    }
    val createCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.CSV.mimeType)) { uri: Uri? ->
        uri?.let { viewModel.onExport(ExportFormat.CSV, it.toString()) }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.onImport(it.toString()) }
    }

    ImportExportScreen(
        state = state,
        onBack = onBack,
        onExportJson = { createJson.launch(viewModel.suggestedFileName(ExportFormat.JSON)) },
        onExportCsv = { createCsv.launch(viewModel.suggestedFileName(ExportFormat.CSV)) },
        onImport = { open.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
        onMessageShown = viewModel::onMessageShown,
    )
}
