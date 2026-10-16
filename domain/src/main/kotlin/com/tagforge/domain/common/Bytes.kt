package com.tagforge.domain.common

/**
 * Immutable byte sequence with content-based equality. Wraps a private copy so callers can never
 * mutate tag payloads after the fact. Indexing returns UNSIGNED values (0..255).
 */
class Bytes private constructor(private val data: ByteArray) {

    val size: Int get() = data.size

    fun isEmpty(): Boolean = data.isEmpty()

    /** Unsigned value (0..255) at [index]. */
    operator fun get(index: Int): Int = data[index].toInt() and 0xFF

    /** Defensive copy of the content. */
    fun toByteArray(): ByteArray = data.copyOf()

    /** Sub-range [from, until). Throws [IndexOutOfBoundsException] when the range is invalid. */
    fun slice(from: Int, until: Int = size): Bytes {
        if (from < 0 || until > size || from > until) {
            throw IndexOutOfBoundsException("slice($from, $until) out of range for size $size")
        }
        return Bytes(data.copyOfRange(from, until))
    }

    operator fun plus(other: Bytes): Bytes = Bytes(data + other.data)

    fun startsWith(prefix: Bytes): Boolean {
        if (prefix.size > size) return false
        for (i in 0 until prefix.size) if (data[i] != prefix.data[i]) return false
        return true
    }

    /** Index of the first occurrence of the unsigned [value], or -1. */
    fun indexOf(value: Int, from: Int = 0): Int {
        for (i in from until size) if (this[i] == value) return i
        return -1
    }

    /** Uppercase hex, [separator] between bytes (default none). */
    fun toHex(separator: String = ""): String {
        val sb = StringBuilder(size * (2 + separator.length))
        for (i in 0 until size) {
            if (i > 0) sb.append(separator)
            val v = this[i]
            sb.append(HEX_DIGITS[v ushr 4]).append(HEX_DIGITS[v and 0x0F])
        }
        return sb.toString()
    }

    override fun equals(other: Any?): Boolean = other is Bytes && data.contentEquals(other.data)

    override fun hashCode(): Int = data.contentHashCode()

    override fun toString(): String = "Bytes(${toHex(" ")})"

    companion object {
        private const val HEX_DIGITS = "0123456789ABCDEF"

        val EMPTY: Bytes = Bytes(ByteArray(0))

        /** Builds from unsigned values; every value MUST be in 0..255. */
        fun of(vararg values: Int): Bytes {
            val array = ByteArray(values.size)
            for (i in values.indices) {
                require(values[i] in 0..0xFF) { "Byte value out of range: ${values[i]}" }
                array[i] = values[i].toByte()
            }
            return Bytes(array)
        }

        fun copyOf(array: ByteArray): Bytes = Bytes(array.copyOf())

        fun concat(parts: List<Bytes>): Bytes {
            val out = ByteArray(parts.sumOf { it.size })
            var offset = 0
            for (part in parts) {
                part.data.copyInto(out, offset)
                offset += part.size
            }
            return Bytes(out)
        }
    }
}
