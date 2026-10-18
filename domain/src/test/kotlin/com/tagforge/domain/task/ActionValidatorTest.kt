package com.tagforge.domain.task

import kotlin.test.Test
import kotlin.test.assertEquals

/** TK-06 "Dangerous URL scheme", TK-08 and the parameter sanity of Sms/Dial: tag content is untrusted. */
class ActionValidatorTest {

    private fun check(action: TaskAction) = ActionValidator.check(action)

    private fun blocked(reason: BlockReason) = ActionCheck.Blocked(reason)

    // ---- URL scheme allowlist ----------------------------------------------------------------------------

    @Test
    fun onlyHttpHttpsTelMailtoGeoAndSmsMayBeOpened() {
        listOf(
            "http://example.com", "https://example.com/a?b=1", "tel:+573001234567", "mailto:a@b.co",
            "geo:4.6,-74.0", "sms:+573001234567?body=hi", "HTTPS://EXAMPLE.COM",
        ).forEach { assertEquals(ActionCheck.Ok, check(TaskAction.OpenUrl(it)), it) }
    }

    @Test
    fun intentFileContentAndJavascriptSchemesAreBlocked() {
        listOf(
            "intent://scan/#Intent;scheme=zxing;end", "file:///sdcard/secret.txt", "content://com.android.contacts/contacts",
            "javascript:alert(1)", "JaVaScRiPt:alert(1)", "data:text/html,<h1>x</h1>", "market://details?id=a.b", "ftp://host/x",
            "android-app://com.example", "tagforge://x",
        ).forEach { assertEquals(blocked(BlockReason.DISALLOWED_SCHEME), check(TaskAction.OpenUrl(it)), it) }
    }

    @Test
    fun urlsWithoutAUsableSchemeOrHostAreBlockedAsInvalid() {
        listOf("example.com", "//example.com", "https://", "http:///path", " intent:evil", "https://exa mple.com", "https://a.b/\u0000").forEach {
            assertEquals(blocked(BlockReason.INVALID_URL), check(TaskAction.OpenUrl(it)), it)
        }
        assertEquals(blocked(BlockReason.EMPTY_VALUE), check(TaskAction.OpenUrl("")))
        assertEquals(blocked(BlockReason.EMPTY_VALUE), check(TaskAction.OpenUrl("   ")))
    }

    // ---- HTTP request ---------------------------------------------------------------------------------------

    @Test
    fun httpsRequestsAreFineAndPlainHttpCarriesAnExtraWarning() {
        assertEquals(ActionCheck.Ok, check(TaskAction.HttpRequest("https://example.com/hook")))
        assertEquals(ActionCheck.Ok, check(TaskAction.HttpRequest("https://example.com/hook", HttpMethod.POST)))
        assertEquals(ActionCheck.Warn(ActionWarning.INSECURE_HTTP), check(TaskAction.HttpRequest("http://192.168.1.5/on")))
    }

    @Test
    fun httpRequestsRefuseEverySchemeExceptHttpAndHttps() {
        listOf("tel:123", "intent://x", "file:///x", "ftp://x/y").forEach {
            assertEquals(blocked(BlockReason.DISALLOWED_SCHEME), check(TaskAction.HttpRequest(it)), it)
        }
        assertEquals(blocked(BlockReason.INVALID_URL), check(TaskAction.HttpRequest("https://")))
        assertEquals(blocked(BlockReason.EMPTY_VALUE), check(TaskAction.HttpRequest("")))
    }

    // ---- numbers and packages -------------------------------------------------------------------------------

    @Test
    fun phoneNumbersAcceptDialableCharactersOnly() {
        listOf("+57 300 123 4567", "3001234567", "*123#", "(601) 555-0100", "112").forEach {
            assertEquals(ActionCheck.Ok, check(TaskAction.Dial(it)), it)
            assertEquals(ActionCheck.Ok, check(TaskAction.SmsCompose(it, "hi")), it)
        }
        listOf("call me", "123; DROP", "tel:123", "+", "()-", "1".repeat(33)).forEach {
            assertEquals(blocked(BlockReason.INVALID_NUMBER), check(TaskAction.Dial(it)), it)
            assertEquals(blocked(BlockReason.INVALID_NUMBER), check(TaskAction.SmsCompose(it, "hi")), it)
        }
        assertEquals(blocked(BlockReason.EMPTY_VALUE), check(TaskAction.Dial("")))
    }

    @Test
    fun smsBodyMayBeEmptyButNotHuge() {
        assertEquals(ActionCheck.Ok, check(TaskAction.SmsCompose("123", "")))
        assertEquals(blocked(BlockReason.TOO_LONG), check(TaskAction.SmsCompose("123", "x".repeat(256))))
    }

    @Test
    fun packageNamesNeedTheJavaIdentifierShape() {
        assertEquals(ActionCheck.Ok, check(TaskAction.LaunchApp("com.spotify.music")))
        assertEquals(ActionCheck.Ok, check(TaskAction.LaunchApp("com.tagforge.app.debug")))
        listOf("spotify", "com..x", "1com.x", "com.x y", "com.x/y", ".com.x").forEach {
            assertEquals(blocked(BlockReason.INVALID_PACKAGE), check(TaskAction.LaunchApp(it)), it)
        }
        assertEquals(blocked(BlockReason.EMPTY_VALUE), check(TaskAction.LaunchApp("")))
    }

