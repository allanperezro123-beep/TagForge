package com.tagforge.domain.exchange

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.common.Outcome
import com.tagforge.domain.history.HistoryEntry
import com.tagforge.domain.history.HistoryKind
import com.tagforge.domain.history.HistoryOutcome
import com.tagforge.domain.ndef.DefaultNdefCodec
import com.tagforge.domain.ndef.NdefMessage
import com.tagforge.domain.ndef.NdefRecord
import com.tagforge.domain.ndef.spec.RecordSpec
import com.tagforge.domain.ndef.spec.WifiAuth
import com.tagforge.domain.ndef.spec.WifiCredentials
import com.tagforge.domain.ndef.spec.WifiEncryption
import com.tagforge.domain.ndef.spec.WifiWsc
import com.tagforge.domain.task.RingerMode
import com.tagforge.domain.task.Task
import com.tagforge.domain.task.TaskAction
import com.tagforge.domain.template.Template
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

/** HT-04: versioned JSON, validate everything before applying anything, never trust the file, never carry passwords. */
class JsonExchangeTest {

    private val exportedAt = Instant.fromEpochMilliseconds(1_700_000_000_000)

    private fun history(n: Int, ndef: Bytes? = null, kind: HistoryKind = HistoryKind.READ) = HistoryEntry(
        id = n.toLong(),
        at = Instant.fromEpochMilliseconds(1_000L * n),
        kind = kind,
        outcome = if (n % 5 == 0) HistoryOutcome.FAILED_TAGLOST else HistoryOutcome.SUCCESS,
        uid = Bytes.of(0x04, n and 0xFF, 0xB2, 0xC3),
        tagType = "NTAG213",
        summary = "entry $n, with \"quotes\" and ünïcode",
        ndef = ndef,
    )

    private fun template(n: Int) = Template(
        id = n.toLong(),
        name = "Template $n",
        records = listOf(RecordSpec.Text("hello $n", "en"), RecordSpec.Uri("https://example.com/$n")),
        createdAt = Instant.fromEpochMilliseconds(10L * n),
        updatedAt = Instant.fromEpochMilliseconds(20L * n),
    )

    private fun task(n: Int, vararg actions: TaskAction) = Task(
        id = n.toLong(),
        name = "Task $n",
        actions = actions.toList(),
        createdAt = Instant.fromEpochMilliseconds(1),
        updatedAt = Instant.fromEpochMilliseconds(2),
        lastRunAt = null,
    )

    private fun ok(result: Outcome<ExchangeData, ImportError>): ExchangeData {
        assertIs<Outcome.Ok<ExchangeData>>(result, "expected a valid bundle but got $result")
        return result.value
    }

    private fun err(result: Outcome<ExchangeData, ImportError>): ImportError {
        assertIs<Outcome.Err<ImportError>>(result, "expected an import error but got $result")
        return result.error
    }

    private fun export(data: ExchangeData) = JsonExchange.export(data, exportedAt)

    private fun edit(text: String, change: (JsonObject) -> JsonObject): String =
        Json.encodeToString(JsonObject.serializer(), change(Json.parseToJsonElement(text).jsonObject))

    // ---- round trip and format -----------------------------------------------------------------------------------

    @Test
    fun fiftyHistoryAndFiveTemplatesComeBackEqual() {
        val ndef = DefaultNdefCodec.encode(NdefMessage(listOf(NdefRecord.text("hi", "en"))))
        val data = ExchangeData(
            history = (1..50).map { history(it, ndef = if (it % 2 == 0) ndef else null, kind = HistoryKind.entries[it % HistoryKind.entries.size]) },
            templates = (1..5).map(::template),
            tasks = emptyList(),
        )

        val restored = ok(JsonExchange.parse(export(data)))

        assertEquals(data.history, restored.history)
        assertEquals(data.templates, restored.templates)
        assertEquals(55, restored.history.size + restored.templates.size)
    }

    @Test
    fun tasksRoundTripToo() {
        val data = ExchangeData(
            emptyList(),
            emptyList(),
            listOf(task(1, TaskAction.SetRinger(RingerMode.VIBRATE), TaskAction.OpenUrl("https://example.com"))),
        )
        assertEquals(data.tasks, ok(JsonExchange.parse(export(data))).tasks)
    }

