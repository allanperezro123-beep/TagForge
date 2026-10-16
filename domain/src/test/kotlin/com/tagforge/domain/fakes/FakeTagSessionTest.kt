package com.tagforge.domain.fakes

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.NdefRecord
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.settings.AppSettings
import com.tagforge.domain.settings.ThemeMode
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.TagFamily
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class FakeTagSessionTest {

    private val hello = NdefMessage(listOf(NdefRecord.text("hello", "en")))

    // ---- identify -----------------------------------------------------------------------------

    @Test
    fun identifyRefinesAnUnidentifiedNtagFromGetVersion() = runTest {
        val session = FakeTagSession.ntag(FakeNtag21x(NtagModel.NTAG215), identified = false)
        assertEquals(TagFamily.UNKNOWN, session.info.family)

        val result = session.identify()

        assertTrue(result is Outcome.Ok)
        assertEquals(NtagModel.NTAG215, session.info.ntag)
        assertEquals(TagFamily.NTAG21X, session.info.family)
        assertEquals(NtagModel.NTAG215, result.value.ntag)
    }

    @Test
    fun identifyLeavesInfoUntouchedWhenGetVersionIsNaked() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG213).apply { getVersionNaks = true }
        val session = FakeTagSession.ntag(tag, identified = false)

        val result = session.identify()

        assertTrue(result is Outcome.Ok)
        assertEquals(null, session.info.ntag)
        assertEquals(TagFamily.UNKNOWN, session.info.family)
    }

    @Test
    fun identifyPropagatesTagLost() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG213).apply { failNext(TagError.TagLost) }
        val session = FakeTagSession.ntag(tag, identified = false)
        assertEquals(Outcome.Err(TagError.TagLost), session.identify())
    }

    // ---- NDEF through Type 2 TLV ----------------------------------------------------------------

    @Test
    fun blankTagReadsAsFormattedButEmpty() = runTest {
        for (model in NtagModel.entries) {
            assertEquals(Outcome.Ok(null), FakeTagSession.ntag(FakeNtag21x(model)).readNdef(), model.displayName)
        }
    }

    @Test
    fun writtenMessageReadsBackOnEveryModel() = runTest {
        for (model in NtagModel.entries) {
            val session = FakeTagSession.ntag(FakeNtag21x(model))
            assertEquals(Outcome.Ok(Unit), session.writeNdef(hello), model.displayName)
            assertEquals(Outcome.Ok(hello), session.readNdef(), model.displayName)
        }
    }

    @Test
    fun writingOnNtag213PreservesTheFactoryLockControlTlv() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG213)
        val session = FakeTagSession.ntag(tag)
        session.writeNdef(hello)
        assertEquals("01 03 A0 0C".hex(), tag.page(4))
        assertEquals(Outcome.Ok(hello), session.readNdef())
    }

    @Test
    fun formatWithoutMessageRestoresTheEmptyNdefTlv() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG213)
        val session = FakeTagSession.ntag(tag)
        session.writeNdef(hello)

        assertEquals(Outcome.Ok(Unit), session.formatNdef(null))

        assertEquals(Outcome.Ok(null), session.readNdef())
        assertEquals("01 03 A0 0C".hex(), tag.page(4))
        assertEquals("34 03 00 FE".hex(), tag.page(5))
    }

    @Test
    fun corruptTlvIsMalformedNotACrash() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG215)
        tag.setPage(4, "03 03 D1 01".hex())   // TLV holds D1 01 05: a header promising 5 payload bytes that are not there
        tag.setPage(5, "05 00 00 00".hex())
        val result = FakeTagSession.ntag(tag).readNdef()
        assertTrue(result is Outcome.Err && result.error is TagError.Malformed, "was $result")
    }

    @Test
    fun zeroedUserAreaIsNotNdef() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG215)
        tag.setPage(4, Bytes.of(0, 0, 0, 0))
        assertEquals(Outcome.Err(TagError.NotNdef), FakeTagSession.ntag(tag).readNdef())
    }

    @Test
    fun passwordProtectedAreaRequiresAuth() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG215)
        tag.setPage(NtagModel.NTAG215.cfg0, "00 00 00 04".hex())
        tag.setPage(NtagModel.NTAG215.cfg1, "80 00 00 00".hex())
        assertEquals(Outcome.Err(TagError.AuthRequired), FakeTagSession.ntag(tag).readNdef())
    }

    @Test
    fun tagWithoutAType2ChipIsNotNdef() = runTest {
        assertEquals(Outcome.Err(TagError.NotNdef), FakeTagSession.withoutNtag().readNdef())
    }

    @Test
    fun oversizedMessageFailsBeforeAnyWrite() = runTest {
        val tag = FakeNtag21x(NtagModel.NTAG213)
        val session = FakeTagSession.ntag(tag)
        val big = NdefMessage(listOf(NdefRecord.text("a".repeat(140), "en")))

        val result = session.writeNdef(big)

        assertEquals(Outcome.Err(TagError.CapacityExceeded(required = 147, available = 137)), result)
        assertEquals(0, tag.writeCount)
    }

    @Test
    fun closeIsRecorded() = runTest {
        val session = FakeTagSession.ntag(FakeNtag21x(NtagModel.NTAG213))
        assertFalse(session.closed)
        session.close()
        assertTrue(session.closed)
    }

    @Test
    fun androidStyleMaxSizeMatchesKnownValues() {
        assertEquals(137, FakeTagSession.androidMaxSize(NtagModel.NTAG213))
        assertEquals(492, FakeTagSession.androidMaxSize(NtagModel.NTAG215))
        assertEquals(868, FakeTagSession.androidMaxSize(NtagModel.NTAG216))
    }

    // ---- other fixtures -------------------------------------------------------------------------

    @Test
    fun fakeSettingsStoreAppliesUpdatesAtomically() = runTest {
        val store = FakeSettingsStore()
        assertEquals(AppSettings(), store.settings.first())

        store.update { it.copy(themeMode = ThemeMode.DARK) }
        store.update { it.copy(hapticsEnabled = false) }

        assertEquals(AppSettings(themeMode = ThemeMode.DARK, hapticsEnabled = false), store.settings.first())
    }

    @Test
    fun fixedClockOnlyMovesWhenAdvanced() {
        val clock = FixedClock(Instant.fromEpochMilliseconds(1_000))
        assertEquals(Instant.fromEpochMilliseconds(1_000), clock.now())
        clock.advanceMillis(500)
        assertEquals(Instant.fromEpochMilliseconds(1_500), clock.now())
    }
}
