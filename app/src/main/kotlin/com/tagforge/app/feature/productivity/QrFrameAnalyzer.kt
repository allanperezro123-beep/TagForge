package com.tagforge.app.feature.productivity

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.PlanarYUVLuminanceSource
import com.tagforge.app.infrastructure.android.QrDecoder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Looks for a QR code in every camera frame. Only the brightness plane is used, which is all ZXing needs, and a code is
 * detected at any rotation, so portrait works without turning the frame. [onFound] fires once, then frames are ignored.
 */
class QrFrameAnalyzer(private val onFound: (String) -> Unit) : ImageAnalysis.Analyzer {

    private val done = AtomicBoolean(false)

    override fun analyze(image: ImageProxy) {
        try {
            if (done.get()) return
            val plane = image.planes.firstOrNull() ?: return
            val buffer = plane.buffer
            val data = ByteArray(buffer.remaining()).also { buffer.get(it) }
            // The row stride can be wider than the image: the source is told the real width and the padded one.
            val source = PlanarYUVLuminanceSource(data, plane.rowStride, image.height, 0, 0, image.width, image.height, false)
            val text = QrDecoder.decode(source, tryHarder = false)
            if (text != null && done.compareAndSet(false, true)) onFound(text)
        } catch (_: IllegalArgumentException) {
            // A frame that does not fit its declared size is skipped; the next one is fine.
        } finally {
            image.close()
        }
    }
}
