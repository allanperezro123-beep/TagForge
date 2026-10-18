package com.tagforge.domain.ports

/**
 * Opt-in storage for a tag password (NS-02). The Android adapter encrypts with an AndroidKeystore key; the user chooses
 * per tag whether to keep one. Nothing else in the app persists a password.
 */
interface SecretStore {
    suspend fun save(key: String, secret: String)

    suspend fun load(key: String): String?

    suspend fun delete(key: String)
}
