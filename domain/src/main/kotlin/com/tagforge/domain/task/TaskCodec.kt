package com.tagforge.domain.task

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.ndef.UriPrefixes
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** Why a task could not be encoded or decoded. The domain never returns user-facing text. */
sealed interface TaskError {
    /** The first byte announces a format this build does not know. */
    data class UnsupportedVersion(val version: Int) : TaskError

    /** Fixed-arity opcodes cannot be skipped safely, so an unknown one rejects the whole task. */
    data class UnknownOpcode(val opcode: Int) : TaskError

    data object Truncated : TaskError

    /** Too many actions, a string over 255 bytes, a name over 32 bytes, or a payload no tag could hold. */
    data object TooLarge : TaskError

    data class Malformed(val detail: String) : TaskError

    /** The NDEF message has no Tag Forge task record. */
    data object NotATask : TaskError
}

/**
 * Compact binary task format, version 1 (documented byte by byte in `docs/task-format-v1.md`). All integers are
 * big-endian.
 *
 * ```
 * header  : hi-nibble = version (1), bit0 = HAS_NAME, bits 1-3 reserved = 0
 * [name]  : u8 length (<= 32) + UTF-8             (only when HAS_NAME)
 * count   : u8 number of actions (0..16)
 * action  : u8 opcode + fixed-arity arguments     (no per-action length byte)
 * str     : u8 length (<= 255) + UTF-8
 * url     : u8 NFC Forum URI prefix code + str
 * ```
 * Decoding is strict and structural: it never executes anything, rejects unknown versions and opcodes, and leaves
 * "is this value sensible" to [ActionValidator].
 */
object TaskCodec {

    private const val VERSION = 1
    private const val HAS_NAME = 0x01
    private const val RESERVED_FLAGS = 0x0E
    private const val U8_MAX = 0xFF
    private const val U16_MAX = 0xFFFF

    private object Op {
        const val OPEN_URL = 0x01
        const val LAUNCH_APP = 0x02
        const val RINGER = 0x03
        const val VOLUME = 0x04
        const val BRIGHTNESS = 0x05
        const val FLASHLIGHT = 0x06
        const val ALARM = 0x07
        const val TIMER = 0x08
        const val SMS = 0x09
        const val DIAL = 0x0A
        const val SPEAK = 0x0B
        const val NOTIFY = 0x0C
        const val HTTP = 0x0E
        const val SETTINGS = 0x0F
        const val WIFI_JOIN = 0x10
        const val CALENDAR = 0x11
        const val MEDIA = 0x12
        const val WIFI_TOGGLE = 0x16
        const val BLUETOOTH_TOGGLE = 0x17
    }

    fun encode(payload: TaskPayload): Outcome<Bytes, TaskError> = try {
        Outcome.Ok(Writer().write(payload))
    } catch (failure: Failure) {
        Outcome.Err(failure.error)
    }

    fun decode(bytes: Bytes): Outcome<TaskPayload, TaskError> = try {
        Outcome.Ok(Reader(bytes).read())
    } catch (failure: Failure) {
        Outcome.Err(failure.error)
    }

    /** Control flow inside this file only: never escapes [encode] / [decode]. */
    private class Failure(val error: TaskError) : RuntimeException(null, null, false, false)

    private fun fail(error: TaskError): Nothing = throw Failure(error)

    // ---- writing --------------------------------------------------------------------------------------------

    private class Writer {
        private val out = ByteArrayOutputStream()

        fun write(payload: TaskPayload): Bytes {
            if (payload.actions.size > TaskLimits.MAX_ACTIONS) fail(TaskError.TooLarge)
            val name = payload.name?.takeIf { it.isNotEmpty() }?.toByteArray(Charsets.UTF_8)
            if (name != null && name.size > TaskLimits.MAX_NAME_BYTES) fail(TaskError.TooLarge)

            u8(VERSION shl 4 or if (name != null) HAS_NAME else 0)
            if (name != null) {
                u8(name.size)
                out.write(name)
            }
            u8(payload.actions.size)
            payload.actions.forEach(::action)
            if (out.size() > TaskLimits.MAX_PAYLOAD_BYTES) fail(TaskError.TooLarge)
            return Bytes.copyOf(out.toByteArray())
        }

        private fun action(action: TaskAction) {
            when (action) {
                is TaskAction.OpenUrl -> { u8(Op.OPEN_URL); url(action.url) }
                is TaskAction.LaunchApp -> { u8(Op.LAUNCH_APP); str(action.packageName) }
                is TaskAction.SetRinger -> { u8(Op.RINGER); u8(action.mode.code) }
                is TaskAction.SetVolume -> { u8(Op.VOLUME); u8(action.stream.code); u8(action.level) }
                is TaskAction.SetBrightness -> { u8(Op.BRIGHTNESS); u8(action.level) }
                is TaskAction.Flashlight -> { u8(Op.FLASHLIGHT); flag(action.on) }
                is TaskAction.Alarm -> { u8(Op.ALARM); u8(action.hour); u8(action.minute); str(action.label) }
                is TaskAction.Timer -> { u8(Op.TIMER); u16(action.seconds); str(action.label) }
                is TaskAction.SmsCompose -> { u8(Op.SMS); str(action.number); str(action.body) }
                is TaskAction.Dial -> { u8(Op.DIAL); str(action.number) }
                is TaskAction.Speak -> { u8(Op.SPEAK); str(action.text) }
                is TaskAction.Notify -> { u8(Op.NOTIFY); str(action.title); str(action.text) }
                is TaskAction.HttpRequest -> { u8(Op.HTTP); u8(action.method.code); url(action.url) }
                is TaskAction.OpenSettings -> { u8(Op.SETTINGS); u8(action.screen.code) }
                is TaskAction.WifiJoin -> { u8(Op.WIFI_JOIN); str(action.ssid); str(action.key); u8(action.auth.code) }
                is TaskAction.CalendarEvent -> { u8(Op.CALENDAR); str(action.title); u16(action.durationMinutes) }
                is TaskAction.MediaKey -> { u8(Op.MEDIA); u8(action.key.code) }
                is TaskAction.WifiToggle -> { u8(Op.WIFI_TOGGLE); flag(action.enabled) }
                is TaskAction.BluetoothToggle -> { u8(Op.BLUETOOTH_TOGGLE); flag(action.enabled) }
            }
        }

