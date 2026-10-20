package com.tagforge.app.infrastructure.android

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.WriterException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** A QR code as dark/light modules, [size] modules per side, quiet zone not included. */
class QrMatrix(val size: Int, private val dark: BooleanArray) {
    operator fun get(x: Int, y: Int): Boolean = dark[y * size + x]
}

/** ARGB pixels of a rendered code. */
class QrImage(val pixels: IntArray, val width: Int, val height: Int)

/** QR generation with zxing-core. No Android classes, so it is unit-testable; the screen turns [QrImage] into a bitmap. */
object QrEncoder {

    /** Null when [text] does not fit in any QR version. */
    fun encode(text: String): QrMatrix? {
        if (text.isEmpty()) return null
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 0,
        )
        return try {
            val bits = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
            val size = bits.width
            QrMatrix(size, BooleanArray(size * size) { bits[it % size, it / size] })
        } catch (_: WriterException) {
            null
        }
    }

    /** Black modules on white, each [scale] pixels wide, with [quietModules] light modules all around. */
    fun toPixels(matrix: QrMatrix, scale: Int, quietModules: Int = DEFAULT_QUIET_MODULES): QrImage {
        val side = (matrix.size + 2 * quietModules) * scale
        val pixels = IntArray(side * side) { WHITE }
        for (y in 0 until matrix.size) {
            for (x in 0 until matrix.size) {
                if (!matrix[x, y]) continue
                for (dy in 0 until scale) {
                    val row = ((y + quietModules) * scale + dy) * side + (x + quietModules) * scale
                    for (dx in 0 until scale) pixels[row + dx] = BLACK
                }
            }
        }
        return QrImage(pixels, side, side)
    }

    const val DEFAULT_QUIET_MODULES = 4
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val BLACK = 0xFF000000.toInt()
}

/** QR reading from already-decoded ARGB pixels; the Photo Picker bitmap is converted by the caller. */
object QrDecoder {

    /** The text of the first QR code found (also in an inverted image), or null when there is none. */
    fun decode(pixels: IntArray, width: Int, height: Int): String? =
        decode(RGBLuminanceSource(width, height, flattenOnWhite(pixels)), tryHarder = true)

    /**
     * Live camera frames use the fast pass ([tryHarder] false): there is always another frame a moment later. A picked
     * picture gets the slow, thorough one.
     */
    fun decode(source: LuminanceSource, tryHarder: Boolean): String? =
        read(BinaryBitmap(HybridBinarizer(source)), tryHarder) ?: read(BinaryBitmap(HybridBinarizer(source.invert())), tryHarder)

    /**
     * A QR saved with a transparent background has RGB 0,0,0 under the clear pixels, which the luminance source would
     * read as black. Compositing over white first is what any viewer shows.
     */
    internal fun flattenOnWhite(pixels: IntArray): IntArray = IntArray(pixels.size) { i ->
        val pixel = pixels[i]
        val alpha = pixel ushr ALPHA_SHIFT
        if (alpha == OPAQUE) {
            pixel
        } else {
            fun over(channel: Int) = (channel * alpha + OPAQUE * (OPAQUE - alpha)) / OPAQUE
            val red = over((pixel shr RED_SHIFT) and CHANNEL)
            val green = over((pixel shr GREEN_SHIFT) and CHANNEL)
            val blue = over(pixel and CHANNEL)
            (OPAQUE shl ALPHA_SHIFT) or (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue
        }
    }

    private const val ALPHA_SHIFT = 24
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val CHANNEL = 0xFF
    private const val OPAQUE = 0xFF

    private fun read(bitmap: BinaryBitmap, tryHarder: Boolean): String? {
        val reader = MultiFormatReader()
        return try {
            reader.decode(bitmap, if (tryHarder) HINTS else FAST_HINTS).text
        } catch (_: NotFoundException) {
            null
        } catch (_: com.google.zxing.ChecksumException) {
            null
        } catch (_: com.google.zxing.FormatException) {
            null
        } finally {
            reader.reset()
        }
    }

    private val FAST_HINTS = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.CHARACTER_SET to "UTF-8",
    )

    private val HINTS = FAST_HINTS + (DecodeHintType.TRY_HARDER to true)
}
