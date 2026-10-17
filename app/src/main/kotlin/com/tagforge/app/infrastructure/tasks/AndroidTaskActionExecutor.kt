package com.tagforge.app.infrastructure.tasks

import com.tagforge.domain.ports.TaskActionExecutor
import com.tagforge.domain.task.ActionOutcome
import com.tagforge.domain.task.Capability
import com.tagforge.domain.task.DeviceFacts
import com.tagforge.domain.task.FeasibilityMatrix
import com.tagforge.domain.task.TaskAction

/** Runs one kind of action on the device. Must report expected failures as [ActionOutcome.Failed], not throw. */
fun interface ActionHandler<in A : TaskAction> {
    suspend fun execute(action: A): ActionOutcome
}

/** One handler per action type. A missing handler is a compile error here AND in the `when` of [AndroidTaskActionExecutor]. */
class TaskHandlers(
    val openUrl: ActionHandler<TaskAction.OpenUrl>,
    val launchApp: ActionHandler<TaskAction.LaunchApp>,
    val setRinger: ActionHandler<TaskAction.SetRinger>,
    val setVolume: ActionHandler<TaskAction.SetVolume>,
    val setBrightness: ActionHandler<TaskAction.SetBrightness>,
    val wifiToggle: ActionHandler<TaskAction.WifiToggle>,
    val wifiJoin: ActionHandler<TaskAction.WifiJoin>,
    val bluetoothToggle: ActionHandler<TaskAction.BluetoothToggle>,
    val openSettings: ActionHandler<TaskAction.OpenSettings>,
    val alarm: ActionHandler<TaskAction.Alarm>,
    val timer: ActionHandler<TaskAction.Timer>,
    val calendarEvent: ActionHandler<TaskAction.CalendarEvent>,
    val smsCompose: ActionHandler<TaskAction.SmsCompose>,
    val dial: ActionHandler<TaskAction.Dial>,
    val speak: ActionHandler<TaskAction.Speak>,
    val flashlight: ActionHandler<TaskAction.Flashlight>,
    val notify: ActionHandler<TaskAction.Notify>,
    val httpRequest: ActionHandler<TaskAction.HttpRequest>,
    val mediaKey: ActionHandler<TaskAction.MediaKey>,
)

/**
 * The Android [TaskActionExecutor]: [capability] is the pure [FeasibilityMatrix] over a fresh [facts] snapshot,
 * [execute] hands the action to its handler. The `when` has no `else`, so every [TaskAction] type is covered at
 * compile time.
 */
class AndroidTaskActionExecutor(
    private val handlers: TaskHandlers,
    private val facts: () -> DeviceFacts,
) : TaskActionExecutor {

    override fun capability(action: TaskAction): Capability = FeasibilityMatrix.capability(action, facts())

    override suspend fun execute(action: TaskAction): ActionOutcome = when (action) {
        is TaskAction.OpenUrl -> handlers.openUrl.execute(action)
        is TaskAction.LaunchApp -> handlers.launchApp.execute(action)
        is TaskAction.SetRinger -> handlers.setRinger.execute(action)
        is TaskAction.SetVolume -> handlers.setVolume.execute(action)
        is TaskAction.SetBrightness -> handlers.setBrightness.execute(action)
        is TaskAction.WifiToggle -> handlers.wifiToggle.execute(action)
        is TaskAction.WifiJoin -> handlers.wifiJoin.execute(action)
        is TaskAction.BluetoothToggle -> handlers.bluetoothToggle.execute(action)
        is TaskAction.OpenSettings -> handlers.openSettings.execute(action)
        is TaskAction.Alarm -> handlers.alarm.execute(action)
        is TaskAction.Timer -> handlers.timer.execute(action)
        is TaskAction.CalendarEvent -> handlers.calendarEvent.execute(action)
        is TaskAction.SmsCompose -> handlers.smsCompose.execute(action)
        is TaskAction.Dial -> handlers.dial.execute(action)
        is TaskAction.Speak -> handlers.speak.execute(action)
        is TaskAction.Flashlight -> handlers.flashlight.execute(action)
        is TaskAction.Notify -> handlers.notify.execute(action)
        is TaskAction.HttpRequest -> handlers.httpRequest.execute(action)
        is TaskAction.MediaKey -> handlers.mediaKey.execute(action)
    }
}
