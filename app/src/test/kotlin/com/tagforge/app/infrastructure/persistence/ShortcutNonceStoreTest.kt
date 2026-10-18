package com.tagforge.app.infrastructure.persistence

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/** W4: one random secret per install, created on first use and kept, never derived from anything guessable. */
class ShortcutNonceStoreTest {

    private class InMemoryPreferences : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }

    @Test
    fun nothingIsIssuedUntilAShortcutIsPinned() = runBlocking {
        assertNull(ShortcutNonceStore(InMemoryPreferences()).peek())
    }

    @Test
    fun theNonceIsCreatedOnceAndSurvivesANewStoreOnTheSameFile() = runBlocking {
        val file = InMemoryPreferences()
        val first = ShortcutNonceStore(file).getOrCreate()

        assertEquals(64, first.length)
        assertEquals(first, ShortcutNonceStore(file).getOrCreate())
        assertEquals(first, ShortcutNonceStore(file).peek())
    }

    @Test
    fun everyInstallGetsItsOwnNonce() = runBlocking {
        val a = ShortcutNonceStore(InMemoryPreferences()).getOrCreate()
        val b = ShortcutNonceStore(InMemoryPreferences()).getOrCreate()

        assertNotEquals(a, b)
    }
}
