package com.tagforge.domain.security

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ports.TagSession
import com.tagforge.domain.ports.TagTransceiver
import com.tagforge.domain.tag.TagError

/**
 * Read-back verification of a plan (G5: success is reported only when the tag proves it).
 * There is deliberately no "unauthenticated READ is refused" probe: many Android controllers drop the connection on a NAK,
 * which cannot be told apart from a lost tag. The decisive proof is PWD_AUTH plus the CFG0/CFG1 read-back, which the
 * chip enforces as AUTH0/PROT.
 */
internal object CheckRunner {

    suspend fun run(session: TagSession, t: TagTransceiver, checks: List<Check>): TagResult<Unit> {
        for (check in checks.sortedBy(::order)) {
            when (val passed = evaluate(session, t, check)) {
                is Outcome.Err -> return passed
                is Outcome.Ok -> if (!passed.value) return Outcome.Err(TagError.Security(SecurityFailure.NotVerified(check)))
            }
        }
        return Outcome.Ok(Unit)
    }

    /** PWD_AUTH first, then everything that may need an authenticated session. */
    private fun order(check: Check): Int = if (check is Check.AuthWorks) 0 else 1

    private suspend fun evaluate(session: TagSession, t: TagTransceiver, check: Check): TagResult<Boolean> = when (check) {
        is Check.AuthWorks -> authWorks(t, check)
        is Check.PageEquals -> pageRead(t, check.page) { it == check.expected }
        is Check.PageHasBits -> pageRead(t, check.page) { page -> (0 until page.size).all { page[it] and check.bits[it] == check.bits[it] } }
        Check.NdefReadOnly -> Outcome.Ok(session.identify().let { it is Outcome.Ok && it.value.ndef?.isWritable == false })
    }

    private suspend fun authWorks(t: TagTransceiver, check: Check.AuthWorks): TagResult<Boolean> =
        when (val r = SecurityIo.authenticate(t, check.password)) {
            is Outcome.Ok -> Outcome.Ok(r.value == check.pack)
            is Outcome.Err -> if (r.error is TagError.AuthFailed) Outcome.Ok(false) else r
        }

    /** A page that cannot be read back (protected) counts as "not verified", a lost tag as a tag error. */
    private suspend fun pageRead(t: TagTransceiver, page: Int, predicate: (Bytes) -> Boolean): TagResult<Boolean> =
        when (val r = SecurityIo.readPages(t, page, page)) {
            is Outcome.Ok -> Outcome.Ok(predicate(r.value))
            is Outcome.Err -> if (r.error == TagError.AuthRequired) Outcome.Ok(false) else r
        }
}
