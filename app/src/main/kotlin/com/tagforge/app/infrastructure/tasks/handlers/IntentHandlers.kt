package com.tagforge.app.infrastructure.tasks.handlers

import android.content.Context
import android.content.Intent
import com.tagforge.app.infrastructure.android.IntentLauncher
import com.tagforge.app.infrastructure.android.LaunchResult
import com.tagforge.app.infrastructure.android.SystemIntents
import com.tagforge.app.infrastructure.tasks.ActionHandler
import com.tagforge.domain.task.ActionCheck
import com.tagforge.domain.task.ActionOutcome
import com.tagforge.domain.task.ActionValidator
import com.tagforge.domain.task.FailReason
import com.tagforge.domain.task.SkipReason
import com.tagforge.domain.task.TaskAction
import kotlin.time.Clock

/** Result of handing an Intent to another app: it opened, or there is nothing installed that can take it. */
internal fun LaunchResult.toOutcome(): ActionOutcome = when (this) {
    LaunchResult.STARTED -> ActionOutcome.Done()
    LaunchResult.NO_HANDLER -> ActionOutcome.Failed(FailReason.NO_HANDLER)
    LaunchResult.DENIED -> ActionOutcome.Failed(FailReason.SECURITY)
}

/** Defence in depth: the use case already skips blocked actions, a handler must still never run one. */
private fun TaskAction.blockedOrNull(): ActionOutcome? =
    if (ActionValidator.check(this) is ActionCheck.Blocked) ActionOutcome.Skipped(SkipReason.BLOCKED) else null

class OpenUrlHandler(private val launcher: IntentLauncher) : ActionHandler<TaskAction.OpenUrl> {
    override suspend fun execute(action: TaskAction.OpenUrl): ActionOutcome =
        action.blockedOrNull() ?: launcher.launch(SystemIntents.view(action.url)).toOutcome()
}

/** Opens the dialer with the number typed in. Never places the call: there is no CALL_PHONE permission. */
class DialHandler(private val launcher: IntentLauncher) : ActionHandler<TaskAction.Dial> {
    override suspend fun execute(action: TaskAction.Dial): ActionOutcome =
        action.blockedOrNull() ?: launcher.launch(SystemIntents.dial(action.number)).toOutcome()
}

/** Opens the messaging app with number and text filled in. Never sends: there is no SEND_SMS permission. */
class SmsComposeHandler(private val launcher: IntentLauncher) : ActionHandler<TaskAction.SmsCompose> {
    override suspend fun execute(action: TaskAction.SmsCompose): ActionOutcome =
        action.blockedOrNull() ?: launcher.launch(SystemIntents.smsCompose(action.number, action.body)).toOutcome()
}

class AlarmHandler(private val launcher: IntentLauncher) : ActionHandler<TaskAction.Alarm> {
    override suspend fun execute(action: TaskAction.Alarm): ActionOutcome =
        action.blockedOrNull() ?: launcher.launch(SystemIntents.alarm(action.hour, action.minute, action.label)).toOutcome()
}

class TimerHandler(private val launcher: IntentLauncher) : ActionHandler<TaskAction.Timer> {
    override suspend fun execute(action: TaskAction.Timer): ActionOutcome =
        action.blockedOrNull() ?: launcher.launch(SystemIntents.timer(action.seconds, action.label)).toOutcome()
}

class CalendarEventHandler(
    private val launcher: IntentLauncher,
    private val clock: Clock,
) : ActionHandler<TaskAction.CalendarEvent> {
    override suspend fun execute(action: TaskAction.CalendarEvent): ActionOutcome = action.blockedOrNull()
        ?: launcher.launch(SystemIntents.calendarEvent(action.title, clock.now().toEpochMilliseconds(), action.durationMinutes)).toOutcome()
}

/** Opens the screen for [com.tagforge.domain.task.SettingsTarget]; this is how toggles an app cannot flip are honoured. */
class OpenSettingsHandler(
    private val launcher: IntentLauncher,
    private val apiLevel: Int,
) : ActionHandler<TaskAction.OpenSettings> {
    override suspend fun execute(action: TaskAction.OpenSettings): ActionOutcome =
        launcher.launchFirst(SystemIntents.settings(action.screen, apiLevel), Intent(android.provider.Settings.ACTION_SETTINGS)).toOutcome()
}

/**
 * Starts another app's launcher activity. Uses package visibility from the `<queries>` launcher intent, so there is no
 * QUERY_ALL_PACKAGES. An app that is not installed is a plain, honest failure.
 */
class LaunchAppHandler(private val context: Context, private val launcher: IntentLauncher) : ActionHandler<TaskAction.LaunchApp> {
    override suspend fun execute(action: TaskAction.LaunchApp): ActionOutcome {
        action.blockedOrNull()?.let { return it }
        val intent = context.packageManager.getLaunchIntentForPackage(action.packageName)
            ?: return ActionOutcome.Failed(FailReason.NOT_INSTALLED, action.packageName)
        return launcher.launch(intent).toOutcome()
    }
}
