package com.tagforge.app.infrastructure.nfc

import android.nfc.tech.NfcA
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
 * Raw NfcA channel. Connects lazily on first use and runs every blocking call on [io] (rule G3).
 * Platform exceptions never escape: they become [com.tagforge.domain.tag.TagError] (rule G4).
 */
class AndroidTransceiver(
    private val nfcA: NfcA,
    private val io: CoroutineDispatcher,
    /** Android keeps one active technology per tag, so the session closes the others before we connect. */
    private val beforeConnect: () -> Unit = {},
) : TagTransceiver {

    override val tech: TagTech = TagTech.NFC_A

    override val maxTransceiveLength: Int get() = nfcA.maxTransceiveLength

    override suspend fun transceive(command: Bytes): TagResult<Bytes> = withContext(io) {
        try {
            if (!nfcA.isConnected) {
                beforeConnect()
                nfcA.connect()
                applyTimeout()
            }
            Outcome.Ok(Bytes.copyOf(nfcA.transceive(command.toByteArray())))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logFailure(command, e)
            Outcome.Err(TagErrorMapper.map(e))
        }
    }

    /** The platform default (about 0.6 s) is tight for a controller that is busy re-selecting the tag after a technology switch. */
    private fun applyTimeout() {
        try {
            nfcA.timeout = TRANSCEIVE_TIMEOUT_MS
        } catch (_: Throwable) {
            // not every controller lets the timeout change; the default still works for single frames
        }
    }

    /** Field reports need the opcode, the exact exception and the frame limit; adb logcat tag `TagForgeNfc`. */
    private fun logFailure(command: Bytes, error: Throwable) {
        try {
            val opcode = if (command.isEmpty()) "--" else "%02X".format(command[0])
            val limit = try { nfcA.maxTransceiveLength } catch (_: Throwable) { -1 }
            Log.w(LOG_TAG, "transceive op=0x$opcode len=${command.size} maxLen=$limit failed: ${TagErrorMapper.describe(error)}")
        } catch (_: Throwable) {
            // logging must never change the outcome of an NFC operation
        }
    }

    fun close() {
        try {
            if (nfcA.isConnected) nfcA.close()
        } catch (_: Throwable) {
            // closing a vanished tag is expected to fail; there is nothing left to release
        }
    }

    private companion object {
        const val LOG_TAG = "TagForgeNfc"
        const val TRANSCEIVE_TIMEOUT_MS = 1_500
    }
}
