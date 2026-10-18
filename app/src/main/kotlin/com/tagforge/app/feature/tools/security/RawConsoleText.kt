package com.tagforge.app.feature.tools.security

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tagforge.app.R
import com.tagforge.app.ui.text.TagErrorText
import com.tagforge.app.ui.text.messageText
import com.tagforge.domain.security.NakMeaning
import com.tagforge.domain.security.RawReply

/** One console line's outcome as text: ACK, a decoded NAK, the answer bytes, "tag lost" or the reason it failed. */
@Composable
fun ConsoleOutcome.text(): String = when (this) {
    is ConsoleOutcome.Reply -> reply.text()
    ConsoleOutcome.TagLost -> stringResource(R.string.console_tag_lost)
    is ConsoleOutcome.Failed -> TagErrorText.of(error).messageText()
}

@Composable
private fun RawReply.text(): String = when (this) {
    RawReply.Ack -> stringResource(R.string.console_reply_ack)
    RawReply.Empty -> stringResource(R.string.console_reply_empty)
    is RawReply.Data -> stringResource(R.string.console_reply_data, bytes.toHex(" "))
    is RawReply.Nak -> stringResource(R.string.console_reply_nak, code, stringResource(meaning.labelRes()))
}

private fun NakMeaning?.labelRes(): Int = when (this) {
    NakMeaning.INVALID_ARGUMENT -> R.string.console_nak_invalid
    NakMeaning.PARITY_OR_CRC -> R.string.console_nak_crc
    NakMeaning.COUNTER_OVERFLOW -> R.string.console_nak_counter
    NakMeaning.EEPROM_WRITE -> R.string.console_nak_eeprom
    null -> R.string.console_nak_other
}
