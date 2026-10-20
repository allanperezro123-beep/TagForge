package com.tagforge.domain.exchange

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What a scanned QR says, in plain terms, and whether it may be offered an "Open" button at all. */
class QrContentTest {

    @Test
    fun aWebLinkShowsItsRealHostAndCanBeOpened() {
        val content = assertIs<QrContent.Link>(QrContentClassifier.classify("https://Example.com/menu?table=4"))

        assertEquals("example.com", content.host)
        assertEquals("https://Example.com/menu?table=4", content.openUri)
        assertTrue(content.secure)
    }

    @Test
    fun aPlainHttpLinkIsFlaggedAsNotSecure() {
        val content = assertIs<QrContent.Link>(QrContentClassifier.classify("http://example.com:8080/x"))

        assertEquals("example.com:8080", content.host)
        assertTrue(!content.secure)
    }

    @Test
    fun aLinkThatHidesItsHostBehindUserInfoIsNeverOpenable() {
        val content = QrContentClassifier.classify("https://paypal.com@evil.example/login")

        assertIs<QrContent.Unsafe>(content)
        assertNull(content.openUri)
    }

    @Test
    fun phoneMailSmsAndGeoAreOpenableThroughTheSameAllowlistAsTasks() {
        assertEquals("tel:+34600111222", QrContentClassifier.classify("tel:+34600111222").openUri)
        assertEquals("mailto:a@b.com?subject=Hi", QrContentClassifier.classify("mailto:a@b.com?subject=Hi").openUri)
        assertEquals("sms:+34600111222?body=Hola", QrContentClassifier.classify("sms:+34600111222?body=Hola").openUri)
        assertEquals("geo:40.4168,-3.7038", QrContentClassifier.classify("geo:40.4168,-3.7038").openUri)
        assertIs<QrContent.Phone>(QrContentClassifier.classify("tel:+34600111222"))
        assertIs<QrContent.Email>(QrContentClassifier.classify("mailto:a@b.com"))
        assertIs<QrContent.Sms>(QrContentClassifier.classify("sms:+34600111222"))
        assertIs<QrContent.Location>(QrContentClassifier.classify("geo:40.4168,-3.7038"))
    }

    @Test
    fun aWifiCodeNamesTheNetworkAndIsNeverOpened() {
        val content = assertIs<QrContent.Wifi>(QrContentClassifier.classify("WIFI:T:WPA;S:Home Net;P:secret123;;"))

        assertEquals("Home Net", content.ssid)
        assertTrue(content.secured)
        assertNull(content.openUri)
    }

    @Test
    fun anOpenWifiCodeIsNotSecured() {
        val content = assertIs<QrContent.Wifi>(QrContentClassifier.classify("WIFI:T:nopass;S:Cafe;;"))

        assertTrue(!content.secured)
    }

    @Test
    fun aContactCardIsRecognisedByItsName() {
        val content = assertIs<QrContent.Contact>(QrContentClassifier.classify("BEGIN:VCARD\nVERSION:3.0\nFN:Ana Perez\nTEL:+34600111222\nEND:VCARD"))

        assertEquals("Ana Perez", content.name)
        assertNull(content.openUri)
    }

    @Test
    fun dangerousSchemesAreNamedButNeverOpenable() {
        listOf("javascript:alert(1)", "intent://scan/#Intent;scheme=zxing;end", "file:///sdcard/a.txt", "content://x/y", "market://details?id=a.b").forEach { raw ->
            val content = QrContentClassifier.classify(raw)
            assertIs<QrContent.Unsafe>(content, raw)
            assertNull(content.openUri, raw)
        }
    }

    @Test
    fun anythingElseIsPlainTextAndNeverOpenable() {
        val content = assertIs<QrContent.Plain>(QrContentClassifier.classify("  table 12 - ready  "))

        assertEquals("table 12 - ready", content.text)
        assertNull(content.openUri)
    }

    @Test
    fun aLinkWithSpacesInItIsPlainTextBecauseItCannotBeOpenedSafely() {
        assertIs<QrContent.Plain>(QrContentClassifier.classify("https://exa mple.com"))
    }

    @Test
    fun theSchemeIsMatchedWithoutCase() {
        assertIs<QrContent.Link>(QrContentClassifier.classify("HTTPS://example.com"))
        assertIs<QrContent.Phone>(QrContentClassifier.classify("TEL:123"))
    }
}
