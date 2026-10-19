package com.tagforge.domain.tools

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.ndef.Type2Tlv
import com.tagforge.domain.ntag.NtagCommands
import com.tagforge.domain.type2.Type2Layout
import com.tagforge.domain.write.PageWrite

/**
 * Decides which pages to write, and in which order, to turn the NDEF area of an NTAG21x into "empty NDEF, nothing left
 * behind" while keeping the Lock/Memory Control TLVs in front of it:
 *
 *  1. the page holding the NDEF TLV length is written first (`03 00 FE`): from then on the tag reads as empty,
 *  2. every other page that still holds stale bytes is zeroed, lowest page first.
 *
 * An interruption after step 1 therefore leaves a valid empty tag; before it, the old message untouched. Pure: no I/O.
 */
internal object Type2ErasePlanner {

    private const val PAGE = NtagCommands.PAGE_SIZE

    /**
     * @param current the user area as it is now (page 4 onwards)
     * @param leading Lock/Memory Control TLVs kept in front of the empty NDEF TLV
     * @param wipeEnd byte offset (exclusive) up to which stale bytes of the old content are zeroed
     */
    fun plan(model: Type2Layout, current: Bytes, leading: Bytes, wipeEnd: Int): List<PageWrite> {
        val framed = Type2Tlv.wrap(Bytes.EMPTY, leading)
        val end = maxOf(framed.size, minOf(wipeEnd, current.size))
        val pageCount = minOf((end + PAGE - 1) / PAGE, current.size / PAGE)

        val image = ByteArray(pageCount * PAGE) { i ->
            when {
                i < framed.size -> framed[i].toByte()
                i < wipeEnd -> 0
                else -> current[i].toByte()
            }
        }

        fun page(source: ByteArray, index: Int) = Bytes.copyOf(source.copyOfRange(index * PAGE, (index + 1) * PAGE))
        val changed = (0 until pageCount).filter { page(image, it) != current.slice(it * PAGE, (it + 1) * PAGE) }

        val lengthPage = (leading.size + 1) / PAGE
        val ordered = changed.filter { it == lengthPage } + changed.filter { it != lengthPage }
        return ordered.map { PageWrite(model.userStart + it, page(image, it)) }
    }
}
