package com.tagforge.domain.fakes

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FakeSecretStoreTest {

    @Test
    fun savesLoadsAndDeletesBySeparateKeys() = runTest {
        val store = FakeSecretStore()
        store.save("04A1B2C3D4E5F6", "12345678")
        store.save("04A1B2C3D4E5F7", "AABBCCDD")

        assertEquals("12345678", store.load("04A1B2C3D4E5F6"))
        assertEquals(listOf("04A1B2C3D4E5F6", "04A1B2C3D4E5F7"), store.keys)

        store.delete("04A1B2C3D4E5F6")
        assertNull(store.load("04A1B2C3D4E5F6"))
        assertEquals("AABBCCDD", store.load("04A1B2C3D4E5F7"))
    }
}
