package com.tagforge.app.infrastructure.tasks.handlers

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.provider.Settings
import com.tagforge.app.infrastructure.android.IntentLauncher
import com.tagforge.app.infrastructure.android.LaunchResult
import com.tagforge.app.infrastructure.android.SystemIntents
import com.tagforge.app.infrastructure.tasks.ActionHandler
import com.tagforge.domain.task.ActionCheck
import com.tagforge.domain.task.ActionOutcome
import com.tagforge.domain.task.ActionValidator
import com.tagforge.domain.task.FailReason
import com.tagforge.domain.task.FallbackReason
import com.tagforge.domain.task.HttpMethod
import com.tagforge.domain.task.JoinAuth
import com.tagforge.domain.task.SkipReason
import com.tagforge.domain.task.TaskAction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/** A fallback that actually opened is "needs your tap"; one that could not open is a failure, never a fake success. */
private fun LaunchResult.asFallback(reason: FallbackReason): ActionOutcome = when (this) {
    LaunchResult.STARTED -> ActionOutcome.OpenedFallback(reason)
    LaunchResult.NO_HANDLER -> ActionOutcome.Failed(FailReason.NO_HANDLER)
    LaunchResult.DENIED -> ActionOutcome.Failed(FailReason.SECURITY)
}

/**
 * Wi-Fi switch. Android 9 and older: toggled directly. Android 10+ forbids apps from doing that, so the internet
 * connectivity panel opens and the result reads "needs your tap" (TK-07).
 */
class WifiToggleHandler(
    private val context: Context,
    private val launcher: IntentLauncher,
    private val apiLevel: Int,
) : ActionHandler<TaskAction.WifiToggle> {

    @Suppress("DEPRECATION")
    override suspend fun execute(action: TaskAction.WifiToggle): ActionOutcome {
        if (apiLevel < Build.VERSION_CODES.Q) {
            val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
            return try {
                if (wifi.setWifiEnabled(action.enabled)) ActionOutcome.Done() else ActionOutcome.Failed(FailReason.UNEXPECTED)
            } catch (_: SecurityException) {
                ActionOutcome.Failed(FailReason.SECURITY)
            }
        }
        return launcher.launchFirst(SystemIntents.connectivityPanel(), SystemIntents.wifiSettings()).asFallback(FallbackReason.CONNECTIVITY_PANEL)
    }
}

/**
 * Joins a network the best way each Android version allows: legacy configuration (9-), a network suggestion the user
 * approves (10), the system "add network" dialog (11+). Every path involving the system UI is reported as a fallback.
 */
class WifiJoinHandler(
    private val context: Context,
    private val launcher: IntentLauncher,
    private val apiLevel: Int,
) : ActionHandler<TaskAction.WifiJoin> {

    override suspend fun execute(action: TaskAction.WifiJoin): ActionOutcome {
        if (ActionValidator.check(action) is ActionCheck.Blocked) return ActionOutcome.Skipped(SkipReason.BLOCKED)
        return when {
            apiLevel >= Build.VERSION_CODES.R -> addNetworkScreen(action)
            apiLevel == Build.VERSION_CODES.Q -> suggest(action)
            else -> legacy(action)
        }
    }

    private fun suggestion(action: TaskAction.WifiJoin): WifiNetworkSuggestion {
        val builder = WifiNetworkSuggestion.Builder().setSsid(action.ssid)
        when (action.auth) {
            JoinAuth.OPEN -> Unit
            JoinAuth.WPA2 -> builder.setWpa2Passphrase(action.key)
            JoinAuth.WPA3 -> builder.setWpa3Passphrase(action.key)
        }
        return builder.build()
    }

    private fun addNetworkScreen(action: TaskAction.WifiJoin): ActionOutcome {
        val intent = Intent(Settings.ACTION_WIFI_ADD_NETWORKS)
            .putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(suggestion(action)))
        return launcher.launch(intent).asFallback(FallbackReason.WIFI_ADD_NETWORK_SCREEN)
    }

    private fun suggest(action: TaskAction.WifiJoin): ActionOutcome {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        return try {
            when (val status = wifi.addNetworkSuggestions(listOf(suggestion(action)))) {
                WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS -> ActionOutcome.OpenedFallback(FallbackReason.WIFI_SUGGESTION_APPROVAL)
                else -> ActionOutcome.Failed(FailReason.UNEXPECTED, status.toString())
            }
        } catch (_: SecurityException) {
            ActionOutcome.Failed(FailReason.SECURITY)
        }
    }

    @Suppress("DEPRECATION")
    private fun legacy(action: TaskAction.WifiJoin): ActionOutcome {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        val config = WifiConfiguration().apply {
            SSID = "\"${action.ssid}\""
            if (action.auth == JoinAuth.OPEN) {
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
            } else {
                preSharedKey = "\"${action.key}\""
            }
        }
        return try {
            val id = wifi.addNetwork(config)
            if (id != -1 && wifi.enableNetwork(id, true)) ActionOutcome.Done() else ActionOutcome.Failed(FailReason.UNEXPECTED)
        } catch (_: SecurityException) {
            ActionOutcome.Failed(FailReason.SECURITY)
        }
    }
}

/** Bluetooth is never switched silently on any Android version: its settings open and the user finishes. */
class BluetoothToggleHandler(private val launcher: IntentLauncher) : ActionHandler<TaskAction.BluetoothToggle> {
    override suspend fun execute(action: TaskAction.BluetoothToggle): ActionOutcome =
        launcher.launch(SystemIntents.bluetoothSettings()).asFallback(FallbackReason.BLUETOOTH_SETTINGS)
}

/**
 * A plain GET or empty POST: no headers and no body ever come from a tag, https by default (http only reaches here
 * after the sheet's extra warning and second tap), at most 10 s in total, redirects are not followed, and the body is
 * never read or logged. The runner guarantees it is in the foreground ([com.tagforge.domain.task.RunTaskUseCase]).
 */
class HttpRequestHandler(private val io: CoroutineDispatcher) : ActionHandler<TaskAction.HttpRequest> {

    override suspend fun execute(action: TaskAction.HttpRequest): ActionOutcome {
        if (ActionValidator.check(action) is ActionCheck.Blocked) return ActionOutcome.Skipped(SkipReason.BLOCKED)
        return withContext(io) { request(action) }
    }

    private fun request(action: TaskAction.HttpRequest): ActionOutcome {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(action.url).openConnection() as HttpURLConnection).apply {
                requestMethod = if (action.method == HttpMethod.POST) "POST" else "GET"
                connectTimeout = TIMEOUT_MILLIS
                readTimeout = TIMEOUT_MILLIS
                instanceFollowRedirects = false
                useCaches = false
                if (action.method == HttpMethod.POST) {
                    doOutput = true
                    setFixedLengthStreamingMode(0)
                }
            }
            val code = connection.responseCode
            if (code in SUCCESS_RANGE) ActionOutcome.Done(code.toString()) else ActionOutcome.Failed(FailReason.HTTP_STATUS, code.toString())
        } catch (_: SocketTimeoutException) {
            ActionOutcome.Failed(FailReason.TIMEOUT)
        } catch (_: IOException) {
            ActionOutcome.Failed(FailReason.NETWORK)
        } catch (_: SecurityException) {
            ActionOutcome.Failed(FailReason.SECURITY)
        } finally {
            connection?.disconnect()
        }
    }

    private companion object {
        /** Connect and read each get 5 s, so the whole request is bounded by 10 s (TK-08). */
        const val TIMEOUT_MILLIS = 5_000
        val SUCCESS_RANGE = 200..399
    }
}
