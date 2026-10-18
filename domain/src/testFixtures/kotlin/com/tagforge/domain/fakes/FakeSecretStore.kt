package com.tagforge.domain.fakes

import com.tagforge.domain.ports.SecretStore

class FakeSecretStore : SecretStore {
    private val items = linkedMapOf<String, String>()

    val keys: List<String> get() = items.keys.toList()

    override suspend fun save(key: String, secret: String) {
        items[key] = secret
    }

    override suspend fun load(key: String): String? = items[key]

    override suspend fun delete(key: String) {
        items.remove(key)
    }
}
