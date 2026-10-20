package com.tagforge.app.feature.productivity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tagforge.app.infrastructure.android.QrDecoder
import com.tagforge.app.infrastructure.android.QrEncoder
import com.tagforge.app.infrastructure.android.QrImage
import com.tagforge.app.infrastructure.android.QrMatrix
import com.tagforge.domain.exchange.QrContent
import com.tagforge.domain.exchange.QrContentClassifier
import com.tagforge.domain.exchange.QrPayloadMapper
import com.tagforge.domain.ndef.spec.RecordSpec
import com.tagforge.domain.ports.TemplateRepository
import com.tagforge.domain.template.Template
import com.tagforge.domain.template.TemplateNames
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/** Loads the image the user picked with the system Photo Picker; no storage permission is involved. */
interface QrImageSource {
    /** Null when the image cannot be opened or decoded. */
    suspend fun load(uri: String): QrImage?
}

/** What reading a code gave. [Found.record] is what the Write wizard will be prefilled with; [Found.content] says what it is. */
sealed interface QrReadResult {
    data object Idle : QrReadResult
    data object Reading : QrReadResult
    data class Found(val text: String, val record: RecordSpec, val content: QrContent = QrContentClassifier.classify(text)) : QrReadResult
    data object NotFound : QrReadResult
    data object Unreadable : QrReadResult
}

sealed interface QrEvent {
    /** Open the Write wizard with this QR text decoded into its records. It only prefills: nothing is armed. */
    data class OpenWrite(val text: String) : QrEvent

    /** Hand an allowlisted address (web, phone, mail, SMS, map) to the system. Only sent after the user pressed Open. */
    data class OpenUri(val uri: String) : QrEvent

    data class Copy(val text: String, val sensitive: Boolean) : QrEvent
    data class TemplateSaved(val name: String) : QrEvent
}

data class QrUiState(
    val generated: QrMatrix? = null,
    val generatedText: String? = null,
    val generationFailed: Boolean = false,
    val pasteText: String = "",
    val result: QrReadResult = QrReadResult.Idle,
    val scanning: Boolean = false,
    val create: QrCreateState = QrCreateState(),
    /** True when [generated] is the code for the message in the Write wizard rather than one made with the form. */
    val fromTag: Boolean = false,
) {
    val canSubmitPaste: Boolean get() = pasteText.isNotBlank()
    val canCreate: Boolean get() = QrPayloadMapper.toQr(create.spec) != null
}

/**
 * State holder for QR codes: show or create a code, and read one from the camera, a picked image or pasted text. What was
 * read is classified ([QrContentClassifier]) so only addresses the task allowlist accepts can ever be opened, and only after a
 * tap. Decoding runs on [decodeDispatcher].
 */
class QrViewModel(
    payload: String?,
    private val images: QrImageSource,
    private val decodeDispatcher: CoroutineDispatcher,
    private val templates: TemplateRepository? = null,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val _state = MutableStateFlow(initial(payload))
    val uiState: StateFlow<QrUiState> = _state.asStateFlow()

    private val eventChannel = Channel<QrEvent>(Channel.BUFFERED)
    val events: Flow<QrEvent> = eventChannel.receiveAsFlow()

    // ---- reading ---------------------------------------------------------------------------------------------

    fun onScanStarted() = _state.update { it.copy(scanning = true, result = QrReadResult.Idle) }

    fun onScanClosed() = _state.update { it.copy(scanning = false) }

    /** A live camera frame produced [text]. The camera closes and the result shows. */
    fun onScanned(text: String) {
        if (!_state.value.scanning) return
        _state.update { it.copy(scanning = false, result = found(text)) }
    }

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

    // ---- what to do with a result ------------------------------------------------------------------------------

    fun onUseResult() {
        val found = _state.value.result as? QrReadResult.Found ?: return
        eventChannel.trySend(QrEvent.OpenWrite(found.text))
    }

    /** Opens the result, but only when the classifier allowed it: anything else has no [QrContent.openUri]. */
    fun onOpenResult() {
        val uri = (_state.value.result as? QrReadResult.Found)?.content?.openUri ?: return
        eventChannel.trySend(QrEvent.OpenUri(uri))
    }

    fun onCopyResult() {
        val found = _state.value.result as? QrReadResult.Found ?: return
        eventChannel.trySend(QrEvent.Copy(found.text, sensitive = found.content is QrContent.Wifi))
    }

    fun onSaveResultAsTemplate(requestedName: String) {
        val found = _state.value.result as? QrReadResult.Found ?: return
        val repository = templates ?: return
        viewModelScope.launch {
            val taken = repository.observeAll().first().map { it.name }
            val name = TemplateNames.resolve(requestedName, taken) ?: return@launch
            val now = clock.now()
            repository.upsert(Template(name = name, records = listOf(found.record), createdAt = now, updatedAt = now))
            eventChannel.trySend(QrEvent.TemplateSaved(name))
        }
    }

    // ---- creating --------------------------------------------------------------------------------------------

    fun onCreateKind(kind: QrKind) = _state.update {
        it.copy(create = QrCreateState(kind, kind.newSpec()), generated = null, generatedText = null, generationFailed = false, fromTag = false)
    }

    fun onCreateSpec(spec: RecordSpec) = _state.update { it.copy(create = it.create.copy(spec = spec)) }

    fun onCreate() {
        val text = QrPayloadMapper.toQr(_state.value.create.spec) ?: return
        val matrix = QrEncoder.encode(text)
        _state.update { it.copy(generated = matrix, generatedText = text.takeIf { matrix != null }, generationFailed = matrix == null, fromTag = false) }
    }

    private fun found(text: String) = QrReadResult.Found(text, QrPayloadMapper.fromQr(text))

    private fun initial(payload: String?): QrUiState {
        if (payload.isNullOrEmpty()) return QrUiState()
        val matrix = QrEncoder.encode(payload)
        return QrUiState(generated = matrix, generatedText = payload.takeIf { matrix != null }, generationFailed = matrix == null, fromTag = true)
    }
}
