package com.tagforge.domain.security

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.fakes.FakeNtag21x
import com.tagforge.domain.fakes.FakeTagSession
import com.tagforge.domain.fakes.FixedClock
import com.tagforge.domain.fakes.PlatformNdefFailingSession
import com.tagforge.domain.fakes.hex
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.NdefRecord
import com.tagforge.domain.ntag.MemoryDump
import com.tagforge.domain.ntag.NtagCommands
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.read.ReadFullTagUseCase
import com.tagforge.domain.read.ReadMemoryUseCase
import com.tagforge.domain.read.ReadTagUseCase
import com.tagforge.domain.read.TagReadout
import com.tagforge.domain.read.TagScan
import com.tagforge.domain.scan.TagOperation
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.TagFamily
import com.tagforge.domain.tag.TagInfo
import com.tagforge.domain.tag.TagTech
import com.tagforge.domain.tools.CloneTagUseCase
import com.tagforge.domain.tools.EraseTagUseCase
import com.tagforge.domain.tools.FormatTagUseCase
import com.tagforge.domain.write.WriteNdefUseCase
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * WU3: a tag whose user memory is behind a password (PROT = 1, or PROT = 0 for writes) can be read, written, erased, formatted,
 * dumped and cloned onto once the user's password is remembered, and never without it. The password is sent by the session
 * decorator on the raw NfcA channel, once per connection, right before the first command.
 */
class UnlockedTagSessionTest {

    private val model = NtagModel.NTAG215
    private val password = PROTECTED_TAG_PASSWORD
    private val wrong = Password.fromHex("00000001")!!
    private val store = SessionCredentials(FixedClock())
    private val message = NdefMessage(listOf(NdefRecord.uri("https://example.com")))
    private val newer = NdefMessage(listOf(NdefRecord.text("hello", "en")))

    private fun protectedTag(protectReads: Boolean = true, uid: Bytes = SPEC_UID): FakeNtag21x =
        passwordProtectedTag(message, protectReads, model, uid)

    private fun unlocked(tag: FakeNtag21x): UnlockedTagSession = UnlockedTagSession(FakeTagSession.ntag(tag), store)

    private fun FakeNtag21x.authFrames() = commandLog.filter { it[0] == NtagCommands.PWD_AUTH }

    private suspend fun <T> TagOperation<T>.on(tag: FakeNtag21x) = execute(unlocked(tag)) { }

    private suspend fun readBack(tag: FakeNtag21x): NdefMessage? {
        tag.setPage(model.cfg0, "04 00 00 FF".hex())   // open the tag to look at it from outside
        return (FakeTagSession.ntag(tag).readNdef() as Outcome.Ok).value
    }

    // ---- without a credential nothing changes ----------------------------------------------------------------------------

    @Test
    fun withoutACredentialAFullyProtectedTagIsNeitherReadNorWrittenAndNoPasswordIsSent() = runTest {
        val tag = protectedTag()
        val seeded = tag.writeCount

        assertEquals(Outcome.Err(TagError.AuthRequired), ReadTagUseCase().on(tag))
        assertEquals(Outcome.Err(TagError.AuthRequired), WriteNdefUseCase(newer) { true }.on(tag))
        assertEquals(0, tag.authFrames().size)
        assertEquals(seeded, tag.writeCount, "nothing was written")
    }

    @Test
    fun withoutACredentialAWriteProtectedTagReadsButRefusesToWrite() = runTest {
        val tag = protectedTag(protectReads = false)

        assertIs<Outcome.Ok<*>>(ReadTagUseCase().on(tag))
        assertEquals(Outcome.Err(TagError.AuthRequired), WriteNdefUseCase(newer) { true }.on(tag))
        assertEquals(0, tag.authFrames().size)
    }

    // ---- with the remembered password ------------------------------------------------------------------------------------

    @Test
    fun readReturnsTheMessageOfAProtectedTagAfterOnePasswordFrame() = runTest {
        val tag = protectedTag()
        store.remember(SPEC_UID, password)

        val readout = assertIs<Outcome.Ok<*>>(ReadTagUseCase().on(tag)).value as TagReadout

        assertEquals(message, readout.message)
        assertEquals(listOf("1B 12 34 56 78".hex()), tag.authFrames(), "one PWD_AUTH for the whole operation, not one per command")
    }

