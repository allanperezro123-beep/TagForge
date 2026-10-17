package com.tagforge.domain.tools

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ndef.DefaultNdefCodec
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.Type2Tlv
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.ports.TagSession
import com.tagforge.domain.ports.TagTransceiver
import com.tagforge.domain.scan.OperationKind
import com.tagforge.domain.scan.OperationPhase
import com.tagforge.domain.scan.TagOperation
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.TagFamily
import com.tagforge.domain.tag.TagInfo
import com.tagforge.domain.tag.TagTech
import com.tagforge.domain.write.OverwriteGuard
import com.tagforge.domain.write.Type2Io
import com.tagforge.domain.write.Type2WritePlanner
import com.tagforge.domain.write.WriteNdefUseCase

/**
 * What the first tap of a clone captured. [userPages] (the user memory, page 04 onwards) is present only when the
 * user asked to copy raw pages; the NDEF [message] is always there.
 */
class CloneSource(val info: TagInfo, val message: NdefMessage, val userPages: Bytes?)

/** A verified clone. The two tags are never UID-identical: only the content was copied. */
data class CloneReport(
    val source: TagInfo,
    val target: TagInfo,
    val messageSize: Int,
    val copiedUserPages: Boolean,
)

/**
 * Clone in two taps (TT-04): [readSource] captures the message, [writeTarget] writes it to a different tag and proves
 * it by reading back. Pages 0-3, lock bytes, capability container, config pages, PWD and PACK of either tag are never
 * written. A non-empty destination triggers the overwrite question (G5); declining writes nothing.
 *
 * Copying raw user pages (also keeps data outside the NDEF TLV) is only allowed between chips of the same model,
 * because the user area carries model-specific bytes such as NTAG213's Lock Control TLV.
 */
class CloneTagUseCase {

    fun readSource(includeUserPages: Boolean = false): TagOperation<CloneSource> = ReadSource(includeUserPages)

    fun writeTarget(source: CloneSource, confirmOverwrite: suspend (existing: NdefMessage?) -> Boolean): TagOperation<CloneReport> =
        WriteTarget(source, confirmOverwrite)

    // ---- step 1: the source ------------------------------------------------------------------------------------------

    private class ReadSource(private val includeUserPages: Boolean) : TagOperation<CloneSource> {
        override val kind: OperationKind = OperationKind.CLONE

        override suspend fun execute(session: TagSession, phase: (OperationPhase) -> Unit): TagResult<CloneSource> {
            phase(OperationPhase.CONNECTING)
            val info = when (val identified = session.identify()) {
                is Outcome.Err -> return identified
                is Outcome.Ok -> identified.value
            }
            if (info.family == TagFamily.MIFARE_CLASSIC && TagTech.NDEF !in info.techs) {
                return Outcome.Err(TagError.Unsupported(TagError.Unsupported.MIFARE_CLASSIC))
            }

            phase(OperationPhase.READING)
            val message = when (val read = session.readNdef()) {
                is Outcome.Ok -> read.value ?: return Outcome.Err(TagError.NotNdef)
                is Outcome.Err -> return if (read.error is TagError.Nak) Outcome.Err(TagError.AuthRequired) else read
            }
            if (!includeUserPages) return Outcome.Ok(CloneSource(session.info, message, userPages = null))

            val model = info.ntag
            val transceiver = session.transceiver(TagTech.NFC_A)
            if (model == null || transceiver == null) return Outcome.Err(TagError.Unsupported(TagError.Unsupported.USER_PAGES_MODEL))
            return when (val area = Type2Io.readCcAndUserArea(transceiver, model)) {
                is Outcome.Err -> area
                is Outcome.Ok -> Outcome.Ok(CloneSource(session.info, message, area.value.user))
            }
        }
    }

    // ---- step 2: the destination -------------------------------------------------------------------------------------

    private class WriteTarget(
        private val source: CloneSource,
        private val confirmOverwrite: suspend (NdefMessage?) -> Boolean,
    ) : TagOperation<CloneReport> {
        override val kind: OperationKind = OperationKind.CLONE

        /** The user may take their time over the overwrite question. */
        override val timeoutMs: Long = CONFIRMATION_TIMEOUT_MS

        override suspend fun execute(session: TagSession, phase: (OperationPhase) -> Unit): TagResult<CloneReport> {
            phase(OperationPhase.CONNECTING)
            val info = when (val identified = session.identify()) {
                is Outcome.Err -> return identified
                is Outcome.Ok -> identified.value
            }
            if (info.uid == source.info.uid) return Outcome.Err(TagError.SameTag)

            return if (source.userPages != null) copyPages(session, info, source.userPages, phase) else copyMessage(session, phase)
        }

        private suspend fun copyMessage(session: TagSession, phase: (OperationPhase) -> Unit): TagResult<CloneReport> =
            when (val written = WriteNdefUseCase(source.message, confirmOverwrite = confirmOverwrite).execute(session, phase)) {
                is Outcome.Err -> written
                is Outcome.Ok -> Outcome.Ok(CloneReport(source.info, written.value.info, written.value.messageSize, copiedUserPages = false))
            }

        private suspend fun copyPages(session: TagSession, info: TagInfo, image: Bytes, phase: (OperationPhase) -> Unit): TagResult<CloneReport> {
            val model = info.ntag
            val transceiver = session.transceiver(TagTech.NFC_A)
            if (model == null || transceiver == null || model != source.info.ntag) {
                return Outcome.Err(TagError.Unsupported(TagError.Unsupported.USER_PAGES_MODEL))
            }
            if (info.ndef?.isWritable == false) return Outcome.Err(TagError.ReadOnly)

            phase(OperationPhase.READING)
            val area = when (val read = Type2Io.readCcAndUserArea(transceiver, model)) {
                is Outcome.Err -> return read
                is Outcome.Ok -> read.value
            }
            if (!area.hasCapabilityContainer) return Outcome.Err(TagError.NotNdef)
            if (area.isReadOnlyPerCc) return Outcome.Err(TagError.ReadOnly)
            OverwriteGuard.check(session, confirmOverwrite)?.let { return it }

            val current = area.user
            if (image.size != current.size) return Outcome.Err(TagError.Malformed("User area is ${image.size} bytes but the tag has ${current.size}"))
            val tlv = (Type2Tlv.unwrap(image) as? Outcome.Ok)?.value
            val steps = Type2WritePlanner.planImage(
                model,
                current,
                image,
                leadingSize = tlv?.leadingBytes,
                lengthSize = if ((tlv?.message?.size ?: 0) <= MAX_SHORT_LENGTH) 1 else LONG_LENGTH_FIELD,
            )

            phase(OperationPhase.WRITING)
            val written = Type2Io.execute(transceiver, steps)
            if (written is Outcome.Err) return written

            phase(OperationPhase.VERIFYING)
            return verify(session, transceiver, model, image)
        }

        private suspend fun verify(session: TagSession, transceiver: TagTransceiver, model: NtagModel, image: Bytes): TagResult<CloneReport> {
            val after = when (val read = Type2Io.readCcAndUserArea(transceiver, model)) {
                is Outcome.Err -> return read
                is Outcome.Ok -> read.value.user
            }
            if (after != image) return Outcome.Err(TagError.VerifyMismatch(image, after))
            val size = DefaultNdefCodec.encode(source.message).size
            return Outcome.Ok(CloneReport(source.info, session.info, size, copiedUserPages = true))
        }
    }

    private companion object {
        const val CONFIRMATION_TIMEOUT_MS = 60_000L
        const val MAX_SHORT_LENGTH = 254
        const val LONG_LENGTH_FIELD = 3
    }
}
