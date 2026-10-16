package com.tagforge.domain.ndef

/** NDEF Type Name Format. Code 7 is reserved and never produced; parsing it is an error. */
enum class Tnf(val code: Int) {
    EMPTY(0),
    WELL_KNOWN(1),
    MIME(2),
    ABSOLUTE_URI(3),
    EXTERNAL(4),
    UNKNOWN(5),
    UNCHANGED(6);

    companion object {
        fun fromCode(code: Int): Tnf? = entries.firstOrNull { it.code == code }
    }
}
