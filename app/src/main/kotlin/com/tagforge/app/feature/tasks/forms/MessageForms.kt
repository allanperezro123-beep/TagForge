package com.tagforge.app.feature.tasks.forms

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.tagforge.app.R
import com.tagforge.domain.task.BlockReason
import com.tagforge.domain.task.TaskAction
import com.tagforge.domain.task.TaskLimits

private val NUMBER_REASONS = setOf(BlockReason.EMPTY_VALUE, BlockReason.INVALID_NUMBER)

@Composable
fun SmsForm(action: TaskAction.SmsCompose, reason: BlockReason?, visibility: FieldVisibility, onChange: (TaskAction) -> Unit) {
    TaskTextField(
        value = action.number,
        onValueChange = { onChange(action.copy(number = it.trim())) },
        label = stringResource(R.string.form_number),
        visibility = visibility,
        reason = reason.takeIf { it in NUMBER_REASONS },
        keyboardType = KeyboardType.Phone,
    )
    TaskTextField(
        value = action.body,
        onValueChange = { onChange(action.copy(body = it)) },
        label = stringResource(R.string.form_sms_body),
        visibility = visibility,
        reason = reason.takeIf { it == BlockReason.TOO_LONG },
        singleLine = false,
        minLines = 3,
    )
    FormNote(stringResource(R.string.form_sms_note))
}

@Composable
fun DialForm(action: TaskAction.Dial, reason: BlockReason?, visibility: FieldVisibility, onChange: (TaskAction) -> Unit) {
    TaskTextField(
        value = action.number,
        onValueChange = { onChange(action.copy(number = it.trim())) },
        label = stringResource(R.string.form_number),
        visibility = visibility,
        reason = reason,
        keyboardType = KeyboardType.Phone,
    )
    FormNote(stringResource(R.string.form_dial_note))
}

@Composable
fun NotifyForm(action: TaskAction.Notify, visibility: FieldVisibility, onChange: (TaskAction) -> Unit) {
    val titleReason = when {
        action.title.isBlank() -> BlockReason.EMPTY_VALUE
        action.title.tooLong() -> BlockReason.TOO_LONG
        else -> null
    }
    TaskTextField(
        value = action.title,
        onValueChange = { onChange(action.copy(title = it)) },
        label = stringResource(R.string.form_notify_title),
        visibility = visibility,
        reason = titleReason,
    )
    TaskTextField(
        value = action.text,
        onValueChange = { onChange(action.copy(text = it)) },
        label = stringResource(R.string.form_notify_text),
        visibility = visibility,
        reason = BlockReason.TOO_LONG.takeIf { action.text.tooLong() },
        singleLine = false,
        minLines = 2,
    )
}

private fun String.tooLong() = toByteArray(Charsets.UTF_8).size > TaskLimits.MAX_STRING_BYTES

@Composable
fun SpeakForm(action: TaskAction.Speak, reason: BlockReason?, visibility: FieldVisibility, onChange: (TaskAction) -> Unit) {
    TaskTextField(
        value = action.text,
        onValueChange = { onChange(action.copy(text = it)) },
        label = stringResource(R.string.form_speak_text),
        visibility = visibility,
        reason = reason,
        singleLine = false,
        minLines = 2,
    )
}
