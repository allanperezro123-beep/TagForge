package com.tagforge.domain.fakes

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.ntag.NtagCommands
import com.tagforge.domain.ntag.NtagModel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Password protection in the fake, pinned against datasheet Rev. 3.2 sections 8.8 and 10.7: PWD_AUTH returns PACK,
 * PWD/PACK always READ as 00, AUTHLIM counts negative attempts and then rejects even the right password.
 */
class FakeNtag21xAuthTest {

    private val model = NtagModel.NTAG215
    private val nak = Bytes.of(0x00)
    private val nakCounterOverflow = Bytes.of(0x04)

    private fun protectedTag(access: String = "80 00 00 00"): FakeNtag21x = FakeNtag21x(model).also {
        it.setPage(model.pwd, "12 34 56 78".hex())
        it.setPage(model.pack, "AB CD 00 00".hex())
        it.setPage(model.cfg1, access.hex())
        it.setPage(model.cfg0, "04 00 00 04".hex())
    }

    private suspend fun FakeNtag21x.send(command: Bytes): Bytes {
        val result = transceive(command)
        check(result is Outcome.Ok) { "transceive failed: $result" }
        return result.value
    }

    private suspend fun FakeNtag21x.auth(password: String): Bytes = send(NtagCommands.pwdAuth(password.hex()))

    @Test
    fun protectedTagNaksReadsUntilTheRightPasswordIsSent() = runTest {
        val tag = protectedTag()

        assertEquals(nak, tag.send(NtagCommands.read(4)), "READ page 04 is protected")
        assertEquals("AB CD".hex(), tag.auth("12 34 56 78"), "PWD_AUTH answers with PACK")
        assertEquals(16, tag.send(NtagCommands.read(4)).size, "READ works after authentication")
    }

    @Test
    fun aWrongPasswordIsNakedAndDoesNotUnlock() = runTest {
        val tag = protectedTag()

        assertEquals(nak, tag.auth("00 00 00 00"))
        assertEquals(nak, tag.send(NtagCommands.read(4)))
    }

    @Test
    fun pwdAndPackAlwaysReadAsZeros() = runTest {
        val tag = protectedTag()
        tag.auth("12 34 56 78")

        assertNotEquals(Bytes.of(0, 0, 0, 0), tag.page(model.pwd), "the real password is stored")
        assertEquals("00".repeat(8).hex(), tag.send(NtagCommands.fastRead(model.pwd, model.pack)))
        assertEquals("00 00 00 00".hex(), tag.send(NtagCommands.read(model.pwd)).slice(0, 4))
        assertEquals("80 00 00 00".hex(), tag.send(NtagCommands.read(model.cfg1)).slice(0, 4), "CFG1 itself stays readable")
    }

    @Test
    fun withAuthLimitZeroAnyNumberOfWrongTriesStillAllowsTheRightPassword() = runTest {
        val tag = protectedTag(access = "80 00 00 00")
        repeat(20) { assertEquals(nak, tag.auth("11 11 11 11")) }

        assertEquals("AB CD".hex(), tag.auth("12 34 56 78"))
    }

    @Test
    fun whenTheLimitIsReachedEvenTheCorrectPasswordIsRejected() = runTest {
        val tag = protectedTag(access = "83 00 00 00")   // PROT, AUTHLIM = 3
        repeat(3) { assertEquals(nak, tag.auth("11 11 11 11")) }

        assertEquals(nakCounterOverflow, tag.auth("12 34 56 78"), "NAK 4h: authentication counter overflow")
        assertEquals(nak, tag.send(NtagCommands.read(4)), "the protected area stays locked")
    }

    @Test
    fun aSuccessfulAuthenticationBeforeTheLimitResetsTheCounter() = runTest {
        val tag = protectedTag(access = "83 00 00 00")
        repeat(2) { tag.auth("11 11 11 11") }
        assertEquals("AB CD".hex(), tag.auth("12 34 56 78"))

        repeat(2) { tag.auth("11 11 11 11") }
        assertEquals("AB CD".hex(), tag.auth("12 34 56 78"), "2 + 2 wrong tries never reached 3 in a row")
    }
}
