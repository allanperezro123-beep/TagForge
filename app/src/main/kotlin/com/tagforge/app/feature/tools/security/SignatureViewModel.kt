package com.tagforge.app.feature.tools.security

import androidx.lifecycle.viewModelScope
import com.tagforge.app.feature.tools.ScanToolViewModel
import com.tagforge.app.feature.tools.ToolReading
import com.tagforge.app.infrastructure.nfc.NfcAvailability
import com.tagforge.domain.scan.ScanCoordinator
import com.tagforge.domain.security.ReadSignatureUseCase
import com.tagforge.domain.security.SignatureReport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SignatureUiState(
    val availability: NfcAvailability = NfcAvailability.ENABLED,
    val reading: ToolReading<SignatureReport> = ToolReading.Waiting,
)

/** State of the READ_SIG screen (NS-07): read the next tag's signature; nothing is ever written. */
class SignatureViewModel(
    coordinator: ScanCoordinator,
    availability: Flow<NfcAvailability>,
) : ScanToolViewModel<SignatureReport>(coordinator, ReadSignatureUseCase(), availability) {

    val uiState: StateFlow<SignatureUiState> = combine(availabilityFlow, readingFlow, ::SignatureUiState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, SignatureUiState())
}
