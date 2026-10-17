package com.tagforge.app.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.ui.graphics.vector.ImageVector
import com.tagforge.app.R
import kotlin.reflect.KClass

/** The destinations shown in the bottom bar. Phase 1 has Read and More; later phases append theirs. */
enum class TopLevelDestination(
    val route: Route,
    val routeClass: KClass<out Route>,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    READ(Route.Read, Route.Read::class, R.string.nav_read, Icons.Outlined.Nfc),
    MORE(Route.More, Route.More::class, R.string.nav_more, Icons.Outlined.MoreHoriz),
}
