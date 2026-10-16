package com.tagforge.app.infrastructure.nfc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

enum class NfcAvailability { UNSUPPORTED, DISABLED, ENABLED }

object NfcAvailabilityMapper {
    fun from(hasAdapter: Boolean, isEnabled: Boolean): NfcAvailability = when {
        !hasAdapter -> NfcAvailability.UNSUPPORTED
        isEnabled -> NfcAvailability.ENABLED
        else -> NfcAvailability.DISABLED
    }

    /** Only a fully ON adapter counts as enabled; transitional states are treated as not yet usable. */
    fun fromAdapterState(state: Int): NfcAvailability =
        if (state == NfcAdapter.STATE_ON) NfcAvailability.ENABLED else NfcAvailability.DISABLED
}

/** Watches the NFC adapter so the UI flips Disabled -> Enabled without a restart (AS-02). */
class NfcAvailabilityMonitor(context: Context) {
    private val appContext = context.applicationContext

    fun current(): NfcAvailability {
        val adapter = NfcAdapter.getDefaultAdapter(appContext)
        return NfcAvailabilityMapper.from(hasAdapter = adapter != null, isEnabled = adapter?.isEnabled == true)
    }

    val availability: Flow<NfcAvailability> = callbackFlow {
        send(current())
        if (NfcAdapter.getDefaultAdapter(appContext) == null) {
            awaitClose { }
            return@callbackFlow
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val state = intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, NfcAdapter.STATE_OFF)
                trySend(NfcAvailabilityMapper.fromAdapterState(state))
            }
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        awaitClose { appContext.unregisterReceiver(receiver) }
    }.distinctUntilChanged()
}
