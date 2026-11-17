package com.tagforge.app.ui.components

import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** S6: a secret is typed on the password keyboard without autocorrect and is dots until the user asks to see it. */
class SecretInputTest {

    @Test
    fun aSecretUsesThePasswordKeyboardWithoutAutocorrect() {
        val options = secretKeyboardOptions()

        assertEquals(KeyboardType.Password, options.keyboardType)
        assertFalse(options.autoCorrectEnabled == true, "autocorrect must be off for a secret")
    }

    @Test
    fun theImeActionOfASecretCanBeChosenByTheForm() {
        assertEquals(ImeAction.Done, secretKeyboardOptions(ImeAction.Done).imeAction)
    }

    @Test
    fun aHiddenSecretIsShownAsDotsAndAVisibleOneAsPlainText() {
        assertTrue(secretTransformation(visible = false) is PasswordVisualTransformation)
        assertSame(VisualTransformation.None, secretTransformation(visible = true))
    }
}
