package com.tagforge.app.infrastructure.android

import com.tagforge.domain.exchange.QrPayloadMapper
import com.tagforge.domain.ndef.spec.RecordSpec
import com.tagforge.domain.ndef.spec.WifiAuth
import com.tagforge.domain.ndef.spec.WifiEncryption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** PR-02: a generated QR image decodes back to the same text, and a record survives the whole trip. */
class QrCodecTest {

    private fun roundTrip(text: String, scale: Int = 6): String? {
        val matrix = assertNotNull(QrEncoder.encode(text), "encoding $text")
        val image = QrEncoder.toPixels(matrix, scale)
        return QrDecoder.decode(image.pixels, image.width, image.height)
    }

    @Test
    fun `wifi qr text survives encode and decode`() {
        assertEquals("WIFI:T:WPA;S:Home;P:pass1234;;", roundTrip("WIFI:T:WPA;S:Home;P:pass1234;;"))
    }

    @Test
    fun `utf8 and long text survive encode and decode`() {
        assertEquals("Café ñandú 日本語", roundTrip("Café ñandú 日本語"))
        val long = "https://example.com/" + "a".repeat(300)
        assertEquals(long, roundTrip(long, scale = 4))
    }

    @Test
    fun `image has a white quiet zone and square pixel size`() {
        val matrix = assertNotNull(QrEncoder.encode("hi"))
        val image = QrEncoder.toPixels(matrix, scale = 3, quietModules = 4)
        assertEquals((matrix.size + 8) * 3, image.width)
        assertEquals(image.width, image.height)
        assertEquals(WHITE, image.pixels[0])
        assertTrue(image.pixels.any { it == BLACK }, "some modules are dark")
    }

    @Test
    fun `a blank image or oversized text has no qr`() {
        assertNull(QrDecoder.decode(IntArray(64 * 64) { WHITE }, 64, 64))
        assertNull(QrEncoder.encode("x".repeat(5000)))
    }

    @Test
    fun `an inverted qr image still decodes`() {
        val matrix = assertNotNull(QrEncoder.encode("https://example.com"))
        val image = QrEncoder.toPixels(matrix, 6)
        val inverted = IntArray(image.pixels.size) { if (image.pixels[it] == BLACK) WHITE else BLACK }
        assertEquals("https://example.com", QrDecoder.decode(inverted, image.width, image.height))
    }

    @Test
    fun `a wifi record becomes an image that decodes back to the same record`() {
        val wifi = RecordSpec.Wifi("Home;5G", "pa:ss\\1234", WifiAuth.WPA2_PSK, WifiEncryption.AES)
        val text = assertNotNull(QrPayloadMapper.toQr(wifi))
        val decoded = assertNotNull(roundTrip(text))
        assertEquals(wifi, QrPayloadMapper.fromQr(decoded))
    }

    private companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
        const val BLACK = 0xFF000000.toInt()
    }
}
