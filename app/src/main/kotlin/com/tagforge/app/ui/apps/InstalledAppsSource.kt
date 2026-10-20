package com.tagforge.app.ui.apps

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Every app with a launcher icon, through the `<queries>` launcher intent in the manifest: no QUERY_ALL_PACKAGES.
 * Labels are read off the main thread; a phone with thousands of entries still opens the sheet at once.
 */
suspend fun loadInstalledApps(packageManager: PackageManager, ownPackage: String): List<InstalledApp> = withContext(Dispatchers.Default) {
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        packageManager.queryIntentActivities(launcher, 0)
    }
    InstalledAppList.prepare(
        resolved.map { InstalledApp(it.loadLabel(packageManager).toString(), it.activityInfo.packageName) },
        ownPackage,
    )
}

/** The name the user knows [packageName] by, or null when it is not installed or not visible to this app. */
fun appLabelOf(packageManager: PackageManager, packageName: String): String? = runCatching {
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getApplicationInfo(packageName, 0)
    }
    packageManager.getApplicationLabel(info).toString()
}.getOrNull()
