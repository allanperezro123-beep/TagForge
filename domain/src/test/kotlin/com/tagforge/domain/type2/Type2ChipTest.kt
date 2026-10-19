package com.tagforge.domain.type2

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.ntag.Region
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Memory maps pinned to the NXP datasheets (MF0ICU1, MF0ICU2, MF0ULX1 Rev. 3.3, NTAG210/212 Rev. 3.0). */
class Type2ChipTest {

    @Test
    fun plainUltralightHas16PagesAnd48UserBytesAndNoFastRead() {
        val chip = Type2Chip.ULTRALIGHT
        assertEquals(16, chip.totalPages)
        assertEquals(4..15, chip.userStart..chip.userEnd)
        assertEquals(48, chip.ccNdefSize)
        assertFalse(chip.supportsFastRead)
    }

    @Test
    fun ultralightCHas48PagesAnd144UserBytesAndNoFastRead() {
        val chip = Type2Chip.ULTRALIGHT_C
        assertEquals(48, chip.totalPages)
        assertEquals(39, chip.userEnd)
        assertEquals(144, chip.ccNdefSize)
        assertFalse(chip.supportsFastRead)
    }

    @Test
    fun ultralightC3desKeyPagesAreHiddenByTheChip() {
        val chip = Type2Chip.ULTRALIGHT_C
        assertEquals(listOf(Region.DYN_LOCK), chip.regionsOf(40))
        assertEquals(listOf(Region.COUNTER), chip.regionsOf(41))
        assertEquals(listOf(Region.AUTH_CONFIG), chip.regionsOf(42))
        assertTrue((44..47).all { chip.regionsOf(it).single().hiddenByChip })
        assertEquals(emptyList(), chip.regionsOf(48))
    }

    @Test
    fun ultralightEv1ConfigurationPagesMatchTheDatasheet() {
        val small = Type2Chip.ULTRALIGHT_EV1_11
        assertEquals(20, small.totalPages)
        assertEquals(listOf(Region.CFG0), small.regionsOf(16))
        assertEquals(listOf(Region.PWD), small.regionsOf(18))
        assertEquals(listOf(Region.PACK), small.regionsOf(19))

        val big = Type2Chip.ULTRALIGHT_EV1_21
        assertEquals(41, big.totalPages)
        assertEquals(35, big.userEnd)
        assertEquals(listOf(Region.DYN_LOCK), big.regionsOf(36))
        assertEquals(listOf(Region.CFG0), big.regionsOf(37))
        assertEquals(listOf(Region.PWD), big.regionsOf(39))
        assertEquals(listOf(Region.PACK), big.regionsOf(40))
        assertTrue(big.supportsFastRead)
    }

    @Test
    fun ntag212ShipsWithTheLockControlTlvThatMustBePreserved() {
        assertEquals(Bytes.of(0x01, 0x03, 0x90, 0x0A, 0x34), Type2Chip.NTAG212.factoryLockControlTlv)
        assertEquals(128, Type2Chip.NTAG212.ccNdefSize)
        assertNull(Type2Chip.NTAG210.factoryLockControlTlv)
        assertEquals(48, Type2Chip.NTAG210.ccNdefSize)
    }

    @Test
    fun pageThreeIsTheCapabilityContainerAndUidPagesComeFirst() {
        Type2Chip.entries.forEach { chip ->
            assertEquals(listOf(Region.UID), chip.regionsOf(0))
            assertEquals(listOf(Region.UID, Region.STATIC_LOCK), chip.regionsOf(2))
            assertEquals(listOf(Region.CC), chip.regionsOf(3))
            assertEquals(listOf(Region.USER), chip.regionsOf(4))
        }
    }

    @Test
    fun getVersionBytesIdentifyTheChipByProductTypeAndStorageSize() {
        assertEquals(Type2Chip.ULTRALIGHT_EV1_11, Type2Chip.fromVersion(0x03, 0x0B))
        assertEquals(Type2Chip.ULTRALIGHT_EV1_21, Type2Chip.fromVersion(0x03, 0x0E))
        assertEquals(Type2Chip.NTAG210, Type2Chip.fromVersion(0x04, 0x0B))
        assertEquals(Type2Chip.NTAG212, Type2Chip.fromVersion(0x04, 0x0E))
        assertNull(Type2Chip.fromVersion(0x04, 0x0F))
    }

    @Test
    fun ntag21xModelsAreType2LayoutsToo() {
        val layout: Type2Layout = NtagModel.NTAG215
        assertTrue(layout.supportsFastRead)
        assertFalse(layout.trustsCcSize)
        assertEquals(496, layout.ccNdefSize)
    }
}
