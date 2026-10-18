package com.tagforge.domain.ports

import com.tagforge.domain.exchange.ExchangeData

/** Everything export and import need from storage, as one unit so an import can be a single transaction. */
interface ExchangeStore {
    /** All history (oldest first or newest first, either is fine), templates and tasks. */
    suspend fun snapshot(): ExchangeData

    /**
     * Adds every entry of [data] whose id is not stored yet, ALL OR NOTHING: if anything fails, nothing is added and
     * the exception propagates. Entries whose id already exists are left untouched. Returns the ids actually added
     * per section so the caller can report imported vs skipped.
     */
    suspend fun addMissing(data: ExchangeData): ExchangeData
}
