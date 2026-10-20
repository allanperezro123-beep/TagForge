package com.tagforge.app.feature.productivity

import app.cash.turbine.test
import com.tagforge.app.infrastructure.android.QrEncoder
import com.tagforge.app.infrastructure.android.QrImage
import com.tagforge.domain.fakes.FixedClock
import com.tagforge.domain.fakes.InMemoryTemplateRepository
import kotlinx.coroutines.flow.first
import kotlin.time.Instant
import com.tagforge.domain.ndef.spec.RecordSpec
import com.tagforge.domain.ndef.spec.WifiAuth
import com.tagforge.domain.ndef.spec.WifiEncryption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** PR-02 screen logic: show a QR for the wizard message, read one from a picked image or pasted text. */
@OptIn(ExperimentalCoroutinesApi::class)
class QrViewModelTest {

    private class FakeImages(private val images: Map<String, QrImage?>) : QrImageSource {
        override suspend fun load(uri: String): QrImage? = images[uri]
    }

    private fun imageOf(text: String): QrImage = QrEncoder.toPixels(assertNotNull(QrEncoder.encode(text)), scale = 6)
    private val blank = QrImage(IntArray(64 * 64) { 0xFFFFFFFF.toInt() }, 64, 64)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val templates = InMemoryTemplateRepository()

    private fun viewModel(payload: String? = null, images: Map<String, QrImage?> = emptyMap()) =
        QrViewModel(payload, FakeImages(images), UnconfinedTestDispatcher(), templates, FixedClock(Instant.fromEpochMilliseconds(5_000)))

    private fun QrViewModel.paste(text: String) {
        onPasteChanged(text)
        onPasteSubmit()
    }

    @Test
    fun aPayloadFromTheWizardIsShownAsAQrCode() = runTest {
        val vm = viewModel(payload = "WIFI:T:WPA;S:Home;P:pass1234;;")

        val state = vm.uiState.value
        assertEquals("WIFI:T:WPA;S:Home;P:pass1234;;", state.generatedText)
        assertTrue(assertNotNull(state.generated).size > 0)
        assertEquals(false, state.generationFailed)
    }

    @Test
    fun noPayloadShowsNoCodeAndAnOversizedOneReportsTheFailure() = runTest {
        val none = viewModel().uiState.value
        assertNull(none.generated)
        assertEquals(false, none.generationFailed)

        val huge = viewModel(payload = "x".repeat(5000)).uiState.value
        assertNull(huge.generated)
        assertEquals(true, huge.generationFailed)
    }

    @Test
    fun aPickedImageWithAWifiCodeIsDecodedToAnEditableWifiRecord() = runTest {
        val vm = viewModel(images = mapOf("content://pic/1" to imageOf("WIFI:T:WPA;S:Home;P:pass1234;;")))

        vm.onImagePicked("content://pic/1")

        val found = assertIs<QrReadResult.Found>(vm.uiState.value.result)
        assertEquals("WIFI:T:WPA;S:Home;P:pass1234;;", found.text)
        assertEquals(RecordSpec.Wifi("Home", "pass1234", WifiAuth.WPA2_PSK, WifiEncryption.AES), found.record)
    }

    @Test
    fun anImageWithoutACodeOrThatCannotBeOpenedSaysSo() = runTest {
        val vm = viewModel(images = mapOf("content://pic/blank" to blank, "content://pic/broken" to null))

        vm.onImagePicked("content://pic/blank")
        assertEquals(QrReadResult.NotFound, vm.uiState.value.result)

        vm.onImagePicked("content://pic/broken")
        assertEquals(QrReadResult.Unreadable, vm.uiState.value.result)
    }

    @Test
    fun pastedTextIsMappedWithoutAnyCameraOrImage() = runTest {
        val vm = viewModel()

        vm.onPasteChanged("https://example.com/a")
        vm.onPasteSubmit()

        assertEquals("https://example.com/a", vm.uiState.value.pasteText)
        val found = assertIs<QrReadResult.Found>(vm.uiState.value.result)
        assertEquals(RecordSpec.Uri("https://example.com/a"), found.record)
    }

    @Test
    fun blankPastedTextProducesNoResult() = runTest {
        val vm = viewModel()

        vm.onPasteChanged("   ")
        vm.onPasteSubmit()

        assertEquals(QrReadResult.Idle, vm.uiState.value.result)
        assertEquals(false, vm.uiState.value.canSubmitPaste)
    }

