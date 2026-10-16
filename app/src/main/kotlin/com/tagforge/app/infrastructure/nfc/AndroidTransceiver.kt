package com.tagforge.app.infrastructure.nfc

import android.nfc.tech.NfcA
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
            }
            Outcome.Ok(Bytes.copyOf(nfcA.transceive(command.toByteArray())))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Outcome.Err(TagErrorMapper.map(e))
        }
    }

    fun close() {
        try {
            if (nfcA.isConnected) nfcA.close()
        } catch (_: Throwable) {
            // closing a vanished tag is expected to fail; there is nothing left to release
        }
    }
}
