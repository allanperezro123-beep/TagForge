package com.tagforge.app.feature.tasks

import com.tagforge.domain.task.TaskAction
import com.tagforge.domain.task.TaskRisk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The small pure pieces behind the action forms (TK-01) and the task list rows (TK-10). */
class ActionInputTest {

    @Test
    fun numberParsesDigitsWithinTheRange() {
        assertEquals(50, ActionInput.number("50", min = 0, max = 100))
        assertEquals(0, ActionInput.number("0", min = 0, max = 100))
        assertEquals(100, ActionInput.number(" 100 ", min = 0, max = 100))
    }

    @Test
    fun numberRejectsBlankNonNumericAndOutOfRangeText() {
        assertNull(ActionInput.number("", min = 0, max = 100))
        assertNull(ActionInput.number("abc", min = 0, max = 100))
        assertNull(ActionInput.number("101", min = 0, max = 100))
        assertNull(ActionInput.number("-1", min = 0, max = 100))
        assertNull(ActionInput.number("99999999999999999999", min = 0, max = 100))
    }

    @Test
    fun digitsOnlyStripsEverythingThatIsNotADigitAndCapsTheLength() {
        assertEquals("12", ActionInput.digitsOnly("1a2-"))
        assertEquals("123", ActionInput.digitsOnly("12345", maxLength = 3))
        assertEquals("", ActionInput.digitsOnly("abc"))
    }

    @Test
    fun riskSummaryListsEachElevatedRiskOnceInTheOrderOfTheActions() {
        val actions = listOf(
            TaskAction.Dial("123"),
            TaskAction.Flashlight(true),
            TaskAction.OpenUrl("https://a.example"),
            TaskAction.Dial("456"),
            TaskAction.HttpRequest("https://b.example"),
        )

        assertEquals(listOf(TaskRisk.CALL, TaskRisk.LINK, TaskRisk.HTTP), TaskRiskSummary.of(actions))
    }

    @Test
    fun riskSummaryIsEmptyWhenNoActionIsRisky() {
        assertEquals(emptyList(), TaskRiskSummary.of(listOf(TaskAction.Flashlight(false), TaskAction.Speak("hi"))))
    }
}
