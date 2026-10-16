package com.tagforge.domain.ntag

import com.tagforge.domain.common.Bytes

/** NTAG21x command frames (datasheet Rev. 3.2, section 9). Pure builders; no I/O. */
object NtagCommands {
    const val GET_VERSION = 0x60
    const val READ = 0x30
    const val FAST_READ = 0x3A
    const val WRITE = 0xA2

    const val PAGE_SIZE = 4
    const val READ_RESPONSE_SIZE = 16

    fun getVersion(): Bytes = Bytes.of(GET_VERSION)

    /** READ returns 16 bytes (4 pages) and rolls over to page 0 past the end of memory. */
    fun read(page: Int): Bytes {
        requirePage(page)
        return Bytes.of(READ, page)
    }

    fun fastRead(startPage: Int, endPage: Int): Bytes {
        requirePage(startPage)
        requirePage(endPage)
        require(startPage <= endPage) { "FAST_READ start $startPage is after end $endPage" }
        return Bytes.of(FAST_READ, startPage, endPage)
    }

    fun write(page: Int, data: Bytes): Bytes {
        requirePage(page)
        require(data.size == PAGE_SIZE) { "WRITE needs exactly $PAGE_SIZE bytes but got ${data.size}" }
        return Bytes.of(WRITE, page) + data
    }

    private fun requirePage(page: Int) = require(page in 0..0xFF) { "Page address out of range: $page" }
}
