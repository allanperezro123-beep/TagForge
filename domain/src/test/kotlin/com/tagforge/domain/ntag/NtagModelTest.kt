package com.tagforge.domain.ntag

import com.tagforge.domain.common.Bytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Every row of the verified datasheet table (NXP NTAG213/215/216 Rev. 3.2, spec "Verified against datasheet"). */
class NtagModelTest {

    @Test
    fun ntag213MatchesDatasheet() = with(NtagModel.NTAG213) {
        assertEquals(45, totalPages)
        assertEquals(0x2C, lastPage)
        assertEquals(4, userStart)
        assertEquals(39, userEnd)
        assertEquals(144, userBytes)
        assertEquals(144, ccNdefSize)
        assertEquals(Bytes.of(0xE1, 0x10, 0x12, 0x00), capabilityContainer)
        assertEquals(40, dynLock)
        assertEquals(41, cfg0)
        assertEquals(42, cfg1)
        assertEquals(43, pwd)
        assertEquals(44, pack)
        assertEquals(0x0F, versionSize)
        assertEquals(2, dynamicLockGranularityPages)
        assertEquals("NTAG213", displayName)
    }

    @Test
    fun ntag215MatchesDatasheet() = with(NtagModel.NTAG215) {
        assertEquals(135, totalPages)
        assertEquals(0x86, lastPage)
        assertEquals(4, userStart)
        assertEquals(129, userEnd)
        assertEquals(504, userBytes)
        assertEquals(496, ccNdefSize)
        assertEquals(Bytes.of(0xE1, 0x10, 0x3E, 0x00), capabilityContainer)
        assertEquals(130, dynLock)
        assertEquals(131, cfg0)
        assertEquals(132, cfg1)
        assertEquals(133, pwd)
        assertEquals(134, pack)
        assertEquals(0x11, versionSize)
        assertEquals(16, dynamicLockGranularityPages)
    }

    @Test
    fun ntag216MatchesDatasheet() = with(NtagModel.NTAG216) {
        assertEquals(231, totalPages)
        assertEquals(0xE6, lastPage)
        assertEquals(4, userStart)
        assertEquals(225, userEnd)
        assertEquals(888, userBytes)
        assertEquals(872, ccNdefSize)
        assertEquals(Bytes.of(0xE1, 0x10, 0x6D, 0x00), capabilityContainer)
        assertEquals(226, dynLock)
        assertEquals(227, cfg0)
        assertEquals(228, cfg1)
        assertEquals(229, pwd)
        assertEquals(230, pack)
        assertEquals(0x13, versionSize)
        assertEquals(16, dynamicLockGranularityPages)
    }

    @Test
    fun ccNdefSizeIsNotTheUserMemorySize() {
        // Reconciliation #1: the budget must use the CC value (byte 2 * 8), never user memory.
        for (model in NtagModel.entries) {
            assertEquals(model.capabilityContainer[2] * 8, model.ccNdefSize)
        }
        assertEquals(144, NtagModel.NTAG213.userBytes)
        assertEquals(504 - 496, NtagModel.NTAG215.userBytes - NtagModel.NTAG215.ccNdefSize)
        assertEquals(888 - 872, NtagModel.NTAG216.userBytes - NtagModel.NTAG216.ccNdefSize)
    }

    @Test
    fun onlyNtag213ShipsALockControlTlv() {
        assertEquals(Bytes.of(0x01, 0x03, 0xA0, 0x0C, 0x34), NtagModel.NTAG213.factoryLockControlTlv)
        assertNull(NtagModel.NTAG215.factoryLockControlTlv)
        assertNull(NtagModel.NTAG216.factoryLockControlTlv)
    }

    @Test
    fun factoryUserPagesMatchTheDatasheetForEachModel() {
        assertEquals(Bytes.of(0x01, 0x03, 0xA0, 0x0C, 0x34, 0x03, 0x00, 0xFE), NtagModel.NTAG213.factoryNdefArea)
        assertEquals(Bytes.of(0x03, 0x00, 0xFE, 0x00), NtagModel.NTAG215.factoryNdefArea)
        assertEquals(Bytes.of(0x03, 0x00, 0xFE, 0x00), NtagModel.NTAG216.factoryNdefArea)
    }

    @Test
    fun fromVersionSizeIdentifiesModelsAndRejectsUnknown() {
        assertEquals(NtagModel.NTAG213, NtagModel.fromVersionSize(0x0F))
        assertEquals(NtagModel.NTAG215, NtagModel.fromVersionSize(0x11))
        assertEquals(NtagModel.NTAG216, NtagModel.fromVersionSize(0x13))
        assertNull(NtagModel.fromVersionSize(0x12))
    }

    @Test
    fun pageRangesAreConsistent() {
        for (model in NtagModel.entries) {
            assertEquals(model.dynLock, model.userEnd + 1)
            assertEquals(model.totalPages - 1, model.pack)
            assertEquals((model.userEnd - model.userStart + 1) * 4, model.userBytes)
        }
    }
}
