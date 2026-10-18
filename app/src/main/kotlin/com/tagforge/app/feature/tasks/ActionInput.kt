package com.tagforge.app.feature.tasks

import com.tagforge.domain.task.TaskAction
import com.tagforge.domain.task.TaskRisk
import com.tagforge.domain.task.risk

/** Text-to-number rules shared by the action forms. Pure, so they are tested without a screen. */
object ActionInput {

    /** The number the user typed, or null when the text is blank, not a plain number, or outside [min]..[max]. */
    fun number(text: String, min: Int, max: Int): Int? = text.trim().toIntOrNull()?.takeIf { it in min..max }

    /** Keeps only digits (so "-" and "." cannot be typed into a number field), at most [maxLength] of them. */
    fun digitsOnly(text: String, maxLength: Int = Int.MAX_VALUE): String = text.filter { it.isDigit() }.take(maxLength)
}

/** The risk badges a task row shows: each elevated kind once, in the order the actions appear. */
object TaskRiskSummary {
    fun of(actions: List<TaskAction>): List<TaskRisk> = actions.map { it.risk }.filter { it != TaskRisk.NONE }.distinct()
}
