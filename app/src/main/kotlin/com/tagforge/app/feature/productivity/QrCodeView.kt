package com.tagforge.app.feature.productivity

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.tagforge.app.infrastructure.android.QrEncoder
import com.tagforge.app.infrastructure.android.QrMatrix
import kotlin.math.floor

/**
 * Draws the code as black squares on a white card, always, even in dark theme: scanners expect dark modules on a
 * light background with a quiet zone. Each module is a whole number of pixels, so the edges stay sharp instead of
 * blurring into grey seams that a scanner struggles with. [description] is what a screen reader says in place of the picture.
 */
@Composable
fun QrCodeView(matrix: QrMatrix, description: String, modifier: Modifier = Modifier) {
    val quiet = QrEncoder.DEFAULT_QUIET_MODULES
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .semantics { contentDescription = description },
    ) {
        val modules = matrix.size + 2 * quiet
        val cell = floor(size.width / modules).coerceAtLeast(1f)
        val origin = (size.width - cell * modules) / 2f
        drawRect(Color.White)
        for (y in 0 until matrix.size) {
            for (x in 0 until matrix.size) {
                if (matrix[x, y]) {
                    drawRect(Color.Black, Offset(origin + (x + quiet) * cell, origin + (y + quiet) * cell), Size(cell, cell))
                }
            }
        }
    }
}
