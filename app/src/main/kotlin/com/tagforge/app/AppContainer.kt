package com.tagforge.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.tagforge.app.infrastructure.nfc.NfcAvailabilityMonitor
import com.tagforge.app.infrastructure.persistence.DataStoreSettingsStore
import com.tagforge.app.infrastructure.persistence.RoomHistoryRepository
import com.tagforge.app.infrastructure.persistence.RoomTemplateRepository
import com.tagforge.app.infrastructure.persistence.TagForgeDatabase
import com.tagforge.domain.history.RecordHistoryUseCase
import com.tagforge.domain.ndef.DefaultNdefCodec
import com.tagforge.domain.ndef.NdefCodec
import com.tagforge.domain.ports.HistoryRepository
import com.tagforge.domain.ports.SettingsStore
import com.tagforge.domain.ports.TemplateRepository
import com.tagforge.domain.read.ReadMemoryUseCase
import com.tagforge.domain.read.ReadTagUseCase
import com.tagforge.domain.scan.ScanCoordinator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.time.Clock

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Dispatchers behind one seam so ViewModels never hardcode `Dispatchers.*` and tests can swap them. */
data class AppDispatchers(
    val main: CoroutineDispatcher = Dispatchers.Main,
    val io: CoroutineDispatcher = Dispatchers.IO,
    val default: CoroutineDispatcher = Dispatchers.Default,
)

/**
 * Manual dependency injection: one place that builds the object graph, created once by [TagForgeApplication].
 * Revisit (Hilt/Koin) only if this grows well past ~15 bindings.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val dispatchers = AppDispatchers()

    /** Outlives every screen: NFC callbacks and in-flight tag operations are not tied to the UI. */
    val applicationScope = CoroutineScope(SupervisorJob() + dispatchers.default)

    val settingsStore: SettingsStore = DataStoreSettingsStore(appContext.settingsDataStore)

    val ndefCodec: NdefCodec = DefaultNdefCodec

    val scanCoordinator = ScanCoordinator(applicationScope, dispatchers.io)

    /** Opened lazily: the first screen that needs history or templates pays for it, not app start. */
    private val database: TagForgeDatabase by lazy { TagForgeDatabase.create(appContext) }

    val historyRepository: HistoryRepository by lazy { RoomHistoryRepository(database.historyDao()) }
    val templateRepository: TemplateRepository by lazy { RoomTemplateRepository(database.templateDao()) }

    val clock: Clock = Clock.System

    val recordHistory: RecordHistoryUseCase by lazy { RecordHistoryUseCase(historyRepository, settingsStore, clock) }

    val readTagUseCase = ReadTagUseCase(ndefCodec)
    val readMemoryUseCase = ReadMemoryUseCase()

    val nfcAvailabilityMonitor = NfcAvailabilityMonitor(appContext)
}