    @Test
    fun writeSucceedsOnAProtectedTagAndTheMessageIsThere() = runTest {
        for (protectReads in listOf(true, false)) {
            val tag = protectedTag(protectReads)
            store.remember(SPEC_UID, password)

            val result = WriteNdefUseCase(newer) { true }.on(tag)

            assertIs<Outcome.Ok<*>>(result, "protectReads=$protectReads: $result")
            assertEquals(1, tag.authFrames().size, "protectReads=$protectReads")
            assertEquals(newer, readBack(tag), "protectReads=$protectReads")
        }
    }

    @Test
    fun eraseAndFormatWorkOnAProtectedTag() = runTest {
        val erased = protectedTag()
        val formatted = protectedTag()
        store.remember(SPEC_UID, password)

        assertIs<Outcome.Ok<*>>(EraseTagUseCase().on(erased))
        assertIs<Outcome.Ok<*>>(FormatTagUseCase().on(formatted))

        assertNull(readBack(erased))
        assertNull(readBack(formatted))
    }

    @Test
    fun theDumpOfAProtectedTagIsCompleteWithTheCredentialAndCutShortWithout() = runTest {
        val tag = protectedTag()

        val cut = assertIs<Outcome.Ok<MemoryDump>>(ReadMemoryUseCase().on(tag)).value
        store.remember(SPEC_UID, password)
        val whole = assertIs<Outcome.Ok<MemoryDump>>(ReadMemoryUseCase().on(tag)).value

        assertNotNull(cut.unreadable, "without the password the dump stops at AUTH0")
        assertNull(whole.unreadable)
        assertEquals(model.totalPages, whole.pages.size)
    }

    @Test
    fun cloneWritesOntoAProtectedTargetOnlyWhenTheTargetsPasswordIsRemembered() = runTest {
        val sourceTag = specTag(model, "04 11 22 33 44 55 66".hex())
        check(runBlocking { FakeTagSession.ntag(sourceTag).writeNdef(message) } is Outcome.Ok)
        val clone = CloneTagUseCase()
        val source = (clone.readSource().on(sourceTag) as Outcome.Ok).value
        val target = protectedTag()

        assertEquals(Outcome.Err(TagError.AuthRequired), clone.writeTarget(source) { true }.on(target))
        store.remember(SPEC_UID, password)
        assertIs<Outcome.Ok<*>>(clone.writeTarget(source) { true }.on(target))

        assertEquals(message, readBack(target))
    }

    @Test
    fun theCredentialOfAnotherTagIsNeverSent() = runTest {
        val tag = protectedTag()
        store.remember("04 11 22 33 44 55 66".hex(), password)

        assertEquals(Outcome.Err(TagError.AuthRequired), ReadTagUseCase().on(tag))
        assertEquals(0, tag.authFrames().size)
    }

    // ---- failures ----------------------------------------------------------------------------------------------------------

    @Test
    fun aWrongRememberedPasswordFailsTheOperationOnceThenIsForgottenAndTheNextOperationSendsNothing() = runTest {
        val tag = protectedTag()
        store.remember(SPEC_UID, wrong)

        val first = ReadTagUseCase().on(tag)
        val second = ReadTagUseCase().on(tag)

        assertEquals(Outcome.Err(TagError.AuthFailed(null)), first)
        assertEquals(Outcome.Err(TagError.AuthRequired), second)
        assertEquals(1, tag.authFrames().size, "the wrong password reached the tag once and never again")
        assertNull(store.credentialFor(SPEC_UID))
    }

    @Test
    fun aLinkLostRightAfterThePasswordSurfacesAsUnknownAndForgetsIt() = runTest {
        val tag = protectedTag().also { it.dropLinkOnRefusedAuth = TagError.TagLost }
        store.remember(SPEC_UID, wrong)

        val result = ReadTagUseCase().on(tag)

        assertEquals(Outcome.Err(TagError.Security(SecurityFailure.AuthOutcomeUnknown)), result)
        assertNull(store.credentialFor(SPEC_UID))
        assertEquals(1, tag.authFrames().size)
    }

    @Test
    fun aPackThatDiffersFromTheStoredOneStopsBeforeAnyDataCommand() = runTest {
        val tag = protectedTag()
        val seeded = tag.writeCount
        store.remember(SPEC_UID, password, pack = "11 22".hex())

        val result = WriteNdefUseCase(newer) { true }.on(tag)

        assertEquals(Outcome.Err(TagError.Security(SecurityFailure.PackMismatch)), result)
        assertEquals(seeded, tag.writeCount)
        assertEquals(1, tag.authFrames().size)
    }

