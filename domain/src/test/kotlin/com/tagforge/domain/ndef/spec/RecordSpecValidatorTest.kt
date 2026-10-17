package com.tagforge.domain.ndef.spec

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** TW-01 "Inline validation": field-keyed error codes, never UI text. */
class RecordSpecValidatorTest {

    private fun errors(spec: RecordSpec): List<FieldError> = RecordSpecValidator.validate(spec)

    private fun assertValid(spec: RecordSpec) = assertEquals(emptyList(), errors(spec), "expected $spec to be valid")

    // ---- text / uri -------------------------------------------------------------------------------

    @Test
    fun emptyTextIsRequiredAndNonEmptyIsValid() {
        assertEquals(listOf(FieldError(SpecField.TEXT, SpecError.REQUIRED)), errors(RecordSpec.Text("")))
        assertEquals(listOf(FieldError(SpecField.TEXT, SpecError.REQUIRED)), errors(RecordSpec.Text("   ")))
        assertValid(RecordSpec.Text("hola", "es"))
    }

    @Test
    fun languageCodeMustLookLikeABcp47Tag() {
        assertEquals(listOf(FieldError(SpecField.LANGUAGE, SpecError.INVALID_LANGUAGE)), errors(RecordSpec.Text("x", "e")))
        assertEquals(listOf(FieldError(SpecField.LANGUAGE, SpecError.INVALID_LANGUAGE)), errors(RecordSpec.Text("x", "")))
        assertValid(RecordSpec.Text("x", "es-CO"))
    }

    @Test
    fun urlNeedsASchemeAndWebUrlsNeedAHost() {
        assertEquals(listOf(FieldError(SpecField.URI, SpecError.REQUIRED)), errors(RecordSpec.Uri("")))
        assertEquals(listOf(FieldError(SpecField.URI, SpecError.INVALID_URL)), errors(RecordSpec.Uri("example.com")))
        assertEquals(listOf(FieldError(SpecField.URI, SpecError.INVALID_URL)), errors(RecordSpec.Uri("https://")))
        assertEquals(listOf(FieldError(SpecField.URI, SpecError.INVALID_URL)), errors(RecordSpec.Uri("https://exa mple.com")))
        assertValid(RecordSpec.Uri("https://example.com/path?q=1"))
        assertValid(RecordSpec.Uri("tel:+573001234567"))
    }

    // ---- email / phone / sms -----------------------------------------------------------------------

    @Test
    fun emailAddressMustHaveLocalPartAtAndDomain() {
        assertEquals(listOf(FieldError(SpecField.TO, SpecError.REQUIRED)), errors(RecordSpec.Email("")))
        assertEquals(listOf(FieldError(SpecField.TO, SpecError.INVALID_EMAIL)), errors(RecordSpec.Email("a@b")))
        assertEquals(listOf(FieldError(SpecField.TO, SpecError.INVALID_EMAIL)), errors(RecordSpec.Email("no-at.com")))
        assertValid(RecordSpec.Email("a@b.co", "Hi", "Body"))
    }

    @Test
    fun phoneNeedsDigitsAndAllowsCommonFormatting() {
        assertEquals(listOf(FieldError(SpecField.NUMBER, SpecError.REQUIRED)), errors(RecordSpec.Phone("")))
        assertEquals(listOf(FieldError(SpecField.NUMBER, SpecError.INVALID_PHONE)), errors(RecordSpec.Phone("call me")))
        assertEquals(listOf(FieldError(SpecField.NUMBER, SpecError.INVALID_PHONE)), errors(RecordSpec.Phone("12")))
        assertValid(RecordSpec.Phone("+57 (300) 123-4567"))
        assertEquals(listOf(FieldError(SpecField.NUMBER, SpecError.INVALID_PHONE)), errors(RecordSpec.Sms("abc", "hi")))
        assertValid(RecordSpec.Sms("3001234567", ""))
    }

    // ---- geo ------------------------------------------------------------------------------------------

    @Test
    fun geoCoordinatesMustBeNumbersInRange() {
        assertEquals(
            listOf(FieldError(SpecField.LATITUDE, SpecError.REQUIRED), FieldError(SpecField.LONGITUDE, SpecError.REQUIRED)),
            errors(RecordSpec.Geo("", "")),
        )
        assertEquals(listOf(FieldError(SpecField.LATITUDE, SpecError.NOT_A_NUMBER)), errors(RecordSpec.Geo("north", "0")))
        assertEquals(listOf(FieldError(SpecField.LATITUDE, SpecError.OUT_OF_RANGE)), errors(RecordSpec.Geo("90.5", "0")))
        assertEquals(listOf(FieldError(SpecField.LONGITUDE, SpecError.OUT_OF_RANGE)), errors(RecordSpec.Geo("0", "-180.1")))
        assertValid(RecordSpec.Geo("90", "180"))
        assertValid(RecordSpec.Geo("-90", "-180"))
    }

    // ---- contact --------------------------------------------------------------------------------------

    @Test
    fun contactNeedsANameAndValidOptionalFields() {
        assertEquals(listOf(FieldError(SpecField.NAME, SpecError.REQUIRED)), errors(RecordSpec.Contact()))
        assertEquals(
            listOf(FieldError(SpecField.PHONE, SpecError.INVALID_PHONE), FieldError(SpecField.EMAIL, SpecError.INVALID_EMAIL)),
            errors(RecordSpec.Contact(name = "Ana", phone = "x", email = "y")),
        )
        assertEquals(listOf(FieldError(SpecField.URL, SpecError.INVALID_URL)), errors(RecordSpec.Contact(name = "Ana", url = "nope")))
        assertValid(RecordSpec.Contact(name = "Ana", phone = "+57300", email = "a@b.co", url = "https://b.co"))
    }

