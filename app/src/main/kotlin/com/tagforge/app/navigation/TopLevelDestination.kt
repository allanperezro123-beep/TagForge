package com.tagforge.app.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.ui.graphics.vector.ImageVector
import com.tagforge.app.R
import kotlin.reflect.KClass

/** The destinations shown in the bottom bar. Phase 4 has Read, Write, Tasks, Tools and More; later phases insert theirs before More. */
enum class TopLevelDestination(
    val route: Route,
    val routeClass: KClass<out Route>,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    READ(Route.Read, Route.Read::class, R.string.nav_read, Icons.Outlined.Nfc),
    WRITE(Route.Write(), Route.Write::class, R.string.nav_write, Icons.Outlined.Edit),
    TASKS(Route.Tasks, Route.Tasks::class, R.string.nav_tasks, Icons.Outlined.TaskAlt),
    TOOLS(Route.Tools, Route.Tools::class, R.string.nav_tools, Icons.Outlined.Build),
    MORE(Route.More, Route.More::class, R.string.nav_more, Icons.Outlined.MoreHoriz),
}
