package com.tagforge.app.infrastructure.tasks

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tagforge.domain.task.DeviceFacts

/**
 * Reads the live grants and hardware that feed [com.tagforge.domain.task.FeasibilityMatrix] and the Permission
 * Center. Cheap enough to call on every query; never prompts and never changes anything.
 */
class AndroidDeviceFacts(private val context: Context) {

    fun snapshot(): DeviceFacts = DeviceFacts(
        apiLevel = Build.VERSION.SDK_INT,
        writeSettingsGranted = Settings.System.canWrite(context),
        notificationPolicyGranted = context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted,
        postNotificationsGranted = notificationsAllowed(),
        hasFlashlight = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH),
    )

    private fun notificationsAllowed(): Boolean {
        val permission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permission && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}