        private fun u8(value: Int) {
            if (value !in 0..U8_MAX) fail(TaskError.Malformed("value $value does not fit one byte"))
            out.write(value)
        }

        private fun u16(value: Int) {
            if (value !in 0..U16_MAX) fail(TaskError.Malformed("value $value does not fit two bytes"))
            out.write(value ushr 8)
            out.write(value and U8_MAX)
        }

        private fun flag(value: Boolean) = u8(if (value) 1 else 0)

        private fun str(text: String) {
            val bytes = text.toByteArray(Charsets.UTF_8)
            if (bytes.size > TaskLimits.MAX_STRING_BYTES) fail(TaskError.TooLarge)
            u8(bytes.size)
            out.write(bytes)
        }

        private fun url(text: String) {
            val split = UriPrefixes.split(text)
            u8(split.code)
            str(split.rest)
        }
    }

    // ---- reading --------------------------------------------------------------------------------------------

    private class Reader(private val bytes: Bytes) {
        private var pos = 0

        fun read(): TaskPayload {
            if (bytes.isEmpty()) fail(TaskError.Truncated)
            val header = bytes[0]
            val version = header ushr 4
            if (version != VERSION) fail(TaskError.UnsupportedVersion(version))
            if (bytes.size > TaskLimits.MAX_PAYLOAD_BYTES) fail(TaskError.TooLarge)
            if (header and RESERVED_FLAGS != 0) fail(TaskError.Malformed("reserved header bits are set"))
            pos = 1

            val name = if (header and HAS_NAME != 0) str(TaskLimits.MAX_NAME_BYTES) else null
            val count = u8()
            if (count > TaskLimits.MAX_ACTIONS) fail(TaskError.TooLarge)

            val actions = List(count) { action() }
            if (pos != bytes.size) fail(TaskError.Malformed("${bytes.size - pos} unexpected trailing bytes"))
            return TaskPayload(name, actions)
        }

        private fun action(): TaskAction = when (val opcode = u8()) {
            Op.OPEN_URL -> TaskAction.OpenUrl(url())
            Op.LAUNCH_APP -> TaskAction.LaunchApp(str())
            Op.RINGER -> TaskAction.SetRinger(enum(RingerMode::fromCode, "ringer mode"))
            Op.VOLUME -> TaskAction.SetVolume(enum(VolumeStream::fromCode, "volume stream"), u8())
            Op.BRIGHTNESS -> TaskAction.SetBrightness(u8())
            Op.FLASHLIGHT -> TaskAction.Flashlight(flag())
            Op.ALARM -> TaskAction.Alarm(u8(), u8(), str())
            Op.TIMER -> TaskAction.Timer(u16(), str())
            Op.SMS -> TaskAction.SmsCompose(str(), str())
            Op.DIAL -> TaskAction.Dial(str())
            Op.SPEAK -> TaskAction.Speak(str())
            Op.NOTIFY -> TaskAction.Notify(str(), str())
            Op.HTTP -> TaskAction.HttpRequest(method = enum(HttpMethod::fromCode, "http method"), url = url())
            Op.SETTINGS -> TaskAction.OpenSettings(enum(SettingsTarget::fromCode, "settings screen"))
            Op.WIFI_JOIN -> TaskAction.WifiJoin(str(), str(), enum(JoinAuth::fromCode, "wifi security"))
            Op.CALENDAR -> TaskAction.CalendarEvent(str(), u16())
            Op.MEDIA -> TaskAction.MediaKey(enum(MediaButton::fromCode, "media key"))
            Op.WIFI_TOGGLE -> TaskAction.WifiToggle(flag())
            Op.BLUETOOTH_TOGGLE -> TaskAction.BluetoothToggle(flag())
            else -> fail(TaskError.UnknownOpcode(opcode))
        }

        private fun u8(): Int {
            if (pos >= bytes.size) fail(TaskError.Truncated)
            return bytes[pos++]
        }

        private fun u16(): Int = (u8() shl 8) or u8()

        private fun flag(): Boolean = when (u8()) {
            0 -> false
            1 -> true
            else -> fail(TaskError.Malformed("flag byte must be 0 or 1"))
        }

        private fun <E> enum(lookup: (Int) -> E?, what: String): E =
            lookup(u8()) ?: fail(TaskError.Malformed("unknown $what"))

        private fun str(maxBytes: Int = TaskLimits.MAX_STRING_BYTES): String {
            val length = u8()
            if (length > maxBytes) fail(TaskError.TooLarge)
            if (pos + length > bytes.size) fail(TaskError.Truncated)
            val slice = bytes.slice(pos, pos + length).toByteArray()
            pos += length
            return try {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(slice))
                    .toString()
            } catch (_: CharacterCodingException) {
                fail(TaskError.Malformed("text is not valid UTF-8"))
            }
        }

        private fun url(): String {
            val code = u8()
            val rest = str()
            return UriPrefixes.join(code, rest) ?: fail(TaskError.Malformed("unknown URI prefix code"))
        }
    }
}
