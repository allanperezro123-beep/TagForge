package com.tagforge.app.infrastructure.tasks.handlers

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tagforge.app.R
import com.tagforge.app.infrastructure.android.IntentLauncher
import com.tagforge.app.infrastructure.android.SystemIntents
import com.tagforge.app.infrastructure.tasks.ActionHandler
import com.tagforge.domain.task.ActionCheck
import com.tagforge.domain.task.ActionOutcome
import com.tagforge.domain.task.ActionValidator
import com.tagforge.domain.task.FailReason
import com.tagforge.domain.task.FallbackReason
import com.tagforge.domain.task.MediaButton
import com.tagforge.domain.task.RingerMode
import com.tagforge.domain.task.SkipReason
import com.tagforge.domain.task.SpecialGrant
import com.tagforge.domain.task.TaskAction
import com.tagforge.domain.task.VolumeStream
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private fun TaskAction.blocked(): ActionOutcome? =
    if (ActionValidator.check(this) is ActionCheck.Blocked) ActionOutcome.Skipped(SkipReason.BLOCKED) else null

/**
 * Ringer mode. "Silent" needs Do Not Disturb access: without it the phone is set to vibrate instead, the Do Not
 * Disturb access screen opens, and the result says exactly that (TK-07), never "done".
 */
class SetRingerHandler(
    private val context: Context,
    private val launcher: IntentLauncher,
) : ActionHandler<TaskAction.SetRinger> {

    override suspend fun execute(action: TaskAction.SetRinger): ActionOutcome {
        val audio = context.getSystemService(AudioManager::class.java)
        val notifications = context.getSystemService(NotificationManager::class.java)
        return try {
            when (action.mode) {
                RingerMode.NORMAL -> audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
                RingerMode.VIBRATE -> audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                RingerMode.SILENT -> {
                    if (!notifications.isNotificationPolicyAccessGranted) return silentFallback(audio)
                    audio.ringerMode = AudioManager.RINGER_MODE_SILENT
                }
            }
            ActionOutcome.Done()
        } catch (_: SecurityException) {
            silentFallback(audio)
        }
    }

    private fun silentFallback(audio: AudioManager): ActionOutcome {
        runCatching { audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE }
        launcher.launch(SystemIntents.notificationPolicyAccess())
        return ActionOutcome.PermissionNeeded(SpecialGrant.NOTIFICATION_POLICY, FallbackReason.VIBRATE_INSTEAD_OF_SILENT)
    }
}

class SetVolumeHandler(
    private val context: Context,
    private val launcher: IntentLauncher,
) : ActionHandler<TaskAction.SetVolume> {

    override suspend fun execute(action: TaskAction.SetVolume): ActionOutcome {
        action.blocked()?.let { return it }
        val audio = context.getSystemService(AudioManager::class.java)
        val stream = when (action.stream) {
            VolumeStream.MEDIA -> AudioManager.STREAM_MUSIC
            VolumeStream.RING -> AudioManager.STREAM_RING
            VolumeStream.ALARM -> AudioManager.STREAM_ALARM
            VolumeStream.NOTIFICATION -> AudioManager.STREAM_NOTIFICATION
        }
        return try {
            val index = Math.round(audio.getStreamMaxVolume(stream) * action.level / PERCENT_MAX)
            audio.setStreamVolume(stream, index, 0)
            ActionOutcome.Done("${action.level}%")
        } catch (_: SecurityException) {
            launcher.launch(SystemIntents.notificationPolicyAccess())
            ActionOutcome.PermissionNeeded(SpecialGrant.NOTIFICATION_POLICY)
        }
    }

    private companion object {
        const val PERCENT_MAX = 100f
    }
}

/** Sends the media key press (and release) to whichever player is active. */
class MediaKeyHandler(private val context: Context) : ActionHandler<TaskAction.MediaKey> {
    override suspend fun execute(action: TaskAction.MediaKey): ActionOutcome {
        val audio = context.getSystemService(AudioManager::class.java)
        val code = when (action.key) {
            MediaButton.PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            MediaButton.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            MediaButton.PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
        }
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        return ActionOutcome.Done()
    }
}

