package com.tagforge.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tagforge.app.BuildConfig
import com.tagforge.app.feature.more.AboutScreen
import com.tagforge.app.feature.more.MoreScreen
import com.tagforge.app.feature.read.ReadRoute
import com.tagforge.app.feature.settings.SettingsRoute

/** App shell: one NavHost, with the bottom bar shown only on top-level destinations. */
@Composable
fun TagForgeApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val showBottomBar = TopLevelDestination.entries.any { destination?.hasRoute(it.routeClass) == true }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                TagForgeBottomBar(currentDestination = destination) { target ->
                    navController.navigate(target.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        },
    ) { padding ->
        TagForgeNavHost(navController = navController, modifier = Modifier.padding(padding))
    }
}

@Composable
fun TagForgeNavHost(
    navController: androidx.navigation.NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(navController = navController, startDestination = Route.Read, modifier = modifier) {
        composable<Route.Read> { ReadRoute() }
        composable<Route.More> {
            MoreScreen(
                onOpenSettings = { navController.navigate(Route.Settings) },
                onOpenAbout = { navController.navigate(Route.About) },
            )
        }
        composable<Route.Settings> { SettingsRoute(onBack = { navController.popBackStack() }) }
        composable<Route.About> {
            AboutScreen(versionName = BuildConfig.VERSION_NAME, onBack = { navController.popBackStack() })
        }
    }
}
