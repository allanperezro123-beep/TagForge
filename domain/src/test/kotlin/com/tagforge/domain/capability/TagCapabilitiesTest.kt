package com.tagforge.domain.capability

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.tag.TagFamily
import com.tagforge.domain.tag.TagInfo
import com.tagforge.domain.tag.TagTech
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TagCapabilitiesTest {

    private fun caps(family: TagFamily, vararg techs: TagTech, ntag: NtagModel? = null) =
        TagCapabilities.of(TagInfo(Bytes.of(1, 2, 3, 4), techs.toSet(), family, ntag))

    private fun channel(c: Capabilities, op: ToolOperation) = (c[op] as Support.Available).channel

    @Test
    fun ntag21xDoesEverythingOverRawNfcAAndNothingHops() {
        val c = caps(TagFamily.NTAG21X, TagTech.NFC_A, TagTech.MIFARE_ULTRALIGHT, TagTech.NDEF, ntag = NtagModel.NTAG215)
        ToolOperation.entries.forEach { assertEquals(AccessChannel.RAW_NFC_A, channel(c, it), "$it") }
        assertTrue(c.readIncludesDump)
    }

    @Test
    fun classicWithoutNxpChipIsExplainedForEveryOperation() {
        val c = caps(TagFamily.MIFARE_CLASSIC, TagTech.NFC_A)
        ToolOperation.entries.forEach { assertEquals(UnsupportedReason.CLASSIC_NEEDS_NXP_CHIP, c.reasonFor(it), "$it") }
    }

    @Test
    fun classicWithNxpChipDumpsAndClonesWithKeysAndUsesThePlatformForNdef() {
        val c = caps(TagFamily.MIFARE_CLASSIC, TagTech.NFC_A, TagTech.MIFARE_CLASSIC, TagTech.NDEF)
        assertEquals(AccessChannel.CLASSIC_KEYS, channel(c, ToolOperation.DUMP))
        assertEquals(AccessChannel.CLASSIC_KEYS, channel(c, ToolOperation.CLONE))
        assertEquals(AccessChannel.PLATFORM_NDEF, channel(c, ToolOperation.READ_NDEF))
        assertEquals(AccessChannel.PLATFORM_NDEF, channel(c, ToolOperation.ERASE))
        assertFalse(c.readIncludesDump)
    }

    @Test
    fun classicWithoutNdefStillDumpsButCannotReadNdef() {
        val c = caps(TagFamily.MIFARE_CLASSIC, TagTech.NFC_A, TagTech.MIFARE_CLASSIC, TagTech.NDEF_FORMATABLE)
        assertTrue(c.isSupported(ToolOperation.DUMP))
        assertEquals(UnsupportedReason.NO_NDEF, c.reasonFor(ToolOperation.READ_NDEF))
        assertTrue(c.isSupported(ToolOperation.FORMAT))
        assertTrue(c.isSupported(ToolOperation.WRITE_NDEF))
    }

    @Test
    fun iso15693DumpsOverNfcVAndKeepsNdefOnThePlatform() {
        val c = caps(TagFamily.ISO15693, TagTech.NFC_V, TagTech.NDEF)
        assertEquals(AccessChannel.RAW_NFC_V, channel(c, ToolOperation.DUMP))
        assertEquals(AccessChannel.PLATFORM_NDEF, channel(c, ToolOperation.READ_NDEF))
        assertEquals(AccessChannel.PLATFORM_NDEF, channel(c, ToolOperation.WRITE_NDEF))
        assertFalse(c.readIncludesDump)
    }

    @Test
    fun iso15693WithoutNdefIsDumpOnly() {
        val c = caps(TagFamily.ISO15693, TagTech.NFC_V)
        assertTrue(c.isSupported(ToolOperation.DUMP))
        assertEquals(UnsupportedReason.NO_NDEF, c.reasonFor(ToolOperation.READ_NDEF))
        assertEquals(UnsupportedReason.NO_NDEF, c.reasonFor(ToolOperation.ERASE))
    }

    @Test
    fun isoDepNeverDumpsBecauseThatNeedsAuthentication() {
        val c = caps(TagFamily.ISO14443_4, TagTech.NFC_A, TagTech.ISO_DEP, TagTech.NDEF)
        assertEquals(UnsupportedReason.NEEDS_AUTHENTICATION, c.reasonFor(ToolOperation.DUMP))
        assertEquals(AccessChannel.PLATFORM_NDEF, channel(c, ToolOperation.READ_NDEF))
        assertEquals(AccessChannel.PLATFORM_NDEF, channel(c, ToolOperation.CLONE))
    }

    @Test
    fun felicaAndNfcBAreInfoOnlyUnlessThePlatformExposesNdef() {
        listOf(caps(TagFamily.FELICA, TagTech.NFC_F), caps(TagFamily.UNKNOWN, TagTech.NFC_B)).forEach { c ->
            ToolOperation.entries.forEach { assertEquals(UnsupportedReason.INFO_ONLY, c.reasonFor(it), "$it") }
        }
        val type3 = caps(TagFamily.FELICA, TagTech.NFC_F, TagTech.NDEF)
        assertTrue(type3.isSupported(ToolOperation.READ_NDEF))
        assertEquals(UnsupportedReason.INFO_ONLY, type3.reasonFor(ToolOperation.DUMP))
    }

    @Test
    fun anyTagWithNdefGetsThePlatformFallbackAndFormatNeedsFormatable() {
        val ndef = caps(TagFamily.UNKNOWN, TagTech.NFC_A, TagTech.NDEF)
        assertEquals(AccessChannel.PLATFORM_NDEF, channel(ndef, ToolOperation.WRITE_NDEF))
        assertEquals(UnsupportedReason.NOT_FORMATABLE, ndef.reasonFor(ToolOperation.FORMAT))
        val blank = caps(TagFamily.UNKNOWN, TagTech.NFC_A, TagTech.NDEF_FORMATABLE)
        assertEquals(AccessChannel.PLATFORM_NDEF, channel(blank, ToolOperation.FORMAT))
        assertEquals(AccessChannel.PLATFORM_NDEF, channel(blank, ToolOperation.WRITE_NDEF))
        assertEquals(UnsupportedReason.NO_NDEF, blank.reasonFor(ToolOperation.READ_NDEF))
    }
}
