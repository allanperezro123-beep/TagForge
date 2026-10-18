package com.tagforge.app.infrastructure.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle

/**
 * Copies [text] and tells Android it is sensitive (`android.content.extra.IS_SENSITIVE`) so keyboards and the clipboard
 * preview do not show it. Used for anything that can carry a tag secret: generated passwords and memory dumps (S8).
 */
object SensitiveClipboard {
    private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

    fun copy(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clip.description.extras = PersistableBundle().apply { putBoolean(EXTRA_IS_SENSITIVE, true) }
        clipboard.setPrimaryClip(clip)
    }
}
