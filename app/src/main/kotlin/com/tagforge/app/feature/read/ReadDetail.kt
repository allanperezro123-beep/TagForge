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
import com.tagforge.app.infrastructure.nfc.TagInfoExtractor
import com.tagforge.app.ui.components.EmptyState
import com.tagforge.app.ui.components.HexRowUi
import com.tagforge.app.ui.components.HexViewer
import com.tagforge.app.ui.components.RecordCard
import com.tagforge.domain.ntag.MemoryDump
import com.tagforge.domain.read.ReadNotice
import com.tagforge.domain.read.TagScan
import com.tagforge.domain.tag.TagFamily
import com.tagforge.domain.tag.TagInfo
import com.tagforge.domain.tag.TagTech

private enum class DetailTab(val labelRes: Int) {
    RECORDS(R.string.read_tab_records),
    INFO(R.string.read_tab_info),
    HEX(R.string.read_tab_hex),
}

/** Result of a successful read: tabs Records | Info | Hex. Pure presentation of an already-built [TagScan]. */
@Composable
fun ReadDetail(scan: TagScan, modifier: Modifier = Modifier) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val tabs = DetailTab.entries

    Column(modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selected) {
            tabs.forEachIndexed { index, tab ->
                Tab(selected = selected == index, onClick = { selected = index }, text = { Text(stringResource(tab.labelRes)) })
            }
        }
        when (tabs[selected]) {
            DetailTab.RECORDS -> RecordsTab(scan)
            DetailTab.INFO -> InfoTab(scan)
            DetailTab.HEX -> HexTab(scan)
        }
    }
}

@Composable
private fun RecordsTab(scan: TagScan) {
    val readout = scan.readout
    if (readout.parsed.isEmpty()) {
        val notice = readout.notice
        EmptyState(
            icon = Icons.Outlined.Inbox,
            title = stringResource(R.string.read_empty_tag_title),
            body = stringResource(
                when (notice) {
                    is ReadNotice.NdefUnreadable -> R.string.read_notice_ndef_unreadable
                    ReadNotice.NotNdef -> R.string.read_notice_not_ndef
                    is ReadNotice.UnsupportedFamily ->
                        if (notice.family == TagFamily.MIFARE_CLASSIC) R.string.read_notice_unsupported_classic else R.string.read_notice_unsupported_family
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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(rows) { _, (label, value) -> InfoRow(label, value) }
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

    val type = info.ntag?.displayName
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
        info.ntag != null -> stringResource(R.string.info_memory_value, info.ntag!!.userBytes, info.ntag!!.ccNdefSize)
        info.ndef != null -> stringResource(R.string.info_memory_total, info.ndef!!.maxSize)
        else -> unknown
    }
    val ndef = when {
        hasMessage -> stringResource(R.string.info_ndef_present)
        info.ndef != null -> stringResource(R.string.info_ndef_empty)
        else -> stringResource(R.string.info_ndef_absent)
    }

    return buildList {
        add(stringResource(R.string.info_uid) to info.uid.toHex(":"))
        add(stringResource(R.string.info_manufacturer) to (TagInfoExtractor.manufacturerFor(info.uid) ?: unknown))
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
private fun techLabel(tech: TagTech): String = when (tech) {
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
        EmptyState(
            icon = Icons.Outlined.Inbox,
            title = stringResource(R.string.read_tab_hex),
            body = stringResource(R.string.hex_unavailable),
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
