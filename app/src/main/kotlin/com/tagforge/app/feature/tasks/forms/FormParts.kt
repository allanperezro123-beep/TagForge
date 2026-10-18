package com.tagforge.app.feature.tasks.forms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tagforge.app.feature.tasks.ActionInput
import com.tagforge.app.ui.text.TaskText
import com.tagforge.domain.task.BlockReason

/**
 * Whether a field shows its error: once the user tried to add the action, or once they started typing in this field.
 * A freshly opened form is never red.
 */
class FieldVisibility(val showAllErrors: Boolean)

/** A text field whose error, when [reason] is set and visible, is written out beneath it (never colour alone). */
@Composable
fun TaskTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visibility: FieldVisibility,
    reason: BlockReason?,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    var touched by rememberSaveable { mutableStateOf(false) }
    val error = reason?.takeIf { visibility.showAllErrors || touched }
    OutlinedTextField(
        value = value,
        onValueChange = {
            touched = true
            onValueChange(it)
        },
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        isError = error != null,
        supportingText = error?.let { { Text(stringResource(TaskText.blocked(it))) } },
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = if (singleLine) ImeAction.Next else ImeAction.Default),
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * A number typed as text. The field keeps what the user typed (so clearing it to retype works) and reports a value only
 * while the text is a number inside [min]..[max]; otherwise it reports [fallback], which the validator then flags.
 */
@Composable
fun NumberField(
    value: Int,
    onValueChange: (Int) -> Unit,
    label: String,
    min: Int,
    max: Int,
    fallback: Int,
    visibility: FieldVisibility,
    reason: BlockReason?,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable { mutableStateOf(value.toString()) }
    TaskTextField(
        value = text,
        onValueChange = { typed ->
            text = ActionInput.digitsOnly(typed, maxLength = max.toString().length)
            onValueChange(ActionInput.number(text, min, max) ?: fallback)
        },
        label = label,
        visibility = visibility,
        reason = reason ?: if (ActionInput.number(text, min, max) == null) BlockReason.OUT_OF_RANGE else null,
        keyboardType = KeyboardType.Number,
        modifier = modifier,
    )
}

/** A small set of mutually exclusive options as chips, with the group named for screen readers. */
@Composable
fun <T> ChoiceChips(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).semantics { contentDescription = title },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
            }
        }
    }
}

/** A 0..100 slider whose current value is written above it. */
@Composable
fun PercentSlider(
    valueText: String,
    percent: Int,
    onPercentChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Text(valueText, style = MaterialTheme.typography.titleSmall)
        Slider(
            value = percent.toFloat(),
            onValueChange = { onPercentChange(it.toInt()) },
            valueRange = 0f..100f,
            modifier = Modifier.semantics { contentDescription = valueText },
        )
    }
}

/** A short explanation under a form. */
@Composable
fun FormNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().padding(top = 4.dp),
    )
}
