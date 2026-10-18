package com.tagforge.app.feature.productivity

import app.cash.turbine.test
import com.tagforge.app.infrastructure.android.QrEncoder
import com.tagforge.app.infrastructure.android.QrImage
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

    private fun viewModel(payload: String? = null, images: Map<String, QrImage?> = emptyMap()) =
        QrViewModel(payload, FakeImages(images), UnconfinedTestDispatcher())

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
}
