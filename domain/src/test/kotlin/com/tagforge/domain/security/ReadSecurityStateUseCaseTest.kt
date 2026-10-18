package com.tagforge.domain.security

import com.tagforge.domain.common.Outcome
import com.tagforge.domain.fakes.FakeTagSession
import com.tagforge.domain.fakes.hex
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.tag.TagError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReadSecurityStateUseCaseTest {

    @Test
    fun readsTheConfigAndLockPagesOfABlankTag() = runTest {
        val state = stateOf(specTag(NtagModel.NTAG215))

        assertEquals(NtagModel.NTAG215, state.model)
        assertEquals("04 00 00 FF".hex(), state.config.cfg0)
        assertEquals("00 00 00 00".hex(), state.config.cfg1)
        assertEquals("00 00".hex(), state.staticLock)
        assertEquals("E1 10 3E 00".hex(), state.cc)
        assertEquals("00 00 00".hex(), state.dynamicLock)
        assertFalse(state.isPasswordProtected)
    }

    @Test
    fun readsTheSameFieldsOnAnotherChipAndSeesLockBits() = runTest {
        val tag = specTag(NtagModel.NTAG213)
        tag.setPage(2, "00 00 12 0F".hex())
        tag.setPage(NtagModel.NTAG213.dynLock, "05 00 01 BD".hex())

        val state = stateOf(tag)

        assertEquals(NtagModel.NTAG213, state.model)
        assertEquals("12 0F".hex(), state.staticLock)
        assertEquals("05 00 01".hex(), state.dynamicLock)
    }

    @Test
    fun aProtectedTagNeedsThePasswordFirst() = runTest {
        val tag = specTag()
        tag.setPage(tag.model.pwd, "12 34 56 78".hex())
        tag.setPage(tag.model.pack, "AB CD 00 00".hex())
        tag.setPage(tag.model.cfg1, "80 00 00 00".hex())
        tag.setPage(tag.model.cfg0, "04 00 00 04".hex())

        val without = ReadSecurityStateUseCase().runOn(sessionOf(tag))
        assertEquals(Outcome.Err(TagError.AuthRequired), without)

        val state = stateOf(tag, Password.fromHex("12345678"))
        assertTrue(state.isPasswordProtected)
        assertEquals(4, state.config.auth0)
        assertEquals("AB CD".hex(), state.pack, "the PACK answered by PWD_AUTH is kept for the clone check")
    }

    @Test
    fun aWrongPasswordIsReportedAsAuthFailed() = runTest {
        val tag = specTag()
        tag.setPage(tag.model.pwd, "12 34 56 78".hex())
        tag.setPage(tag.model.cfg1, "80 00 00 00".hex())
        tag.setPage(tag.model.cfg0, "04 00 00 04".hex())

        val result = ReadSecurityStateUseCase(Password.fromHex("00000001")).runOn(sessionOf(tag))

        assertEquals(Outcome.Err(TagError.AuthFailed(null)), result)
    }

    @Test
    fun aTagThatIsNotAnNtagIsUnsupported() = runTest {
        val result = ReadSecurityStateUseCase().runOn(FakeTagSession.withoutNtag())

        assertEquals(SecurityFailure.UnsupportedChip, result.securityFailure())
    }

    @Test
    fun tagLostWhileReadingIsTagLost() = runTest {
        val tag = specTag()
        tag.failNextAfter(1, TagError.TagLost)

        assertEquals(Outcome.Err(TagError.TagLost), ReadSecurityStateUseCase().runOn(sessionOf(tag)))
    }
}
