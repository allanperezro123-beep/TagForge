package com.tagforge.domain.classic

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.fakes.FakeClassicSession
import com.tagforge.domain.fakes.FakeMifareClassic
import com.tagforge.domain.fakes.FakeTagSession
import com.tagforge.domain.ntag.Region
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tools.DumpMemoryUseCase
import com.tagforge.domain.tools.DumpReport
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** MIFARE Classic dump: public keys only, sector by sector, one technology (the Classic block channel). */
class ClassicDumpTest {

    private val random = Bytes.of(0x13, 0x37, 0xC0, 0xDE, 0xBA, 0xBE)

    private suspend fun dump(session: FakeClassicSession): Outcome<DumpReport, TagError> = DumpMemoryUseCase().execute(session) {}

    @Test
    fun aFactoryCardIsDumpedBlockByBlockWithoutTouchingThePlatform() = runTest {
        val card = FakeMifareClassic().also { it.setBlock(4, Bytes.copyOf(ByteArray(16) { 0x44 })) }
        val session = FakeClassicSession(card)
        val dump = (dump(session) as Outcome.Ok).value.dump

        assertEquals(64, dump.pages.size)
        assertEquals(Bytes.copyOf(ByteArray(16) { 0x44 }), dump.pages[4])
        assertTrue(dump.unreadableBlocks.isEmpty())
        assertEquals(0, session.platformCalls)
        assertEquals(emptyList(), card.writeLog)
    }

    @Test
    fun theRolesAreManufacturerDataAndTrailers() = runTest {
        val dump = (dump(FakeClassicSession()) as Outcome.Ok).value.dump
        assertEquals(Region.UID, dump.annotations[0].region)
        assertEquals(Region.USER, dump.annotations[1].region)
        assertEquals(Region.SECTOR_TRAILER, dump.annotations[3].region)
        assertEquals(Region.SECTOR_TRAILER, dump.annotations[63].region)
    }

    @Test
    fun keyAOfATrailerReadsAsZerosAsOnARealCard() = runTest {
        val dump = (dump(FakeClassicSession()) as Outcome.Ok).value.dump
        assertEquals(Bytes.of(0, 0, 0, 0, 0, 0), dump.pages[3].slice(0, 6))
        assertEquals(Bytes.of(0xFF, 0x07, 0x80, 0x69), dump.pages[3].slice(6, 10))
    }

    @Test
    fun onlyTheThreePublicKeysEverReachTheCard() = runTest {
        val card = FakeMifareClassic().also { c -> (0 until 16).forEach { c.setKeys(it, random, random) } }
        dump(FakeClassicSession(card))
        assertTrue(card.authAttempts.isNotEmpty())
        assertTrue(card.authAttempts.all { (_, key, _) -> key in ClassicKeys.ALL }, "${card.authAttempts.map { it.second }.distinct()}")
    }

    @Test
    fun aSectorProtectedByAPrivateKeyIsReportedLockedNotAnError() = runTest {
        val card = FakeMifareClassic().also { it.setKeys(3, random, random) }
        val dump = (dump(FakeClassicSession(card)) as Outcome.Ok).value.dump
        assertEquals((12..15).toSet(), dump.unreadableBlocks)
        assertTrue(dump.rows[12].contains("??"))
        assertEquals(64, dump.pages.size)
    }

    @Test
    fun theNdefAndMadKeysOpenTheirSectors() = runTest {
        val card = FakeMifareClassic().also {
            it.setKeys(0, ClassicKeys.MAD, FakeMifareClassic.FACTORY)
            it.setKeys(1, ClassicKeys.NDEF, random)
        }
        val dump = (dump(FakeClassicSession(card)) as Outcome.Ok).value.dump
        assertTrue(dump.unreadableBlocks.isEmpty())
    }

    @Test
    fun keyBAloneCanOpenASector() = runTest {
        val card = FakeMifareClassic().also { it.setKeys(2, random, FakeMifareClassic.FACTORY) }
        val dump = (dump(FakeClassicSession(card)) as Outcome.Ok).value.dump
        assertTrue(dump.unreadableBlocks.isEmpty())
    }

    @Test
    fun aBlockTheAccessBitsRefuseIsUnreadableAndTheRestOfTheSectorStillReads() = runTest {
        val card = FakeMifareClassic().also { it.deniedReads += 5; it.setBlock(6, Bytes.copyOf(ByteArray(16) { 0x66 })) }
        val dump = (dump(FakeClassicSession(card)) as Outcome.Ok).value.dump
        assertEquals(setOf(5), dump.unreadableBlocks)
        assertEquals(Bytes.copyOf(ByteArray(16) { 0x66 }), dump.pages[6])
    }

    @Test
    fun aVanishedCardIsTagLostAndAPhoneWithoutNxpIsExplained() = runTest {
        val card = FakeMifareClassic().also { it.loseTag() }
        assertEquals(Outcome.Err(TagError.TagLost), dump(FakeClassicSession(card)))
        val nonNxp = DumpMemoryUseCase().execute(FakeTagSession.withoutNtag()) {}
        assertEquals(Outcome.Err(TagError.Unsupported(TagError.Unsupported.MIFARE_CLASSIC)), nonNxp)
    }

    @Test
    fun theFrameLimitIsEnforcedLikeOnARealController() = runTest {
        val card = FakeMifareClassic(maxTransceiveLength = 15)
        val dump = (dump(FakeClassicSession(card)) as Outcome.Ok).value.dump
        assertEquals(64, dump.unreadableBlocks.size, "a controller that cannot move 16 bytes reads nothing")
    }

    @Test
    fun a4KCardHas256BlocksIncludingTheSixteenBlockSectors() = runTest {
        val dump = (dump(FakeClassicSession(FakeMifareClassic(ClassicLayout.CLASSIC_4K))) as Outcome.Ok).value.dump
        assertEquals(256, dump.pages.size)
        assertEquals(Region.SECTOR_TRAILER, dump.annotations[143].region)
        assertEquals(Region.USER, dump.annotations[142].region)
    }
}
