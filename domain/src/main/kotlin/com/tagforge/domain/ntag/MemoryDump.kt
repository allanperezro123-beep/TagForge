package com.tagforge.domain.ntag

import com.tagforge.domain.common.Bytes

/**
 * Raw pages of a tag. [unreadable] is the page range the tag refused to give (password protection); pages before
 * it are present in [pages]. [model] is null when the chip is not a recognised NTAG21x.
 */
data class MemoryDump(
    val model: NtagModel?,
    val pages: List<Bytes>,
    val unreadable: IntRange?,
) {
    /** One `PP: HH HH HH HH |aaaa|` line per page. */
    val rows: List<String> get() = pages.mapIndexed { page, data -> formatRow(page, data) }

    companion object {
        private const val FIRST_PRINTABLE = 0x20
        private const val LAST_PRINTABLE = 0x7E

        fun formatRow(page: Int, data: Bytes): String {
            val ascii = buildString {
                for (i in 0 until data.size) {
                    val value = data[i]
                    append(if (value in FIRST_PRINTABLE..LAST_PRINTABLE) value.toChar() else '.')
                }
            }
            return "%02X: %s |%s|".format(page, data.toHex(" "), ascii)
        }
    }
}
