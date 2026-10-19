package com.tagforge.domain.tools

import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.iso15693.Iso15693
import com.tagforge.domain.ports.TagSession
import com.tagforge.domain.scan.OperationKind
import com.tagforge.domain.scan.OperationPhase
import com.tagforge.domain.scan.TagOperation
import com.tagforge.domain.tag.TagInfo
import com.tagforge.domain.tag.TagTech

/** Identifies the next tag and returns what the OS and the chip say about it. Never writes (TT-05). */
class IdentifyTagUseCase : TagOperation<TagInfo> {

    override val kind: OperationKind = OperationKind.READ

    override suspend fun execute(session: TagSession, phase: (OperationPhase) -> Unit): TagResult<TagInfo> {
        phase(OperationPhase.CONNECTING)
        val info = when (val identified = session.identify()) {
            is Outcome.Err -> return identified
            is Outcome.Ok -> identified.value
        }
        // ISO 15693: the memory layout comes from Get System Info, asked over NfcV only (no platform call in this operation).
        val transceiver = session.transceiver(TagTech.NFC_V)
        return if (transceiver != null && TagTech.NFC_V in info.techs) Iso15693.complete(info, transceiver) else Outcome.Ok(info)
    }
}
