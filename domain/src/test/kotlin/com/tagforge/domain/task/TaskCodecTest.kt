package com.tagforge.domain.task

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** TK-02 and the hostile-payload half of TK-06: golden bytes, round trips, and typed rejection of anything odd. */
class TaskCodecTest {

    private fun encode(vararg actions: TaskAction, name: String? = null): Bytes =
        (TaskCodec.encode(TaskPayload(name, actions.toList())) as Outcome.Ok).value

    private fun decodeOk(bytes: Bytes): TaskPayload = (TaskCodec.decode(bytes) as Outcome.Ok).value

    private fun decodeError(bytes: Bytes): TaskError = (TaskCodec.decode(bytes) as Outcome.Err).error

    // ---- sizes (spec wins over design: no per-action length byte) --------------------------------------------

    @Test
    fun anEmptyTaskIsTwoBytes() {
        assertEquals(Bytes.of(0x10, 0x00), encode())
    }

    @Test
    fun aSingleRingerActionIsFourBytes() {
        assertEquals(Bytes.of(0x10, 0x01, 0x03, 0x01), encode(TaskAction.SetRinger(RingerMode.VIBRATE)))
        assertEquals(Bytes.of(0x10, 0x01, 0x03, 0x00), encode(TaskAction.SetRinger(RingerMode.SILENT)))
        assertEquals(Bytes.of(0x10, 0x01, 0x03, 0x02), encode(TaskAction.SetRinger(RingerMode.NORMAL)))
    }

    @Test
    fun openUrlUsesTheUriPrefixTableAndIsSixteenBytesInTotal() {
        val bytes = encode(TaskAction.OpenUrl("https://example.com"))

        // header, count, opcode, prefix 04 (https://), len 11, "example.com"
        val expected = Bytes.of(0x10, 0x01, 0x01, 0x04, 0x0B) + Bytes.copyOf("example.com".toByteArray())
        assertEquals(expected, bytes)
        assertEquals(16, bytes.size, "design said 17 because of an argLen byte that fixed-arity opcodes no longer need")
    }

    // ---- golden layouts per opcode ---------------------------------------------------------------------------

    @Test
    fun theNameIsOptionalAndLengthPrefixed() {
        assertEquals(Bytes.of(0x11, 0x02, 0x48, 0x69, 0x00), encode(name = "Hi"))
        assertEquals(Bytes.of(0x10, 0x00), encode(name = null))
        assertEquals(Bytes.of(0x10, 0x00), encode(name = ""), "an empty name sets no flag")
    }

    @Test
    fun fixedArityNumericOpcodes() {
        assertEquals(Bytes.of(0x10, 0x01, 0x04, 0x02, 0x28), encode(TaskAction.SetVolume(VolumeStream.ALARM, 40)))
        assertEquals(Bytes.of(0x10, 0x01, 0x05, 0xC8), encode(TaskAction.SetBrightness(200)))
        assertEquals(Bytes.of(0x10, 0x01, 0x06, 0x01), encode(TaskAction.Flashlight(true)))
        assertEquals(Bytes.of(0x10, 0x01, 0x16, 0x00), encode(TaskAction.WifiToggle(false)))
        assertEquals(Bytes.of(0x10, 0x01, 0x17, 0x01), encode(TaskAction.BluetoothToggle(true)))
        assertEquals(Bytes.of(0x10, 0x01, 0x12, 0x01), encode(TaskAction.MediaKey(MediaButton.NEXT)))
        assertEquals(Bytes.of(0x10, 0x01, 0x0F, 0x04), encode(TaskAction.OpenSettings(SettingsTarget.AIRPLANE)))
    }

    @Test
    fun stringsAreUtf8WithAOneByteLength() {
        assertEquals(
            Bytes.of(0x10, 0x01, 0x0B, 0x05, 0xC3, 0xB1, 0xE2, 0x82, 0xAC),
            encode(TaskAction.Speak("ñ€")),
        )
        assertEquals(
            Bytes.of(0x10, 0x01, 0x07, 0x07, 0x1E, 0x01, 0x41),
            encode(TaskAction.Alarm(7, 30, "A")),
        )
        assertEquals(
            Bytes.of(0x10, 0x01, 0x08, 0x01, 0x2C, 0x01, 0x74),
            encode(TaskAction.Timer(300, "t")),
            "u16 is big-endian: 300 = 01 2C",
        )
    }

    @Test
    fun wifiJoinCarriesSsidKeyAndAuth() {
        val bytes = encode(TaskAction.WifiJoin("Home", "pass1234", JoinAuth.WPA2))

        val expected = Bytes.of(0x10, 0x01, 0x10, 0x04) + Bytes.copyOf("Home".toByteArray()) +
            Bytes.of(0x08) + Bytes.copyOf("pass1234".toByteArray()) + Bytes.of(0x01)
        assertEquals(expected, bytes)
    }

    @Test
    fun httpHasAMethodAndAUrlAndNothingElse() {
        val bytes = encode(TaskAction.HttpRequest("https://example.com/h", HttpMethod.POST))

        val expected = Bytes.of(0x10, 0x01, 0x0E, 0x01, 0x04, 0x0D) + Bytes.copyOf("example.com/h".toByteArray())
        assertEquals(expected, bytes)
    }

