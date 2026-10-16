package com.tagforge.app.infrastructure.nfc

import android.nfc.FormatException
import android.nfc.TagLostException
import com.tagforge.domain.tag.TagError
import java.io.IOException

/**
 * Single place that turns platform exceptions into [TagError] (rules G3/G4): a lost tag is TagLost, never a crash
 * and never a success. Callers MUST rethrow `CancellationException` before using this.
 */
object TagErrorMapper {
    fun map(throwable: Throwable): TagError = when (throwable) {
        is TagLostException -> TagError.TagLost            // before IOException: it is a subclass
        is FormatException -> TagError.Malformed(throwable.message.orEmpty())
        is IOException -> TagError.Io(throwable.message)
        is SecurityException -> TagError.TagLost           // "Tag is out of date": the Tag handle is no longer valid
        else -> TagError.Io(throwable.message)
    }
}
