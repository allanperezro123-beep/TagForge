package com.tagforge.app

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.tagforge.app.infrastructure.android.IntentLauncher
import com.tagforge.app.infrastructure.nfc.NfcAvailabilityMonitor
import com.tagforge.app.infrastructure.persistence.DataStoreSettingsStore
import com.tagforge.app.infrastructure.persistence.RoomExchangeStore
import com.tagforge.app.infrastructure.persistence.RoomHistoryRepository
import com.tagforge.app.infrastructure.persistence.RoomTaskRepository
import com.tagforge.app.infrastructure.persistence.RoomTemplateRepository
import com.tagforge.app.infrastructure.persistence.ShortcutNonceStore
import com.tagforge.app.infrastructure.persistence.TagForgeDatabase
import com.tagforge.app.infrastructure.security.KeystoreSecretStore
import com.tagforge.app.infrastructure.tasks.AndroidDeviceFacts
import com.tagforge.app.infrastructure.tasks.AndroidTaskActionExecutor
import com.tagforge.app.infrastructure.tasks.TaskHandlers
import com.tagforge.app.infrastructure.tasks.handlers.AlarmHandler
import com.tagforge.app.infrastructure.tasks.handlers.BluetoothToggleHandler
import com.tagforge.app.infrastructure.tasks.handlers.CalendarEventHandler
import com.tagforge.app.infrastructure.tasks.handlers.DialHandler
import com.tagforge.app.infrastructure.tasks.handlers.FlashlightHandler
import com.tagforge.app.infrastructure.tasks.handlers.HttpRequestHandler
import com.tagforge.app.infrastructure.tasks.handlers.LaunchAppHandler
import com.tagforge.app.infrastructure.tasks.handlers.MediaKeyHandler
import com.tagforge.app.infrastructure.tasks.handlers.NotifyHandler
import com.tagforge.app.infrastructure.tasks.handlers.OpenSettingsHandler
import com.tagforge.app.infrastructure.tasks.handlers.OpenUrlHandler
import com.tagforge.app.infrastructure.tasks.handlers.SetBrightnessHandler
import com.tagforge.app.infrastructure.tasks.handlers.SetRingerHandler
import com.tagforge.app.infrastructure.tasks.handlers.SetVolumeHandler
import com.tagforge.app.infrastructure.tasks.handlers.SmsComposeHandler
import com.tagforge.app.infrastructure.tasks.handlers.SpeakHandler
import com.tagforge.app.infrastructure.tasks.handlers.TimerHandler
import com.tagforge.app.infrastructure.tasks.handlers.WifiJoinHandler
import com.tagforge.app.infrastructure.tasks.handlers.WifiToggleHandler
import com.tagforge.domain.history.RecordHistoryUseCase
import com.tagforge.domain.ndef.DefaultNdefCodec
import com.tagforge.domain.ndef.NdefCodec
import com.tagforge.domain.ports.ExchangeStore
import com.tagforge.domain.ports.HistoryRepository
import com.tagforge.domain.ports.SecretStore
import com.tagforge.domain.ports.SettingsStore
import com.tagforge.domain.ports.TaskActionExecutor
import com.tagforge.domain.ports.TaskRepository
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

    /** The secret that lets only our own pinned shortcuts arm a write on open (exported MainActivity, W4). */
    val shortcutNonceStore = ShortcutNonceStore(appContext.settingsDataStore)

    val ndefCodec: NdefCodec = DefaultNdefCodec

    val scanCoordinator = ScanCoordinator(applicationScope, dispatchers.io)

    /** Opened lazily: the first screen that needs history or templates pays for it, not app start. */
    private val database: TagForgeDatabase by lazy { TagForgeDatabase.create(appContext) }

    val historyRepository: HistoryRepository by lazy { RoomHistoryRepository(database.historyDao()) }
    val templateRepository: TemplateRepository by lazy { RoomTemplateRepository(database.templateDao()) }

    val taskRepository: TaskRepository by lazy { RoomTaskRepository(database.taskDao()) }

    /** Backup export and all-or-nothing import (HT-04). */
    val exchangeStore: ExchangeStore by lazy { RoomExchangeStore(database) }

    val clock: Clock = Clock.System

    /** Live grants and hardware: feeds the feasibility matrix and the Permission Center. */
    val deviceFacts = AndroidDeviceFacts(appContext)

    /** Runs confirmed task actions on the device. One handler per action type; the executor refuses to compile without them. */
    val taskExecutor: TaskActionExecutor by lazy {
        val launcher = IntentLauncher(appContext)
        val api = Build.VERSION.SDK_INT
        AndroidTaskActionExecutor(
            handlers = TaskHandlers(
                openUrl = OpenUrlHandler(launcher),
                launchApp = LaunchAppHandler(appContext, launcher),
                setRinger = SetRingerHandler(appContext, launcher),
                setVolume = SetVolumeHandler(appContext, launcher),
                setBrightness = SetBrightnessHandler(appContext, launcher),
                wifiToggle = WifiToggleHandler(appContext, launcher, api),
                wifiJoin = WifiJoinHandler(appContext, launcher, api),
                bluetoothToggle = BluetoothToggleHandler(launcher),
                openSettings = OpenSettingsHandler(launcher, api),
                alarm = AlarmHandler(launcher),
                timer = TimerHandler(launcher),
                calendarEvent = CalendarEventHandler(launcher, clock),
                smsCompose = SmsComposeHandler(launcher),
                dial = DialHandler(launcher),
                speak = SpeakHandler(appContext),
                flashlight = FlashlightHandler(appContext),
                notify = NotifyHandler(appContext),
                httpRequest = HttpRequestHandler(dispatchers.io),
                mediaKey = MediaKeyHandler(appContext),
            ),
            facts = deviceFacts::snapshot,
        )
    }

    val recordHistory: RecordHistoryUseCase by lazy { RecordHistoryUseCase(historyRepository, settingsStore, clock) }

    /** Opt-in tag passwords, sealed with an AndroidKeystore key (NS-02). */
    val secretStore: SecretStore by lazy { KeystoreSecretStore(appContext, dispatchers.io) }

    /** The raw console warning is shown once per app session (NS-08); this lives exactly as long as the process. */
    val rawConsoleAcknowledged = kotlinx.coroutines.flow.MutableStateFlow(false)

    val readTagUseCase = ReadTagUseCase(ndefCodec)
    val readMemoryUseCase = ReadMemoryUseCase()

    val nfcAvailabilityMonitor = NfcAvailabilityMonitor(appContext)
}
