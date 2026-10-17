package com.tagforge.domain.tools

import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ports.TagSession
import com.tagforge.domain.scan.OperationKind
import com.tagforge.domain.scan.OperationPhase
import com.tagforge.domain.scan.TagOperation
import com.tagforge.domain.tag.TagInfo

/** Identifies the next tag and returns what the OS and the chip say about it. Never writes (TT-05). */
class IdentifyTagUseCase : TagOperation<TagInfo> {

    override val kind: OperationKind = OperationKind.READ

    override suspend fun execute(session: TagSession, phase: (OperationPhase) -> Unit): TagResult<TagInfo> {
        phase(OperationPhase.CONNECTING)
        return session.identify()
    }
}
