package com.tagforge.domain.write

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.fakes.FakeNtag21x
import com.tagforge.domain.fakes.FakeTagSession
import com.tagforge.domain.fakes.PlatformNdefFailingSession
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.NdefRecord
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tools.CloneReport
import com.tagforge.domain.tools.CloneSource
import com.tagforge.domain.tools.CloneTagUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * On a real phone erase (raw NfcA only) worked on NTAG215 while write and clone (which also went through Android's
 * `Ndef` technology) failed with "tag lost". An NTAG21x is therefore read and verified over raw NfcA, never through
 * the platform `Ndef` technology: these tests make that technology unusable and expect every flow to carry on.
 */
class RawNtagNdefAccessTest {

    private val message = NdefMessage(listOf(NdefRecord.uri("https://example.com")))
    private val older = NdefMessage(listOf(NdefRecord.text("old", "en")))
    private val models = NtagModel.entries

    private fun platformDown(tag: FakeNtag21x) = PlatformNdefFailingSession(FakeTagSession.ntag(tag))

    @Test
    fun writingABlankNtagNeverTouchesThePlatformNdefTechnology() = runTest {
        for (model in models) {
            val tag = FakeNtag21x(model)
            val session = platformDown(tag)

            val result = WriteNdefUseCase(message) { true }.execute(session) {}

            assertIs<Outcome.Ok<WriteReport>>(result, "write on $model")
            assertEquals(0, session.platformReads, "no Ndef-technology read on $model")
            assertEquals(message, (FakeTagSession.ntag(tag).readNdef() as Outcome.Ok).value)
        }
    }

    @Test
    fun overwritingAsksTheUserWithTheExistingMessageReadOverRawNfcA() = runTest {
        for (model in models) {
            val tag = FakeNtag21x(model)
            check(FakeTagSession.ntag(tag).writeNdef(older) is Outcome.Ok)
            var shown: NdefMessage? = null

            val result = WriteNdefUseCase(message) { existing -> shown = existing; true }.execute(platformDown(tag)) {}

            assertIs<Outcome.Ok<WriteReport>>(result, "overwrite on $model")
            assertEquals(older, shown)
        }
    }

    @Test
    fun decliningTheOverwriteStillCancelsWithoutWriting() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG215)
        check(FakeTagSession.ntag(tag).writeNdef(older) is Outcome.Ok)
        val before = tag.snapshot()

        val result = WriteNdefUseCase(message) { false }.execute(platformDown(tag)) {}

        assertEquals(Outcome.Err(TagError.Cancelled), result)
        assertEquals(before, tag.snapshot())
    }

    @Test
    fun cloningTheMessageReadsTheSourceOverRawNfcAAndVerifiesTheTarget() = runTest {
        for (model in models) {
            val source = FakeNtag21x(model)
            check(FakeTagSession.ntag(source).writeNdef(message) is Outcome.Ok)
            val target = FakeNtag21x(model, uid = Bytes.of(4, 1, 2, 3, 4, 5, 6))
            val clone = CloneTagUseCase()

            val captured = clone.readSource().execute(platformDown(source)) {}
            assertIs<Outcome.Ok<CloneSource>>(captured, "read source on $model")
            val copied = clone.writeTarget(captured.value) { true }.execute(platformDown(target)) {}

            assertIs<Outcome.Ok<CloneReport>>(copied, "clone on $model")
            assertEquals(message, (FakeTagSession.ntag(target).readNdef() as Outcome.Ok).value)
        }
    }

    @Test
    fun aProtectedNtagStillReportsAuthRequiredNotTagLost() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG215)
        check(FakeTagSession.ntag(tag).writeNdef(older) is Outcome.Ok)
        tag.setPage(NtagModel.NTAG215.cfg0, Bytes.of(0, 0, 0, 0x04))   // AUTH0 = page 4 ...
        tag.setPage(NtagModel.NTAG215.cfg1, Bytes.of(0x80, 0, 0, 0))   // ... and PROT = reads need the password too

        val result = WriteNdefUseCase(message) { true }.execute(platformDown(tag)) {}

        assertEquals(Outcome.Err(TagError.AuthRequired), result)
    }
}
