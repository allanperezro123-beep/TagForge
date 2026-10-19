package com.tagforge.app.infrastructure.nfc

import android.nfc.tech.NfcV
import android.util.Log
import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ports.TagTransceiver
import com.tagforge.domain.tag.TagTech
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Raw NfcV (ISO 15693) channel, the counterpart of [AndroidTransceiver]. Connects lazily, runs every blocking call on
 * [io] (G3) and turns platform exceptions into typed errors (G4). Only read frames are ever sent through it.
 */
class AndroidNfcVTransceiver(
    private val nfcV: NfcV,
    private val io: CoroutineDispatcher,
    /** Android keeps one active technology per tag, so the session closes the others before we connect. */
    private val beforeConnect: () -> Unit = {},
) : TagTransceiver {

    override val tech: TagTech = TagTech.NFC_V

    override val maxTransceiveLength: Int get() = nfcV.maxTransceiveLength

    override suspend fun transceive(command: Bytes): TagResult<Bytes> = withContext(io) {
        try {
            if (!nfcV.isConnected) {
                beforeConnect()
                nfcV.connect()
            }
            Outcome.Ok(Bytes.copyOf(nfcV.transceive(command.toByteArray())))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logFailure(command, e)
            Outcome.Err(TagErrorMapper.map(e))
        }
    }

    /** A tag that stays silent to an unsupported command may drop the link: close and connect again. */
    override suspend fun reconnect(): TagResult<Unit> = withContext(io) {
        try {
            if (nfcV.isConnected) nfcV.close()
            beforeConnect()
            nfcV.connect()
            Outcome.Ok(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Outcome.Err(TagErrorMapper.map(e))
        }
    }

    private fun logFailure(command: Bytes, error: Throwable) {
        try {
            val opcode = if (command.size < 2) "--" else "%02X".format(command[1])
            Log.w(LOG_TAG, "nfcv cmd=0x$opcode len=${command.size} failed: ${TagErrorMapper.describe(error)}")
        } catch (_: Throwable) {
            // logging must never change the outcome of an NFC operation
        }
    }

    fun close() {
        try {
            if (nfcV.isConnected) nfcV.close()
        } catch (_: Throwable) {
            // closing a vanished tag is expected to fail; there is nothing left to release
        }
    }

    private companion object {
        const val LOG_TAG = "TagForgeNfc"
    }
}
