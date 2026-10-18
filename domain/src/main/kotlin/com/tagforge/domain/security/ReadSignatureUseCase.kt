package com.tagforge.domain.security

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ntag.NtagCommands
import com.tagforge.domain.ntag.NtagResponses
import com.tagforge.domain.ports.TagSession
import com.tagforge.domain.scan.OperationKind
import com.tagforge.domain.scan.OperationPhase
import com.tagforge.domain.scan.TagOperation

/**
 * What can be said about a signature. The NXP public key is NOT in the datasheet and is not bundled, and secp128r1 is not in
 * the JDK/Android providers, so the only honest value is [NotVerified]: the app never declares a tag genuine.
 */
sealed interface SignatureVerification {
    data object NotVerified : SignatureVerification
}

data class SignatureReport(val uid: Bytes, val signature: Bytes, val verification: SignatureVerification)

/** READ_SIG (`3C 00`): the 32-byte ECC signature over the UID, shown as hex next to the UID (NS-07). */
class ReadSignatureUseCase : TagOperation<SignatureReport> {
    override val kind: OperationKind = OperationKind.SECURITY

    override suspend fun execute(session: TagSession, phase: (OperationPhase) -> Unit): TagResult<SignatureReport> {
        phase(OperationPhase.CONNECTING)
        val link = when (val linked = SecurityIo.connect(session)) {
            is Outcome.Err -> return linked
            is Outcome.Ok -> linked.value
        }
        phase(OperationPhase.READING)
        val response = when (val sent = link.transceiver.transceive(NtagCommands.readSig())) {
            is Outcome.Err -> return sent
            is Outcome.Ok -> sent.value
        }
        return when (val checked = NtagResponses.checkData(response, NtagCommands.SIGNATURE_SIZE)) {
            is Outcome.Err -> checked
            is Outcome.Ok -> Outcome.Ok(SignatureReport(link.info.uid, checked.value, SignatureVerification.NotVerified))
        }
    }
}
