package com.tagforge.app.feature.tools

import androidx.lifecycle.viewModelScope
import com.tagforge.app.infrastructure.nfc.NfcAvailability
import com.tagforge.domain.common.Bytes
import com.tagforge.domain.ntag.MemoryDump
import com.tagforge.domain.ntag.NtagMemoryMap
import com.tagforge.domain.ntag.Region
import com.tagforge.domain.scan.ScanCoordinator
import com.tagforge.domain.tools.DumpMemoryUseCase
import com.tagforge.domain.tools.DumpReport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** How many pages one row shows: one (4 bytes) or four (16 bytes). */
enum class DumpLayout(val pagesPerRow: Int) { PAGE_PER_ROW(1), SIXTEEN_BYTES(4) }

/**
 * One displayed row. [regions] lists, per page, the roles of that page; [bytes] already shows zeros for pages the chip
 * hides (PWD, PACK), so no screen can accidentally display what a buggy fake might hold there.
 */
data class DumpRow(
    val pages: List<Int>,
    val bytes: Bytes,
    val regions: List<List<Region>>,
    val hiddenByChip: Boolean,
) {
    val startPage: Int get() = pages.first()
    val primaryRegion: Region? get() = regions.firstOrNull()?.firstOrNull()
}

/** Pure row building for the dump view. */
object DumpRows {
    fun build(dump: MemoryDump, layout: DumpLayout): List<DumpRow> =
        dump.pages.indices.chunked(layout.pagesPerRow).map { pages ->
            DumpRow(
                pages = pages,
                bytes = Bytes.concat(pages.map(dump::displayBytes)),
                regions = pages.map { page -> dump.model?.let { NtagMemoryMap.regionsOf(it, page) }.orEmpty() },
                hiddenByChip = pages.any(dump::isHiddenByChip),
            )
        }
}

data class DumpUiState(
    val availability: NfcAvailability = NfcAvailability.ENABLED,
    val reading: ToolReading<DumpReport> = ToolReading.Waiting,
    val layout: DumpLayout = DumpLayout.PAGE_PER_ROW,
    /** The page whose explanation sheet is open. */
    val selectedPage: Int? = null,
    /** True for one frame after "copy": the screen shows a confirmation on Android versions that show none themselves. */
    val copyNotice: Boolean = false,
) {
    val report: DumpReport? get() = (reading as? ToolReading.Result<DumpReport>)?.value
}

/** State of the annotated memory dump (TT-03): the reading plus the presentation choices (layout, selected row). */
class DumpViewModel(
    coordinator: ScanCoordinator,
    availability: Flow<NfcAvailability>,
) : ScanToolViewModel<DumpReport>(coordinator, DumpMemoryUseCase(), availability) {

    private val layout = MutableStateFlow(DumpLayout.PAGE_PER_ROW)
    private val selectedPage = MutableStateFlow<Int?>(null)
    private val copyNotice = MutableStateFlow(false)

    val uiState: StateFlow<DumpUiState> = combine(availabilityFlow, readingFlow, layout, selectedPage, copyNotice, ::DumpUiState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, DumpUiState())

    fun onLayoutChanged(value: DumpLayout) {
        layout.value = value
    }

    fun onPageSelected(page: Int?) {
        selectedPage.value = page
    }

    fun onCopied() {
        copyNotice.value = true
    }

    fun onCopyNoticeShown() {
        copyNotice.value = false
    }
}
