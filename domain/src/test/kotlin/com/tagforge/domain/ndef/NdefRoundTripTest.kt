package com.tagforge.domain.ndef

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** NC-12 round-trip property: parse(encode(m)) == m and unwrap(wrap(b)) == b over seeded random messages. */
class NdefRoundTripTest {

    private val codec = DefaultNdefCodec
    private val payloadSizes = listOf(0, 1, 254, 255, 256, 1000)

    private fun randomBytes(random: Random, size: Int): Bytes = Bytes.copyOf(random.nextBytes(size))

    private fun randomRecord(random: Random): NdefRecord {
        // UNCHANGED (6) is only valid inside a chunk sequence, so builders never produce it.
        val tnf = listOf(Tnf.EMPTY, Tnf.WELL_KNOWN, Tnf.MIME, Tnf.ABSOLUTE_URI, Tnf.EXTERNAL, Tnf.UNKNOWN).random(random)
        val type = if (tnf == Tnf.EMPTY || tnf == Tnf.UNKNOWN) Bytes.EMPTY else randomBytes(random, random.nextInt(1, 40))
        val id = if (random.nextBoolean()) randomBytes(random, random.nextInt(1, 10)) else Bytes.EMPTY
        val payload = if (tnf == Tnf.EMPTY) Bytes.EMPTY else randomBytes(random, payloadSizes.random(random))
        return NdefRecord(tnf, type, id, payload)
    }

    private fun randomMessage(random: Random): NdefMessage =
        NdefMessage(List(random.nextInt(1, 6)) { randomRecord(random) })

    @Test
    fun parseOfEncodeIsIdentityForRandomMessages() {
        val random = Random(20260416)
        repeat(300) { iteration ->
            val message = randomMessage(random)
            val decoded = codec.decode(codec.encode(message))
            assertIs<Outcome.Ok<NdefMessage>>(decoded, "iteration $iteration decoded to $decoded")
            assertEquals(message, decoded.value, "iteration $iteration")
        }
    }

    @Test
    fun generatorCoversTheBoundarySizesAndBothRecordForms() {
        val random = Random(20260416)
        val sizes = mutableSetOf<Int>()
        var sawShort = false
        var sawLong = false
        repeat(300) {
            for (record in randomMessage(random).records) {
                sizes += record.payload.size
                if (record.payload.size <= 255) sawShort = true else sawLong = true
            }
        }
        assertEquals(payloadSizes.toSet(), sizes, "every boundary payload size must be generated")
        assertTrue(sawShort && sawLong)
    }

    @Test
    fun unwrapOfWrapIsIdentityAcrossLengths() {
        val random = Random(7)
        for (size in payloadSizes) {
            val body = randomBytes(random, size)
            val result = Type2Tlv.unwrap(Type2Tlv.wrap(body))
            assertIs<Outcome.Ok<Type2Tlv.Area>>(result)
            assertEquals(body, result.value.message, "size $size")
            assertEquals(0, result.value.leadingBytes)
        }
    }

    @Test
    fun fullPipelineMessageThroughTlvAndBackIsIdentity() {
        val random = Random(99)
        repeat(100) { iteration ->
            val message = randomMessage(random)
            val area = Type2Tlv.unwrap(Type2Tlv.wrap(codec.encode(message)))
            assertIs<Outcome.Ok<Type2Tlv.Area>>(area)
            val decoded = codec.decode(area.value.message)
            assertIs<Outcome.Ok<NdefMessage>>(decoded)
            assertEquals(message, decoded.value, "iteration $iteration")
        }
    }
}
