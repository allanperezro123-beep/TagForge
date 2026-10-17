package com.tagforge.app.infrastructure.persistence.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One history row. Enums are stored by their stable `code` (never `name`) so renames and R8 can't corrupt data.
 * [family] holds the tag type text (`NTAG213` or the family code). [ndef] never contains credentials.
 */
@Entity(
    tableName = "history",
    indices = [Index("at"), Index("kind"), Index("uid_hex")],
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val kind: String,
    @ColumnInfo(name = "uid_hex") val uidHex: String?,
    val family: String?,
    val summary: String,
    val ndef: ByteArray?,
    val success: Boolean,
    @ColumnInfo(name = "error_code") val errorCode: String?,
) {
    // ByteArray needs content-based equality for a data class to behave.
    override fun equals(other: Any?): Boolean =
        other is HistoryEntity &&
            id == other.id && at == other.at && kind == other.kind && uidHex == other.uidHex &&
            family == other.family && summary == other.summary && ndef.contentEquals(other.ndef) &&
            success == other.success && errorCode == other.errorCode

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + at.hashCode()
        result = 31 * result + kind.hashCode()
        result = 31 * result + (uidHex?.hashCode() ?: 0)
        result = 31 * result + (family?.hashCode() ?: 0)
        result = 31 * result + summary.hashCode()
        result = 31 * result + (ndef?.contentHashCode() ?: 0)
        result = 31 * result + success.hashCode()
        result = 31 * result + (errorCode?.hashCode() ?: 0)
        return result
    }
}
