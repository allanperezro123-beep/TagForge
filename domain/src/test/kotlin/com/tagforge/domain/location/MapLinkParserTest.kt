package com.tagforge.domain.location

import kotlin.test.Test
import kotlin.test.assertEquals

class MapLinkParserTest {

    private fun coordinates(text: String): MapLink.Coordinates = MapLinkParser.parse(text) as MapLink.Coordinates

    @Test
    fun aGoogleMapsViewportLinkGivesItsCenter() {
        val found = coordinates("https://www.google.com/maps/@40.4168,-3.7038,15z")

        assertEquals("40.4168", found.latitude)
        assertEquals("-3.7038", found.longitude)
    }

    @Test
    fun aPlaceLinkPrefersThePinOverTheViewportAndKeepsThePlaceName() {
        val url = "https://www.google.com/maps/place/Puerta+del+Sol/@40.4169,-3.7035,17z/data=!3m1!4b1!4m6!3m5!1s0xd42287a:0x1!8m2!3d40.4169473!4d-3.7035285"

        val found = coordinates(url)

        assertEquals("40.4169473", found.latitude)
        assertEquals("-3.7035285", found.longitude)
        assertEquals("Puerta del Sol", found.label)
    }

    @Test
    fun theQueryFormsOfGoogleAndAppleMapsAreUnderstood() {
        assertEquals("40.4168" to "-3.7038", coordinates("https://www.google.com/maps?q=40.4168,-3.7038").let { it.latitude to it.longitude })
        assertEquals("40.4168" to "-3.7038", coordinates("https://maps.google.com/?ll=40.4168,-3.7038&z=10").let { it.latitude to it.longitude })
        assertEquals("40.4168" to "-3.7038", coordinates("https://www.google.com/maps/search/?api=1&query=40.4168%2C-3.7038").let { it.latitude to it.longitude })
        assertEquals("40.4168" to "-3.7038", coordinates("https://maps.apple.com/?ll=40.4168,-3.7038&q=Madrid").let { it.latitude to it.longitude })
    }

    @Test
    fun aGeoUriGivesItsCoordinatesAndALabelWhenThereIsOne() {
        assertEquals("40.4168" to "-3.7038", coordinates("geo:40.4168,-3.7038?z=12").let { it.latitude to it.longitude })

        val labelled = coordinates("geo:0,0?q=40.4168,-3.7038(Sol)")
        assertEquals("40.4168" to "-3.7038", labelled.latitude to labelled.longitude)
        assertEquals("Sol", labelled.label)
    }

    @Test
    fun coordinatesTypedByThemselvesAreAccepted() {
        assertEquals("40.4168" to "-3.7038", coordinates("40.4168, -3.7038").let { it.latitude to it.longitude })
        assertEquals("-34.6037" to "-58.3816", coordinates("  -34.6037,-58.3816 ").let { it.latitude to it.longitude })
    }

    @Test
    fun aShortLinkHasNoCoordinatesSoItIsKeptAsAWebLink() {
        assertEquals(MapLink.Web("https://maps.app.goo.gl/AbCdEf12345"), MapLinkParser.parse("https://maps.app.goo.gl/AbCdEf12345"))
        assertEquals(MapLink.Web("https://goo.gl/maps/AbCdEf"), MapLinkParser.parse("https://goo.gl/maps/AbCdEf"))
    }

    @Test
    fun aGoogleMapsShareKeepsTheLinkAndTheTextBeforeItAsTheTitle() {
        val shared = "Torre Eiffel\nhttps://maps.app.goo.gl/abc123"

        assertEquals(MapLink.Web("https://maps.app.goo.gl/abc123", "Torre Eiffel"), MapLinkParser.parse(shared))
    }

    @Test
    fun aSharedLinkWithCoordinatesBecomesCoordinates() {
        val found = coordinates("Mi casa https://www.google.com/maps?q=10.5,-66.9")

        assertEquals("10.5" to "-66.9", found.latitude to found.longitude)
    }

    @Test
    fun coordinatesOutsideTheEarthAreNotCoordinates() {
        assertEquals(MapLink.Unrecognized, MapLinkParser.parse("91.0, 10.0"))
        assertEquals(MapLink.Unrecognized, MapLinkParser.parse("10.0, 181.0"))
    }

    @Test
    fun onlyWebAndGeoSchemesAreEverAccepted() {
        assertEquals(MapLink.Unrecognized, MapLinkParser.parse("javascript:alert(1)"))
        assertEquals(MapLink.Unrecognized, MapLinkParser.parse("intent://maps#Intent;scheme=geo;end"))
        assertEquals(MapLink.Unrecognized, MapLinkParser.parse("file:///sdcard/secret.txt"))
        assertEquals(MapLink.Unrecognized, MapLinkParser.parse("just some words"))
    }

    @Test
    fun aLinkThatHidesItsRealHostBehindUserInfoIsRejected() {
        assertEquals(MapLink.Unrecognized, MapLinkParser.parse("https://www.google.com@evil.example/maps"))
    }
}
