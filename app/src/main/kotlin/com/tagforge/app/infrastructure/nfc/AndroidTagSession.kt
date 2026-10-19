package com.tagforge.app.infrastructure.nfc

import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.nfc.tech.NfcA
import android.nfc.tech.NfcV
import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ndef.NdefCodec
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.Tnf
import com.tagforge.domain.ntag.NtagCommands
import com.tagforge.domain.ntag.NtagResponses
import com.tagforge.domain.ports.TagSession
import com.tagforge.domain.ports.TagTransceiver
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.TagFamily
import com.tagforge.domain.tag.UltralightType
import com.tagforge.domain.type2.Type2Identifier
import com.tagforge.domain.type2.Type2Identity
import com.tagforge.domain.tag.TagInfo
import com.tagforge.domain.tag.TagTech
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * [TagSession] over a real `android.nfc.Tag`. Every blocking call runs on [io] (G3) and every platform exception is
 * mapped to a typed [TagError] (G4). Raw NTAG writes go through [transceiver]; [writeNdef] is the generic path
 * for every other NDEF tag (and for unformatted tags that Android can format while writing).
 */
class AndroidTagSession(
    private val tag: Tag,
    private val codec: NdefCodec,
    private val io: CoroutineDispatcher,
) : TagSession {

    override var info: TagInfo = TagInfoExtractor.basicInfo(tag)
        private set

    private val nfcATransceiver: AndroidTransceiver? by lazy {
        NfcA.get(tag)?.let { AndroidTransceiver(it, io, beforeConnect = ::closeNdefQuietly) }
    }

    private val nfcVTransceiver: AndroidNfcVTransceiver? by lazy {
        NfcV.get(tag)?.let { AndroidNfcVTransceiver(it, io, beforeConnect = ::closeNdefQuietly) }
    }

    /**
     * `Ndef.get` returns a NEW object every time and `isConnected` is per object, so the instance that was connected
     * must be remembered: closing a fresh one would silently do nothing and leave the Ndef technology active.
     */
    private var ndefTech: Ndef? = null

    private fun ndefTech(): Ndef? = ndefTech ?: Ndef.get(tag)?.also { ndefTech = it }

    private fun closeNdefQuietly() {
        try {
            ndefTech?.takeIf { it.isConnected }?.close()
        } catch (_: Throwable) {
            // already gone
        } finally {
            ndefTech = null
        }
    }

    private fun closeRawChannels() {
        nfcATransceiver?.close()
        nfcVTransceiver?.close()
    }

    private fun closeFormatableQuietly() {
        try {
            NdefFormatable.get(tag)?.takeIf { it.isConnected }?.close()
        } catch (_: Throwable) {
            // already gone
        }
    }

    private var identifyDone = false

    override suspend fun identify(): TagResult<TagInfo> {
        val transceiver = nfcATransceiver
        val maybeType2 = TagTech.NFC_A in info.techs && !identifyDone &&
            (TagTech.MIFARE_ULTRALIGHT in info.techs || info.sak == 0)
        if (transceiver != null && maybeType2) {
            // Android's own Ultralight type is the fallback for chips that do not know GET_VERSION (UL, UL-C).
            val androidType = if (TagTech.MIFARE_ULTRALIGHT in info.techs) info.details.ultralight?.type ?: UltralightType.UNKNOWN else null
            when (val identified = Type2Identifier.identify(transceiver, androidType)) {
                is Outcome.Err -> return identified
                is Outcome.Ok -> {
                    identifyDone = true
                    when (val identity = identified.value) {
                        is Type2Identity.Ntag -> info = info.copy(ntag = identity.model, family = TagInfoExtractor.familyFor(info.techs, identity.model))
                        is Type2Identity.Other -> info = info.copy(type2 = identity.chip, family = identity.chip.family)
                        is Type2Identity.NtagI2c -> info = info.copy(family = TagFamily.NTAG_I2C)
                        null -> Unit
                    }
                }
            }
        }
        return Outcome.Ok(info)
    }

    override fun transceiver(tech: TagTech): TagTransceiver? = when (tech) {
        TagTech.NFC_A -> nfcATransceiver
        TagTech.NFC_V -> nfcVTransceiver
        else -> null
    }

    override suspend fun readNdef(): TagResult<NdefMessage?> = withContext(io) {
        val ndef = ndefTech() ?: return@withContext readWithoutNdefTech()
        try {
            if (!ndef.isConnected) {
                closeRawChannels()
                ndef.connect()
            }
            val platformMessage = ndef.ndefMessage ?: return@withContext Outcome.Ok(null)
            decode(platformMessage.toByteArray())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Outcome.Err(TagErrorMapper.map(e))
        }
    }

    private fun decode(bytes: ByteArray): TagResult<NdefMessage?> =
        when (val decoded = codec.decode(Bytes.copyOf(bytes))) {
            is Outcome.Err -> Outcome.Err(TagError.Malformed(decoded.error.toString()))
            is Outcome.Ok -> {
                val records = decoded.value.records
                val onlyEmptyRecord = records.size == 1 && records[0].tnf == Tnf.EMPTY && records[0].payload.isEmpty()
                Outcome.Ok(if (records.isEmpty() || onlyEmptyRecord) null else decoded.value)
            }
        }

    /**
     * Android exposes the `Ndef` tech only when it could parse the capability container and TLVs. For an NTAG whose
     * user memory is password-protected that fails, so we probe the first user page: a refusal means "protected".
     */
    private suspend fun readWithoutNdefTech(): TagResult<NdefMessage?> {
        val layout = info.layout
        val transceiver = nfcATransceiver
        if (layout == null || transceiver == null) return Outcome.Err(TagError.NotNdef)

        val probe = transceiver.transceive(NtagCommands.read(layout.userStart))
        return when (probe) {
            is Outcome.Err -> Outcome.Err(TagError.AuthRequired)   // NAK often surfaces as a dropped connection
            is Outcome.Ok -> when (NtagResponses.checkData(probe.value, NtagCommands.READ_RESPONSE_SIZE)) {
                is Outcome.Err -> Outcome.Err(TagError.AuthRequired)
                is Outcome.Ok -> Outcome.Err(TagError.NotNdef)
            }
        }
    }

    override suspend fun writeNdef(message: NdefMessage): TagResult<Unit> = withContext(io) {
        try {
            val platformMessage = android.nfc.NdefMessage(codec.encode(message).toByteArray())
            val ndef = ndefTech()
            if (ndef != null) {
                if (!ndef.isConnected) {
                    closeRawChannels()
                    ndef.connect()
                }
                when {
                    !ndef.isWritable -> Outcome.Err(TagError.ReadOnly)
                    platformMessage.byteArrayLength > ndef.maxSize ->
                        Outcome.Err(TagError.CapacityExceeded(platformMessage.byteArrayLength, ndef.maxSize))
                    else -> {
                        ndef.writeNdefMessage(platformMessage)
                        Outcome.Ok(Unit)
                    }
                }
            } else {
                // An unformatted tag: format it and write in one step.
                val formatable = NdefFormatable.get(tag) ?: return@withContext Outcome.Err(TagError.NotNdef)
                closeRawChannels()
                formatable.connect()
                formatable.format(platformMessage)
                Outcome.Ok(Unit)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Outcome.Err(TagErrorMapper.map(e))
        }
    }

    /**
     * Formats an unformatted tag through `NdefFormatable`. Android only formats here; a tag that already has NDEF is
     * handled by the raw NTAG path in the domain. After a successful format this `Tag` handle cannot be re-read.
     */
    override suspend fun formatNdef(initial: NdefMessage?): TagResult<Unit> = withContext(io) {
        try {
            val formatable = NdefFormatable.get(tag) ?: return@withContext Outcome.Err(TagError.Unsupported("format"))
            closeRawChannels()
            closeNdefQuietly()
            if (!formatable.isConnected) formatable.connect()
            val first = if (initial == null || initial.isEmpty) {
                android.nfc.NdefMessage(android.nfc.NdefRecord(android.nfc.NdefRecord.TNF_EMPTY, null, null, null))
            } else {
                android.nfc.NdefMessage(codec.encode(initial).toByteArray())
            }
            formatable.format(first)
            Outcome.Ok(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Outcome.Err(TagErrorMapper.map(e))
        }
    }

    override suspend fun makeReadOnly(): TagResult<Unit> = Outcome.Err(TagError.Unsupported("makeReadOnly"))

    override suspend fun close() {
        withContext(io) {
            closeRawChannels()
            closeNdefQuietly()
            closeFormatableQuietly()
        }
    }
}
