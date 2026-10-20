package com.tagforge.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.tagforge.app.BuildConfig
import com.tagforge.app.feature.history.HistoryRoute
import com.tagforge.app.feature.history.TagDetailRoute
import com.tagforge.app.feature.more.AboutScreen
import com.tagforge.app.feature.more.MoreScreen
import com.tagforge.app.feature.productivity.BatchWriteRoute
import com.tagforge.app.feature.productivity.ImportExportRoute
import com.tagforge.app.feature.productivity.QrRoute
import com.tagforge.app.feature.productivity.ShortcutLinks
import com.tagforge.app.feature.productivity.ShortcutTarget
import com.tagforge.app.feature.read.ReadRoute
import com.tagforge.app.feature.settings.PermissionCenterRoute
import com.tagforge.app.feature.settings.SettingsRoute
import com.tagforge.app.feature.tasks.TaskBuilderRoute
import com.tagforge.app.feature.tasks.TasksRoute
import com.tagforge.app.feature.tools.CloneRoute
import com.tagforge.app.feature.tools.DumpRoute
import com.tagforge.app.feature.tools.TechInfoRoute
import com.tagforge.app.feature.tools.security.RawConsoleRoute
import com.tagforge.app.feature.tools.security.SecurityAction
import com.tagforge.app.feature.tools.security.SecurityRoute
import com.tagforge.app.feature.tools.security.SignatureRoute
import com.tagforge.app.feature.tools.ToolsRoute
import com.tagforge.app.feature.templates.TemplatesRoute
import com.tagforge.app.feature.write.WriteRoute

/** App shell: one NavHost, with the bottom bar shown only on top-level destinations. */
@Composable
fun TagForgeApp(
    openPermissionCenter: Boolean = false,
    onPermissionCenterOpened: () -> Unit = {},
    shortcut: ShortcutTarget? = null,
    onShortcutOpened: () -> Unit = {},
    sharedText: String? = null,
    onSharedTextOpened: () -> Unit = {},
) {
    val navController = rememberNavController()
    LaunchedEffect(openPermissionCenter) {
        if (openPermissionCenter) {
            navController.navigate(Route.PermissionCenter)
            onPermissionCenterOpened()
        }
    }
    LaunchedEffect(shortcut) {
        if (shortcut != null) {
            val route = ShortcutLinks.route(shortcut)
            if (shortcut is ShortcutTarget.WriteTemplate) navController.navigate(route) else navController.navigateToTopLevel(route)
            onShortcutOpened()
        }
    }
    LaunchedEffect(sharedText) {
        if (sharedText != null) {
            navController.navigate(Route.Write(sharedText = sharedText))
            onSharedTextOpened()
        }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val showBottomBar = TopLevelDestination.entries.any { destination?.hasRoute(it.routeClass) == true }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                TagForgeBottomBar(currentDestination = destination) { target -> navController.navigateToTopLevel(target.route) }
            }
        },
    ) { padding ->
        TagForgeNavHost(navController = navController, modifier = Modifier.padding(padding))
    }
}

@Composable
fun TagForgeNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(navController = navController, startDestination = Route.Read, modifier = modifier) {
        composable<Route.Read> {
            ReadRoute(onOpenUnlock = { navController.navigate(Route.Security(SecurityAction.UNLOCK.name)) })
        }
        composable<Route.Write> { entry ->
            val route = entry.toRoute<Route.Write>()
            WriteRoute(
                templateId = route.templateId,
                qrText = route.qrText,
                armOnOpen = route.armOnOpen,
                sharedText = route.sharedText,
                onShowQr = { navController.navigate(Route.Qr(it)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable<Route.Qr> { entry ->
            QrRoute(
                payload = entry.toRoute<Route.Qr>().payload,
                onBack = { navController.popBackStack() },
                onOpenWrite = { navController.navigate(Route.Write(qrText = it)) },
            )
        }
        composable<Route.Tasks> {
            TasksRoute(
                onNew = { navController.navigate(Route.TaskBuilder()) },
                onOpen = { navController.navigate(Route.TaskBuilder(it)) },
            )
        }
        composable<Route.TaskBuilder> { entry ->
            TaskBuilderRoute(taskId = entry.toRoute<Route.TaskBuilder>().taskId, onBack = { navController.popBackStack() })
        }
        composable<Route.Tools> {
            ToolsRoute(
                onOpenDump = { navController.navigate(Route.Dump) },
                onOpenClone = { navController.navigate(Route.Clone) },
                onOpenTech = { navController.navigate(Route.TechInfo) },
                onOpenSecurity = { navController.navigate(Route.Security()) },
            )
        }
        composable<Route.Dump> { DumpRoute(onBack = { navController.popBackStack() }) }
        composable<Route.Clone> { CloneRoute(onBack = { navController.popBackStack() }) }
        composable<Route.TechInfo> { TechInfoRoute(onBack = { navController.popBackStack() }) }
        composable<Route.Security> { entry ->
            SecurityRoute(
                initialAction = entry.toRoute<Route.Security>().action?.let { name -> SecurityAction.entries.firstOrNull { it.name == name } },
                onBack = { navController.popBackStack() },
                onOpenSignature = { navController.navigate(Route.Signature) },
                onOpenConsole = { navController.navigate(Route.RawConsole) },
            )
        }
        composable<Route.Signature> { SignatureRoute(onBack = { navController.popBackStack() }) }
        composable<Route.RawConsole> { RawConsoleRoute(onBack = { navController.popBackStack() }) }
        composable<Route.More> {
            MoreScreen(
                onOpenHistory = { navController.navigate(Route.History) },
                onOpenTemplates = { navController.navigate(Route.Templates) },
                onOpenImportExport = { navController.navigate(Route.ImportExport) },
                onOpenBatch = { navController.navigate(Route.Batch) },
                onOpenQr = { navController.navigate(Route.Qr()) },
                onOpenPermissions = { navController.navigate(Route.PermissionCenter) },
                onOpenSettings = { navController.navigate(Route.Settings) },
                onOpenAbout = { navController.navigate(Route.About) },
            )
        }
        composable<Route.History> {
            HistoryRoute(
                onBack = { navController.popBackStack() },
                onOpen = { navController.navigate(Route.TagDetail(it)) },
                onScan = { navController.navigateToTopLevel(Route.Read) },
            )
        }
        composable<Route.TagDetail> { entry ->
            TagDetailRoute(
                historyId = entry.toRoute<Route.TagDetail>().historyId,
                onBack = { navController.popBackStack() },
                onShowQr = { navController.navigate(Route.Qr(it)) },
            )
        }
        composable<Route.Templates> {
            TemplatesRoute(
                onBack = { navController.popBackStack() },
                onOpen = { navController.navigate(Route.Write(it)) },
                onShowQr = { navController.navigate(Route.Qr(it)) },
                onGoToWrite = { navController.navigateToTopLevel(Route.Write()) },
            )
        }
        composable<Route.ImportExport> { ImportExportRoute(onBack = { navController.popBackStack() }) }
        composable<Route.Batch> { BatchWriteRoute(onBack = { navController.popBackStack() }) }
        composable<Route.Settings> { SettingsRoute(onBack = { navController.popBackStack() }) }
        composable<Route.PermissionCenter> { PermissionCenterRoute(onBack = { navController.popBackStack() }) }
        composable<Route.About> {
            AboutScreen(versionName = BuildConfig.VERSION_NAME, onBack = { navController.popBackStack() })
        }
    }
}

/** Switches tab the way the bottom bar does: one back stack per tab, state saved and restored. */
private fun NavHostController.navigateToTopLevel(route: Route) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
