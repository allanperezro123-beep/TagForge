package com.tagforge.domain.security

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ports.TagSession
import com.tagforge.domain.scan.OperationKind
import com.tagforge.domain.scan.OperationPhase
import com.tagforge.domain.scan.TagOperation
import com.tagforge.domain.tag.TagError

data class AuthReport(val pack: Bytes, val packMatches: Boolean?) {
    /** The tag answered a PACK other than the one expected: it may be a clone. */
    val possibleClone: Boolean get() = packMatches == false
}

data class AuthLimitInfo(val authLimit: Int?)

/**
 * One PWD_AUTH per tap (NS-03). There is no retry, no list of passwords and no counter-guessing: the only password this
 * class can ever send is the one the user typed. A wrong password is a real attempt against the tag's AUTHLIM.
 */
class AuthenticateUseCase(private val password: Password, private val expectedPack: Bytes? = null) : TagOperation<AuthReport> {
    override val kind: OperationKind = OperationKind.SECURITY

    override suspend fun execute(session: TagSession, phase: (OperationPhase) -> Unit): TagResult<AuthReport> {
        phase(OperationPhase.CONNECTING)
        val link = when (val linked = SecurityIo.connect(session)) {
            is Outcome.Err -> return linked
            is Outcome.Ok -> linked.value
        }
        phase(OperationPhase.READING)
        return when (val pack = SecurityIo.authenticate(link.transceiver, password.bytes)) {
            is Outcome.Err -> pack
            is Outcome.Ok -> Outcome.Ok(AuthReport(pack.value, expectedPack?.let { it == pack.value }))
        }
    }
}

/** Reads CFG1 to learn AUTHLIM before the first try. Null when the configuration is behind the password and cannot be read. */
class ReadAuthLimitUseCase : TagOperation<AuthLimitInfo> {
    override val kind: OperationKind = OperationKind.SECURITY

    override suspend fun execute(session: TagSession, phase: (OperationPhase) -> Unit): TagResult<AuthLimitInfo> {
        phase(OperationPhase.CONNECTING)
        val link = when (val linked = SecurityIo.connect(session)) {
            is Outcome.Err -> return linked
            is Outcome.Ok -> linked.value
        }
        phase(OperationPhase.READING)
        return when (val cfg1 = SecurityIo.readPages(link.transceiver, link.model.cfg1, link.model.cfg1)) {
            is Outcome.Ok -> Outcome.Ok(AuthLimitInfo(cfg1.value[0] and AUTHLIM_MASK))
            is Outcome.Err -> if (cfg1.error == TagError.AuthRequired) Outcome.Ok(AuthLimitInfo(null)) else cfg1
        }
    }

    private companion object {
        const val AUTHLIM_MASK = 0x07
    }
}