/** Needs "Modify system settings". Without it the grant screen opens and the result says "permission needed". */
class SetBrightnessHandler(
    private val context: Context,
    private val launcher: IntentLauncher,
) : ActionHandler<TaskAction.SetBrightness> {

    override suspend fun execute(action: TaskAction.SetBrightness): ActionOutcome {
        action.blocked()?.let { return it }
        if (!Settings.System.canWrite(context)) {
            launcher.launch(SystemIntents.manageWriteSettings(context.packageName))
            return ActionOutcome.PermissionNeeded(SpecialGrant.WRITE_SETTINGS)
        }
        return try {
            val resolver = context.contentResolver
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, action.level)
            ActionOutcome.Done()
        } catch (_: SecurityException) {
            ActionOutcome.PermissionNeeded(SpecialGrant.WRITE_SETTINGS)
        }
    }
}

/** `CameraManager.setTorchMode` needs no CAMERA permission. */
class FlashlightHandler(private val context: Context) : ActionHandler<TaskAction.Flashlight> {
    override suspend fun execute(action: TaskAction.Flashlight): ActionOutcome {
        val manager = context.getSystemService(CameraManager::class.java)
        return try {
            val id = manager.cameraIdList.firstOrNull { cameraId ->
                val characteristics = manager.getCameraCharacteristics(cameraId)
                characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return ActionOutcome.Failed(FailReason.UNAVAILABLE)
            manager.setTorchMode(id, action.on)
            ActionOutcome.Done()
        } catch (_: CameraAccessException) {
            ActionOutcome.Failed(FailReason.UNAVAILABLE)
        } catch (_: IllegalArgumentException) {
            ActionOutcome.Failed(FailReason.UNAVAILABLE)
        }
    }
}

/** Reads text aloud. Waits for the engine to initialise and for the utterance to finish (at most [MAX_SPEECH_MILLIS]). */
class SpeakHandler(private val context: Context) : ActionHandler<TaskAction.Speak> {

    override suspend fun execute(action: TaskAction.Speak): ActionOutcome {
        action.blocked()?.let { return it }
        var engine: TextToSpeech? = null
        try {
            val ready = suspendCancellableCoroutine { continuation ->
                engine = TextToSpeech(context.applicationContext) { status ->
                    if (continuation.isActive) continuation.resume(status == TextToSpeech.SUCCESS)
                }
            }
            val tts = engine
            if (!ready || tts == null) return ActionOutcome.Failed(FailReason.UNAVAILABLE)

            val finished = withTimeoutOrNull(MAX_SPEECH_MILLIS) {
                suspendCancellableCoroutine { continuation -> speak(tts, action.text, continuation) }
            }
            return if (finished == false) ActionOutcome.Failed(FailReason.UNAVAILABLE) else ActionOutcome.Done()
        } finally {
            engine?.shutdown()
        }
    }

    private fun speak(tts: TextToSpeech, text: String, continuation: CancellableContinuation<Boolean>) {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                if (continuation.isActive) continuation.resume(true)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (continuation.isActive) continuation.resume(false)
            }
        })
        val queued = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        if (queued != TextToSpeech.SUCCESS && continuation.isActive) continuation.resume(false)
    }

    private companion object {
        const val UTTERANCE_ID = "tagforge-task"
        const val MAX_SPEECH_MILLIS = 30_000L
    }
}

/** Posts a notification on the "tasks" channel. On Android 13+ it needs the runtime permission (see Permission Center). */
class NotifyHandler(private val context: Context) : ActionHandler<TaskAction.Notify> {

    override suspend fun execute(action: TaskAction.Notify): ActionOutcome {
        action.blocked()?.let { return it }
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) return ActionOutcome.PermissionNeeded(SpecialGrant.POST_NOTIFICATIONS)

        ensureChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_task)
            .setContentTitle(action.title)
            .setContentText(action.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(action.text))
            .setAutoCancel(true)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), notification)
            ActionOutcome.Done()
        } catch (_: SecurityException) {
            ActionOutcome.PermissionNeeded(SpecialGrant.POST_NOTIFICATIONS)
        }
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.tasks_notification_channel), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }

    companion object {
        const val CHANNEL_ID = "tasks"
    }
}
