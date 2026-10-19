package com.tagforge.app.infrastructure.nfc

import android.nfc.FormatException
import android.nfc.TagLostException
import com.tagforge.domain.common.Bytes
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.tag.TagError
import com.tagforge.domain.tag.TagFamily
import com.tagforge.domain.tag.TagTech
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pure helpers behind the Android adapters (TS-03, rules G3/G4). Anything touching `Tag` itself is device-verified. */
class TagInfoExtractorTest {

    // ---- manufacturer ---------------------------------------------------------------------------

    @Test
    fun uidPrefixZeroFourIsNxp() {
        assertEquals("NXP", TagInfoExtractor.manufacturerFor(Bytes.of(0x04, 0xA2, 0x3B, 0x1C, 0x5D, 0x6E, 0x80)))
    }

    @Test
    fun otherKnownPrefixesAreNamedAndUnknownOnesAreNull() {
        assertEquals("STMicroelectronics", TagInfoExtractor.manufacturerFor(Bytes.of(0x02, 1, 2, 3, 4, 5, 6)))
        assertNull(TagInfoExtractor.manufacturerFor(Bytes.of(0x99, 1, 2, 3, 4, 5, 6)))
    }

    @Test
    fun randomUidsAndEmptyUidsHaveNoManufacturer() {
        assertNull(TagInfoExtractor.manufacturerFor(Bytes.of(0x08, 0x11, 0x22, 0x33)))   // 08 = random UID marker
        assertNull(TagInfoExtractor.manufacturerFor(Bytes.EMPTY))
    }

    @Test
    fun prefixZeroFourIsNxpForAnyUidLength() {
        assertEquals("NXP", TagInfoExtractor.manufacturerFor(Bytes.of(0x04, 0x11, 0x22, 0x33)))
    }

    // ---- ATQA / SAK -----------------------------------------------------------------------------

    @Test
    fun atqaAndSakAreFormattedAsHex() {
        assertEquals("0x0044", TagInfoExtractor.formatAtqa(Bytes.of(0x00, 0x44)))
        assertEquals("0x0004", TagInfoExtractor.formatAtqa(Bytes.of(0x00, 0x04)))
        assertEquals("0x00", TagInfoExtractor.formatSak(0x00))
        assertEquals("0x20", TagInfoExtractor.formatSak(0x20))
    }

    // ---- technologies and family ----------------------------------------------------------------

    @Test
    fun androidTechClassNamesMapToDomainTechs() {
        val expected = mapOf(
            "android.nfc.tech.NfcA" to TagTech.NFC_A,
            "android.nfc.tech.NfcB" to TagTech.NFC_B,
            "android.nfc.tech.NfcF" to TagTech.NFC_F,
            "android.nfc.tech.NfcV" to TagTech.NFC_V,
            "android.nfc.tech.IsoDep" to TagTech.ISO_DEP,
            "android.nfc.tech.Ndef" to TagTech.NDEF,
            "android.nfc.tech.NdefFormatable" to TagTech.NDEF_FORMATABLE,
            "android.nfc.tech.MifareClassic" to TagTech.MIFARE_CLASSIC,
            "android.nfc.tech.MifareUltralight" to TagTech.MIFARE_ULTRALIGHT,
        )
        for ((className, tech) in expected) assertEquals(tech, TagInfoExtractor.techFor(className), className)
        assertNull(TagInfoExtractor.techFor("android.nfc.tech.NfcBarcode"))
    }

    @Test
    fun familyFollowsTheMostSpecificTechnology() {
        assertEquals(TagFamily.NTAG21X, TagInfoExtractor.familyFor(setOf(TagTech.NFC_A), NtagModel.NTAG215))
        assertEquals(TagFamily.MIFARE_CLASSIC, TagInfoExtractor.familyFor(setOf(TagTech.NFC_A, TagTech.MIFARE_CLASSIC), null))
        assertEquals(TagFamily.ULTRALIGHT, TagInfoExtractor.familyFor(setOf(TagTech.NFC_A, TagTech.MIFARE_ULTRALIGHT), null))
        assertEquals(TagFamily.ISO14443_4, TagInfoExtractor.familyFor(setOf(TagTech.NFC_A, TagTech.ISO_DEP), null))
        assertEquals(TagFamily.ISO15693, TagInfoExtractor.familyFor(setOf(TagTech.NFC_V), null))
        assertEquals(TagFamily.FELICA, TagInfoExtractor.familyFor(setOf(TagTech.NFC_F), null))
        assertEquals(TagFamily.UNKNOWN, TagInfoExtractor.familyFor(setOf(TagTech.NFC_A), null))
    }

    // ---- MIFARE Classic on phones without the NXP controller ---------------------------------------