    @Test
    fun theFileCarriesSchemaOneAndAllThreeSections() {
        val root = Json.parseToJsonElement(export(ExchangeData(listOf(history(1)), listOf(template(1)), emptyList()))).jsonObject
        assertEquals(1, root.getValue("schema").jsonPrimitive.int)
        assertEquals(1, root.getValue("history").jsonArray.size)
        assertEquals(1, root.getValue("templates").jsonArray.size)
        assertEquals(0, root.getValue("tasks").jsonArray.size)
    }

    @Test
    fun anEmptyExportIsAValidBundle() {
        assertEquals(ExchangeData(emptyList(), emptyList(), emptyList()), ok(JsonExchange.parse(export(ExchangeData(emptyList(), emptyList(), emptyList())))))
    }

    // ---- rejecting files ---------------------------------------------------------------------------------------

    @Test
    fun anUnknownSchemaVersionIsRejectedBeforeLookingAtTheEntries() {
        val text = edit(export(ExchangeData(listOf(history(1)), emptyList(), emptyList()))) { JsonObject(it + ("schema" to JsonPrimitive(2))) }
        assertEquals(ImportError.UnsupportedSchema(2), err(JsonExchange.parse(text)))
    }

    @Test
    fun textThatIsNotJsonOrNotABundleIsRejected() {
        assertEquals(ImportError.NotJson, err(JsonExchange.parse("this is not json {")))
        assertEquals(ImportError.NotABundle, err(JsonExchange.parse("""{"hello":"world"}""")))
        assertEquals(ImportError.NotABundle, err(JsonExchange.parse("[1,2,3]")))
        assertEquals(ImportError.NotABundle, err(JsonExchange.parse("""{"schema":"one"}""")))
    }

    @Test
    fun aMalformedSeventhEntryImportsNothingAndNamesEntrySeven() {
        val text = edit(export(ExchangeData((1..10).map { history(it) }, emptyList(), emptyList()))) { root ->
            val entries = root.getValue("history").jsonArray.toMutableList()
            entries[6] = JsonObject(entries[6].jsonObject + ("at" to JsonPrimitive("yesterday")))
            JsonObject(root + ("history" to JsonArray(entries)))
        }

        assertEquals(ImportError.BadEntry(ExchangeSection.HISTORY, 7, EntryProblem.BAD_STRUCTURE), err(JsonExchange.parse(text)))
    }

    @Test
    fun entriesWithValuesTheAppDoesNotKnowAreRejectedByNumber() {
        fun broken(field: String, value: String) = edit(export(ExchangeData(listOf(history(1), history(2)), emptyList(), emptyList()))) { root ->
            val entries = root.getValue("history").jsonArray.toMutableList()
            entries[1] = JsonObject(entries[1].jsonObject + (field to JsonPrimitive(value)))
            JsonObject(root + ("history" to JsonArray(entries)))
        }
        assertEquals(ImportError.BadEntry(ExchangeSection.HISTORY, 2, EntryProblem.UNKNOWN_KIND), err(JsonExchange.parse(broken("kind", "teleport"))))
        assertEquals(ImportError.BadEntry(ExchangeSection.HISTORY, 2, EntryProblem.UNKNOWN_OUTCOME), err(JsonExchange.parse(broken("outcome", "meh"))))
        assertEquals(ImportError.BadEntry(ExchangeSection.HISTORY, 2, EntryProblem.BAD_HEX), err(JsonExchange.parse(broken("uid", "04ZZ"))))
        assertEquals(ImportError.BadEntry(ExchangeSection.HISTORY, 2, EntryProblem.UNREADABLE_NDEF), err(JsonExchange.parse(broken("ndef", "D10105"))))
        assertEquals(ImportError.BadEntry(ExchangeSection.HISTORY, 2, EntryProblem.TOO_LONG), err(JsonExchange.parse(broken("summary", "x".repeat(5_000)))))
    }

    @Test
    fun unknownExtraKeysAreIgnoredAndRepeatedIdsKeepTheFirstEntry() {
        val text = edit(export(ExchangeData(listOf(history(1), history(2)), emptyList(), emptyList()))) { root ->
            val entries = root.getValue("history").jsonArray.toMutableList()
            entries[0] = JsonObject(entries[0].jsonObject + ("future" to JsonPrimitive("field")))
            entries += JsonObject(entries[1].jsonObject + ("summary" to JsonPrimitive("same id, later copy")))
            JsonObject(root + ("history" to JsonArray(entries)))
        }

        val restored = ok(JsonExchange.parse(text))

        assertEquals(listOf(1L, 2L), restored.history.map { it.id })
        assertEquals(history(2).summary, restored.history[1].summary)
    }

