package com.tagforge.app.feature.read

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tagforge.app.R
import com.tagforge.app.feature.tools.TechSections
import com.tagforge.app.infrastructure.nfc.TagInfoExtractor
import com.tagforge.app.ui.components.EmptyState
import com.tagforge.app.ui.components.HexRowUi
import com.tagforge.app.ui.components.HexViewer
import com.tagforge.app.ui.components.RecordCard
import com.tagforge.domain.capability.AccessChannel
import com.tagforge.domain.capability.Support
import com.tagforge.domain.capability.TagCapabilities
import com.tagforge.domain.capability.ToolOperation
import com.tagforge.domain.capability.UnsupportedReason
import com.tagforge.domain.ntag.MemoryDump
import com.tagforge.domain.read.ReadNotice
import com.tagforge.domain.read.TagScan
import com.tagforge.domain.tag.TagFamily
import com.tagforge.domain.tag.TagInfo
import com.tagforge.domain.tag.TagTech

private enum class DetailTab(val labelRes: Int) {
    RECORDS(R.string.read_tab_records),
    INFO(R.string.read_tab_info),
    TECHS(R.string.read_tab_techs),
    HEX(R.string.read_tab_hex),
}

/** Result of a successful read: tabs Records | Info | Hex. Pure presentation of an already-built [TagScan]. */
@Composable
fun ReadDetail(scan: TagScan, modifier: Modifier = Modifier) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    // The Techs tab only exists when the OS reported something per technology (never for stored history entries).
    val hasTechs = scan.readout.info.details.sections(scan.readout.info).isNotEmpty()
    val tabs = DetailTab.entries.filter { it != DetailTab.TECHS || hasTechs }

    Column(modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selected) {
            tabs.forEachIndexed { index, tab ->
                Tab(selected = selected == index, onClick = { selected = index }, text = { Text(stringResource(tab.labelRes)) })
            }
        }
        when (tabs[selected]) {
            DetailTab.RECORDS -> RecordsTab(scan)
            DetailTab.INFO -> InfoTab(scan)
            DetailTab.TECHS -> TechSections(scan.readout.info)
            DetailTab.HEX -> HexTab(scan)
        }
    }
}