    @Test
    fun usingTheResultOpensTheWizardOnceAndOnlyWhenThereIsOne() = runTest {
        val vm = viewModel()

        vm.events.test {
            vm.onUseResult()
            vm.onPasteChanged("tel:+34600111222")
            vm.onPasteSubmit()
            vm.onUseResult()
            assertEquals(QrEvent.OpenWrite("tel:+34600111222"), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun dismissingTheResultClearsIt() = runTest {
        val vm = viewModel()
        vm.onPasteChanged("hello")
        vm.onPasteSubmit()
        assertIs<QrReadResult.Found>(vm.uiState.value.result)

        vm.onDismissResult()

        assertEquals(QrReadResult.Idle, vm.uiState.value.result)
    }

    // ---- live camera ---------------------------------------------------------------------------------------------

    @Test
    fun aCameraFrameCountsOnlyWhileTheScannerIsOpenAndThenClosesIt() = runTest {
        val vm = viewModel()

        vm.onScanned("https://example.com")
        assertEquals(QrReadResult.Idle, vm.uiState.value.result)

        vm.onScanStarted()
        assertTrue(vm.uiState.value.scanning)
        vm.onScanned("https://example.com")

        assertEquals(false, vm.uiState.value.scanning)
        assertIs<QrReadResult.Found>(vm.uiState.value.result)
    }

    // ---- what the result may do ---------------------------------------------------------------------------------

    @Test
    fun aWebLinkOpensOnlyAfterTheUserAsksAndOnlyThatLink() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.paste("https://example.com/menu")
            expectNoEvents()

            vm.onOpenResult()

            assertEquals(QrEvent.OpenUri("https://example.com/menu"), awaitItem())
        }
    }

    @Test
    fun dangerousOrNonOpenableContentNeverProducesAnOpenEvent() = runTest {
        val vm = viewModel()
        vm.events.test {
            for (text in listOf("javascript:alert(1)", "intent://x#Intent;end", "file:///sdcard/a", "https://paypal.com@evil.example/", "WIFI:T:WPA;S:Home;P:pass1234;;", "just text")) {
                vm.paste(text)
                vm.onOpenResult()
            }
            expectNoEvents()
        }
    }

    @Test
    fun copyingAWifiCodeIsMarkedSensitiveButPlainTextIsNot() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.paste("WIFI:T:WPA;S:Home;P:pass1234;;")
            vm.onCopyResult()
            assertEquals(QrEvent.Copy("WIFI:T:WPA;S:Home;P:pass1234;;", sensitive = true), awaitItem())

            vm.paste("table 4")
            vm.onCopyResult()
            assertEquals(QrEvent.Copy("table 4", sensitive = false), awaitItem())
        }
    }

    @Test
    fun aWifiCodeIsOfferedToTheWriterAndOnlyPrefillsIt() = runTest {
        val vm = viewModel()
        vm.paste("WIFI:T:WPA;S:Home;P:pass1234;;")
        vm.events.test {
            vm.onUseResult()
            assertEquals(QrEvent.OpenWrite("WIFI:T:WPA;S:Home;P:pass1234;;"), awaitItem())
        }
    }

    @Test
    fun theResultCanBeSavedAsATemplateWithTheRecordItDecodedTo() = runTest {
        val vm = viewModel()
        vm.paste("https://example.com/a")
        vm.events.test {
            vm.onSaveResultAsTemplate("Menu")

            assertEquals(QrEvent.TemplateSaved("Menu"), awaitItem())
        }
        assertEquals(listOf<RecordSpec>(RecordSpec.Uri("https://example.com/a")), templates.observeAll().first().single().records)
    }

    // ---- creating --------------------------------------------------------------------------------------------

    @Test
    fun aWifiCodeCanBeCreatedOnceTheNetworkIsNamed() = runTest {
        val vm = viewModel()
        vm.onCreateKind(QrKind.WIFI)
        assertEquals(false, vm.uiState.value.canCreate)

        vm.onCreateSpec(RecordSpec.Wifi("Home", "pass1234"))
        vm.onCreate()

        assertEquals(true, vm.uiState.value.canCreate)
        assertEquals("WIFI:T:WPA;S:Home;P:pass1234;;", vm.uiState.value.generatedText)
        assertEquals(false, vm.uiState.value.fromTag)
        assertNotNull(vm.uiState.value.generated)
    }

    @Test
    fun changingTheKindClearsTheCodeShownBefore() = runTest {
        val vm = viewModel()
        vm.onCreateSpec(RecordSpec.Uri("https://example.com"))
        vm.onCreate()
        assertNotNull(vm.uiState.value.generated)

        vm.onCreateKind(QrKind.TEXT)

        assertNull(vm.uiState.value.generated)
        assertEquals(false, vm.uiState.value.canCreate)
    }

    @Test
    fun aTextTooLongForAnyQrReportsTheFailure() = runTest {
        val vm = viewModel()
        vm.onCreateKind(QrKind.TEXT)
        vm.onCreateSpec(RecordSpec.Text("x".repeat(5000)))

        vm.onCreate()

        assertEquals(true, vm.uiState.value.generationFailed)
        assertNull(vm.uiState.value.generated)
    }

    @Test
    fun theCodeForTheWizardMessageIsFlaggedAsComingFromTheTag() = runTest {
        assertEquals(true, viewModel(payload = "https://example.com").uiState.value.fromTag)
    }
}
