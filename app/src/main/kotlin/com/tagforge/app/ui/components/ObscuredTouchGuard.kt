package com.tagforge.app.ui.components

import android.app.Activity
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Tapjacking guard (S4). Another app can draw a window over ours and make the user tap "Run", "Write" or a typed
 * confirmation while believing they are tapping something harmless. Android marks every touch that reached us through
 * another visible window as "obscured"; with `filterTouchesWhenObscured` set the view DROPS those touches instead of
 * delivering them. The cost is that a screen filter or bubble overlay makes the guarded screen ignore taps until it is
 * closed, so this is only applied where a tap has irreversible effects, not app-wide.
 */
fun Activity.rejectObscuredTouches() {
    window.decorView.filterTouchesWhenObscured = true
}

/**
 * Same guard for the window this composable is shown in. A `ModalBottomSheet` or an `AlertDialog` is its OWN window, so the
 * Activity's decor view does not protect it: call this from inside its content. Inside a normal screen it protects the
 * Activity window and restores the previous setting when the screen leaves.
 */
@Composable
fun RejectObscuredTouches() {
    val content: View = LocalView.current
    DisposableEffect(content) {
        // The view hosting this composition (always attached here) and the decor of its window, whichever drops the touch first.
        val guarded = listOf(content, content.rootView).distinct()
        val previous = guarded.map { it.filterTouchesWhenObscured }
        guarded.forEach { it.filterTouchesWhenObscured = true }
        onDispose { guarded.forEachIndexed { index, view -> view.filterTouchesWhenObscured = previous[index] } }
    }
}