    // ---- wifi -----------------------------------------------------------------------------------------

    @Test
    fun wifiSsidIsRequiredAndLimitedTo32Bytes() {
        assertEquals(
            listOf(FieldError(SpecField.SSID, SpecError.REQUIRED)),
            errors(RecordSpec.Wifi(ssid = "", password = "pass1234")),
        )
        assertValid(RecordSpec.Wifi(ssid = "a".repeat(32), password = "pass1234"))
        assertEquals(
            listOf(FieldError(SpecField.SSID, SpecError.SSID_TOO_LONG)),
            errors(RecordSpec.Wifi(ssid = "a".repeat(33), password = "pass1234")),
        )
        // 17 two-byte characters = 34 bytes, only 17 chars: the limit is in bytes
        assertEquals(
            listOf(FieldError(SpecField.SSID, SpecError.SSID_TOO_LONG)),
            errors(RecordSpec.Wifi(ssid = "é".repeat(17), password = "pass1234")),
        )
    }

    @Test
    fun wifiKeyMustBe8To63CharactersUnlessOpen() {
        assertEquals(
            listOf(FieldError(SpecField.PASSWORD, SpecError.PASSWORD_LENGTH)),
            errors(RecordSpec.Wifi(ssid = "Home", password = "123", auth = WifiAuth.WPA2_PSK)),
        )
        assertEquals(
            listOf(FieldError(SpecField.PASSWORD, SpecError.PASSWORD_LENGTH)),
            errors(RecordSpec.Wifi(ssid = "Home", password = "x".repeat(64), auth = WifiAuth.WPA2_PSK)),
        )
        assertValid(RecordSpec.Wifi(ssid = "Home", password = "12345678"))
        assertValid(RecordSpec.Wifi(ssid = "Home", password = "x".repeat(63)))
        assertValid(RecordSpec.Wifi(ssid = "Home", password = "ab".repeat(32)))   // 64 hex digits = raw PSK
        assertValid(RecordSpec.Wifi(ssid = "Cafe", password = "", auth = WifiAuth.OPEN, encryption = WifiEncryption.NONE))
        assertValid(RecordSpec.Wifi(ssid = "Cafe", password = "123", auth = WifiAuth.OPEN, encryption = WifiEncryption.NONE))
    }

    // ---- bluetooth / app / mime / external ---------------------------------------------------------------

    @Test
    fun bluetoothMacMustBeSixOctets() {
        assertEquals(listOf(FieldError(SpecField.MAC, SpecError.REQUIRED)), errors(RecordSpec.Bluetooth("")))
        assertEquals(listOf(FieldError(SpecField.MAC, SpecError.INVALID_MAC)), errors(RecordSpec.Bluetooth("AA:BB:CC")))
        assertValid(RecordSpec.Bluetooth("aa:bb:cc:dd:ee:ff", "Speaker"))
    }

    @Test
    fun packageNameNeedsAtLeastTwoDottedSegments() {
        assertEquals(listOf(FieldError(SpecField.PACKAGE, SpecError.REQUIRED)), errors(RecordSpec.AppLaunch("")))
        assertEquals(listOf(FieldError(SpecField.PACKAGE, SpecError.INVALID_PACKAGE)), errors(RecordSpec.AppLaunch("camera")))
        assertEquals(listOf(FieldError(SpecField.PACKAGE, SpecError.INVALID_PACKAGE)), errors(RecordSpec.AppLaunch("1com.a")))
        assertValid(RecordSpec.AppLaunch("com.tagforge.app"))
    }

    @Test
    fun mimeNeedsTypeSlashSubtypeAndAPayload() {
        assertEquals(
            listOf(FieldError(SpecField.MIME_TYPE, SpecError.INVALID_MIME_TYPE)),
            errors(RecordSpec.Mime("plain", "hello")),
        )
        assertEquals(
            listOf(FieldError(SpecField.MIME_TYPE, SpecError.REQUIRED), FieldError(SpecField.PAYLOAD, SpecError.REQUIRED)),
            errors(RecordSpec.Mime("", "")),
        )
        assertValid(RecordSpec.Mime("text/plain", "hello"))
    }

    @Test
    fun externalNeedsDomainTypeAndEvenHex() {
        assertEquals(
            listOf(FieldError(SpecField.DOMAIN, SpecError.REQUIRED), FieldError(SpecField.TYPE, SpecError.REQUIRED)),
            errors(RecordSpec.External("", "", "")),
        )
        assertEquals(listOf(FieldError(SpecField.PAYLOAD, SpecError.INVALID_HEX)), errors(RecordSpec.External("a.com", "t", "ABC")))
        assertEquals(listOf(FieldError(SpecField.PAYLOAD, SpecError.INVALID_HEX)), errors(RecordSpec.External("a.com", "t", "ZZ")))
        assertValid(RecordSpec.External("a.com", "t", "01 02 ff"))
        assertValid(RecordSpec.External("a.com", "t", ""))
    }

    @Test
    fun validateAllReportsTheIndexOfEachInvalidRecord() {
        val specs = listOf(RecordSpec.Text("ok"), RecordSpec.Uri(""), RecordSpec.Phone("x"))
        val byIndex = RecordSpecValidator.validateAll(specs)
        assertEquals(setOf(1, 2), byIndex.keys)
        assertTrue(byIndex.getValue(1).single().error == SpecError.REQUIRED)
    }
}
