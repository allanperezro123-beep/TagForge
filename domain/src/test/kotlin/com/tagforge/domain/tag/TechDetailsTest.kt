package com.tagforge.domain.tag

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.fakes.hex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** TT-05: per-technology info, mapped from Android constants and formatted without any raw write. */
class TechDetailsTest {

    private fun info(techs: Set<TagTech>, details: TechDetails = TechDetails(), atqa: Bytes? = null, sak: Int? = null, historical: Bytes? = null) =
        TagInfo(uid = "04 11 22 33".hex(), techs = techs, family = TagFamily.UNKNOWN, atqa = atqa, sak = sak, historicalBytes = historical, details = details)

    private fun TechDetails.entries(info: TagInfo, tech: TagTech) = sections(info).single { it.tech == tech }.entries.associate { it.key to it.value }

    // ---- Android constant mapping -------------------------------------------------------------------------------------

    @Test
    fun ultralightTypesMapFromAndroidCodes() {
        assertEquals(UltralightType.ULTRALIGHT, UltralightType.fromAndroid(1))
        assertEquals(UltralightType.ULTRALIGHT_C, UltralightType.fromAndroid(2))
        assertEquals(UltralightType.UNKNOWN, UltralightType.fromAndroid(-1))
        assertEquals(UltralightType.UNKNOWN, UltralightType.fromAndroid(99))
    }

    @Test
    fun classicTypesMapFromAndroidCodes() {
        assertEquals(ClassicType.CLASSIC, ClassicType.fromAndroid(0))
        assertEquals(ClassicType.PLUS, ClassicType.fromAndroid(1))
        assertEquals(ClassicType.PRO, ClassicType.fromAndroid(2))
        assertEquals(ClassicType.UNKNOWN, ClassicType.fromAndroid(-1))
    }

    // ---- sections ---------------------------------------------------------------------------------------------------------

    @Test
    fun anIsoDepCardShowsItsHistoricalBytesInHex() {
        val details = TechDetails(isoDep = IsoDepDetails(hiLayerResponse = null, extendedLengthSupported = true, timeoutMs = 618))
        val tag = info(setOf(TagTech.NFC_A, TagTech.ISO_DEP), details, atqa = "03 44".hex(), sak = 0x20, historical = "78 77 80 02".hex())

        val entries = details.entries(tag, TagTech.ISO_DEP)

        assertEquals(TechValue.Text("78 77 80 02"), entries[DetailKey.HISTORICAL_BYTES])
        assertEquals(TechValue.Unknown, entries[DetailKey.HI_LAYER_RESPONSE])
        assertEquals(TechValue.Flag(true), entries[DetailKey.EXTENDED_LENGTH])
        assertEquals(TechValue.Text("618"), entries[DetailKey.TIMEOUT_MS])
    }

    @Test
    fun hiLayerResponseIsShownWhenPresentAndEmptyHistoricalBytesAreUnknown() {
        val details = TechDetails(isoDep = IsoDepDetails(hiLayerResponse = "80 70".hex(), extendedLengthSupported = false, timeoutMs = null))
        val tag = info(setOf(TagTech.ISO_DEP), details, historical = Bytes.EMPTY)

        val entries = details.entries(tag, TagTech.ISO_DEP)

        assertEquals(TechValue.Text("80 70"), entries[DetailKey.HI_LAYER_RESPONSE])
        assertEquals(TechValue.Unknown, entries[DetailKey.HISTORICAL_BYTES])
        assertEquals(TechValue.Flag(false), entries[DetailKey.EXTENDED_LENGTH])
        assertEquals(TechValue.Unknown, entries[DetailKey.TIMEOUT_MS])
    }

    @Test
    fun nfcAShowsAtqaAndSak() {
        val tag = info(setOf(TagTech.NFC_A), atqa = "00 44".hex(), sak = 0x08)

        val entries = TechDetails().entries(tag, TagTech.NFC_A)

        assertEquals(TechValue.Text("00 44"), entries[DetailKey.ATQA])
        assertEquals(TechValue.Text("0x08"), entries[DetailKey.SAK])
    }

