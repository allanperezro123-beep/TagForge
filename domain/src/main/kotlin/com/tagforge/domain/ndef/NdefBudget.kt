package com.tagforge.domain.ndef

import com.tagforge.domain.ntag.NtagModel

/**
 * Size accounting for the write wizard's budget meter.
 * @property used encoded NDEF message bytes (without TLV framing)
 * @property capacity maximum message bytes: `Ndef.maxSize` once a tag is detected, a preset otherwise
 * @property perRecord encoded size of each record (sums to [used])
 * @property tlvOverhead NDEF TLV tag + length (1 or 3 bytes) + terminator, shown separately from [used]
 */
data class BudgetReport(
    val used: Int,
    val capacity: Int,
    val perRecord: List<Int>,
    val tlvOverhead: Int,
) {
    val fits: Boolean get() = used <= capacity
    val overBy: Int get() = maxOf(0, used - capacity)
    val usedFraction: Float get() = if (capacity == 0) 0f else used.toFloat() / capacity
}

object NdefBudget {

    private const val MAX_ONE_BYTE_TLV_LENGTH = 254
    private const val TLV_TAG_SIZE = 1
    private const val TERMINATOR_SIZE = 1

    fun budget(message: NdefMessage, capacity: Int): BudgetReport {
        val perRecord = message.records.map(DefaultNdefCodec::encodedSize)
        val used = perRecord.sum()
        return BudgetReport(used, capacity, perRecord, tlvOverhead(used))
    }

    /** TLV tag (1) + length (1 or 3) + terminator (1) = 3, or 5 for messages of 255+ bytes. */
    fun tlvOverhead(messageSize: Int): Int {
        val lengthField = if (messageSize <= MAX_ONE_BYTE_TLV_LENGTH) 1 else 3
        return TLV_TAG_SIZE + lengthField + TERMINATOR_SIZE
    }

    /**
     * What Android's `Ndef.getMaxSize()` reports for a freshly formatted [model]: CC size minus the factory Lock
     * Control TLV (213 only) minus the NDEF TLV header (2 bytes, 4 for long messages). NTAG213 = 137, 215 = 492,
     * 216 = 868. Used as the meter capacity for a chosen target so it matches what a scanned tag will report.
     */
    fun androidMaxSize(model: NtagModel): Int {
        val area = model.ccNdefSize - (model.factoryLockControlTlv?.size ?: 0)
        return area - if (area - 2 <= MAX_ONE_BYTE_TLV_LENGTH) 2 else 4
    }

    /** The smallest (cheapest) chip whose [androidMaxSize] holds [messageSize] bytes, or null when none does. */
    fun smallestChipFor(messageSize: Int): NtagModel? =
        NtagModel.entries.firstOrNull { androidMaxSize(it) >= messageSize }

    /**
     * Capacity when no tag has been scanned: CC NDEF size, minus the factory Lock Control TLV (NTAG213 only),
     * minus the NDEF TLV header (2 or 4 bytes) and the terminator. NTAG213 = 136.
     */
    fun presetCapacity(model: NtagModel): Int {
        val area = model.ccNdefSize - (model.factoryLockControlTlv?.size ?: 0)
        val withShortHeader = area - 2 - TERMINATOR_SIZE
        return if (withShortHeader <= MAX_ONE_BYTE_TLV_LENGTH) withShortHeader else area - 4 - TERMINATOR_SIZE
    }
}