    // ---- round trip -------------------------------------------------------------------------------------------

    @Test
    fun everyActionTypeRoundTripsWithAndWithoutAName() {
        val all = listOf(
            TaskAction.SetRinger(RingerMode.SILENT),
            TaskAction.SetVolume(VolumeStream.NOTIFICATION, 100),
            TaskAction.SetBrightness(0),
            TaskAction.WifiToggle(true),
            TaskAction.WifiJoin("Café", "", JoinAuth.OPEN),
            TaskAction.BluetoothToggle(false),
            TaskAction.OpenSettings(SettingsTarget.MOBILE_DATA),
            TaskAction.OpenUrl("intent://scan/#Intent;end"),
            TaskAction.LaunchApp("com.example.app"),
            TaskAction.Alarm(23, 59, "Late"),
            TaskAction.Timer(65535, ""),
            TaskAction.CalendarEvent("Standup ñ", 15),
            TaskAction.SmsCompose("+573001234567", "hola"),
            TaskAction.Dial("*123#"),
            TaskAction.Speak("hello"),
            TaskAction.Flashlight(false),
            TaskAction.Notify("T", "B"),
            TaskAction.HttpRequest("http://host/x", HttpMethod.GET),
            TaskAction.MediaKey(MediaButton.PREVIOUS),
        )

        assertEquals(TaskPayload(null, all.take(16)), decodeOk(encode(*all.take(16).toTypedArray())))
        assertEquals(TaskPayload("Night mode", all.takeLast(3)), decodeOk(encode(*all.takeLast(3).toTypedArray(), name = "Night mode")))
    }

    @Test
    fun randomTasksOfUpToSixteenActionsRoundTrip() {
        val random = Random(20261017)
        repeat(300) {
            val actions = List(random.nextInt(0, 17)) { randomAction(random) }
            val payload = TaskPayload(if (random.nextBoolean()) randomText(random, 12).ifEmpty { null } else null, actions)

            val bytes = (TaskCodec.encode(payload) as Outcome.Ok).value

            assertEquals(payload, decodeOk(bytes), "round trip failed for $payload")
        }
    }

    // ---- hostile and broken input ----------------------------------------------------------------------------

    @Test
    fun anUnknownVersionIsRejectedWithItsNumber() {
        assertEquals(TaskError.UnsupportedVersion(2), decodeError(Bytes.of(0x20, 0x00)))
        assertEquals(TaskError.UnsupportedVersion(0), decodeError(Bytes.of(0x00, 0x00)))
        assertEquals(TaskError.UnsupportedVersion(15), decodeError(Bytes.of(0xF0, 0x01, 0x03, 0x01)))
    }

    @Test
    fun anUnknownOpcodeIsATypedErrorNotASkip() {
        assertEquals(TaskError.UnknownOpcode(0x7F), decodeError(Bytes.of(0x10, 0x01, 0x7F)))
        assertEquals(TaskError.UnknownOpcode(0x0D), decodeError(Bytes.of(0x10, 0x01, 0x0D, 0x00, 0x10)), "Vibrate was dropped from v1")
        assertEquals(TaskError.UnknownOpcode(0x00), decodeError(Bytes.of(0x10, 0x01, 0x00)))
    }

    @Test
    fun twoHundredActionsIsTooLarge() {
        val declaresTwoHundred = Bytes.of(0x10, 200) + Bytes.concat(List(200) { Bytes.of(0x03, 0x01) })
        assertEquals(TaskError.TooLarge, decodeError(declaresTwoHundred))
        assertEquals(TaskError.TooLarge, decodeError(Bytes.of(0x10, 17, 0x03, 0x01)), "17 is one too many even when truncated")
    }

    @Test
    fun aFiveKilobytePayloadIsTooLargeBeforeAnyParsing() {
        val huge = Bytes.of(0x10, 0x01, 0x0B, 0xFF) + Bytes.copyOf(ByteArray(5 * 1024) { 'a'.code.toByte() })
        assertEquals(TaskError.TooLarge, decodeError(huge))
    }

    @Test
    fun encodingRefusesWhatTheFormatCannotHold() {
        val seventeen = List(17) { TaskAction.Flashlight(true) }
        assertEquals(Outcome.Err(TaskError.TooLarge), TaskCodec.encode(TaskPayload(null, seventeen)))

        val longString = "x".repeat(256)
        assertEquals(Outcome.Err(TaskError.TooLarge), TaskCodec.encode(TaskPayload(null, listOf(TaskAction.Speak(longString)))))
        assertTrue(TaskCodec.encode(TaskPayload(null, listOf(TaskAction.Speak("x".repeat(255))))) is Outcome.Ok, "255 bytes is the limit")

        val longName = "n".repeat(33)
        assertEquals(Outcome.Err(TaskError.TooLarge), TaskCodec.encode(TaskPayload(longName, emptyList())))
        assertTrue(TaskCodec.encode(TaskPayload("n".repeat(32), emptyList())) is Outcome.Ok, "32 bytes is the limit")
    }

