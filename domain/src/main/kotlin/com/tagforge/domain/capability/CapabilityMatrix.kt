package com.tagforge.domain.capability

import com.tagforge.domain.tag.TagFamily
import com.tagforge.domain.tag.TagInfo
import com.tagforge.domain.tag.TagTech

/**
 * Pure family -> supported operations table (no I/O), unit tested. The rules, in order:
 *
 *  1. A Type 2 chip Tag Forge knows page by page (NTAG21x and the other Type 2 chips) does everything over raw NfcA.
 *  2. MIFARE Classic needs the NXP controller (the `MifareClassic` technology); with it, dump and clone use the public
 *     default keys and NDEF goes through the platform. Without it nothing is possible, and the reason says so.
 *  3. Everything else: raw dump only where a raw protocol is implemented (ISO 15693), and NDEF through the platform
 *     whenever Android exposes `Ndef` / `NdefFormatable` for the tag.
 */
object TagCapabilities {

    fun of(info: TagInfo): Capabilities = Capabilities(ToolOperation.entries.associateWith { support(info, it) })

    private fun support(info: TagInfo, operation: ToolOperation): Support = when {
        info.layout != null -> Support.Available(AccessChannel.RAW_NFC_A)
        info.family == TagFamily.MIFARE_CLASSIC -> classic(info, operation)
        else -> generic(info, operation)
    }

    private fun classic(info: TagInfo, operation: ToolOperation): Support {
        val techs = info.techs
        if (TagTech.MIFARE_CLASSIC !in techs && TagTech.NDEF !in techs && TagTech.NDEF_FORMATABLE !in techs) {
            return Support.Unavailable(UnsupportedReason.CLASSIC_NEEDS_NXP_CHIP)
        }
        return when (operation) {
            ToolOperation.DUMP, ToolOperation.CLONE ->
                if (TagTech.MIFARE_CLASSIC in techs) {
                    Support.Available(AccessChannel.CLASSIC_KEYS)
                } else {
                    Support.Unavailable(UnsupportedReason.CLASSIC_NEEDS_NXP_CHIP)
                }
            else -> platformNdef(info, operation)
        }
    }

    private fun generic(info: TagInfo, operation: ToolOperation): Support = when (operation) {
        ToolOperation.DUMP -> when {
            TagTech.NFC_V in info.techs && info.family == TagFamily.ISO15693 -> Support.Available(AccessChannel.RAW_NFC_V)
            info.family == TagFamily.ISO14443_4 || info.family == TagFamily.DESFIRE ->
                Support.Unavailable(UnsupportedReason.NEEDS_AUTHENTICATION)
            else -> Support.Unavailable(UnsupportedReason.INFO_ONLY)
        }
        else -> platformNdef(info, operation)
    }

    /** NDEF through Android's `Ndef` / `NdefFormatable`; the reason distinguishes "no NDEF" from "nothing at all". */
    private fun platformNdef(info: TagInfo, operation: ToolOperation): Support {
        val ndef = TagTech.NDEF in info.techs
        val formatable = TagTech.NDEF_FORMATABLE in info.techs
        val available = Support.Available(AccessChannel.PLATFORM_NDEF)
        val noNdef = Support.Unavailable(if (ndef || formatable || hasRawProtocol(info)) UnsupportedReason.NO_NDEF else UnsupportedReason.INFO_ONLY)
        return when (operation) {
            ToolOperation.READ_NDEF, ToolOperation.ERASE, ToolOperation.CLONE -> if (ndef) available else noNdef
            ToolOperation.WRITE_NDEF -> if (ndef || formatable) available else noNdef
            ToolOperation.FORMAT -> when {
                formatable -> available
                ndef -> Support.Unavailable(UnsupportedReason.NOT_FORMATABLE)
                else -> noNdef
            }
            ToolOperation.DUMP -> Support.Unavailable(UnsupportedReason.INFO_ONLY)
        }
    }

    private fun hasRawProtocol(info: TagInfo): Boolean =
        info.family == TagFamily.ISO15693 || info.family == TagFamily.MIFARE_CLASSIC
}