    // ---- the connection ----------------------------------------------------------------------------------------------------

    @Test
    fun aLinkThatWentDownIsAuthenticatedAgainBeforeTheNextCommand() = runTest {
        val tag = protectedTag()
        store.remember(SPEC_UID, password)
        val transceiver = assertNotNull(unlocked(tag).transceiver(TagTech.NFC_A))
        val readProtectedPage = NtagCommands.fastRead(model.userStart, model.userStart)

        assertIs<Outcome.Ok<*>>(transceiver.transceive(readProtectedPage))
        assertIs<Outcome.Ok<*>>(transceiver.transceive(readProtectedPage))
        assertEquals(1, tag.authFrames().size, "a live connection stays authenticated")

        tag.powerCycle()
        assertIs<Outcome.Ok<*>>(transceiver.transceive(readProtectedPage), "the tag forgot the password with the field")
        assertEquals(2, tag.authFrames().size)
    }

    @Test
    fun anExplicitReconnectAuthenticatesAgainToo() = runTest {
        val tag = protectedTag()
        store.remember(SPEC_UID, password)
        val transceiver = assertNotNull(unlocked(tag).transceiver(TagTech.NFC_A))
        val readProtectedPage = NtagCommands.fastRead(model.userStart, model.userStart)
        transceiver.transceive(readProtectedPage)

        transceiver.reconnect()
        transceiver.transceive(readProtectedPage)

        assertEquals(2, tag.authFrames().size)
    }

    @Test
    fun anExplicitPasswordFrameIsPassedThroughUntouched() = runTest {
        val tag = protectedTag()
        store.remember(SPEC_UID, password)
        val transceiver = assertNotNull(unlocked(tag).transceiver(TagTech.NFC_A))

        transceiver.transceive(NtagCommands.pwdAuth("00 00 00 01".hex()))

        assertEquals(listOf("1B 00 00 00 01".hex()), tag.authFrames(), "the decorator adds no frame of its own around a typed password")
    }

    @Test
    fun theSessionOtherwiseBehavesLikeTheOneItWraps() = runTest {
        val inner = FakeTagSession.ntag(protectedTag())
        val session = UnlockedTagSession(inner, store)

        assertEquals(inner.info, session.info)
        assertNull(session.transceiver(TagTech.NFC_V))
        assertSame(session.transceiver(TagTech.NFC_A), session.transceiver(TagTech.NFC_A), "one decorated channel per session")
        assertTrue(session.transceiver(TagTech.NFC_A) !== inner.transceiver(TagTech.NFC_A))
    }

    @Test
    fun withoutNdefTechAProtectedNtagStillReadsWritesErasesFormatsAndDumpsWhenUnlocked() = runTest {
        fun withoutNdef(tag: FakeNtag21x): PlatformNdefFailingSession {
            val noNdefInfo = TagInfo(
                uid = tag.uid,
                techs = setOf(TagTech.NFC_A, TagTech.MIFARE_ULTRALIGHT),
                family = TagFamily.NTAG21X,
                ntag = tag.model,
                atqa = Bytes.of(0x00, 0x44),
                sak = 0x00,
                historicalBytes = null,
                ndef = null,
            )
            return PlatformNdefFailingSession(FakeTagSession(noNdefInfo, tag))
        }

        val tag = protectedTag()
        assertEquals(Outcome.Err(TagError.AuthRequired), ReadFullTagUseCase().execute(UnlockedTagSession(withoutNdef(tag), store)) {})

        store.remember(SPEC_UID, password)
        val fullRead = assertIs<Outcome.Ok<TagScan>>(ReadFullTagUseCase().execute(UnlockedTagSession(withoutNdef(tag), store)) {}).value
        assertEquals(message, fullRead.readout.message)
        assertNull(fullRead.dump?.unreadable)

        assertIs<Outcome.Ok<*>>(WriteNdefUseCase(newer) { true }.execute(UnlockedTagSession(withoutNdef(tag), store)) {})
        assertEquals(newer, readBack(tag))

        assertIs<Outcome.Ok<*>>(EraseTagUseCase().execute(UnlockedTagSession(withoutNdef(tag), store)) {})
        assertNull(readBack(tag))

        val toFormat = protectedTag()
        assertIs<Outcome.Ok<*>>(FormatTagUseCase().execute(UnlockedTagSession(withoutNdef(toFormat), store)) {})
        assertNull(readBack(toFormat))
    }
}