    @Test
    fun theLengthLimitIsInBytesNotCharacters() {
        val oneByteTooMany = "€".repeat(86)   // 3 bytes each = 258
        assertEquals(Outcome.Err(TaskError.TooLarge), TaskCodec.encode(TaskPayload(null, listOf(TaskAction.Speak(oneByteTooMany)))))
    }

    @Test
    fun valuesTheByteFieldsCannotHoldAreRejectedOnEncode() {
        assertIs<Outcome.Err<TaskError>>(TaskCodec.encode(TaskPayload(null, listOf(TaskAction.SetBrightness(256)))))
        assertIs<Outcome.Err<TaskError>>(TaskCodec.encode(TaskPayload(null, listOf(TaskAction.SetBrightness(-1)))))
        assertIs<Outcome.Err<TaskError>>(TaskCodec.encode(TaskPayload(null, listOf(TaskAction.Timer(65536)))))
    }

    @Test
    fun truncatedInputIsReportedNeverACrash() {
        assertEquals(TaskError.Truncated, decodeError(Bytes.EMPTY))
        assertEquals(TaskError.Truncated, decodeError(Bytes.of(0x10)))
        assertEquals(TaskError.Truncated, decodeError(Bytes.of(0x10, 0x01)), "declares one action, has none")
        assertEquals(TaskError.Truncated, decodeError(Bytes.of(0x10, 0x01, 0x01, 0x04, 0x0B, 0x65, 0x78)), "string cut short")
        assertEquals(TaskError.Truncated, decodeError(Bytes.of(0x11, 0x05, 0x48)), "name cut short")
    }

    @Test
    fun structurallyOddInputIsMalformed() {
        assertIs<TaskError.Malformed>(decodeError(Bytes.of(0x10, 0x00, 0xFF)), "trailing bytes")
        assertIs<TaskError.Malformed>(decodeError(Bytes.of(0x12, 0x00)), "reserved header flag")
        assertIs<TaskError.Malformed>(decodeError(Bytes.of(0x10, 0x01, 0x03, 0x09)), "ringer mode 9")
        assertIs<TaskError.Malformed>(decodeError(Bytes.of(0x10, 0x01, 0x06, 0x02)), "flashlight flag 2")
        assertIs<TaskError.Malformed>(decodeError(Bytes.of(0x10, 0x01, 0x0B, 0x02, 0xC3, 0x28)), "invalid UTF-8")
        assertIs<TaskError.Malformed>(decodeError(Bytes.of(0x10, 0x01, 0x01, 0x7F, 0x00)), "URI prefix code outside the table")
    }

    @Test
    fun decodingIsStructuralSoSemanticallyWrongValuesPassThroughForTheValidatorToBlock() {
        val decoded = decodeOk(Bytes.of(0x10, 0x01, 0x07, 99, 99, 0x00))

        assertEquals(TaskAction.Alarm(99, 99, ""), decoded.actions.single())
    }

    // ---- generators ------------------------------------------------------------------------------------------

    private fun randomText(random: Random, max: Int): String {
        val alphabet = "abcXYZ 019ñé€-_.:/"
        return String(CharArray(random.nextInt(0, max + 1)) { alphabet[random.nextInt(alphabet.length)] })
    }

    private fun randomAction(random: Random): TaskAction = when (random.nextInt(19)) {
        0 -> TaskAction.SetRinger(RingerMode.entries.random(random))
        1 -> TaskAction.SetVolume(VolumeStream.entries.random(random), random.nextInt(0, 101))
        2 -> TaskAction.SetBrightness(random.nextInt(0, 256))
        3 -> TaskAction.WifiToggle(random.nextBoolean())
        4 -> TaskAction.WifiJoin(randomText(random, 32), randomText(random, 20), JoinAuth.entries.random(random))
        5 -> TaskAction.BluetoothToggle(random.nextBoolean())
        6 -> TaskAction.OpenSettings(SettingsTarget.entries.random(random))
        7 -> TaskAction.OpenUrl(listOf("https://", "http://www.", "tel:", "mailto:", "geo:", "sms:", "").random(random) + randomText(random, 20))
        8 -> TaskAction.LaunchApp(randomText(random, 20))
        9 -> TaskAction.Alarm(random.nextInt(0, 24), random.nextInt(0, 60), randomText(random, 10))
        10 -> TaskAction.Timer(random.nextInt(0, 65536), randomText(random, 10))
        11 -> TaskAction.CalendarEvent(randomText(random, 20), random.nextInt(0, 65536))
        12 -> TaskAction.SmsCompose(randomText(random, 12), randomText(random, 30))
        13 -> TaskAction.Dial(randomText(random, 12))
        14 -> TaskAction.Speak(randomText(random, 30))
        15 -> TaskAction.Flashlight(random.nextBoolean())
        16 -> TaskAction.Notify(randomText(random, 20), randomText(random, 20))
        17 -> TaskAction.HttpRequest("https://" + randomText(random, 20), HttpMethod.entries.random(random))
        else -> TaskAction.MediaKey(MediaButton.entries.random(random))
    }
}
