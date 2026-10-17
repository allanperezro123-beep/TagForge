package com.tagforge.domain.ndef

import com.tagforge.domain.common.Bytes

data class NdefMessage(val records: List<NdefRecord>) {
    val isEmpty: Boolean get() = records.isEmpty()

    companion object {
        val EMPTY = NdefMessage(emptyList())

        /**
         * The canonical "nothing here" message: ONE record with TNF 0 and no content. Platform write APIs reject a
         * message with zero records, so this is what erases or formats a tag on the generic (non-raw) path.
         */
        val BLANK = NdefMessage(listOf(NdefRecord(Tnf.EMPTY, Bytes.EMPTY, payload = Bytes.EMPTY)))
    }
}
