package com.tagforge.app.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Reference-counted FLAG_SECURE for one window. A screen that shows a tag password [acquire]s it and releases it when it
 * leaves; the flag is cleared only when the last holder lets go, and never if the window was secure before we touched it.
 */
class SecureWindowFlag(private val read: () -> Boolean, private val write: (Boolean) -> Unit) {
    private var holders = 0
    private var setByUs = false

    fun acquire(): Hold {
        if (holders == 0 && !read()) {
            write(true)
            setByUs = true
        }
        holders++
        return Hold()
    }

    inner class Hold internal constructor() {
        private var released = false

        fun release() {
            if (released) return
            released = true
            holders--
            if (holders == 0 && setByUs) {
                write(false)
                setByUs = false
            }
        }
    }
}

/** Keeps the Activity window secure (no screenshots, no recents thumbnail, no screen recording) while this is composed (W7). */
@Composable
fun SecureWindow() {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity) {
        val flag = flagsByActivity.getOrPut(activity) {
            SecureWindowFlag(
                read = { activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0 },
                write = { on ->
                    if (on) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                },
            )
        }
        val hold = flag.acquire()
        onDispose {
            hold.release()
        }
    }
}

private val flagsByActivity = java.util.WeakHashMap<Activity, SecureWindowFlag>()

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
