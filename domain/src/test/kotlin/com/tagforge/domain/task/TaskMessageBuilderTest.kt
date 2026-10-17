package com.tagforge.domain.task

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.ndef.NdefBudget
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.NdefRecord
import com.tagforge.domain.ndef.Tnf
import com.tagforge.domain.ntag.NtagModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** TK-03 (layout and budget) with the reconciliation notes applied: MIME `application/x-tf`, P <= 83 on a 136 B budget. */
class TaskMessageBuilderTest {

    private val app = "com.tagforge.app"

    /** A task whose encoded payload is exactly [size] bytes: header + count + opcode + length byte + text. */
    private fun draftOfPayloadSize(size: Int): TaskDraft {
        val textLength = size - 4
        require(textLength in 0..255)
        return TaskDraft().add(TaskAction.Speak("x".repeat(textLength)))
    }

    private fun payloadSize(draft: TaskDraft): Int = (TaskCodec.encode(draft.payload) as Outcome.Ok).value.size

    private fun analyse(draft: TaskDraft, capacity: Int) = TaskMessageBuilder.analyse(draft, app, capacity)

    // ---- layout -----------------------------------------------------------------------------------------------

    @Test
    fun theMessageIsTheTaskMimeRecordFollowedByTheAarAndNothingElse() {
        val draft = TaskDraft("Night").add(TaskAction.SetRinger(RingerMode.VIBRATE))

        val message = (TaskMessageBuilder.build(draft.payload, app) as Outcome.Ok).value

        assertEquals(2, message.records.size)
        val task = message.records[0]
        assertEquals(Tnf.MIME, task.tnf)
        assertEquals(Bytes.copyOf("application/x-tf".toByteArray()), task.type)
        assertEquals((TaskCodec.encode(draft.payload) as Outcome.Ok).value, task.payload)
        assertEquals(NdefRecord.aar(app), message.records[1], "the AAR is last: dispatch uses record 0, so it must never be first")
        assertEquals(16, "application/x-tf".length)
    }

    @Test
    fun theAarFollowsTheRuntimeApplicationIdSoDebugBuildsLaunchThemselves() {
        val message = (TaskMessageBuilder.build(TaskPayload(null, listOf(TaskAction.Flashlight(true))), "com.tagforge.app.debug") as Outcome.Ok).value

        assertEquals(NdefRecord.aar("com.tagforge.app.debug"), message.records.last())
    }

    @Test
    fun anUnencodablePayloadYieldsTheCodecError() {
        val tooMany = TaskPayload(null, List(17) { TaskAction.Flashlight(true) })

        assertEquals(Outcome.Err(TaskError.TooLarge), TaskMessageBuilder.build(tooMany, app))
    }

    // ---- budget (reconciliation #2) -----------------------------------------------------------------------------

    @Test
    fun onAnNtag213ThePayloadFitsWhen19PlusPPlus34IsAtMost136() {
        val capacity = NdefBudget.presetCapacity(NtagModel.NTAG213)
        assertEquals(136, capacity)

        val fits = analyse(draftOfPayloadSize(83), capacity)
        assertEquals(19 + 83 + 34, fits.budget.used)
        assertEquals(136, fits.budget.used)
        assertTrue(fits.budget.fits)
        assertNull(fits.block)

        val over = analyse(draftOfPayloadSize(84), capacity)
        assertEquals(137, over.budget.used)
        assertFalse(over.budget.fits)
        assertEquals(TaskBlock.OVER_CAPACITY, over.block)
    }

    @Test
    fun againstWhatAndroidReportsForAnNtag213AnExactFitOf137IsAllowed() {
        val capacity = NdefBudget.androidMaxSize(NtagModel.NTAG213)
        assertEquals(137, capacity)

        assertNull(analyse(draftOfPayloadSize(84), capacity).block)
        assertEquals(TaskBlock.OVER_CAPACITY, analyse(draftOfPayloadSize(85), capacity).block)
    }

    @Test
    fun theMeterCountsTlvAndTerminatorSeparatelyFromTheRecords() {
        val report = analyse(draftOfPayloadSize(50), 137).budget

        assertEquals(listOf(19 + 50, 34), report.perRecord)
        assertEquals(19 + 50 + 34, report.used)
        assertEquals(3, report.tlvOverhead, "TLV tag + 1-byte length + terminator")
    }

    @Test
    fun maxPayloadIsTheFormulaBehindThoseNumbers() {
        assertEquals(83, TaskMessageBuilder.maxPayload(136, app))
        assertEquals(84, TaskMessageBuilder.maxPayload(137, app))
        assertEquals(70, TaskMessageBuilder.maxPayload(136, app, mimeType = "application/vnd.tagforge.task"), "the spec's 29-byte MIME example")
        assertEquals(77, TaskMessageBuilder.maxPayload(136, "com.tagforge.app.debug"), "the debug AAR is 6 bytes longer")
    }

