package com.tagforge.domain.ntag

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.type2.Type2Chip

/**
 * Raw pages of a tag. [unreadable] is the page range the tag refused to give (password protection); pages before
 * it are present in [pages]. [model] is null when the chip is not a recognised NTAG21x.
 */
data class MemoryDump(
    val model: NtagModel?,
    val pages: List<Bytes>,
    val unreadable: IntRange?,
    /** The Ultralight-family / NTAG210/212 chip, when [model] is null because it is not an NTAG213/215/216. */
    val chip: Type2Chip? = null,
) {
    /** One `PP: HH HH HH HH |aaaa|` line per page. */
    val rows: List<String> get() = pages.mapIndexed { page, data -> formatRow(page, data) }

    /** The role of every dumped page; empty for tags that are not NTAG21x. */
    val annotations: List<PageAnnotation>
        get() = chip?.let { c -> (0 until minOf(pages.size, c.totalPages)).map { PageAnnotation(it, c.regionsOf(it)) } }
            ?: NtagMemoryMap.annotate(model, pages.size)

    /** True for PWD and PACK: they exist but the chip never reveals their content. */
    fun isHiddenByChip(page: Int): Boolean =
        (model?.let { m -> NtagMemoryMap.regionsOf(m, page) } ?: chip?.regionsOf(page).orEmpty()).any { it.hiddenByChip }

    /** The bytes to show for [page]: the real ones, except for pages the chip hides, which are shown as zeros. */
    fun displayBytes(page: Int): Bytes = if (isHiddenByChip(page)) HIDDEN else pages[page]

    companion object {
        private const val FIRST_PRINTABLE = 0x20
        private const val LAST_PRINTABLE = 0x7E
        private val HIDDEN = Bytes.of(0, 0, 0, 0)

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
