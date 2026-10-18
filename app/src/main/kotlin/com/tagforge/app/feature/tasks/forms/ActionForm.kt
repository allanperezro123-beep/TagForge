package com.tagforge.app.feature.tasks.forms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.app.feature.tasks.ActionKind
import com.tagforge.app.feature.tasks.EditingAction
import com.tagforge.app.feature.tasks.titleRes
import com.tagforge.app.feature.taskrunner.RiskBadge
import com.tagforge.domain.task.ActionCheck
import com.tagforge.domain.task.TaskAction
import com.tagforge.domain.task.risk

/**
 * The form of the action being added or edited: the fields for its kind, the risk badge when it is one of the elevated
 * kinds, and Add/Update. Errors show once the user typed in a field or pressed Add; a blocked action cannot be added.
 */
@Composable
fun ActionForm(
    editing: EditingAction,
    onChange: (TaskAction) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val action = editing.action
    val kind = ActionKind.of(action)
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(kind.titleRes()),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        RiskBadge(action.risk)

        ActionFields(action, editing, onChange)

        if ((editing.check as? ActionCheck.Warn) != null) {
            Text(
                stringResource(R.string.form_insecure_http),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel)) }
            Button(onClick = onConfirm) {
                Text(stringResource(if (editing.index == null) R.string.builder_form_add else R.string.builder_form_update))
            }
        }
    }
}

/** The fields for one action. Each form reports a new, whole action; the ViewModel validates it. */
@Composable
private fun ActionFields(action: TaskAction, editing: EditingAction, onChange: (TaskAction) -> Unit) {
    val reason = editing.blockReason
    val visibility = FieldVisibility(editing.showAllErrors)
    when (action) {
        is TaskAction.SetRinger -> RingerForm(action, onChange)
        is TaskAction.SetVolume -> VolumeForm(action, onChange)
        is TaskAction.SetBrightness -> BrightnessForm(action, onChange)
        is TaskAction.MediaKey -> MediaForm(action, onChange)
        is TaskAction.Flashlight -> OnOffForm(action.on) { onChange(action.copy(on = it)) }
        is TaskAction.WifiToggle -> OnOffForm(action.enabled) { onChange(action.copy(enabled = it)) }
        is TaskAction.BluetoothToggle -> OnOffForm(action.enabled) { onChange(action.copy(enabled = it)) }
        is TaskAction.OpenSettings -> SettingsForm(action, onChange)
        is TaskAction.WifiJoin -> WifiJoinForm(action, visibility, onChange)
        is TaskAction.OpenUrl -> OpenUrlForm(action, reason, visibility, onChange)
        is TaskAction.LaunchApp -> LaunchAppForm(action, reason, visibility, onChange)
        is TaskAction.HttpRequest -> HttpForm(action, reason, visibility, onChange)
        is TaskAction.SmsCompose -> SmsForm(action, reason, visibility, onChange)
        is TaskAction.Dial -> DialForm(action, reason, visibility, onChange)
        is TaskAction.Notify -> NotifyForm(action, visibility, onChange)
        is TaskAction.Speak -> SpeakForm(action, reason, visibility, onChange)
        is TaskAction.Alarm -> AlarmForm(action, reason, visibility, onChange)
        is TaskAction.Timer -> TimerForm(action, reason, visibility, onChange)
        is TaskAction.CalendarEvent -> CalendarForm(action, reason, visibility, onChange)
    }
}
