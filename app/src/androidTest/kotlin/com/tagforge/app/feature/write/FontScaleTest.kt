package com.tagforge.app.feature.write

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.tagforge.app.ui.theme.TagForgeTheme
import com.tagforge.domain.ndef.spec.RecordSpec
import com.tagforge.domain.write.WriteDraft
import org.junit.Rule
import org.junit.Test

/**
 * PR-04: at 200% font scale the wizard review still scrolls, and its main controls are reachable and not clipped.
 * Instrumented because it needs real text layout; it is run on a device with the P5 checklist.
 */
class FontScaleTest {

    @get:Rule
    val compose = createComposeRule()

    private val records = listOf<RecordSpec>(RecordSpec.Uri("https://example.com"))
    private val state = WriteUiState(
        step = WriteStep.REVIEW,
        records = records,
        analysis = WriteDraft(records).analyse(capacity = 137, appPackage = "com.tagforge.app"),
        capacity = 137,
        canWrite = true,
    )

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test
    fun reviewControlsAreReachableAtTwiceTheFontSize() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                TagForgeTheme {
                    WizardScreen(state, noActions(), SnackbarHostState(), onBack = {})
                }
            }
        }

        compose.onNodeWithText(string(com.tagforge.app.R.string.write_action)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(string(com.tagforge.app.R.string.write_save_template)).performScrollTo().assertIsDisplayed()
    }

    private fun noActions() = WriteActions(
        onTypeSelected = {},
        onDraftChanged = {},
        onFieldTouched = {},
        onContinue = {},
        onAddRecord = {},
        onEditRecord = {},
        onRemoveRecord = {},
        onMoveRecord = { _, _ -> },
        onIncludeAarChanged = {},
        onTargetSelected = {},
        onWrite = {},
        onSaveTemplate = {},
        onShowQr = {},
        onOverwriteDecision = {},
        onRetry = {},
        onWriteAnother = {},
        onSheetDismissed = {},
        onUndoRemove = {},
        onOpenNfcSettings = {},
    )
}
