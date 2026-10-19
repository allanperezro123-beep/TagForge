package com.tagforge.domain.write

import com.tagforge.domain.common.Outcome
import com.tagforge.domain.fakes.FakeNtag21x
import com.tagforge.domain.fakes.FakeTagSession
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.NdefRecord
import com.tagforge.domain.ntag.NtagCommands
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tools.CloneTagUseCase
import com.tagforge.domain.tools.DumpMemoryUseCase
import com.tagforge.domain.tools.EraseTagUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Real controllers refuse frames longer than `NfcA.getMaxTransceiveLength()` (commonly 253) with an IOException.
 * The fake enforces that limit, so these tests prove no flow ever depends on a frame bigger than the controller allows,
 * on every NTAG21x (NTAG215 has 126 user pages = 504 bytes, NTAG216 has 222 = 888 bytes).
 */
class TransceiveLimitTest {

    private val message = NdefMessage(listOf(NdefRecord.uri("https://example.com"), NdefRecord.text("hola", "es")))
    private val models = listOf(NtagModel.NTAG215, NtagModel.NTAG216)

    @Test
    fun theFakeRefusesAResponseLongerThanTheControllerLimitLikeAndroidDoes() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG215)

        val oversized = tag.transceive(NtagCommands.fastRead(4, 129))   // 126 pages = 504 bytes > 253

        assertIs<Outcome.Err<TagError>>(oversized)
        assertIs<TagError.Io>(oversized.error)
    }

    @Test
    fun theFakeRefusesACommandLongerThanTheControllerLimit() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG215)

        val oversized = tag.transceive(com.tagforge.domain.common.Bytes.copyOf(ByteArray(254)))

        assertIs<Outcome.Err<TagError>>(oversized)
        assertIs<TagError.Io>(oversized.error)
    }

    @Test
    fun verifiedWriteWorksOnEveryBigNtagWithAnEnforcedLimit() = runTest {
        for (model in models) {
            val tag = FakeNtag21x(model)
            val session = FakeTagSession.ntag(tag)

            val result = WriteNdefUseCase(message) { true }.execute(session) {}

            assertIs<Outcome.Ok<WriteReport>>(result, "write on $model")
        }
    }

    @Test
    fun eraseAndDumpWorkOnEveryBigNtagWithAnEnforcedLimit() = runTest {
        for (model in models) {
            val tag = FakeNtag21x(model)
            check(FakeTagSession.ntag(tag).writeNdef(message) is Outcome.Ok)

            assertIs<Outcome.Ok<*>>(DumpMemoryUseCase().execute(FakeTagSession.ntag(tag)) {}, "dump on $model")
            assertIs<Outcome.Ok<*>>(EraseTagUseCase().execute(FakeTagSession.ntag(tag)) {}, "erase on $model")
        }
    }

    @Test
    fun cloneOfTheMessageAndOfTheRawPagesWorksOnEveryBigNtag() = runTest {
        for (model in models) {
            for (rawPages in listOf(false, true)) {
                val source = FakeNtag21x(model)
                check(FakeTagSession.ntag(source).writeNdef(message) is Outcome.Ok)
                val target = FakeNtag21x(model, uid = com.tagforge.domain.common.Bytes.of(4, 1, 2, 3, 4, 5, 6))
                val clone = CloneTagUseCase()

                val captured = clone.readSource(rawPages).execute(FakeTagSession.ntag(source)) {}
                assertIs<Outcome.Ok<*>>(captured, "read source $model raw=$rawPages")
                val copied = clone.writeTarget((captured as Outcome.Ok).value) { true }
                    .execute(FakeTagSession.ntag(target)) {}

                assertIs<Outcome.Ok<*>>(copied, "clone $model raw=$rawPages")
                assertEquals(message, (FakeTagSession.ntag(target).readNdef() as Outcome.Ok).value)
            }
        }
    }

    @Test
    fun eraseAndDumpOnlyNeedSixteenByteFramesSoEvenTheTightestControllerWorks() = runTest {
        for (model in models) {
            val seeded = FakeNtag21x(model)
            check(FakeTagSession.ntag(seeded).writeNdef(message) is Outcome.Ok)
            val tight = FakeNtag21x(model, maxTransceiveLength = 16)
            seeded.snapshot().forEachIndexed { index, page -> tight.setPage(index, page) }

            assertIs<Outcome.Ok<*>>(DumpMemoryUseCase().execute(FakeTagSession.ntag(tight)) {}, "dump on $model")
            assertIs<Outcome.Ok<*>>(EraseTagUseCase().execute(FakeTagSession.ntag(tight)) {}, "erase on $model")
        }
    }
}