    @Test
    fun aNinetyBytePayloadOverflowsAnNtag213AndTheAppRecommendsTheNext() {
        val draft = draftOfPayloadSize(90)
        assertEquals(90, payloadSize(draft))

        val onNtag213 = analyse(draft, NdefBudget.androidMaxSize(NtagModel.NTAG213))

        assertEquals(TaskBlock.OVER_CAPACITY, onNtag213.block)
        assertEquals(NtagModel.NTAG215, onNtag213.suggestedChip)
        assertEquals(19 + 90 + 34, onNtag213.budget.used)
        assertEquals(143 - 137, onNtag213.budget.overBy)

        val onNtag215 = analyse(draft, NdefBudget.androidMaxSize(NtagModel.NTAG215))
        assertNull(onNtag215.block)
        assertNull(onNtag215.suggestedChip)
        assertNotNull(onNtag215.message)
    }

    @Test
    fun aSingleRingerActionLeavesPlentyOfRoomOnAnNtag213() {
        val analysis = analyse(TaskDraft().add(TaskAction.SetRinger(RingerMode.VIBRATE)), NdefBudget.presetCapacity(NtagModel.NTAG213))

        assertEquals(19 + 4 + 34, analysis.budget.used)
        assertTrue(analysis.budget.capacity - analysis.budget.used >= 66)
        assertNull(analysis.block)
    }

    @Test
    fun theNameCostsBytesOnTheTag() {
        val plain = analyse(TaskDraft().add(TaskAction.Flashlight(true)), 137).budget.used
        val named = analyse(TaskDraft("Night").add(TaskAction.Flashlight(true)), 137).budget.used

        assertEquals(plain + 1 + 5, named)
    }

    // ---- why the Write button is off -------------------------------------------------------------------------------

    @Test
    fun anEmptyTaskCannotBeWritten() {
        val analysis = analyse(TaskDraft("Empty"), 137)

        assertEquals(TaskBlock.NO_ACTIONS, analysis.block)
        assertNull(analysis.message)
    }

    @Test
    fun aBlockedActionStopsTheWriteAndSaysWhich() {
        val draft = TaskDraft()
            .add(TaskAction.SetRinger(RingerMode.VIBRATE))
            .add(TaskAction.OpenUrl("javascript:alert(1)"))

        val analysis = analyse(draft, 137)

        assertEquals(TaskBlock.INVALID_ACTION, analysis.block)
        assertEquals(mapOf(1 to BlockReason.DISALLOWED_SCHEME), analysis.invalid)
        assertNull(analysis.message)
    }

    @Test
    fun anUnencodableDraftReportsTheCodecError() {
        val analysis = analyse(TaskDraft().add(TaskAction.Speak("x".repeat(300))), 137)

        assertEquals(TaskBlock.INVALID_ACTION, analysis.block, "the validator catches a 300-byte string first")
        val unencodable = TaskDraft("n".repeat(40)).add(TaskAction.Flashlight(true))
        assertEquals(TaskBlock.ENCODE_ERROR, analyse(unencodable, 137).block)
        assertEquals(TaskError.TooLarge, analyse(unencodable, 137).error)
    }

    @Test
    fun aValidDraftYieldsTheMessageToWrite() {
        val draft = TaskDraft("Go").add(TaskAction.Flashlight(true)).add(TaskAction.OpenUrl("https://example.com"))

        val analysis = analyse(draft, 137)

        assertNull(analysis.block)
        assertEquals((TaskMessageBuilder.build(draft.payload, app) as Outcome.Ok).value, analysis.message)
    }

    // ---- reading a tag ----------------------------------------------------------------------------------------

    @Test
    fun aWrittenMessageDecodesBackToTheSameTask() {
        val payload = TaskPayload("Night", listOf(TaskAction.SetRinger(RingerMode.SILENT), TaskAction.OpenUrl("https://example.com")))
        val message = (TaskMessageBuilder.build(payload, app) as Outcome.Ok).value

        assertEquals(Outcome.Ok(payload), TaskMessageBuilder.extract(message))
    }

    @Test
    fun theTaskRecordIsFoundEvenWhenItIsNotFirstAndTheTypeCaseDiffers() {
        val payload = TaskPayload(null, listOf(TaskAction.Flashlight(true)))
        val bytes = (TaskCodec.encode(payload) as Outcome.Ok).value
        val message = NdefMessage(listOf(NdefRecord.text("hi"), NdefRecord.mime("Application/X-TF", bytes)))

        assertEquals(Outcome.Ok(payload), TaskMessageBuilder.extract(message))
    }

    @Test
    fun aMessageWithoutATaskRecordIsNotATask() {
        assertEquals(Outcome.Err(TaskError.NotATask), TaskMessageBuilder.extract(NdefMessage(listOf(NdefRecord.text("hi")))))
        assertEquals(Outcome.Err(TaskError.NotATask), TaskMessageBuilder.extract(NdefMessage.EMPTY))
        assertNull(TaskMessageBuilder.find(NdefMessage(listOf(NdefRecord.mime("text/plain", Bytes.of(1))))))
    }

    @Test
    fun aTaskRecordWithAHostilePayloadSurfacesTheCodecError() {
        val message = NdefMessage(listOf(NdefRecord.mime("application/x-tf", Bytes.of(0x20, 0x00))))

        val result = TaskMessageBuilder.extract(message)

        assertIs<Outcome.Err<TaskError>>(result)
        assertEquals(TaskError.UnsupportedVersion(2), result.error)
    }
}
