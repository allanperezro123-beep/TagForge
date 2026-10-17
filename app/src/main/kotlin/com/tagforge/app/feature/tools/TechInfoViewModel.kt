package com.tagforge.app.feature.tools

import androidx.lifecycle.viewModelScope
import com.tagforge.app.infrastructure.nfc.NfcAvailability
import com.tagforge.domain.scan.ScanCoordinator
import com.tagforge.domain.tag.TagInfo
import com.tagforge.domain.tools.IdentifyTagUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class TechInfoUiState(
    val availability: NfcAvailability = NfcAvailability.ENABLED,
    val reading: ToolReading<TagInfo> = ToolReading.Waiting,
)

/** State of the "Tag technologies" tool (TT-05): identify the next tag and show what each technology reports. */
class TechInfoViewModel(
    coordinator: ScanCoordinator,
    availability: Flow<NfcAvailability>,
) : ScanToolViewModel<TagInfo>(coordinator, IdentifyTagUseCase(), availability) {

    val uiState: StateFlow<TechInfoUiState> = combine(availabilityFlow, readingFlow, ::TechInfoUiState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, TechInfoUiState())
}
