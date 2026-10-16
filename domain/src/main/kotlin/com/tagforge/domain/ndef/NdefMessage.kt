package com.tagforge.domain.ndef

data class NdefMessage(val records: List<NdefRecord>) {
    val isEmpty: Boolean get() = records.isEmpty()

    companion object {
        val EMPTY = NdefMessage(emptyList())
    }
}
