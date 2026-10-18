package com.tagforge.app.infrastructure.persistence

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tagforge.app.feature.productivity.ShortcutNonce
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/** Holds the install's shortcut nonce (see [ShortcutNonce]). Kept out of AppSettings so it is never exported or shown. */
class ShortcutNonceStore(
    private val dataStore: DataStore<Preferences>,
    private val random: Random = SecureRandom().asKotlinRandom(),
) {
    /** The nonce if a shortcut was ever pinned; never creates one (so a stray link cannot trigger creation). */
    suspend fun peek(): String? =
        dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.first()[KEY]

    suspend fun getOrCreate(): String {
        var result = ""
        dataStore.edit { preferences ->
            result = preferences[KEY] ?: ShortcutNonce.generate(random).also { preferences[KEY] = it }
        }
        return result
    }

    private companion object {
        val KEY = stringPreferencesKey("shortcut_nonce")
    }
}
