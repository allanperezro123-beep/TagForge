package com.tagforge.app.ui.apps

import kotlin.test.Test
import kotlin.test.assertEquals

class InstalledAppListTest {

    private fun app(label: String, pkg: String) = InstalledApp(label, pkg)

    @Test
    fun appsAreListedAlphabeticallyIgnoringCaseAndAccents() {
        val raw = listOf(app("zeta", "z.z"), app("Álbum", "a.a"), app("Beta", "b.b"), app("alpha", "a.l"))

        val labels = InstalledAppList.prepare(raw, ownPackage = "x.x").map { it.label }

        assertEquals(listOf("Álbum", "alpha", "Beta", "zeta"), labels)
    }

    @Test
    fun anAppWithSeveralLauncherActivitiesAppearsOnce() {
        val raw = listOf(app("Mail", "com.mail"), app("Mail (settings)", "com.mail"), app("Maps", "com.maps"))

        val prepared = InstalledAppList.prepare(raw, ownPackage = "x.x")

        assertEquals(listOf("com.mail", "com.maps"), prepared.map { it.packageName })
    }

    @Test
    fun aBlankLabelFallsBackToThePackageNameSoTheRowIsNeverEmpty() {
        val prepared = InstalledAppList.prepare(listOf(app("  ", "com.hidden.app")), ownPackage = "x.x")

        assertEquals("com.hidden.app", prepared.single().label)
    }

    @Test
    fun ourOwnAppIsNotOfferedBecauseLaunchingItFromATagIsPointless() {
        val prepared = InstalledAppList.prepare(listOf(app("Tag Forge", "com.tagforge.app"), app("Maps", "com.maps")), ownPackage = "com.tagforge.app")

        assertEquals(listOf("com.maps"), prepared.map { it.packageName })
    }

    @Test
    fun searchMatchesPartOfTheNameWithoutCaseOrAccents() {
        val apps = listOf(app("Cámara", "com.cam"), app("Calendario", "com.cal"), app("Maps", "com.maps"))

        assertEquals(listOf("com.cam"), InstalledAppList.filter(apps, "CAMA").map { it.packageName })
        assertEquals(listOf("com.cam", "com.cal"), InstalledAppList.filter(apps, "ca").map { it.packageName })
    }

    @Test
    fun searchAlsoMatchesThePackageSoPowerUsersCanFindAnApp() {
        val apps = listOf(app("Maps", "com.google.android.apps.maps"), app("Notes", "com.notes"))

        assertEquals(listOf("com.google.android.apps.maps"), InstalledAppList.filter(apps, "apps.maps").map { it.packageName })
    }

    @Test
    fun aBlankSearchKeepsEveryApp() {
        val apps = listOf(app("A", "a"), app("B", "b"))

        assertEquals(apps, InstalledAppList.filter(apps, "   "))
    }
}
