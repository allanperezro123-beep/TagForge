package com.tagforge.app.infrastructure.nfc

import android.nfc.TagLostException
import android.nfc.tech.MifareClassic
import android.util.Log
import com.tagforge.domain.classic.ClassicKeyType
import com.tagforge.domain.classic.ClassicLayout
import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ports.ClassicChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Block access to a MIFARE Classic through `android.nfc.tech.MifareClassic` (only present on phones with the NXP
 * controller). Same contract as the other channels: blocking calls on [io] (G3), platform exceptions become typed
 * errors (G4). After a rejected key the card is re-selected, because the chip drops back to idle and the next
 * authentication would otherwise fail for a reason that has nothing to do with its key.
 */
class AndroidClassicChannel(
    private val classic: MifareClassic,
    private val io: CoroutineDispatcher,
    /** Android keeps one active technology per tag, so the session closes the others before we connect. */
    private val beforeConnect: () -> Unit = {},
) : ClassicChannel {

    override val layout: ClassicLayout = ClassicLayout.ofSectors(classic.sectorCount) ?: ClassicLayout.CLASSIC_1K

    override suspend fun authenticate(sector: Int, key: Bytes, type: ClassicKeyType): TagResult<Boolean> = withContext(io) {
        try {
            ensureConnected()
            val accepted = if (type == ClassicKeyType.A) {
                classic.authenticateSectorWithKeyA(sector, key.toByteArray())
            } else {
                classic.authenticateSectorWithKeyB(sector, key.toByteArray())
            }
            if (accepted) Outcome.Ok(true) else reselect()
        } catch (e: CancellationException) {
            throw e
        } catch (e: TagLostException) {
            Outcome.Err(TagErrorMapper.map(e))
        } catch (e: Throwable) {
            logFailure("auth sector=$sector", e)
            reselect()   // an I/O error during authentication means "rejected" far more often than "gone"
        }
    }

    override suspend fun readBlock(block: Int): TagResult<Bytes> = withContext(io) {
        try {
            ensureConnected()
            Outcome.Ok(Bytes.copyOf(classic.readBlock(block)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logFailure("read block=$block", e)
            Outcome.Err(TagErrorMapper.map(e))
        }
    }

    override suspend fun writeBlock(block: Int, data: Bytes): TagResult<Unit> = withContext(io) {
        try {
            ensureConnected()
            classic.writeBlock(block, data.toByteArray())
            Outcome.Ok(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logFailure("write block=$block", e)
            Outcome.Err(TagErrorMapper.map(e))
        }
    }

    /** Close and connect again; Ok(false) tells the caller "key rejected", Err(TagLost) that the card is really gone. */
    private fun reselect(): TagResult<Boolean> = try {
        if (classic.isConnected) classic.close()
        beforeConnect()
        classic.connect()
        Outcome.Ok(false)
    } catch (e: Throwable) {
        Outcome.Err(TagErrorMapper.map(e))
    }

    private fun ensureConnected() {
        if (!classic.isConnected) {
            beforeConnect()
            classic.connect()
        }
    }

    private fun logFailure(what: String, error: Throwable) {
        try {
            Log.w(LOG_TAG, "classic $what failed: ${TagErrorMapper.describe(error)}")
        } catch (_: Throwable) {
            // logging must never change the outcome of an NFC operation
        }
    }

    fun close() {
        try {
            if (classic.isConnected) classic.close()
        } catch (_: Throwable) {
            // closing a vanished tag is expected to fail; there is nothing left to release
        }
    }

    private companion object {
        const val LOG_TAG = "TagForgeNfc"
    }
}
