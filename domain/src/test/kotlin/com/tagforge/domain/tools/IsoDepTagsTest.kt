package com.tagforge.domain.tools

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.fakes.FakeGenericNdefSession
import com.tagforge.domain.fakes.hex
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.NdefRecord
import com.tagforge.domain.read.ReadFullTagUseCase
import com.tagforge.domain.tag.DetailKey
import com.tagforge.domain.tag.IsoDepDetails
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.TagFamily
import com.tagforge.domain.tag.TagInfo
import com.tagforge.domain.tag.TagTech
import com.tagforge.domain.tag.TechDetails
import com.tagforge.domain.tag.TechValue
import com.tagforge.domain.write.WriteNdefUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** ISO 14443-4 (NTAG 424 DNA, DESFire, Java cards): ATS info and Type 4 NDEF only. No authentication, no raw dump. */
class IsoDepTagsTest {

    private val hello = NdefMessage(listOf(NdefRecord.uri("https://example.com")))

    @Test
    fun theAtsHistoricalBytesAndHiLayerResponseAreShownAsHex() {
        val info = TagInfo(
            uid = Bytes.of(4, 1, 2, 3, 4, 5, 6),
            techs = setOf(TagTech.NFC_A, TagTech.ISO_DEP),
            family = TagFamily.ISO14443_4,
            historicalBytes = "80".hex(),
            details = TechDetails(isoDep = IsoDepDetails(hiLayerResponse = "75 77 81 02".hex(), extendedLengthSupported = true, timeoutMs = 618)),
        )
        val entries = info.details.sections(info).single { it.tech == TagTech.ISO_DEP }.entries.associate { it.key to it.value }
        assertEquals(TechValue.Text("80"), entries[DetailKey.HISTORICAL_BYTES])
        assertEquals(TechValue.Text("75 77 81 02"), entries[DetailKey.HI_LAYER_RESPONSE])
        assertEquals(TechValue.Flag(true), entries[DetailKey.EXTENDED_LENGTH])
    }

    @Test
    fun aFormattedType4TagIsReadWithoutAnyRawDumpAttempt() = runTest {
        val session = FakeGenericNdefSession(message = hello, family = TagFamily.ISO14443_4)
        val scan = (ReadFullTagUseCase().execute(session) {} as Outcome.Ok).value
        assertEquals(hello, scan.readout.message)
        assertNull(scan.dump)
        assertNull(scan.dumpError)
    }

    @Test
    fun aType4TagCanBeClonedToAnotherThroughNdefOnly() = runTest {
        val source = FakeGenericNdefSession(message = hello, family = TagFamily.ISO14443_4)
        val target = FakeGenericNdefSession(family = TagFamily.ISO14443_4, uid = Bytes.of(9, 9, 9, 9))
        val clone = CloneTagUseCase()
        val captured = (clone.readSource().execute(source) {} as Outcome.Ok).value
        assertIs<Outcome.Ok<*>>(clone.writeTarget(captured) { true }.execute(target) {})
        assertEquals(hello, target.current)
    }

    @Test
    fun aLockedType4TagIsRefusedWithoutWriting() = runTest {
        val session = FakeGenericNdefSession(writable = false, family = TagFamily.ISO14443_4)
        assertEquals(Outcome.Err(TagError.ReadOnly), WriteNdefUseCase(hello, confirmOverwrite = { true }).execute(session) {})
        assertEquals(emptyList(), session.writes)
    }

    @Test
    fun aCardWithoutAnNdefApplicationIsNotNdefNotAnError() = runTest {
        val session = FakeGenericNdefSession(
            family = TagFamily.ISO14443_4,
            techs = setOf(TagTech.NFC_A, TagTech.ISO_DEP),
        )
        val result = EraseTagUseCase().execute(session) {}
        assertEquals(Outcome.Err(TagError.NotNdef), result)
    }
}
