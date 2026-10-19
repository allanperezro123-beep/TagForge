package com.tagforge.app.feature.tools

import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.tagforge.app.R
import com.tagforge.app.ui.theme.extendedColors
import com.tagforge.domain.ntag.Region
import com.tagforge.domain.tag.DetailKey
import com.tagforge.domain.tag.TechValue

/** Short name of a page role, shown in legend chips and next to a row. Never the only cue: colour is extra. */
@StringRes
fun Region.labelRes(): Int = when (this) {
    Region.UID -> R.string.region_uid
    Region.STATIC_LOCK -> R.string.region_static_lock
    Region.CC -> R.string.region_cc
    Region.USER -> R.string.region_user
    Region.DYN_LOCK -> R.string.region_dyn_lock
    Region.CFG0 -> R.string.region_cfg0
    Region.CFG1 -> R.string.region_cfg1
    Region.PWD -> R.string.region_pwd
    Region.PACK -> R.string.region_pack
    Region.COUNTER -> R.string.region_counter
    Region.AUTH_CONFIG -> R.string.region_auth_config
    Region.KEY -> R.string.region_key
    Region.SECTOR_TRAILER -> R.string.region_sector_trailer
}

/** One-paragraph explanation for the row-tap bottom sheet. */
@StringRes
fun Region.explanationRes(): Int = when (this) {
    Region.UID -> R.string.region_uid_explain
    Region.STATIC_LOCK -> R.string.region_static_lock_explain
    Region.CC -> R.string.region_cc_explain
    Region.USER -> R.string.region_user_explain
    Region.DYN_LOCK -> R.string.region_dyn_lock_explain
    Region.CFG0 -> R.string.region_cfg0_explain
    Region.CFG1 -> R.string.region_cfg1_explain
    Region.PWD -> R.string.region_pwd_explain
    Region.PACK -> R.string.region_pack_explain
    Region.COUNTER -> R.string.region_counter_explain
    Region.AUTH_CONFIG -> R.string.region_auth_config_explain
    Region.KEY -> R.string.region_key_explain
    Region.SECTOR_TRAILER -> R.string.region_sector_trailer_explain
}

/** Band colour per role: all are theme colours, so contrast follows the theme (and the legend text carries the meaning). */
@Composable
fun Region.bandColor(): Color = when (this) {
    Region.UID -> MaterialTheme.colorScheme.outline
    Region.STATIC_LOCK, Region.DYN_LOCK, Region.SECTOR_TRAILER -> MaterialTheme.extendedColors.warning
    Region.CC -> MaterialTheme.colorScheme.tertiary
    Region.USER -> MaterialTheme.colorScheme.primary
    Region.CFG0, Region.CFG1, Region.AUTH_CONFIG, Region.COUNTER -> MaterialTheme.colorScheme.secondary
    Region.PWD, Region.PACK, Region.KEY -> MaterialTheme.colorScheme.error
}

@StringRes
fun DetailKey.labelRes(): Int = when (this) {
    DetailKey.ATQA -> R.string.tech_atqa
    DetailKey.SAK -> R.string.tech_sak
    DetailKey.APPLICATION_DATA -> R.string.tech_application_data
    DetailKey.PROTOCOL_INFO -> R.string.tech_protocol_info
    DetailKey.SYSTEM_CODE -> R.string.tech_system_code
    DetailKey.MANUFACTURER -> R.string.tech_manufacturer
    DetailKey.DSF_ID -> R.string.tech_dsf_id
    DetailKey.RESPONSE_FLAGS -> R.string.tech_response_flags
    DetailKey.AFI -> R.string.tech_afi
    DetailKey.BLOCK_COUNT -> R.string.tech_block_count
    DetailKey.BLOCK_SIZE -> R.string.tech_block_size
    DetailKey.HISTORICAL_BYTES -> R.string.tech_historical_bytes
    DetailKey.HI_LAYER_RESPONSE -> R.string.tech_hi_layer_response
    DetailKey.EXTENDED_LENGTH -> R.string.tech_extended_length
    DetailKey.TIMEOUT_MS -> R.string.tech_timeout
    DetailKey.ULTRALIGHT_TYPE -> R.string.tech_ultralight_type
    DetailKey.PAGES -> R.string.tech_pages
    DetailKey.CLASSIC_TYPE -> R.string.tech_classic_type
    DetailKey.SECTORS -> R.string.tech_sectors
    DetailKey.BLOCKS -> R.string.tech_blocks
    DetailKey.SIZE_BYTES -> R.string.tech_size
}

/** Units that belong to the label, not to the stored value, so a value can be copied as plain digits. */
@Composable
fun TechValue.display(key: DetailKey): String = when (this) {
    is TechValue.Text -> when (key) {
        DetailKey.SIZE_BYTES -> stringResource(R.string.tech_value_bytes, text)
        DetailKey.TIMEOUT_MS -> stringResource(R.string.tech_value_ms, text)
        else -> text
    }
    is TechValue.Flag -> stringResource(if (on) R.string.common_yes else R.string.common_no)
    TechValue.Unknown -> stringResource(R.string.tech_not_reported)
}
