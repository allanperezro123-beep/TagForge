package com.tagforge.domain.security

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ntag.NtagCommands
import com.tagforge.domain.ntag.NtagConfig
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.ntag.NtagResponses
import com.tagforge.domain.ports.TagSession
import com.tagforge.domain.ports.TagTransceiver
import com.tagforge.domain.scan.OperationKind
import com.tagforge.domain.scan.OperationPhase
import com.tagforge.domain.scan.TagOperation
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.TagInfo
import com.tagforge.domain.tag.TagTech

/** What the security screens need to know about a tag before anything is planned. Read only. */
data class SecurityState(
    val info: TagInfo,
    val model: NtagModel,
    val config: NtagConfig,
    /** Bytes 2-3 of page 02. */
    val staticLock: Bytes,
    /** Capability container, page 03. */
    val cc: Bytes,
    /** Bytes 0-2 of the dynamic lock page (byte 3 always reads BDh). */
    val dynamicLock: Bytes,
    /** PACK answered by PWD_AUTH when the state was read with a password; null otherwise. */
    val pack: Bytes? = null,
) {
    val isPasswordProtected: Boolean get() = config.isProtectionActive(model)
}

/**
 * Reads CFG0/CFG1, the lock bytes and the CC of the next NTAG21x (optionally after PWD_AUTH). Never writes.
 * A tag whose configuration is behind a password answers [TagError.AuthRequired] until a [password] is given.
 */
class ReadSecurityStateUseCase(private val password: Password? = null) : TagOperation<SecurityState> {

    override val kind: OperationKind = OperationKind.SECURITY

    override suspend fun execute(session: TagSession, phase: (OperationPhase) -> Unit): TagResult<SecurityState> {
        phase(OperationPhase.CONNECTING)
        val info = when (val identified = session.identify()) {
            is Outcome.Err -> return identified
            is Outcome.Ok -> identified.value
        }
        val model = info.ntag
        val transceiver = session.transceiver(TagTech.NFC_A)
        if (model == null || transceiver == null) return Outcome.Err(TagError.Security(SecurityFailure.UnsupportedChip))

        phase(OperationPhase.READING)
        val pack = password?.let { authenticate(transceiver, it) }
        if (pack is Outcome.Err) return pack
        return read(info, model, transceiver, (pack as? Outcome.Ok)?.value)
    }

    private suspend fun read(info: TagInfo, model: NtagModel, t: TagTransceiver, pack: Bytes?): TagResult<SecurityState> {
        val head = when (val r = fastRead(t, STATIC_LOCK_PAGE, CC_PAGE)) { is Outcome.Err -> return r; is Outcome.Ok -> r.value }
        val cfg = when (val r = fastRead(t, model.cfg0, model.cfg1)) { is Outcome.Err -> return r; is Outcome.Ok -> r.value }
        val dyn = when (val r = fastRead(t, model.dynLock, model.dynLock)) { is Outcome.Err -> return r; is Outcome.Ok -> r.value }
        val config = NtagConfig.parse(cfg) ?: return Outcome.Err(TagError.Malformed("CFG0/CFG1 answered ${cfg.size} bytes"))
        return Outcome.Ok(SecurityState(info, model, config, head.slice(2, 4), head.slice(4, 8), dyn.slice(0, 3), pack))
    }

    private suspend fun authenticate(t: TagTransceiver, password: Password): TagResult<Bytes> =
        SecurityIo.authenticate(t, password.bytes)

    private suspend fun fastRead(t: TagTransceiver, from: Int, to: Int): TagResult<Bytes> =
        SecurityIo.readPages(t, from, to)

    private companion object {
        const val STATIC_LOCK_PAGE = 2
        const val CC_PAGE = 3
    }
}
