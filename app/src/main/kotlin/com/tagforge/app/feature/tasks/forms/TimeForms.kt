package com.tagforge.app.feature.tasks.forms

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tagforge.app.R
import com.tagforge.app.ui.text.TaskText
import com.tagforge.domain.task.BlockReason
import com.tagforge.domain.task.TaskAction

private const val MAX_HOUR = 23
private const val MAX_MINUTE = 59
private const val MAX_U16 = 65535
private const val NOT_A_NUMBER = -1

@Composable
fun AlarmForm(action: TaskAction.Alarm, reason: BlockReason?, visibility: FieldVisibility, onChange: (TaskAction) -> Unit) {
    NumberField(
        value = action.hour,
        onValueChange = { onChange(action.copy(hour = it)) },
        label = stringResource(R.string.form_alarm_hour),
        min = 0,
        max = MAX_HOUR,
        fallback = NOT_A_NUMBER,
        visibility = visibility,
        reason = null,
    )
    NumberField(
        value = action.minute,
        onValueChange = { onChange(action.copy(minute = it)) },
        label = stringResource(R.string.form_alarm_minute),
        min = 0,
        max = MAX_MINUTE,
        fallback = NOT_A_NUMBER,
        visibility = visibility,
        reason = null,
    )
    TaskTextField(
        value = action.label,
        onValueChange = { onChange(action.copy(label = it)) },
        label = stringResource(R.string.form_label),
        visibility = visibility,
        reason = reason.takeIf { it == BlockReason.TOO_LONG },
    )
}

@Composable
fun TimerForm(action: TaskAction.Timer, reason: BlockReason?, visibility: FieldVisibility, onChange: (TaskAction) -> Unit) {
    NumberField(
        value = action.seconds,
        onValueChange = { onChange(action.copy(seconds = it)) },
        label = stringResource(R.string.form_timer_seconds),
        min = 1,
        max = MAX_U16,
        fallback = NOT_A_NUMBER,
        visibility = visibility,
        reason = null,
    )
    if (action.seconds > 0) FormNote(stringResource(R.string.form_timer_total, TaskText.duration(action.seconds)))
    TaskTextField(
        value = action.label,
        onValueChange = { onChange(action.copy(label = it)) },
        label = stringResource(R.string.form_label),
        visibility = visibility,
        reason = reason.takeIf { it == BlockReason.TOO_LONG },
    )
}

@Composable
fun CalendarForm(action: TaskAction.CalendarEvent, reason: BlockReason?, visibility: FieldVisibility, onChange: (TaskAction) -> Unit) {
    TaskTextField(
        value = action.title,
        onValueChange = { onChange(action.copy(title = it)) },
        label = stringResource(R.string.form_event_title),
        visibility = visibility,
        reason = reason.takeIf { it == BlockReason.EMPTY_VALUE },
    )
    NumberField(
        value = action.durationMinutes,
        onValueChange = { onChange(action.copy(durationMinutes = it)) },
        label = stringResource(R.string.form_event_duration),
        min = 1,
        max = MAX_U16,
        fallback = NOT_A_NUMBER,
        visibility = visibility,
        reason = null,
    )
}
