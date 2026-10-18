package com.tagforge.domain.security

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.fakes.FakeTagSession
import com.tagforge.domain.fakes.hex
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.tag.TagError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** NS-07: READ_SIG shows the 32 signature bytes and the UID, and never claims a tag is genuine (no NXP key is bundled). */
class ReadSignatureUseCaseTest {

    @Test
    fun readsThirtyTwoBytesAndTheUidOnEveryChip() = runTest {
        for (model in NtagModel.entries) {
            val tag = specTag(model)
            tag.signature = Bytes.copyOf(ByteArray(32) { (it * 3).toByte() })

            val report = ReadSignatureUseCase().runOn(sessionOf(tag)).value()

            assertEquals(tag.signature, report.signature, model.displayName)
            assertEquals(SPEC_UID, report.uid)
            assertEquals(listOf("3C 00".hex()), tag.commandLog)
        }
    }

    @Test
    fun theStatusIsAlwaysNotVerified() = runTest {
        val real = ReadSignatureUseCase().runOn(sessionOf(specTag())).value()
        val zeros = specTag().also { it.signature = Bytes.copyOf(ByteArray(32)) }
        val blank = ReadSignatureUseCase().runOn(sessionOf(zeros)).value()

        assertEquals(SignatureVerification.NotVerified, real.verification)
        assertEquals(SignatureVerification.NotVerified, blank.verification, "even an all-zero signature is never judged")
    }

    @Test
    fun aSignatureOfTheWrongSizeIsMalformed() = runTest {
        val tag = specTag()
        tag.signature = Bytes.of(1, 2, 3)

        val result = ReadSignatureUseCase().runOn(sessionOf(tag))

        assertEquals(Outcome.Err(TagError.Malformed("Expected 32 bytes but received 3")), result)
    }

    @Test
    fun aTagThatIsNotAnNtagIsUnsupported() = runTest {
        val failure = ReadSignatureUseCase().runOn(FakeTagSession.withoutNtag()).securityFailure()

        assertEquals(SecurityFailure.UnsupportedChip, failure)
    }

    @Test
    fun tagLostWhileReadingIsTagLost() = runTest {
        val tag = specTag()
        tag.failNext(TagError.TagLost)

        assertEquals(Outcome.Err(TagError.TagLost), ReadSignatureUseCase().runOn(sessionOf(tag)))
    }
}