@Composable
private fun RecordsTab(scan: TagScan) {
    val readout = scan.readout
    if (readout.parsed.isEmpty()) {
        val notice = readout.notice
        val capabilities = TagCapabilities.of(readout.info)
        // A tag whose raw memory lives in another tool (never mixed into this scan) gets a pointer to it.
        val dumpInTools = capabilities.isSupported(ToolOperation.DUMP) && !capabilities.readIncludesDump
        EmptyState(
            icon = Icons.Outlined.Inbox,
            title = stringResource(R.string.read_empty_tag_title),
            body = stringResource(
                when (notice) {
                    is ReadNotice.NdefUnreadable -> R.string.read_notice_ndef_unreadable
                    ReadNotice.NotNdef -> if (dumpInTools) R.string.read_notice_not_ndef_dump else R.string.read_notice_not_ndef
                    is ReadNotice.UnsupportedFamily -> when (notice.family) {
                        TagFamily.MIFARE_CLASSIC -> R.string.read_notice_unsupported_classic
                        TagFamily.ISO14443_4, TagFamily.DESFIRE -> R.string.read_notice_needs_auth
                        else -> R.string.read_notice_unsupported_family
                    }
                    null -> R.string.read_empty_tag_body
                },
            ),
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                pluralStringResource(R.plurals.read_records_count, readout.parsed.size, readout.parsed.size),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        itemsIndexed(readout.parsed) { index, parsed -> RecordCard(parsed, index) }
    }
}

@Composable
private fun InfoTab(scan: TagScan) {
    val info = scan.readout.info
    val rows = tagInfoRows(info, scan.readout.message != null)
    val capabilities = capabilityRows(info)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(rows) { _, (label, value) -> InfoRow(label, value) }
        item {
            Text(stringResource(R.string.cap_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
        itemsIndexed(capabilities) { _, (label, value) -> InfoRow(label, value) }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer { Text(value, style = MaterialTheme.typography.bodyLarge) }
    }
}

/** Label/value pairs for the Info tab and the Protected screen (identity from pages 0-3 only). */
@Composable
internal fun tagInfoRows(info: TagInfo, hasMessage: Boolean): List<Pair<String, String>> {
    val unknown = stringResource(R.string.common_unknown)
    val yes = stringResource(R.string.common_yes)
    val no = stringResource(R.string.common_no)
    fun flag(value: Boolean?) = when (value) {
        true -> yes
        false -> no
        null -> unknown
    }

    val type = info.chipName
        ?: if (info.family == TagFamily.UNKNOWN && TagTech.NFC_A in info.techs) {
            stringResource(
                R.string.info_type_unknown_nfca,
                info.atqa?.let(TagInfoExtractor::formatAtqa) ?: unknown,
                info.sak?.let(TagInfoExtractor::formatSak) ?: unknown,
            )
        } else {
            familyLabel(info.family)
        }

    val memory = when {
        info.layout != null -> info.layout!!.let { stringResource(R.string.info_memory_value, (it.userEnd - it.userStart + 1) * 4, it.ccNdefSize) }
        info.ndef != null -> stringResource(R.string.info_memory_total, info.ndef!!.maxSize)
        else -> unknown
    }
    val ndef = when {
        hasMessage -> stringResource(R.string.info_ndef_present)
        info.ndef != null -> stringResource(R.string.info_ndef_empty)
        else -> stringResource(R.string.info_ndef_absent)
    }

    return buildList {
        add(uidLabel(info) to info.displayUid.toHex(":"))
        add(stringResource(R.string.info_manufacturer) to (TagInfoExtractor.manufacturerFor(info) ?: unknown))
        add(stringResource(R.string.info_type) to type)
        add(stringResource(R.string.info_technologies) to info.techs.joinToString(", ") { techLabel(it) })
        info.atqa?.let { add(stringResource(R.string.info_atqa) to TagInfoExtractor.formatAtqa(it)) }
        info.sak?.let { add(stringResource(R.string.info_sak) to TagInfoExtractor.formatSak(it)) }
        add(stringResource(R.string.info_memory) to memory)
        add(stringResource(R.string.info_writable) to flag(info.ndef?.isWritable))
        add(stringResource(R.string.info_can_lock) to flag(info.ndef?.canMakeReadOnly))
        add(stringResource(R.string.info_ndef) to ndef)
    }
}

@Composable
private fun familyLabel(family: TagFamily): String = stringResource(
    when (family) {
        TagFamily.NTAG21X -> R.string.info_family_ntag
        TagFamily.NTAG_I2C -> R.string.info_family_ntag_i2c
        TagFamily.ULTRALIGHT -> R.string.info_family_ultralight
        TagFamily.MIFARE_CLASSIC -> R.string.info_family_classic
        TagFamily.DESFIRE -> R.string.info_family_desfire
        TagFamily.ISO15693 -> R.string.info_family_iso15693
        TagFamily.FELICA -> R.string.info_family_felica
        TagFamily.ISO14443_4 -> R.string.info_family_iso14443_4
        TagFamily.UNKNOWN -> R.string.common_unknown
    },
)

/** Technology identifiers are proper names, shown as Android names them. */
internal fun techLabel(tech: TagTech): String = when (tech) {
    TagTech.NFC_A -> "NfcA"
    TagTech.NFC_B -> "NfcB"
    TagTech.NFC_F -> "NfcF"
    TagTech.NFC_V -> "NfcV"
    TagTech.ISO_DEP -> "IsoDep"
    TagTech.NDEF -> "Ndef"
    TagTech.NDEF_FORMATABLE -> "NdefFormatable"
    TagTech.MIFARE_CLASSIC -> "MifareClassic"
    TagTech.MIFARE_ULTRALIGHT -> "MifareUltralight"
}

@Composable
private fun HexTab(scan: TagScan) {
    val dump = scan.dump
    if (dump == null) {
        val capabilities = TagCapabilities.of(scan.readout.info)
        // dump == null with no error means "not attempted": it would need another technology, so it lives in Tools.
        val inTools = scan.dumpError == null && capabilities.isSupported(ToolOperation.DUMP) && !capabilities.readIncludesDump
        EmptyState(
            icon = Icons.Outlined.Inbox,
            title = stringResource(R.string.read_tab_hex),
            body = stringResource(if (inTools) R.string.hex_use_tools else R.string.hex_unavailable),
        )
        return
    }
    HexViewer(
        rows = dump.toRows(),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        footerNote = dump.unreadable?.let { stringResource(R.string.hex_unreadable_range, it.first, it.last) },
    )
}

private fun MemoryDump.toRows(): List<HexRowUi> = pages.mapIndexed { page, bytes -> HexRowUi(page, bytes) }

/** FeliCa calls the identifier IDm and a type B card PUPI; everything else says UID. */
@Composable
private fun uidLabel(info: TagInfo): String = stringResource(
    when {
        TagTech.NFC_F in info.techs && info.family == TagFamily.FELICA -> R.string.info_idm
        info.techs.contains(TagTech.NFC_B) && !info.techs.contains(TagTech.NFC_A) -> R.string.info_pupi
        else -> R.string.info_uid
    },
)

/** The capability matrix as rows: per operation, "Available (how)" or the reason it is not. */
@Composable
private fun capabilityRows(info: TagInfo): List<Pair<String, String>> {
    val capabilities = TagCapabilities.of(info)
    return ToolOperation.entries.map { operation ->
        val label = stringResource(operation.labelRes())
        val value = when (val support = capabilities[operation]) {
            is Support.Available -> stringResource(
                when (support.channel) {
                    AccessChannel.RAW_NFC_A -> R.string.cap_via_nfca
                    AccessChannel.RAW_NFC_V -> R.string.cap_via_nfcv
                    AccessChannel.CLASSIC_KEYS -> R.string.cap_via_classic
                    AccessChannel.PLATFORM_NDEF -> R.string.cap_via_platform
                },
            )
            is Support.Unavailable -> stringResource(
                when (support.reason) {
                    UnsupportedReason.CLASSIC_NEEDS_NXP_CHIP -> R.string.cap_reason_classic_nxp
                    UnsupportedReason.INFO_ONLY -> R.string.cap_reason_info_only
                    UnsupportedReason.NO_NDEF -> R.string.cap_reason_no_ndef
                    UnsupportedReason.NEEDS_AUTHENTICATION -> R.string.cap_reason_needs_auth
                    UnsupportedReason.NOT_FORMATABLE -> R.string.cap_reason_not_formatable
                },
            )
        }
        label to value
    }
}

private fun ToolOperation.labelRes(): Int = when (this) {
    ToolOperation.READ_NDEF -> R.string.cap_read_ndef
    ToolOperation.DUMP -> R.string.cap_dump
    ToolOperation.WRITE_NDEF -> R.string.cap_write
    ToolOperation.ERASE -> R.string.cap_erase
    ToolOperation.FORMAT -> R.string.cap_format
    ToolOperation.CLONE -> R.string.cap_clone
}