    @Test
    fun nfcBNfcFAndNfcVShowTheirDiscoveryData() {
        val details = TechDetails(
            nfcB = NfcBDetails(applicationData = "AA BB CC DD".hex(), protocolInfo = "81 71 71".hex()),
            nfcF = NfcFDetails(systemCode = "88 B4".hex(), manufacturer = "01 02 03 04 05 06 07 08".hex()),
            nfcV = NfcVDetails(dsfId = 0x0A, responseFlags = 0x00),
        )
        val tag = info(setOf(TagTech.NFC_B, TagTech.NFC_F, TagTech.NFC_V), details)

        assertEquals(mapOf(DetailKey.APPLICATION_DATA to TechValue.Text("AA BB CC DD"), DetailKey.PROTOCOL_INFO to TechValue.Text("81 71 71")), details.entries(tag, TagTech.NFC_B))
        assertEquals(mapOf(DetailKey.SYSTEM_CODE to TechValue.Text("88 B4"), DetailKey.MANUFACTURER to TechValue.Text("01 02 03 04 05 06 07 08")), details.entries(tag, TagTech.NFC_F))
        assertEquals(mapOf(DetailKey.DSF_ID to TechValue.Text("0x0A"), DetailKey.RESPONSE_FLAGS to TechValue.Text("0x00")), details.entries(tag, TagTech.NFC_V))
    }

    @Test
    fun ultralightShowsTypeAndPageCount() {
        val ultralightC = TechDetails(ultralight = UltralightDetails(UltralightType.ULTRALIGHT_C))
        val plain = TechDetails(ultralight = UltralightDetails(UltralightType.ULTRALIGHT))
        val unknown = TechDetails(ultralight = UltralightDetails(UltralightType.UNKNOWN))

        assertEquals(mapOf(DetailKey.ULTRALIGHT_TYPE to TechValue.Text("Ultralight C"), DetailKey.PAGES to TechValue.Text("48")), ultralightC.entries(info(setOf(TagTech.MIFARE_ULTRALIGHT), ultralightC), TagTech.MIFARE_ULTRALIGHT))
        assertEquals(mapOf(DetailKey.ULTRALIGHT_TYPE to TechValue.Text("Ultralight"), DetailKey.PAGES to TechValue.Text("16")), plain.entries(info(setOf(TagTech.MIFARE_ULTRALIGHT), plain), TagTech.MIFARE_ULTRALIGHT))
        assertEquals(mapOf(DetailKey.ULTRALIGHT_TYPE to TechValue.Unknown, DetailKey.PAGES to TechValue.Unknown), unknown.entries(info(setOf(TagTech.MIFARE_ULTRALIGHT), unknown), TagTech.MIFARE_ULTRALIGHT))
    }

    @Test
    fun classicShowsTypeSectorsBlocksAndSize() {
        val oneK = TechDetails(classic = ClassicDetails(ClassicType.CLASSIC, sectorCount = 16, blockCount = 64, sizeBytes = 1024))
        val mini = TechDetails(classic = ClassicDetails(ClassicType.PLUS, sectorCount = 5, blockCount = 20, sizeBytes = 320))

        assertEquals(
            mapOf(
                DetailKey.CLASSIC_TYPE to TechValue.Text("Classic"),
                DetailKey.SECTORS to TechValue.Text("16"),
                DetailKey.BLOCKS to TechValue.Text("64"),
                DetailKey.SIZE_BYTES to TechValue.Text("1024"),
            ),
            oneK.entries(info(setOf(TagTech.MIFARE_CLASSIC), oneK), TagTech.MIFARE_CLASSIC),
        )
        assertEquals(TechValue.Text("320"), mini.entries(info(setOf(TagTech.MIFARE_CLASSIC), mini), TagTech.MIFARE_CLASSIC)[DetailKey.SIZE_BYTES])
        assertEquals(TechValue.Text("Plus"), mini.entries(info(setOf(TagTech.MIFARE_CLASSIC), mini), TagTech.MIFARE_CLASSIC)[DetailKey.CLASSIC_TYPE])
    }

    @Test
    fun sectionsFollowAFixedOrderAndSkipTechnologiesTheTagLacks() {
        val details = TechDetails(
            isoDep = IsoDepDetails(null, false, null),
            nfcB = NfcBDetails("00".hex(), "00".hex()),
        )
        val tag = info(setOf(TagTech.ISO_DEP, TagTech.NFC_A, TagTech.NFC_B, TagTech.NDEF), details, atqa = "03 44".hex(), sak = 0x20)

        assertEquals(listOf(TagTech.NFC_A, TagTech.NFC_B, TagTech.ISO_DEP), details.sections(tag).map { it.tech })
    }

    @Test
    fun aTechnologyPresentWithoutDetailsIsOmittedRatherThanShownEmpty() {
        val tag = info(setOf(TagTech.NFC_V), TechDetails())   // the OS gave us no NfcV object

        assertEquals(emptyList(), TechDetails().sections(tag))
        assertNull(TechDetails().nfcV)
    }
}
