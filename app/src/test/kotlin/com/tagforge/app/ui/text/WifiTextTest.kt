package com.tagforge.app.ui.text

import com.tagforge.app.R
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** W2: WiFi cards must label the codes real tags carry, and name the ones nobody mapped instead of hiding the card. */
class WifiTextTest {

    @Test
    fun knownAuthCodesMapToTheirOwnLabels() {
        assertEquals(WifiLabel(R.string.wifi_auth_open), authLabel(0x0001))
        assertEquals(WifiLabel(R.string.wifi_auth_wpa), authLabel(0x0002))
        assertEquals(WifiLabel(R.string.wifi_auth_wpa2), authLabel(0x0020))
        assertEquals(WifiLabel(R.string.wifi_auth_wpa_wpa2), authLabel(0x0022))
        assertEquals(WifiLabel(R.string.wifi_auth_wpa3), authLabel(0x0040))
    }

    @Test
    fun unknownAuthCodesShowTheRawCode() {
        assertEquals(WifiLabel(R.string.wifi_auth_other, "0x1234"), authLabel(0x1234))
        assertEquals(WifiLabel(R.string.wifi_auth_other, "0x0008"), authLabel(0x0008))
    }

    @Test
    fun knownEncryptionCodesMapToTheirOwnLabels() {
        assertEquals(WifiLabel(R.string.wifi_enc_none), encryptionLabel(0x0001))
        assertEquals(WifiLabel(R.string.wifi_enc_tkip), encryptionLabel(0x0004))
        assertEquals(WifiLabel(R.string.wifi_enc_aes), encryptionLabel(0x0008))
        assertEquals(WifiLabel(R.string.wifi_enc_tkip_aes), encryptionLabel(0x000C))
    }

    @Test
    fun unknownEncryptionCodesShowTheRawCode() {
        assertEquals(WifiLabel(R.string.wifi_enc_other, "0x00F0"), encryptionLabel(0x00F0))
        assertNull(encryptionLabel(0x0008).code)
    }
}
