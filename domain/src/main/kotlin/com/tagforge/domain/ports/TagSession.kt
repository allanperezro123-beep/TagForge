package com.tagforge.domain.ports

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.tag.TagInfo
import com.tagforge.domain.tag.TagTech

/** Raw command channel to one tag technology (NfcA, MIFARE Ultralight, ...). */
interface TagTransceiver {
    val tech: TagTech
    val maxTransceiveLength: Int

    /** Sends [command] and returns the raw response (a lone 4-bit NAK arrives as a 1-byte response). */
    suspend fun transceive(command: Bytes): TagResult<Bytes>
}

/**
 * One tag, as seen by the domain. The Android adapter wraps `android.nfc.tech.*`; tests use FakeTagSession.
 * Implementations never throw for tag failures: they map them to [com.tagforge.domain.tag.TagError].
 */
interface TagSession {
    /** Best-known info. Starts as what the OS reports for free and is completed by [identify]. */
    val info: TagInfo

    /** Performs the I/O needed to complete [info] (GET_VERSION, NDEF capacity). Only TagLost/Io are errors. */
    suspend fun identify(): TagResult<TagInfo>

    /** Null when the technology is not present on this tag. */
    fun transceiver(tech: TagTech): TagTransceiver?

    /** Ok(null) = formatted but empty; Err(NotNdef) = no NDEF; Err(Malformed) = unparsable NDEF; Err(AuthRequired) = protected. */
    suspend fun readNdef(): TagResult<NdefMessage?>

    suspend fun writeNdef(message: NdefMessage): TagResult<Unit>

    suspend fun formatNdef(initial: NdefMessage?): TagResult<Unit>

    suspend fun makeReadOnly(): TagResult<Unit>

    suspend fun close()
}
