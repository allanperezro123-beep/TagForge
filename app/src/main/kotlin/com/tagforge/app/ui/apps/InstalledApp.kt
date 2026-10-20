package com.tagforge.app.ui.apps

import com.tagforge.app.ui.text.foldForSearch
import java.util.Locale

/** One launchable app as the picker lists it: what the user recognises ([label]) and what the record stores ([packageName]). */
data class InstalledApp(val label: String, val packageName: String)

/** The pure part of the app picker: turning the raw launcher list into what the user scrolls and searches. */
object InstalledAppList {

    /** Alphabetical, one row per package, a blank label replaced by the package name, our own app left out. */
    fun prepare(raw: List<InstalledApp>, ownPackage: String): List<InstalledApp> =
        raw.asSequence()
            .filter { it.packageName != ownPackage }
            .distinctBy { it.packageName }
            .map { if (it.label.isBlank()) it.copy(label = it.packageName) else it.copy(label = it.label.trim()) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label.foldForSearch() })
            .toList()

    /** Rows whose label or package contains [query], ignoring case and accents. A blank query keeps everything. */
    fun filter(apps: List<InstalledApp>, query: String): List<InstalledApp> {
        val needle = query.trim().foldForSearch()
        if (needle.isEmpty()) return apps
        return apps.filter { needle in it.label.foldForSearch() || needle in it.packageName.lowercase(Locale.ROOT) }
    }
}
