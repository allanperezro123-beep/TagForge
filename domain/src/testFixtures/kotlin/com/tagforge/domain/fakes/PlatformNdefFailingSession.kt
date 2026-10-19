package com.tagforge.domain.fakes

import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ports.TagSession
import com.tagforge.domain.tag.TagError

/**
 * A phone on which Android's `Ndef` technology misbehaves (the technology switch NfcA -> Ndef -> NfcA drops the tag)
 * while raw NfcA frames keep working. [FakeTagSession.readNdef] reads raw pages, so it cannot show this difference.
 * Counts how often the platform path was used.
 */
class PlatformNdefFailingSession(
    private val delegate: FakeTagSession,
    private val error: TagError = TagError.TagLost,
) : TagSession by delegate {

    var platformReads: Int = 0
        private set

    override suspend fun readNdef(): TagResult<NdefMessage?> {
        platformReads++
        return Outcome.Err(error)
    }
}
