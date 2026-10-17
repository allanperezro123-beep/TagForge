package com.tagforge.domain.write

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.ndef.Type2Tlv
import com.tagforge.domain.ntag.NtagCommands
import com.tagforge.domain.ntag.NtagModel

/** One WRITE command: an absolute page number and its four bytes. */
data class PageWrite(val page: Int, val data: Bytes)

/**
 * Decides which pages to write, and in which order, so an interrupted write can never leave a TLV that declares more
 * bytes than are present (TW-03):
 *
 *  1. the page(s) holding the NDEF TLV header are rewritten with a LENGTH OF ZERO (the tag now reads as empty),
 *  2. the body pages are written,
 *  3. the header page(s) are rewritten with the real length, last.
 *
 * Pure: no I/O, so the ordering is testable without a tag.
 */
object Type2WritePlanner {

    private const val TERMINATOR = 0xFE
    private const val MAX_SHORT_LENGTH = 254
    private const val LONG_LENGTH_SIZE = 3
    private const val PAGE = NtagCommands.PAGE_SIZE

    /**
     * @param current the user area as it is now (page 4 onwards), used to skip pages that already match
     * @param message encoded NDEF message
     * @param leading Lock/Memory Control TLVs that must stay in front of the NDEF TLV
     * @return the ordered writes, or null when the framed message does not fit the tag's NDEF area
     */
    fun plan(model: NtagModel, current: Bytes, message: Bytes, leading: Bytes): List<PageWrite>? {
        var framed = Type2Tlv.wrap(message, leading)
        val area = model.ccNdefSize
        if (framed.size > area) {
            // The terminator TLV is optional when the message fills the area exactly (NFC Forum Type 2 TLV rules).
            if (framed.size - 1 == area && framed[framed.size - 1] == TERMINATOR) framed = framed.slice(0, framed.size - 1) else return null
        }

        val pageCount = (framed.size + PAGE - 1) / PAGE
        val image = ByteArray(pageCount * PAGE)
        framed.toByteArray().copyInto(image)
        current.toByteArray().copyInto(image, destinationOffset = framed.size, startIndex = framed.size, endIndex = image.size)

        val lengthSize = if (message.size <= MAX_SHORT_LENGTH) 1 else LONG_LENGTH_SIZE
        val headerPages = (leading.size / PAGE)..((leading.size + lengthSize) / PAGE)

        val cleared = image.copyOf().also { it[leading.size + 1] = 0 }

        fun page(source: ByteArray, index: Int) = Bytes.copyOf(source.copyOfRange(index * PAGE, (index + 1) * PAGE))
        fun existing(index: Int) = current.slice(index * PAGE, (index + 1) * PAGE)
        fun write(index: Int, data: Bytes) = PageWrite(model.userStart + index, data)

        val steps = ArrayList<PageWrite>()
        for (p in headerPages) page(cleared, p).let { if (it != existing(p)) steps += write(p, it) }
        for (p in 0 until pageCount) {
            if (p in headerPages) continue
            page(image, p).let { if (it != existing(p)) steps += write(p, it) }
        }
        for (p in headerPages) page(image, p).let { if (it != page(cleared, p)) steps += write(p, it) }
        return steps
    }
}
