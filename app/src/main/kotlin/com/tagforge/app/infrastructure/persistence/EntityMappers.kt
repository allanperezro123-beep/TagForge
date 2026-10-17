package com.tagforge.app.infrastructure.persistence

import com.tagforge.app.infrastructure.persistence.entity.HistoryEntity
import com.tagforge.app.infrastructure.persistence.entity.TemplateEntity
import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Hex
import com.tagforge.domain.history.HistoryEntry
import com.tagforge.domain.history.HistoryKind
import com.tagforge.domain.history.HistoryOutcome
import com.tagforge.domain.ndef.spec.RecordSpecJson
import com.tagforge.domain.template.Template
import kotlin.time.Instant

internal fun HistoryEntry.toEntity() = HistoryEntity(
    id = id,
    at = at.toEpochMilliseconds(),
    kind = kind.code,
    uidHex = uid?.toHex(),
    family = tagType,
    summary = summary,
    ndef = ndef?.toByteArray(),
    success = outcome.isSuccess,
    errorCode = if (outcome.isSuccess) null else outcome.code,
)

internal fun HistoryEntity.toDomain() = HistoryEntry(
    id = id,
    at = Instant.fromEpochMilliseconds(at),
    kind = HistoryKind.fromCode(kind),
    outcome = if (success) HistoryOutcome.SUCCESS else HistoryOutcome.fromCode(errorCode),
    uid = uidHex?.let(Hex::parse),
    tagType = family,
    summary = summary,
    ndef = ndef?.let(Bytes::copyOf),
)

internal fun Template.toEntity() = TemplateEntity(
    id = id,
    name = name,
    // Intentional: templates keep the WiFi password in clear (a reusable WiFi tag needs it), unlike history, which
    // redacts it (HistorySummarizer.redact). Mitigated by allowBackup=false and the app-private database.
    recordsJson = RecordSpecJson.encode(records),
    createdAt = createdAt.toEpochMilliseconds(),
    updatedAt = updatedAt.toEpochMilliseconds(),
)

/** Unreadable JSON loads as an empty template rather than crashing the list: the name stays visible so it can be deleted. */
internal fun TemplateEntity.toDomain() = Template(
    id = id,
    name = name,
    records = RecordSpecJson.decode(recordsJson).orEmpty(),
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    updatedAt = Instant.fromEpochMilliseconds(updatedAt),
)
