package com.tagforge.app.feature.productivity

import com.tagforge.domain.ndef.spec.RecordSpec

/** What the "Create a QR code" form can make. Each kind edits one [RecordSpec]; [com.tagforge.domain.exchange.QrPayloadMapper] turns it into the QR text. */
enum class QrKind {
    TEXT, URL, WIFI, CONTACT, LOCATION;

    fun newSpec(): RecordSpec = when (this) {
        TEXT -> RecordSpec.Text()
        URL -> RecordSpec.Uri()
        WIFI -> RecordSpec.Wifi()
        CONTACT -> RecordSpec.Contact()
        LOCATION -> RecordSpec.Geo()
    }
}

/** The form: the chosen [kind] and what has been typed so far. */
data class QrCreateState(val kind: QrKind = QrKind.URL, val spec: RecordSpec = QrKind.URL.newSpec())
