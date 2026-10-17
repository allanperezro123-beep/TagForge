package com.tagforge.app.ui.text

import androidx.annotation.StringRes
import com.tagforge.app.R
import com.tagforge.domain.ndef.spec.WifiAuth
import com.tagforge.domain.ndef.spec.WifiEncryption

@StringRes
fun WifiAuth.labelRes(): Int = when (this) {
    WifiAuth.OPEN -> R.string.wifi_auth_open
    WifiAuth.WPA_PSK -> R.string.wifi_auth_wpa
    WifiAuth.WPA2_PSK -> R.string.wifi_auth_wpa2
}

@StringRes
fun WifiEncryption.labelRes(): Int = when (this) {
    WifiEncryption.NONE -> R.string.wifi_enc_none
    WifiEncryption.TKIP -> R.string.wifi_enc_tkip
    WifiEncryption.AES -> R.string.wifi_enc_aes
}
