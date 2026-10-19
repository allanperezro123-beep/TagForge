package com.tagforge.domain.type2

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.fakes.FakeNtag21x
import com.tagforge.domain.fakes.FakeType2Chip
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.UltralightType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class Type2IdentifierTest {

    private fun version(subtype: Int, product: Int, size: Int) = Bytes.of(0x00, 0x04, product, subtype, 0x01, 0x00, size, 0x03)

    @Test
    fun parsesEveryGetVersionAnswerOfTheSupportedChips() {
        assertEquals(Type2Identity.Other(Type2Chip.ULTRALIGHT_EV1_11), Type2Identifier.parseVersion(version(0x01, 0x03, 0x0B)))
        assertEquals(Type2Identity.Other(Type2Chip.ULTRALIGHT_EV1_21), Type2Identifier.parseVersion(version(0x02, 0x03, 0x0E)))
        assertEquals(Type2Identity.Other(Type2Chip.NTAG210), Type2Identifier.parseVersion(version(0x01, 0x04, 0x0B)))
        assertEquals(Type2Identity.Other(Type2Chip.NTAG212), Type2Identifier.parseVersion(version(0x01, 0x04, 0x0E)))
        assertEquals(Type2Identity.Ntag(NtagModel.NTAG215), Type2Identifier.parseVersion(version(0x02, 0x04, 0x11)))
    }

    @Test
    fun ntagI2cIsNeverMistakenForAnNtag216() {
        val i2c = version(0x05, 0x04, 0x13)
        assertEquals(Type2Identity.NtagI2c(0x13), Type2Identifier.parseVersion(i2c))
        assertNull(com.tagforge.domain.ntag.NtagResponses.parseVersion(i2c))
    }

    @Test
    fun aNakOrAnUnknownChipIsNotIdentified() {
        assertNull(Type2Identifier.parseVersion(Bytes.of(0x00)))
        assertNull(Type2Identifier.parseVersion(version(0x01, 0x04, 0x77)))
    }

    @Test
    fun ultralightIsRecognisedAfterGetVersionDropsTheLinkAndTheTagIsReconnected() = runTest {
        val tag = FakeType2Chip(Type2Chip.ULTRALIGHT)
        val result = Type2Identifier.identify(tag, UltralightType.ULTRALIGHT)
        assertEquals(Outcome.Ok(Type2Identity.Other(Type2Chip.ULTRALIGHT)), result)
        assertEquals(1, tag.reconnectCount)
        // The link works again: the next command is answered.
        assertIs<Outcome.Ok<*>>(tag.transceive(Bytes.of(0x30, 0x00)))
    }

    @Test
    fun ultralightCIsNamedAfterWhatAndroidReported() = runTest {
        val result = Type2Identifier.identify(FakeType2Chip(Type2Chip.ULTRALIGHT_C), UltralightType.ULTRALIGHT_C)
        assertEquals(Outcome.Ok(Type2Identity.Other(Type2Chip.ULTRALIGHT_C)), result)
    }

    @Test
    fun ultralightEv1AnswersGetVersionSoNoReconnectIsNeeded() = runTest {
        val tag = FakeType2Chip(Type2Chip.ULTRALIGHT_EV1_21)
        val result = Type2Identifier.identify(tag, UltralightType.ULTRALIGHT)
        assertEquals(Outcome.Ok(Type2Identity.Other(Type2Chip.ULTRALIGHT_EV1_21)), result)
        assertEquals(0, tag.reconnectCount)
    }

    @Test
    fun aTagThatReallyVanishedIsStillTagLostAfterTheReconnectAttempt() = runTest {
        val tag = FakeType2Chip(Type2Chip.ULTRALIGHT)
        tag.loseTagAfter(0)
        assertEquals(Outcome.Err(TagError.TagLost), Type2Identifier.identify(tag, UltralightType.ULTRALIGHT))
    }

    @Test
    fun withoutAnUltralightHintTheOldRuleHolds() = runTest {
        val vanished = FakeType2Chip(Type2Chip.ULTRALIGHT).also { it.loseTagAfter(0) }
        assertEquals(Outcome.Err(TagError.TagLost), Type2Identifier.identify(vanished))
        val plainIoError = FakeType2Chip(Type2Chip.ULTRALIGHT).also { it.failNext(TagError.Io("x")) }
        assertEquals(Outcome.Ok(null), Type2Identifier.identify(plainIoError))
    }

    @Test
    fun ntag215IsStillIdentifiedByGetVersionWithoutAnyReconnect() = runTest {
        val result = Type2Identifier.identify(FakeNtag21x(NtagModel.NTAG215), UltralightType.ULTRALIGHT)
        assertEquals(Outcome.Ok(Type2Identity.Ntag(NtagModel.NTAG215)), result)
    }
}