    @Test
    fun classicSaksAreRecognisedAndOtherNfcASaksAreNot() {
        for (sak in listOf(0x08, 0x09, 0x18, 0x88, 0x98)) assertTrue(TagInfoExtractor.isMifareClassicSak(sak), "SAK ${TagInfoExtractor.formatSak(sak)}")
        for (sak in listOf(0x00, 0x04, 0x20, 0x60)) assertFalse(TagInfoExtractor.isMifareClassicSak(sak), "SAK ${TagInfoExtractor.formatSak(sak)}")
    }

    @Test
    fun anNfcAOnlyTagWithAClassicSakIsStillFlaggedAsClassicSoItCanBeExplained() {
        assertEquals(TagFamily.MIFARE_CLASSIC, TagInfoExtractor.familyFor(setOf(TagTech.NFC_A), null, sak = 0x08))
        assertEquals(TagFamily.UNKNOWN, TagInfoExtractor.familyFor(setOf(TagTech.NFC_A), null, sak = 0x00))
        assertEquals(TagFamily.UNKNOWN, TagInfoExtractor.familyFor(setOf(TagTech.NFC_A), null, sak = null))
    }

    @Test
    fun moreSpecificTechnologiesBeatTheSakGuess() {
        assertEquals(TagFamily.ULTRALIGHT, TagInfoExtractor.familyFor(setOf(TagTech.NFC_A, TagTech.MIFARE_ULTRALIGHT), null, sak = 0x08))
        assertEquals(TagFamily.ISO14443_4, TagInfoExtractor.familyFor(setOf(TagTech.NFC_A, TagTech.ISO_DEP), null, sak = 0x08))
        assertEquals(TagFamily.NTAG21X, TagInfoExtractor.familyFor(setOf(TagTech.NFC_A), NtagModel.NTAG213, sak = 0x08))
    }

    // ---- exception mapping (G3 / G4) --------------------------------------------------------------

    @Test
    fun tagLostExceptionMapsToTagLostEvenThoughItIsAnIoException() {
        assertEquals(TagError.TagLost, TagErrorMapper.map(TagLostException("gone")))
    }

    @Test
    fun otherIoExceptionsMapToIoWithTheirMessage() {
        assertEquals(TagError.Io("IOException: Transceive failed"), TagErrorMapper.map(IOException("Transceive failed")))
        assertEquals(TagError.Io("IOException"), TagErrorMapper.map(IOException()))
    }

    @Test
    fun formatExceptionMapsToMalformed() {
        // android.jar stubs drop the constructor message, so only the mapped type is asserted here
        assertIs<TagError.Malformed>(TagErrorMapper.map(FormatException("bad NDEF")))
    }

    @Test
    fun outdatedTagSecurityExceptionIsReportedAsAStaleHandleNotAsALostTag() {
        // the user still retries, but a field report can now tell it apart from a real TagLostException
        assertEquals(TagError.Io("SecurityException: Tag is out of date"), TagErrorMapper.map(SecurityException("Tag is out of date")))
    }

    @Test
    fun unexpectedExceptionsBecomeIoNeverACrash() {
        assertEquals(TagError.Io("IllegalStateException: boom"), TagErrorMapper.map(IllegalStateException("boom")))
    }

    @Test
    fun type2ChipsAndIso15693FamiliesAreRecognisedAndNamed() {
        assertEquals(TagFamily.ULTRALIGHT, TagInfoExtractor.familyFor(setOf(TagTech.NFC_A), null, type2 = com.tagforge.domain.type2.Type2Chip.ULTRALIGHT_EV1_21))
        assertEquals(TagFamily.NTAG21X, TagInfoExtractor.familyFor(setOf(TagTech.NFC_A), null, type2 = com.tagforge.domain.type2.Type2Chip.NTAG212))
        assertEquals(TagFamily.ISO15693, TagInfoExtractor.familyFor(setOf(TagTech.NFC_V), null))
    }

    @Test
    fun anIso15693ManufacturerIsReadFromItsOwnUidLayout() {
        val icode = com.tagforge.domain.tag.TagInfo(com.tagforge.domain.fakes.FakeIso15693.ICODE_UID, setOf(TagTech.NFC_V), TagFamily.ISO15693)
        assertEquals("NXP", TagInfoExtractor.manufacturerFor(icode))
        val st = icode.copy(uid = com.tagforge.domain.fakes.FakeIso15693.ST_UID)
        assertEquals("STMicroelectronics", TagInfoExtractor.manufacturerFor(st))
        val nfcA = com.tagforge.domain.tag.TagInfo(Bytes.of(0x04, 1, 2, 3, 4, 5, 6), setOf(TagTech.NFC_A), TagFamily.NTAG21X)
        assertEquals("NXP", TagInfoExtractor.manufacturerFor(nfcA))
    }
}
