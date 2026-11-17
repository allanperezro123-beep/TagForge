package com.tagforge.domain.write

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.ports.TagTransceiver
import com.tagforge.domain.security.DynamicLockTable
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.type2.Type2Layout

/**
 * Why did the tag refuse the FIRST write (nothing has changed yet)? A NAK alone cannot tell a permanently locked page from a
 * page behind a password (PROT = 0 protects writes from AUTH0 on while everything stays readable), and the two need opposite
 * advice. The capability container and the lock bits are readable evidence of a lock; when none of them says "locked" on a
 * chip that has passwords, the password is the remaining explanation.
 */
internal object WriteRefusal {
    private const val STATIC_LOCK_PAGE = 2
    private const val FIRST_STATIC_LOCKED_PAGE = 3   // the CC; bit n of the static lock bytes (page 02, bytes 2-3) locks page n up to 15
    private const val LAST_STATIC_LOCKED_PAGE = 15

    /**
     * [ReadOnly][TagError.ReadOnly] when the CC or a lock bit covers [page]; [AuthRequired][TagError.AuthRequired] when they
     * do not, or when a probe read is itself refused; a link error is returned as it is, never guessed into one of those.
     * Chips without passwords (everything but NTAG21x) keep the old answer: a refused first write means read-only.
     */
    suspend fun diagnose(transceiver: TagTransceiver, layout: Type2Layout?, area: CcAndUserArea?, page: Int): TagError {
        val ntag = layout as? NtagModel ?: return TagError.ReadOnly
        val cc = area?.cc ?: when (val read = probe(transceiver, ntag, Type2Io.CC_PAGE)) {
            is Outcome.Err -> return read.error
            is Outcome.Ok -> read.value
        }
        if (cc[3] != 0) return TagError.ReadOnly

        val locked = when (val verdict = lockBitsCover(transceiver, ntag, page)) {
            is Outcome.Err -> return verdict.error
            is Outcome.Ok -> verdict.value
        }
        return if (locked) TagError.ReadOnly else TagError.AuthRequired
    }

    private suspend fun lockBitsCover(transceiver: TagTransceiver, ntag: NtagModel, page: Int): TagResult<Boolean> {
        if (page in FIRST_STATIC_LOCKED_PAGE..LAST_STATIC_LOCKED_PAGE) {
            val lock = when (val read = probe(transceiver, ntag, STATIC_LOCK_PAGE)) {
                is Outcome.Err -> return read
                is Outcome.Ok -> read.value
            }
            return Outcome.Ok((lock[2] or (lock[3] shl 8)) and (1 shl page) != 0)
        }
        val group = DynamicLockTable.layout(ntag).groupOf(page) ?: return Outcome.Ok(false)
        val lock = when (val read = probe(transceiver, ntag, ntag.dynLock)) {
            is Outcome.Err -> return read
            is Outcome.Ok -> read.value
        }
        return Outcome.Ok((lock[0] or (lock[1] shl 8)) and (1 shl group.index) != 0)
    }

    /** A READ that the tag refuses means the page is behind the password: [TagError.AuthRequired]. */
    private suspend fun probe(transceiver: TagTransceiver, ntag: NtagModel, page: Int): TagResult<Bytes> =
        when (val read = Type2Io.readPages(transceiver, ntag, page, page)) {
            is Outcome.Ok -> read
            is Outcome.Err -> if (read.error is TagError.Nak) Outcome.Err(TagError.AuthRequired) else read
        }
}
