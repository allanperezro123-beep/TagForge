package com.tagforge.domain.security

import com.tagforge.domain.fakes.hex
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.tag.TagError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** S7: a data class prints its fields, so anything that carries a password or PACK must not print them (logs, crash reports). */
class SecretRedactionTest {

    private val password = "12 34 56 78".hex()
    private val pack = "AB CD".hex()

    private fun assertNoSecret(text: String) {
        val squashed = text.uppercase().replace(" ", "")
        assertFalse("12345678" in squashed, "password leaked: $text")
        assertFalse("ABCD" in squashed, "PACK leaked: $text")
    }

    @Test
    fun aPasswordPageWriteDoesNotPrintTheNewPassword() {
        val step = PlanStep.WritePage(page = 133, old = null, new = password)

        assertNoSecret(step.toString())
        assertTrue("133" in step.toString(), "the page is still named: $step")
    }

    @Test
    fun anAuthenticationStepDoesNotPrintItsPassword() {
        assertNoSecret(PlanStep.Authenticate(password).toString())
    }

    @Test
    fun aRawFrameStepDoesNotPrintTheFrame() {
        assertNoSecret(PlanStep.Raw("1B 12 34 56 78".hex()).toString())
    }

    @Test
    fun checksDoNotPrintPasswordsPacksOrExpectedSecretPages() {
        assertNoSecret(Check.AuthWorks(password, pack).toString())
        assertNoSecret(Check.PageEquals(133, password).toString())
        assertNoSecret(SecurityFailure.NotVerified(Check.AuthWorks(password, pack)).toString())
    }

    @Test
    fun aWholePlanPrintsNoSecretEvenThoughItsStepsDo() {
        val plan = SecurityPlan(
            SecurityOperation.RAW_WRITE, "04 A1 B2 C3 D4 E5 F6".hex(), NtagModel.NTAG215,
            listOf(PlanStep.Authenticate(password), PlanStep.WritePage(134, null, "AB CD 00 00".hex())),
            emptySet(), listOf(Check.AuthWorks(password, pack)),
        )

        assertNoSecret(plan.toString())
        assertEquals(listOf("1B 12 34 56 78".hex()), plan.commands.take(1), "the real frames are still available to the executor")
    }

    @Test
    fun nonSecretChecksStayReadable() {
        assertEquals("PageHasBits(page=2, bits=Bytes(00 00 F0 FF))", Check.PageHasBits(2, "00 00 F0 FF".hex()).toString())
        assertEquals("NdefReadOnly", Check.NdefReadOnly.toString().removePrefix("Check.").ifEmpty { "NdefReadOnly" })
        val failure = TagError.Security(SecurityFailure.UidMismatch)
        assertTrue("UidMismatch" in failure.toString())
    }
}
