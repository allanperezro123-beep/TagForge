package com.tagforge.domain.classic

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.common.TagResult
import com.tagforge.domain.ports.ClassicChannel
import com.tagforge.domain.ports.TagSession
import com.tagforge.domain.tag.TagError

/**
 * The ONLY door to a MIFARE Classic card for the rest of the domain. Reads and authentication pass straight through; a
 * write is refused for every sector trailer and for the manufacturer block, whatever the caller asked. A wrong trailer
 * can lock a sector for good, so this rule is code, not a convention: nothing but this class calls
 * [ClassicChannel.writeBlock] (a source scan in the tests keeps it that way).
 */
class SafeClassic(private val channel: ClassicChannel) {

    val layout: ClassicLayout get() = channel.layout

    suspend fun authenticate(sector: Int, key: Bytes, type: ClassicKeyType): TagResult<Boolean> = channel.authenticate(sector, key, type)

    suspend fun readBlock(block: Int): TagResult<Bytes> = channel.readBlock(block)

    suspend fun writeBlock(block: Int, data: Bytes): TagResult<Unit> {
        if (block !in 0 until layout.blockCount || data.size != ClassicLayout.BLOCK_SIZE) {
            return Outcome.Err(TagError.Malformed("Not a 16-byte MIFARE Classic block: $block"))
        }
        if (layout.isManufacturerBlock(block)) return Outcome.Err(TagError.Unsupported(TagError.Unsupported.CLASSIC_MANUFACTURER_BLOCK))
        if (layout.isTrailer(block)) return Outcome.Err(TagError.Unsupported(TagError.Unsupported.CLASSIC_TRAILER))
        return channel.writeBlock(block, data)
    }

    companion object {
        fun of(session: TagSession): SafeClassic? = session.classic()?.let(::SafeClassic)
    }
}

/** How a sector was opened: with which public key and as Key A or Key B. */
data class SectorAccess(val sector: Int, val key: Bytes, val type: ClassicKeyType)

/** Opens sectors with the public keys only. Remembers the last key that worked: cards usually use one for every sector. */
class SectorOpener(private val classic: SafeClassic) {

    private var lastWorking: Pair<Bytes, ClassicKeyType>? = null

    /** Ok(access) when a public key opened [sector]; Ok(null) when none did (a private key protects it). */
    suspend fun open(sector: Int): TagResult<SectorAccess?> {
        for ((key, type) in candidates(sector)) {
            when (val result = classic.authenticate(sector, key, type)) {
                is Outcome.Err -> return result
                is Outcome.Ok -> if (result.value) {
                    lastWorking = key to type
                    return Outcome.Ok(SectorAccess(sector, key, type))
                }
            }
        }
        return Outcome.Ok(null)
    }

    private fun candidates(sector: Int): List<Pair<Bytes, ClassicKeyType>> {
        val keys = if (sector == 0 || sector == MAD2_SECTOR) listOf(ClassicKeys.MAD) + ClassicKeys.ALL else ClassicKeys.ALL
        val ordered = ClassicKeyType.entries.flatMap { type -> keys.map { it to type } }.distinct()
        return lastWorking?.let { first -> listOf(first) + ordered.filter { it != first } } ?: ordered
    }

    private companion object {
        const val MAD2_SECTOR = 16
    }
}