    // ---- ranges ---------------------------------------------------------------------------------------------

    @Test
    fun numericParametersMustBeInRange() {
        assertEquals(ActionCheck.Ok, check(TaskAction.Alarm(0, 0)))
        assertEquals(ActionCheck.Ok, check(TaskAction.Alarm(23, 59)))
        assertEquals(blocked(BlockReason.OUT_OF_RANGE), check(TaskAction.Alarm(24, 0)))
        assertEquals(blocked(BlockReason.OUT_OF_RANGE), check(TaskAction.Alarm(7, 60)))
        assertEquals(blocked(BlockReason.OUT_OF_RANGE), check(TaskAction.Alarm(-1, 0)))

        assertEquals(ActionCheck.Ok, check(TaskAction.Timer(1)))
        assertEquals(blocked(BlockReason.OUT_OF_RANGE), check(TaskAction.Timer(0)))
        assertEquals(blocked(BlockReason.OUT_OF_RANGE), check(TaskAction.Timer(65536)))

        assertEquals(ActionCheck.Ok, check(TaskAction.SetVolume(VolumeStream.MEDIA, 100)))
        assertEquals(blocked(BlockReason.OUT_OF_RANGE), check(TaskAction.SetVolume(VolumeStream.MEDIA, 101)))
        assertEquals(ActionCheck.Ok, check(TaskAction.SetBrightness(255)))
        assertEquals(blocked(BlockReason.OUT_OF_RANGE), check(TaskAction.SetBrightness(256)))
        assertEquals(blocked(BlockReason.OUT_OF_RANGE), check(TaskAction.CalendarEvent("x", 0)))
    }

    @Test
    fun textActionsNeedTextAndWifiNeedsAUsableNetwork() {
        assertEquals(blocked(BlockReason.EMPTY_VALUE), check(TaskAction.Speak(" ")))
        assertEquals(blocked(BlockReason.EMPTY_VALUE), check(TaskAction.Notify("", "body")))
        assertEquals(ActionCheck.Ok, check(TaskAction.Notify("Title", "")))
        assertEquals(blocked(BlockReason.EMPTY_VALUE), check(TaskAction.CalendarEvent(" ", 30)))
        assertEquals(blocked(BlockReason.TOO_LONG), check(TaskAction.Speak("x".repeat(256))))

        assertEquals(ActionCheck.Ok, check(TaskAction.WifiJoin("Home", "pass1234", JoinAuth.WPA2)))
        assertEquals(ActionCheck.Ok, check(TaskAction.WifiJoin("Cafe", "", JoinAuth.OPEN)))
        assertEquals(blocked(BlockReason.EMPTY_VALUE), check(TaskAction.WifiJoin("", "pass1234", JoinAuth.WPA2)))
        assertEquals(blocked(BlockReason.OUT_OF_RANGE), check(TaskAction.WifiJoin("a".repeat(33), "pass1234")))
        assertEquals(blocked(BlockReason.OUT_OF_RANGE), check(TaskAction.WifiJoin("Home", "short", JoinAuth.WPA2)))
    }

    @Test
    fun switchesAndShortcutsHaveNothingToValidate() {
        listOf(
            TaskAction.SetRinger(RingerMode.SILENT), TaskAction.WifiToggle(true), TaskAction.BluetoothToggle(false),
            TaskAction.Flashlight(true), TaskAction.MediaKey(MediaButton.NEXT), TaskAction.OpenSettings(SettingsTarget.NFC),
        ).forEach { assertEquals(ActionCheck.Ok, check(it), it.toString()) }
    }

    // ---- S2: the host the user sees is the host that is contacted ------------------------------------------------------------

    @Test
    fun urlsWithUserInfoAreBlockedBecauseTheyDisguiseTheRealHost() {
        listOf(
            "https://trusted.example.com@evil.example.net/login", "http://user:secret@example.com/", "https://a.com\\@evil.com/",
            "https://@evil.com", "HTTPS://bank.com@evil.com",
        ).forEach {
            assertEquals(blocked(BlockReason.INVALID_URL), check(TaskAction.OpenUrl(it)), it)
            assertEquals(blocked(BlockReason.INVALID_URL), check(TaskAction.HttpRequest(it, HttpMethod.GET)), it)
        }
    }

    @Test
    fun anAtSignOutsideTheAuthorityIsFine() {
        listOf("https://example.com/@user", "https://example.com/a?mail=a@b.co", "https://example.com/#x@y", "mailto:a@b.co")
            .forEach { assertEquals(ActionCheck.Ok, check(TaskAction.OpenUrl(it)), it) }
    }

    @Test
    fun theDisplayedHostIsTheAuthorityInLowerCase() {
        assertEquals("example.com", UrlHost.of("https://Example.COM/a/b?c=d"))
        assertEquals("example.com:8443", UrlHost.of("HTTP://example.com:8443/x"))
        assertEquals("192.168.1.5", UrlHost.of("http://192.168.1.5/on"))
        assertEquals("example.com", UrlHost.of("https://example.com"))
        assertEquals(null, UrlHost.of("tel:+573001234567"))
        assertEquals(null, UrlHost.of("mailto:a@b.co"))
    }
}
