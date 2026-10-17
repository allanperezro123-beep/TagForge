package com.tagforge.domain.ndef.spec

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.fakes.hex
import com.tagforge.domain.ndef.DefaultNdefCodec
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.NdefRecord
import com.tagforge.domain.ndef.Tnf
import kotlin.test.Test
import kotlin.test.assertEquals

/** NC-07: External type records and Android Application Records. */
class AarTest {

    private fun encode(vararg records: NdefRecord): Bytes = DefaultNdefCodec.encode(NdefMessage(records.toList()))

    @Test
    fun aarSingleRecordExactBytes() {
        assertEquals(
            "D4 0F 05 61 6E 64 72 6F 69 64 2E 63 6F 6D 3A 70 6B 67 63 6F 6D 2E 61".hex(),
            encode(NdefRecord.aar("com.a")),
        )
    }

    @Test
    fun appAarAsLastOfTwoIsThirtyFourBytesAndEndsTheMessage() {
        val bytes = encode(NdefRecord.text("hi", "en"), NdefRecord.aar("com.tagforge.app"))
        val aar = bytes.slice(bytes.size - 34)
        assertEquals(34, aar.size)
        assertEquals("54 0F 10".hex(), aar.slice(0, 3))   // ME + TNF external, type length 15, payload length 16
    }

    @Test
    fun externalRecordUsesTnfFourAndJoinsDomainAndType() {
        val record = NdefRecord.external("example.com", "ticket", "01 02".hex())
        assertEquals(Tnf.EXTERNAL, record.tnf)
        assertEquals(Bytes.copyOf("example.com:ticket".toByteArray()), record.type)
        assertEquals("D4 12 02".hex(), encode(record).slice(0, 3))
    }

    @Test
    fun mimeRecordUsesTnfTwoAndMediaTypeAsType() {
        val record = NdefRecord.mime("text/plain", Bytes.copyOf("hey".toByteArray()))
        assertEquals(Tnf.MIME, record.tnf)
        assertEquals("D2 0A 03".hex(), encode(record).slice(0, 3))
    }
}
