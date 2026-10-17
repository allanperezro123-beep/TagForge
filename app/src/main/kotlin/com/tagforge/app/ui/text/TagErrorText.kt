package com.tagforge.app.ui.text

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tagforge.app.R
import com.tagforge.domain.tag.TagError

/** Localised text for a [TagError]: an optional [title], a [message] and the format [args] of [message]. */
data class ErrorText(
    @StringRes val message: Int,
    val args: List<Any> = emptyList(),
    @StringRes val title: Int? = null,
)

/** The domain never returns user-facing text; this is the single place that chooses it. */
object TagErrorText {
    fun of(error: TagError): ErrorText = when (error) {
        TagError.TagLost -> ErrorText(R.string.error_tag_lost_body, title = R.string.error_tag_lost_title)
        TagError.Timeout -> ErrorText(R.string.error_timeout)
        TagError.Cancelled -> ErrorText(R.string.error_cancelled)
        TagError.NotNdef -> ErrorText(R.string.error_not_ndef)
        TagError.ReadOnly -> ErrorText(R.string.error_read_only)
        TagError.AuthRequired -> ErrorText(R.string.error_auth_required)
        is TagError.Unsupported -> ErrorText(R.string.error_unsupported, listOf(error.what))
        is TagError.CapacityExceeded -> ErrorText(R.string.error_capacity, listOf(error.required, error.available))
        is TagError.AuthFailed -> ErrorText(R.string.error_auth_failed)
        is TagError.VerifyMismatch -> ErrorText(R.string.error_verify_mismatch)
        is TagError.Nak -> ErrorText(R.string.error_nak, listOf(error.code))
        is TagError.Malformed -> ErrorText(R.string.error_malformed)
        is TagError.Io -> ErrorText(R.string.error_io)
    }
}

@Composable
fun ErrorText.messageText(): String = stringResource(message, *args.toTypedArray())

@Composable
fun ErrorText.titleText(): String? = title?.let { stringResource(it) }
