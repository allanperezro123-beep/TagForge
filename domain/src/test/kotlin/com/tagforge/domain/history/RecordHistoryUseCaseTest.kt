package com.tagforge.domain.history

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.fakes.FakeNtag21x
import com.tagforge.domain.fakes.FakeSettingsStore
import com.tagforge.domain.fakes.FakeTagSession
import com.tagforge.domain.fakes.FixedClock
import com.tagforge.domain.fakes.InMemoryHistoryRepository
import com.tagforge.domain.ndef.DefaultNdefCodec
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.NdefRecord
import com.tagforge.domain.ndef.spec.RecordSpec
import com.tagforge.domain.ndef.spec.RecordSpecEncoder
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.read.ReadFullTagUseCase
import com.tagforge.domain.read.TagScan
import com.tagforge.domain.settings.AppSettings
import com.tagforge.domain.tag.TagError
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** HT-01 "Read logged", TW-06 "Failed write logged", and the save_history switch. */
class RecordHistoryUseCaseTest {

    private val repository = InMemoryHistoryRepository()
    private val clock = FixedClock(Instant.fromEpochMilliseconds(1_000))
    private val settings = FakeSettingsStore()
    private val recorder = RecordHistoryUseCase(repository, settings, clock)

    private suspend fun scanOf(message: NdefMessage): Pair<TagScan, FakeTagSession> {
        val session = FakeTagSession.ntag(FakeNtag21x(NtagModel.NTAG213))
        session.writeNdef(message)
        val scan = (ReadFullTagUseCase().execute(session) {} as Outcome.Ok).value
        return scan to session
    }

    private suspend fun entries(): List<HistoryEntry> = repository.observe().first()

    @Test
    fun aSuccessfulReadIsLoggedWithUidTypeSummaryAndRawNdef() = runTest {
        val message = NdefMessage(listOf(NdefRecord.text("hola", "es")))
        val (scan, _) = scanOf(message)

        recorder.recordRead(scan)

        val entry = entries().single()
        assertEquals(HistoryKind.READ, entry.kind)
        assertEquals(HistoryOutcome.SUCCESS, entry.outcome)
        assertEquals(FakeNtag21x.DEFAULT_UID, entry.uid)
        assertEquals("NTAG213", entry.tagType)
        assertEquals("hola", entry.summary)
        assertEquals(DefaultNdefCodec.encode(message), entry.ndef)
        assertEquals(Instant.fromEpochMilliseconds(1_000), entry.at)
    }

    @Test
    fun anEmptyTagReadIsStillLoggedWithoutNdef() = runTest {
        val (scan, _) = scanOf(NdefMessage.EMPTY)
        recorder.recordRead(scan)
        val entry = entries().single()
        assertNull(entry.ndef)
        assertEquals("", entry.summary)
    }

    @Test
    fun aFailedWriteLogsTheTagLostOutcomeWithTheIntendedContent() = runTest {
        val (_, session) = scanOf(NdefMessage.EMPTY)
        val intended = NdefMessage(listOf(NdefRecord.uri("https://example.com")))

        recorder.recordWrite(session.info, intended, TagError.TagLost)

        val entry = entries().single()
        assertEquals(HistoryKind.WRITE, entry.kind)
        assertEquals(HistoryOutcome.FAILED_TAGLOST, entry.outcome)
        assertEquals("https://example.com", entry.summary)
        assertEquals(DefaultNdefCodec.encode(intended), entry.ndef)
    }

    @Test
    fun aSuccessfulWriteIsLoggedAsSuccess() = runTest {
        val (_, session) = scanOf(NdefMessage.EMPTY)
        recorder.recordWrite(session.info, NdefMessage(listOf(NdefRecord.text("hi"))), null)
        assertEquals(HistoryOutcome.SUCCESS, entries().single().outcome)
    }

    @Test
    fun aWriteThatNeverReachedATagHasNoUid() = runTest {
        recorder.recordWrite(null, NdefMessage(listOf(NdefRecord.text("hi"))), TagError.Timeout)
        val entry = entries().single()
        assertNull(entry.uid)
        assertNull(entry.tagType)
        assertEquals(HistoryOutcome.FAILED_OTHER, entry.outcome)
    }

    @Test
    fun wifiPasswordsNeverReachTheStoredSummaryOrBytes() = runTest {
        val wifi = RecordSpecEncoder.encodeMessage(listOf(RecordSpec.Wifi("Home", "pass1234")), null)!!
        val (_, session) = scanOf(NdefMessage.EMPTY)

        recorder.recordWrite(session.info, wifi, null)
        val (scan, _) = scanOf(wifi)
        recorder.recordRead(scan)

        val passwordHex = Bytes.copyOf("pass1234".toByteArray()).toHex()
        val stored = entries()
        assertEquals(2, stored.size)
        for (entry in stored) {
            assertEquals("Home", entry.summary)
            assertFalse(passwordHex in entry.ndef!!.toHex(), "entry ${entry.kind} leaked the key")
        }
    }

    @Test
    fun nothingIsStoredWhenSaveHistoryIsOff() = runTest {
        settings.update { it.copy(saveHistory = false) }
        val (scan, session) = scanOf(NdefMessage(listOf(NdefRecord.text("hi"))))

        recorder.recordRead(scan)
        recorder.recordWrite(session.info, NdefMessage(listOf(NdefRecord.text("hi"))), null)

        assertTrue(entries().isEmpty())
    }

    @Test
    fun savingIsOnByDefaultAndRecordingResumesWhenTurnedBackOn() = runTest {
        assertTrue(AppSettings().saveHistory)
        settings.update { it.copy(saveHistory = false) }
        val (scan, _) = scanOf(NdefMessage(listOf(NdefRecord.text("hi"))))
        recorder.recordRead(scan)
        settings.update { it.copy(saveHistory = true) }
        recorder.recordRead(scan)
        assertEquals(1, entries().size)
    }
}
