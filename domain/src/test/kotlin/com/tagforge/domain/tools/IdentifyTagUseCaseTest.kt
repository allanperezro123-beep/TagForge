package com.tagforge.domain.tools

import com.tagforge.domain.common.Outcome
import com.tagforge.domain.fakes.FakeNtag21x
import com.tagforge.domain.fakes.FakeTagSession
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.scan.OperationKind
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.TagFamily
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** The Tools "Tag technologies" screen only needs the identity of the tag: no NDEF read, no writes. */
class IdentifyTagUseCaseTest {

    @Test
    fun anUnidentifiedNtagComesBackWithItsChipModelAndNothingIsWritten() {
        val tag = FakeNtag21x(NtagModel.NTAG215)

        val result = runBlocking { IdentifyTagUseCase().execute(FakeTagSession.ntag(tag, identified = false)) {} }

        assertIs<Outcome.Ok<*>>(result)
        assertEquals(NtagModel.NTAG215, (result as Outcome.Ok).value.ntag)
        assertEquals(0, tag.writeCount)
    }

    @Test
    fun aClassicCardIsDescribedNotRejected() {
        val result = runBlocking { IdentifyTagUseCase().execute(FakeTagSession.withoutNtag(TagFamily.MIFARE_CLASSIC)) {} }

        assertEquals(TagFamily.MIFARE_CLASSIC, (result as Outcome.Ok).value.family)
    }

    @Test
    fun tagLostIsAFailureAndTheKindIsRead() {
        val tag = FakeNtag21x(NtagModel.NTAG213)
        tag.failNext(TagError.TagLost)

        val result = runBlocking { IdentifyTagUseCase().execute(FakeTagSession.ntag(tag, identified = false)) {} }

        assertEquals(Outcome.Err(TagError.TagLost), result)
        assertEquals(OperationKind.READ, IdentifyTagUseCase().kind)
    }
}
