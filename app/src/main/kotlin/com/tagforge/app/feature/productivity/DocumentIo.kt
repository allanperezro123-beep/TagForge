package com.tagforge.app.feature.productivity

/** What reading a user-picked document gave. */
sealed interface DocumentRead {
    class Bytes(val data: ByteArray) : DocumentRead
    data object TooLarge : DocumentRead
    data object Unreadable : DocumentRead
}

/**
 * Reads and writes files the user picked through the Storage Access Framework. [target]/[source] are `content://`
 * URI strings: the picker grants access to exactly that document, so the app needs no storage permission.
 */
interface DocumentIo {
    /** True when every byte was written. */
    suspend fun write(target: String, data: ByteArray): Boolean

    /** Never reads more than [maxBytes] + 1 bytes into memory. */
    suspend fun read(source: String, maxBytes: Int): DocumentRead
}
