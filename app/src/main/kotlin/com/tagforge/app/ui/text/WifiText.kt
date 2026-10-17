package com.tagforge.app.ui.text

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
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

/** A string resource plus, for codes nobody named, the raw code to fill its single `%1$s` placeholder. */
data class WifiLabel(@StringRes val res: Int, val code: String? = null)

@Composable
fun WifiLabel.text(): String = if (code == null) stringResource(res) else stringResource(res, code)

private const val AUTH_OPEN = 0x0001
private const val AUTH_WPA_PSK = 0x0002
private const val AUTH_WPA2_PSK = 0x0020
private const val AUTH_WPA_WPA2_MIXED = 0x0022
private const val AUTH_WPA3_SAE = 0x0040

private const val ENC_NONE = 0x0001
private const val ENC_TKIP = 0x0004
private const val ENC_AES = 0x0008
private const val ENC_TKIP_AES = 0x000C

/** Label for a raw WSC authentication code: tags in the wild carry more than the three this app writes. */
fun authLabel(code: Int): WifiLabel = when (code) {
    AUTH_OPEN -> WifiLabel(R.string.wifi_auth_open)
    AUTH_WPA_PSK -> WifiLabel(R.string.wifi_auth_wpa)
    AUTH_WPA2_PSK -> WifiLabel(R.string.wifi_auth_wpa2)
    AUTH_WPA_WPA2_MIXED -> WifiLabel(R.string.wifi_auth_wpa_wpa2)
    AUTH_WPA3_SAE -> WifiLabel(R.string.wifi_auth_wpa3)
    else -> WifiLabel(R.string.wifi_auth_other, hexCode(code))
}

/** Label for a raw WSC encryption code. */
fun encryptionLabel(code: Int): WifiLabel = when (code) {
    ENC_NONE -> WifiLabel(R.string.wifi_enc_none)
    ENC_TKIP -> WifiLabel(R.string.wifi_enc_tkip)
    ENC_AES -> WifiLabel(R.string.wifi_enc_aes)
    ENC_TKIP_AES -> WifiLabel(R.string.wifi_enc_tkip_aes)
    else -> WifiLabel(R.string.wifi_enc_other, hexCode(code))
}

private fun hexCode(code: Int): String = "0x" + code.toString(16).uppercase().padStart(4, '0')
