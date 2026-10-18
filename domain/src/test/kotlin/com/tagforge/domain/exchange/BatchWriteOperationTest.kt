package com.tagforge.domain.exchange

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.fakes.FakeNtag21x
import com.tagforge.domain.fakes.FakeTagSession
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.NdefRecord
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.scan.OperationKind
import com.tagforge.domain.tag.TagError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** PR-01 "Same tag twice": a UID already written in this batch needs an explicit confirmation before it is rewritten. */
class BatchWriteOperationTest {

    private val message = NdefMessage(listOf(NdefRecord.uri("https://x.co/t/008")))
    private val seen = FakeNtag21x(NtagModel.NTAG213, uid = Bytes.of(0x04, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66))

    private class Question(val existing: NdefMessage?, val duplicate: Boolean)

    private fun operation(alreadyWritten: Set<Bytes>, asked: MutableList<Question>, answer: Boolean = true) =
        BatchWriteOperation(message, alreadyWritten = { it in alreadyWritten }) { existing, duplicate ->
            asked += Question(existing, duplicate)
            answer
        }

    @Test
    fun aBlankNewTagIsWrittenWithoutAnyQuestion() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG213)
        val asked = mutableListOf<Question>()

        val result = operation(emptySet(), asked).execute(FakeTagSession.ntag(tag)) {}

        assertIs<Outcome.Ok<*>>(result)
        assertTrue(asked.isEmpty())
        assertEquals(OperationKind.WRITE, operation(emptySet(), asked).kind)
    }

    @Test
    fun theSameTagAgainAsksAndDecliningWritesNothing() = runTest {
        val session = FakeTagSession.ntag(seen)
        val asked = mutableListOf<Question>()

        val result = operation(setOf(session.info.uid), asked, answer = false).execute(session) {}

        assertEquals(Outcome.Err(TagError.Cancelled), result)
        assertEquals(1, asked.size)
        assertTrue(asked.single().duplicate)
        assertEquals(0, seen.writeCount)
    }

    @Test
    fun confirmingTheSameTagRewritesItAndAsksOnlyOnce() = runTest {
        val session = FakeTagSession.ntag(seen)
        val first = NdefMessage(listOf(NdefRecord.text("previous", "en")))
        session.writeNdef(first)
        val asked = mutableListOf<Question>()

        val result = operation(setOf(session.info.uid), asked).execute(session) {}

        assertIs<Outcome.Ok<*>>(result)
        assertEquals(1, asked.size, "the duplicate question also covers the existing content")
        assertEquals(message, (session.readNdef() as Outcome.Ok).value)
    }

    @Test
    fun aDifferentTagThatAlreadyHoldsDataStillAsksTheNormalOverwriteQuestion() = runTest {
        val other = FakeNtag21x(NtagModel.NTAG213, uid = Bytes.of(0x04, 0xAA, 0xBB, 0xCC, 0xDD, 0xEE, 0xFF))
        val session = FakeTagSession.ntag(other)
        val old = NdefMessage(listOf(NdefRecord.text("old", "en")))
        session.writeNdef(old)
        val asked = mutableListOf<Question>()

        operation(emptySet(), asked, answer = false).execute(session) {}

        assertEquals(1, asked.size)
        assertEquals(old, asked.single().existing)
        assertEquals(false, asked.single().duplicate)
    }
}
