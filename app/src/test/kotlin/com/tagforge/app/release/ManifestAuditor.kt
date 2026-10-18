package com.tagforge.app.release

import org.w3c.dom.Document
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

/** RB-04: what the merged release manifest must and must not contain. Pure, so the rules themselves are unit-tested. */
object ManifestAuditor {

    const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

    /** Exactly what the spec allows an app of this kind to ask for. */
    val ALLOWED_PERMISSIONS: Set<String> = setOf(
        "android.permission.NFC",
        "android.permission.VIBRATE",
        "android.permission.INTERNET",
        "android.permission.POST_NOTIFICATIONS",
        "android.permission.WRITE_SETTINGS",
        "com.android.alarm.permission.SET_ALARM",
        "android.permission.BLUETOOTH_CONNECT",
        "android.permission.CAMERA",
        "android.permission.ACCESS_NOTIFICATION_POLICY",
        "android.permission.CHANGE_WIFI_STATE",
    )

    val FORBIDDEN_PERMISSIONS: Set<String> = setOf(
        "android.permission.QUERY_ALL_PACKAGES",
        "android.permission.SEND_SMS",
        "android.permission.CALL_PHONE",
    )

    fun parse(xml: String): Document =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(xml.byteInputStream())

    /** Human-readable violations; empty means the manifest passes. */
    fun audit(xml: String, ownPackage: String = "com.tagforge.app"): List<String> {
        val doc = parse(xml)
        val problems = mutableListOf<String>()
        val application = doc.getElementsByTagName("application").item(0) as? Element
        if (application == null) return listOf("no <application> element")

        if (application.attr("debuggable") == "true") problems += "application is debuggable"
        val backupOff = application.attr("allowBackup") == "false"
        val rules = application.attr("dataExtractionRules").isNotEmpty() || application.attr("fullBackupContent").isNotEmpty()
        if (!backupOff && !rules) problems += "allowBackup is on and no data extraction rules exclude the database"

        val permissions = names(doc, "uses-permission") + names(doc, "uses-permission-sdk-23")
        for (permission in permissions) {
            if (permission in FORBIDDEN_PERMISSIONS) problems += "forbidden permission $permission"
            else if (permission !in ALLOWED_PERMISSIONS && !permission.startsWith("$ownPackage.")) problems += "permission outside the allowlist: $permission"
        }

        val nfc = elements(doc, "uses-feature").firstOrNull { it.attr("name") == "android.hardware.nfc" }
        if (nfc == null) problems += "missing <uses-feature android.hardware.nfc>"
        else if (nfc.attr("required") != "false") problems += "nfc feature must be required=false"
        return problems
    }

    private fun Element.attr(name: String): String = getAttributeNS(ANDROID_NS, name)

    private fun elements(doc: Document, tag: String): List<Element> =
        doc.getElementsByTagName(tag).let { nodes -> (0 until nodes.length).map { nodes.item(it) as Element } }

    private fun names(doc: Document, tag: String): List<String> = elements(doc, tag).map { it.attr("name") }
}
