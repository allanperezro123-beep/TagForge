package com.tagforge.app.feature.tasks.forms

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tagforge.app.R
import com.tagforge.app.ui.text.TaskText
import com.tagforge.domain.task.MediaButton
import com.tagforge.domain.task.RingerMode
import com.tagforge.domain.task.TaskAction
import com.tagforge.domain.task.VolumeStream
import kotlin.math.roundToInt

private const val BRIGHTNESS_MAX = 255

@Composable
fun RingerForm(action: TaskAction.SetRinger, onChange: (TaskAction) -> Unit) {
    ChoiceChips(
        title = stringResource(R.string.form_ringer_label),
        options = RingerMode.entries,
        selected = action.mode,
        label = { stringResource(TaskText.ringerRes(it)) },
        onSelect = { onChange(action.copy(mode = it)) },
    )
}

@Composable
fun VolumeForm(action: TaskAction.SetVolume, onChange: (TaskAction) -> Unit) {
    ChoiceChips(
        title = stringResource(R.string.form_stream_label),
        options = VolumeStream.entries,
        selected = action.stream,
        label = { stringResource(TaskText.streamRes(it)) },
        onSelect = { onChange(action.copy(stream = it)) },
    )
    PercentSlider(
        valueText = stringResource(R.string.form_volume_level, action.level),
        percent = action.level,
        onPercentChange = { onChange(action.copy(level = it)) },
    )
}

@Composable
fun BrightnessForm(action: TaskAction.SetBrightness, onChange: (TaskAction) -> Unit) {
    val percent = (action.level * 100f / BRIGHTNESS_MAX).roundToInt()
    PercentSlider(
        valueText = stringResource(R.string.form_brightness_level, percent),
        percent = percent,
        onPercentChange = { onChange(action.copy(level = (it * BRIGHTNESS_MAX / 100f).roundToInt())) },
    )
}

@Composable
fun MediaForm(action: TaskAction.MediaKey, onChange: (TaskAction) -> Unit) {
    ChoiceChips(
        title = stringResource(R.string.form_media_label),
        options = MediaButton.entries,
        selected = action.key,
        label = { stringResource(TaskText.mediaRes(it)) },
        onSelect = { onChange(action.copy(key = it)) },
    )
}

/** On / Off for flashlight, Wi-Fi and Bluetooth. */
@Composable
fun OnOffForm(on: Boolean, onChange: (Boolean) -> Unit) {
    ChoiceChips(
        title = stringResource(R.string.form_state_label),
        options = listOf(true, false),
        selected = on,
        label = { stringResource(if (it) R.string.form_state_on else R.string.form_state_off) },
        onSelect = onChange,
    )
}
