package com.tagforge.app.infrastructure.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.tagforge.domain.ndef.NdefCodec
import com.tagforge.domain.scan.ScanCoordinator
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Owns NFC reader mode for one Activity (TS-01): enabled while the Activity is resumed, released on pause. Foreground
 * dispatch is deliberately not used. Reader mode suppresses system tag dispatch and sounds, so a tag is never handled
 * twice. The callback runs on a binder thread and only hands the tag to the [ScanCoordinator], which does all I/O.
 */
class NfcReaderController(
    private val activity: Activity,
    private val coordinator: ScanCoordinator,
    private val codec: NdefCodec,
    private val io: CoroutineDispatcher,
) : DefaultLifecycleObserver, NfcAdapter.ReaderCallback {

    private val adapter: NfcAdapter? get() = NfcAdapter.getDefaultAdapter(activity)

    override fun onResume(owner: LifecycleOwner) = enable()

    override fun onPause(owner: LifecycleOwner) = disable()

    /** Safe to call repeatedly; also used when NFC is switched on while the app is already in front. */
    fun enable() {
        val nfc = adapter ?: return
        if (!nfc.isEnabled) return
        val options = Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, PRESENCE_CHECK_DELAY_MS) }
        nfc.enableReaderMode(activity, this, READER_FLAGS, options)
    }

    fun disable() {
        val nfc = adapter ?: return
        try {
            nfc.disableReaderMode(activity)
        } catch (_: IllegalStateException) {
            // the adapter is already gone (NFC switched off); nothing to release
        }
    }

    override fun onTagDiscovered(tag: Tag) {
        coordinator.onTagDiscovered(AndroidTagSession(tag, codec, io))
    }

    companion object {
        /** NfcA/B/F/V, no platform sounds, and NOT SKIP_NDEF_CHECK so the Ndef technology stays available. */
        const val READER_FLAGS: Int =
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS

        private const val PRESENCE_CHECK_DELAY_MS = 250
    }
}
