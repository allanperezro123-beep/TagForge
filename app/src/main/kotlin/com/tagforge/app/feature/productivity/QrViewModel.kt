package com.tagforge.app.feature.productivity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tagforge.app.infrastructure.android.QrDecoder
import com.tagforge.app.infrastructure.android.QrEncoder
import com.tagforge.app.infrastructure.android.QrImage
import com.tagforge.app.infrastructure.android.QrMatrix
import com.tagforge.domain.exchange.QrPayloadMapper
import com.tagforge.domain.ndef.spec.RecordSpec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Loads the image the user picked with the system Photo Picker; no storage permission is involved. */
interface QrImageSource {
    /** Null when the image cannot be opened or decoded. */
    suspend fun load(uri: String): QrImage?
}

/** What reading a code gave. [Found.record] is what the Write wizard will be prefilled with. */
sealed interface QrReadResult {
    data object Idle : QrReadResult
    data object Reading : QrReadResult
    data class Found(val text: String, val record: RecordSpec) : QrReadResult
    data object NotFound : QrReadResult
    data object Unreadable : QrReadResult
}

sealed interface QrEvent {
    /** Open the Write wizard with this QR text decoded into its records. */
    data class OpenWrite(val text: String) : QrEvent
}

data class QrUiState(
    val generated: QrMatrix? = null,
    val generatedText: String? = null,
    val generationFailed: Boolean = false,
    val pasteText: String = "",
    val result: QrReadResult = QrReadResult.Idle,
) {
    val canSubmitPaste: Boolean get() = pasteText.isNotBlank()
}

/**
 * State holder for QR codes (PR-02). Showing a code needs only the text; reading one works from a picked image or from
 * pasted text, so the feature never asks for the camera permission. Decoding runs on [decodeDispatcher].
 */
class QrViewModel(
    payload: String?,
    private val images: QrImageSource,
    private val decodeDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(initial(payload))
    val uiState: StateFlow<QrUiState> = _state.asStateFlow()

    private val eventChannel = Channel<QrEvent>(Channel.BUFFERED)
    val events: Flow<QrEvent> = eventChannel.receiveAsFlow()

    fun onImagePicked(uri: String) {
        _state.update { it.copy(result = QrReadResult.Reading) }
        viewModelScope.launch {
            val image = images.load(uri)
            val result = if (image == null) {
                QrReadResult.Unreadable
            } else {
                val text = withContext(decodeDispatcher) { QrDecoder.decode(image.pixels, image.width, image.height) }
                text?.let(::found) ?: QrReadResult.NotFound
            }
            _state.update { it.copy(result = result) }
        }
    }

    fun onPasteChanged(text: String) = _state.update { it.copy(pasteText = text) }

    fun onPasteSubmit() {
        val text = _state.value.pasteText
        if (text.isBlank()) return
        _state.update { it.copy(result = found(text)) }
    }

    fun onDismissResult() = _state.update { it.copy(result = QrReadResult.Idle) }

    fun onUseResult() {
        val found = _state.value.result as? QrReadResult.Found ?: return
        eventChannel.trySend(QrEvent.OpenWrite(found.text))
    }

    private fun found(text: String) = QrReadResult.Found(text, QrPayloadMapper.fromQr(text))

    private fun initial(payload: String?): QrUiState {
        if (payload.isNullOrEmpty()) return QrUiState()
        val matrix = QrEncoder.encode(payload)
        return QrUiState(generated = matrix, generatedText = payload.takeIf { matrix != null }, generationFailed = matrix == null)
    }
}
