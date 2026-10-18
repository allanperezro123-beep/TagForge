package com.tagforge.app.feature.productivity.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.tagforge.app.MainActivity
import com.tagforge.app.R
import com.tagforge.app.feature.productivity.ShortcutLinks
import com.tagforge.app.feature.productivity.ShortcutTarget

/**
 * Home-screen widget with two buttons, Scan and Write (PR-03). Each button opens the app on that screen through the
 * same links as the launcher shortcuts. The widget shows no tag contents and holds no state, so it needs no updates.
 */
class QuickScanWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = RemoteViews(context.packageName, R.layout.widget_quick_scan).apply {
            setOnClickPendingIntent(R.id.widget_scan, open(context, ShortcutTarget.Scan, REQUEST_SCAN))
            setOnClickPendingIntent(R.id.widget_write, open(context, ShortcutTarget.Write, REQUEST_WRITE))
        }
        appWidgetManager.updateAppWidget(appWidgetIds, views)
    }

    private fun open(context: Context, target: ShortcutTarget, requestCode: Int): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ShortcutLinks.uri(target)), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private companion object {
        const val REQUEST_SCAN = 1
        const val REQUEST_WRITE = 2
    }
}
