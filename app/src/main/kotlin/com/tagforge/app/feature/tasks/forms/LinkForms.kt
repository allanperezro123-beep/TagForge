package com.tagforge.app.feature.tasks.forms

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.tagforge.app.R
import com.tagforge.app.ui.text.TaskText
import com.tagforge.domain.task.BlockReason
import com.tagforge.domain.task.HttpMethod
import com.tagforge.domain.task.JoinAuth
import com.tagforge.domain.task.SettingsTarget
import com.tagforge.domain.task.TaskAction

private const val MAX_SSID_BYTES = 32
private const val MIN_WPA_KEY = 8
private const val MAX_WPA_KEY = 63

@Composable
fun OpenUrlForm(action: TaskAction.OpenUrl, reason: BlockReason?, visibility: FieldVisibility, onChange: (TaskAction) -> Unit) {
    TaskTextField(
        value = action.url,
        onValueChange = { onChange(action.copy(url = it.trim())) },
        label = stringResource(R.string.form_url),
        visibility = visibility,
        reason = reason,
        placeholder = stringResource(R.string.form_url_hint),
        keyboardType = KeyboardType.Uri,
    )
    FormNote(stringResource(R.string.form_url_note))
}

@Composable
fun LaunchAppForm(action: TaskAction.LaunchApp, reason: BlockReason?, visibility: FieldVisibility, onChange: (TaskAction) -> Unit) {
    TaskTextField(
        value = action.packageName,
        onValueChange = { onChange(action.copy(packageName = it.trim())) },
        label = stringResource(R.string.form_package),
        visibility = visibility,
        reason = reason,
        placeholder = stringResource(R.string.form_package_hint),
        keyboardType = KeyboardType.Uri,
    )
    FormNote(stringResource(R.string.form_package_note))
}

@Composable
fun HttpForm(action: TaskAction.HttpRequest, reason: BlockReason?, visibility: FieldVisibility, onChange: (TaskAction) -> Unit) {
    TaskTextField(
        value = action.url,
        onValueChange = { onChange(action.copy(url = it.trim())) },
        label = stringResource(R.string.form_http_url),
        visibility = visibility,
        reason = reason,
        placeholder = stringResource(R.string.form_url_hint),
        keyboardType = KeyboardType.Uri,
    )
    ChoiceChips(
        title = stringResource(R.string.form_http_method),
        options = HttpMethod.entries,
        selected = action.method,
        label = { stringResource(if (it == HttpMethod.GET) R.string.form_http_get else R.string.form_http_post) },
        onSelect = { onChange(action.copy(method = it)) },
    )
    FormNote(stringResource(R.string.form_http_note))
}

@Composable
fun SettingsForm(action: TaskAction.OpenSettings, onChange: (TaskAction) -> Unit) {
    ChoiceChips(
        title = stringResource(R.string.form_settings_label),
        options = SettingsTarget.entries,
        selected = action.screen,
        label = { stringResource(TaskText.settingsRes(it)).replaceFirstChar(Char::titlecase) },
        onSelect = { onChange(action.copy(screen = it)) },
    )
    FormNote(stringResource(R.string.form_settings_note))
}

@Composable
fun WifiJoinForm(action: TaskAction.WifiJoin, visibility: FieldVisibility, onChange: (TaskAction) -> Unit) {
    val ssidReason = when {
        action.ssid.isBlank() -> BlockReason.EMPTY_VALUE
        action.ssid.toByteArray(Charsets.UTF_8).size > MAX_SSID_BYTES -> BlockReason.OUT_OF_RANGE
        else -> null
    }
    val keyReason = BlockReason.OUT_OF_RANGE.takeIf { action.key.length !in MIN_WPA_KEY..MAX_WPA_KEY }
    TaskTextField(
        value = action.ssid,
        onValueChange = { onChange(action.copy(ssid = it)) },
        label = stringResource(R.string.form_ssid),
        visibility = visibility,
        reason = ssidReason,
    )
    if (action.auth != JoinAuth.OPEN) {
        TaskTextField(
            value = action.key,
            onValueChange = { onChange(action.copy(key = it)) },
            label = stringResource(R.string.form_wifi_key),
            visibility = visibility,
            reason = keyReason,
        )
    }
    ChoiceChips(
        title = stringResource(R.string.form_wifi_auth),
        options = JoinAuth.entries,
        selected = action.auth,
        label = { stringResource(TaskText.joinAuthRes(it)).replaceFirstChar(Char::titlecase) },
        onSelect = { onChange(action.copy(auth = it, key = if (it == JoinAuth.OPEN) "" else action.key)) },
    )
    FormNote(stringResource(R.string.form_wifi_note))
}
