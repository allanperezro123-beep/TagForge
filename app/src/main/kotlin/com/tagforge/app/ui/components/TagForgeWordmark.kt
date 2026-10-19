package com.tagforge.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.tagforge.app.R

/** [Small] is the top-bar title (24 dp mark); [Large] is the About header. */
enum class WordmarkSize(val markHeight: Dp, val gap: Dp) {
    Small(markHeight = 24.dp, gap = 8.dp),
    Large(markHeight = 48.dp, gap = 12.dp),
}

/**
 * The Tag Forge lock-up: the gradient mark, then "Tag" in the primary role and "Forge" in the tertiary role so both
 * re-theme with the scheme. Announced once as the app name; the mark and the two words are decorative on their own.
 */
@Composable
fun TagForgeWordmark(modifier: Modifier = Modifier, size: WordmarkSize = WordmarkSize.Small) {
    val scheme = MaterialTheme.colorScheme
    val description = stringResource(R.string.app_name)
    val name = remember(scheme.primary, scheme.tertiary) { wordmarkName(scheme.primary, scheme.tertiary) }
    val textStyle = if (size == WordmarkSize.Large) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleLarge
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(size.gap),
    ) {
        Image(
            painter = painterResource(logoMarkRes(scheme.surface)),
            contentDescription = null,
            modifier = Modifier.size(width = size.markHeight * MARK_ASPECT, height = size.markHeight),
        )
        Text(name, style = textStyle.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em))
    }
}

/**
 * The mark has a variant per surface (the light one has darker gradient stops). It is chosen from the Compose colour
 * scheme rather than the system night qualifier, because the in-app theme setting can contradict the system one.
 */
internal fun logoMarkRes(surface: Color): Int =
    if (surface.luminance() < DARK_SURFACE_LUMINANCE) R.drawable.ic_logo_mark_dark else R.drawable.ic_logo_mark_light

internal fun wordmarkName(tag: Color, forge: Color): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(color = tag)) { append("Tag") }
    withStyle(SpanStyle(color = forge)) { append("Forge") }
}

/** Width over height of ic_logo_mark_*.xml (64 x 40 dp). */
private const val MARK_ASPECT = 64f / 40f

/** Relative luminance where white and black text have the same WCAG contrast; darker surfaces get the dark mark. */
private const val DARK_SURFACE_LUMINANCE = 0.179f
