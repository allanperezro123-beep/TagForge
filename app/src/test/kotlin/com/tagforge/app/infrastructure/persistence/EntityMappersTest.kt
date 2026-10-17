package com.tagforge.app.infrastructure.persistence

import com.tagforge.app.infrastructure.persistence.entity.HistoryEntity
import com.tagforge.app.infrastructure.persistence.entity.TemplateEntity
import com.tagforge.domain.common.Bytes
import com.tagforge.domain.history.HistoryEntry
import com.tagforge.domain.history.HistoryKind
import com.tagforge.domain.history.HistoryOutcome
import com.tagforge.domain.ndef.spec.RecordSpec
import com.tagforge.domain.template.Template
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/** HT-01 / ADR 10: rows keep explicit stable codes, never enum names, and survive a round trip. */
class EntityMappersTest {

    private val at = Instant.fromEpochMilliseconds(1_700_000_000_123)

    private fun entry(
        outcome: HistoryOutcome = HistoryOutcome.SUCCESS,
        uid: Bytes? = Bytes.of(0x04, 0xA2, 0x3B, 0x1C, 0x5D, 0x6E, 0x80),
        ndef: Bytes? = Bytes.of(0xD1, 0x01, 0x05, 0x54, 0x02, 0x65, 0x6E, 0x68, 0x69),
    ) = HistoryEntry(7, at, HistoryKind.WRITE, outcome, uid, "NTAG213", "hi", ndef)

    @Test
    fun aSuccessfulEntryUsesStableCodesAndHexUid() {
        val entity = entry().toEntity()

        assertEquals(1_700_000_000_123, entity.at)
        assertEquals("write", entity.kind)
        assertEquals("04A23B1C5D6E80", entity.uidHex)
        assertEquals("NTAG213", entity.family)
        assertEquals(true, entity.success)
        assertNull(entity.errorCode)
    }

    @Test
    fun aFailedEntryKeepsTheOutcomeCodeInErrorCode() {
        val entity = entry(outcome = HistoryOutcome.FAILED_TAGLOST).toEntity()
        assertEquals(false, entity.success)
        assertEquals("failed_taglost", entity.errorCode)
    }

    @Test
    fun entryRoundTripsThroughTheEntity() {
        val original = entry(outcome = HistoryOutcome.FAILED_VERIFY)
        assertEquals(original, original.toEntity().toDomain())
    }

    @Test
    fun entryWithoutUidOrNdefRoundTrips() {
        val original = entry(uid = null, ndef = null)
        val entity = original.toEntity()
        assertNull(entity.uidHex)
        assertNull(entity.ndef)
        assertEquals(original, entity.toDomain())
    }

    @Test
    fun ndefBytesAreCopiedNotAliased() {
        val bytes = Bytes.of(1, 2, 3)
        val entity = entry(ndef = bytes).toEntity()
        assertContentEquals(byteArrayOf(1, 2, 3), entity.ndef)
        entity.ndef!![0] = 99
        assertEquals(Bytes.of(1, 2, 3), bytes)
    }

    @Test
    fun anUnknownStoredOutcomeBecomesAGenericFailureNotACrash() {
        val entity = entry().toEntity().copy(success = false, errorCode = "code-from-the-future")
        assertEquals(HistoryOutcome.FAILED_OTHER, entity.toDomain().outcome)
    }

    @Test
    fun templateRoundTripsWithItsRecords() {
        val records = listOf(RecordSpec.Wifi("Home", "pass1234"), RecordSpec.Text("hola", "es"))
        val original = Template(3, "WiFi casa", records, at, Instant.fromEpochMilliseconds(1_700_000_999_000))

        val entity: TemplateEntity = original.toEntity()

        assertEquals(1_700_000_000_123, entity.createdAt)
        assertEquals(true, "\"wifi\"" in entity.recordsJson)
        assertEquals(original, entity.toDomain())
    }

    @Test
    fun aTemplateWithUnreadableJsonLoadsWithNoRecordsInsteadOfCrashing() {
        val entity = TemplateEntity(id = 1, name = "Broken", recordsJson = "not json", createdAt = 0, updatedAt = 0)
        assertEquals(emptyList(), entity.toDomain().records)
        assertEquals("Broken", entity.toDomain().name)
    }

    @Test
    fun historyEntityEqualityComparesNdefContent() {
        val a = HistoryEntity(1, 0, "read", null, null, "", byteArrayOf(1), true, null)
        val b = HistoryEntity(1, 0, "read", null, null, "", byteArrayOf(1), true, null)
        assertEquals(a, b)
    }
}
