package com.tagforge.domain.location

import com.tagforge.domain.ndef.spec.RecordSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** The text another app shares to Tag Forge is untrusted: it only ever becomes a record to review, never an action. */
class SharedTextTest {

    private fun record(text: String?): RecordSpec = (SharedText.parse(text) as SharedTextResult.Record).spec

    @Test
    fun aMapsLinkWithCoordinatesBecomesAGeoRecord() {
        assertEquals(RecordSpec.Geo("40.4168", "-3.7038", ""), record("https://www.google.com/maps/@40.4168,-3.7038,15z"))
    }

    @Test
    fun aPlaceNameBecomesTheGeoQuery() {
        val spec = record("https://www.google.com/maps/place/Puerta+del+Sol/@40.4169,-3.7035,17z/data=!3d40.4169473!4d-3.7035285")

        assertEquals(RecordSpec.Geo("40.4169473", "-3.7035285", "Puerta del Sol"), spec)
    }

    @Test
    fun aShortLinkBecomesAWebLinkRecordThatOpensMapsWhenScanned() {
        assertEquals(RecordSpec.Uri("https://maps.app.goo.gl/abc123"), record("Torre Eiffel\nhttps://maps.app.goo.gl/abc123"))
    }

    @Test
    fun anyOtherWebLinkIsKeptAsALinkRecord() {
        assertEquals(RecordSpec.Uri("https://example.com/menu"), record("Menu: https://example.com/menu"))
    }

    @Test
    fun plainTextIsKeptAsATextRecord() {
        assertEquals(RecordSpec.Text("hola mundo"), record("  hola mundo  "))
    }

    @Test
    fun aDangerousSchemeIsOnlyEverTextNeverALink() {
        assertEquals(RecordSpec.Text("javascript:alert(1)"), record("javascript:alert(1)"))
        assertEquals(RecordSpec.Text("intent://x#Intent;end"), record("intent://x#Intent;end"))
    }

    @Test
    fun emptyOrMissingTextIsRefused() {
        assertIs<SharedTextResult.Empty>(SharedText.parse(null))
        assertIs<SharedTextResult.Empty>(SharedText.parse("   \n"))
    }

    @Test
    fun aHugeShareIsRefusedInsteadOfBeingTruncatedIntoAWrongLink() {
        assertIs<SharedTextResult.TooLong>(SharedText.parse("https://example.com/" + "a".repeat(SharedText.MAX_CHARS)))
        assertIs<SharedTextResult.Record>(SharedText.parse("a".repeat(SharedText.MAX_CHARS)))
    }
}
