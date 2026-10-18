package com.tagforge.app.infrastructure.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.tagforge.app.MainActivity
import com.tagforge.app.R
import com.tagforge.app.feature.productivity.ShortcutLinks
import com.tagforge.app.feature.productivity.ShortcutTarget

/** What asking the launcher to pin a shortcut led to. */
enum class PinResult { REQUESTED, UNSUPPORTED }

/**
 * Pins a "write this template" shortcut to the home screen (PR-03). The link carries the template id and this install's
 * secret nonce (the only link that may start waiting for a tag on open; see ShortcutNonce). Nothing is written until a
 * tag is held to the phone.
 */
object ShortcutPinner {

    fun pinTemplate(context: Context, templateId: Long, templateName: String, nonce: String): PinResult {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return PinResult.UNSUPPORTED
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(ShortcutLinks.uri(ShortcutTarget.WriteTemplate(templateId), nonce)),
            context,
            MainActivity::class.java,
        )
        val info = ShortcutInfoCompat.Builder(context, "template-$templateId")
            .setShortLabel(context.getString(R.string.shortcut_pinned_label, templateName))
            .setLongLabel(context.getString(R.string.shortcut_pinned_long_label, templateName))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_write))
            .setIntent(intent)
            .build()
        return if (ShortcutManagerCompat.requestPinShortcut(context, info, null)) PinResult.REQUESTED else PinResult.UNSUPPORTED
    }
}