    // ---- never trust the content -----------------------------------------------------------------------------------

    @Test
    fun taskActionsAreRevalidatedOnImportAndDangerousSchemesAreRefused() {
        for (url in listOf("intent://evil#Intent;end", "javascript:alert(1)", "file:///sdcard/x", "content://contacts")) {
            val data = ExchangeData(emptyList(), emptyList(), listOf(task(1, TaskAction.OpenUrl("https://ok.example")), task(2, TaskAction.OpenUrl(url))))
            assertEquals(
                ImportError.BadEntry(ExchangeSection.TASKS, 2, EntryProblem.BLOCKED_ACTION),
                err(JsonExchange.parse(export(data))),
                "scheme of $url must be refused",
            )
        }
    }

    @Test
    fun aTaskWithMoreThanSixteenActionsIsRefused() {
        val tooMany = task(1, *Array(17) { TaskAction.Flashlight(on = it % 2 == 0) })
        assertEquals(
            ImportError.BadEntry(ExchangeSection.TASKS, 1, EntryProblem.INVALID_VALUE),
            err(JsonExchange.parse(export(ExchangeData(emptyList(), emptyList(), listOf(tooMany))))),
        )
    }

    @Test
    fun aTemplateWithoutAUsableNameIsRefused() {
        val nameless = template(1).copy(name = "   ")
        assertEquals(
            ImportError.BadEntry(ExchangeSection.TEMPLATES, 1, EntryProblem.INVALID_VALUE),
            err(JsonExchange.parse(export(ExchangeData(emptyList(), listOf(nameless), emptyList())))),
        )
    }

    @Test
    fun aFileWithAbsurdlyManyEntriesIsRefusedOutright() {
        val flood = ExchangeData((1..JsonExchange.MAX_ENTRIES_PER_SECTION + 1).map { history(it) }, emptyList(), emptyList())
        assertEquals(ImportError.TooMany(ExchangeSection.HISTORY), err(JsonExchange.parse(export(flood))))
    }

    // ---- passwords never travel -------------------------------------------------------------------------------------

    private val secret = "pass1234"
    private val secretHex = "7061737331323334"

    private fun wifiNdef() = DefaultNdefCodec.encode(
        NdefMessage(listOf(WifiWsc.record(WifiCredentials("Home", secret, WifiAuth.WPA2_PSK, WifiEncryption.AES)))),
    )

    @Test
    fun exportedFilesNeverContainAWifiPassword() {
        val data = ExchangeData(
            history = listOf(history(1, ndef = wifiNdef())),
            templates = listOf(template(1).copy(records = listOf(RecordSpec.Wifi(ssid = "Home", password = secret)))),
            tasks = listOf(task(1, TaskAction.WifiJoin(ssid = "Home", key = secret))),
        )

        val text = export(data)

        assertFalse(secret in text, "plain text password in the export")
        assertFalse(secretHex.lowercase() in text.lowercase(), "hex-encoded password in the export")
        val restored = ok(JsonExchange.parse(text))
        assertEquals("Home", (restored.templates.single().records.single() as RecordSpec.Wifi).ssid)
        assertEquals("", (restored.templates.single().records.single() as RecordSpec.Wifi).password)
        assertEquals("", (restored.tasks.single().actions.single() as TaskAction.WifiJoin).key)
    }

    @Test
    fun aPasswordSmuggledIntoAnImportedHistoryEntryIsStrippedOnTheWay() {
        val tainted = edit(export(ExchangeData(listOf(history(1)), emptyList(), emptyList()))) { root ->
            val entry = root.getValue("history").jsonArray.single().jsonObject
            JsonObject(root + ("history" to JsonArray(listOf(JsonObject(entry + ("ndef" to JsonPrimitive(wifiNdef().toHex())))))))
        }

        val stored = ok(JsonExchange.parse(tainted)).history.single().ndef!!

        assertFalse(secretHex in stored.toHex().lowercase(), "the imported entry still holds the key")
        assertTrue(stored.size > 0)
    }
}
