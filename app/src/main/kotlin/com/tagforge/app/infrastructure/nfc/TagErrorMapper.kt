package com.tagforge.app.infrastructure.nfc

import android.nfc.FormatException
import android.nfc.TagLostException
import com.tagforge.domain.tag.TagError

/**
 * Single place that turns platform exceptions into [TagError] (rules G3/G4): a lost tag is TagLost, never a crash
 * and never a success. Callers MUST rethrow `CancellationException` before using this.
 */
object TagErrorMapper {
    fun map(throwable: Throwable): TagError = when (throwable) {
        is TagLostException -> TagError.TagLost            // the platform says the tag left the field: the only TagLost
        is FormatException -> TagError.Malformed(throwable.message.orEmpty())
        // "Tag is out of date": the Tag handle is no longer valid. The user retries just like after a lost tag, but a
        // field report must be able to tell the two apart, so it travels as a communication error with its class.
        else -> TagError.Io(describe(throwable))
    }

    /** `ClassName: message`, so a report reads "IOException: Transceive failed" instead of an anonymous failure. */
    fun describe(throwable: Throwable): String {
        val name = throwable.javaClass.simpleName.ifEmpty { throwable.javaClass.name }
        return throwable.message?.takeIf { it.isNotBlank() }?.let { "$name: $it" } ?: name
    }
}
