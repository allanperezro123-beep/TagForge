package com.tagforge.domain.exchange

import com.tagforge.domain.common.Outcome
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlin.time.Instant

/**
 * The backup file (HT-04): `{"schema":1,"exportedAt":...,"history":[...],"templates":[...],"tasks":[...]}`.
 *
 * Importing is validate-all-then-apply: [parse] either returns every entry, already checked, or the first problem
 * and nothing else, so the caller can apply the result in one transaction. Duplicate ids inside a file keep the first
 * entry. Unknown keys are ignored so a newer file with extra fields still reads; an unknown `schema` is refused.
 */
object JsonExchange {

    const val SCHEMA = 1
    const val MAX_ENTRIES_PER_SECTION = 20_000

    private val json = Json {
        classDiscriminator = "t"
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun export(data: ExchangeData, exportedAt: Instant): String {
        val root = buildJsonObject {
            put("schema", SCHEMA)
            put("exportedAt", exportedAt.toEpochMilliseconds())
            put("history", array(HistoryDto.serializer(), data.history.map(ExchangeConversions::toDto)))
            put("templates", array(TemplateDto.serializer(), data.templates.map(ExchangeConversions::toDto)))
            put("tasks", array(TaskDto.serializer(), data.tasks.map(ExchangeConversions::toDto)))
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    fun parse(text: String): Outcome<ExchangeData, ImportError> {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return Outcome.Err(ImportError.NotJson)
        if (root !is JsonObject) return Outcome.Err(ImportError.NotABundle)
        val schema = (root["schema"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
            ?: return Outcome.Err(ImportError.NotABundle)
        if (schema != SCHEMA) return Outcome.Err(ImportError.UnsupportedSchema(schema))

        val history = section(root, "history", ExchangeSection.HISTORY, HistoryDto.serializer(), ExchangeConversions::history) { it.id }
        val templates = section(root, "templates", ExchangeSection.TEMPLATES, TemplateDto.serializer(), ExchangeConversions::template) { it.id }
        val tasks = section(root, "tasks", ExchangeSection.TASKS, TaskDto.serializer(), ExchangeConversions::task) { it.id }
        return when {
            history is Outcome.Err -> history
            templates is Outcome.Err -> templates
            tasks is Outcome.Err -> tasks
            else -> Outcome.Ok(
                ExchangeData(
                    (history as Outcome.Ok).value,
                    (templates as Outcome.Ok).value,
                    (tasks as Outcome.Ok).value,
                ),
            )
        }
    }

    private fun <D, T> section(
        root: JsonObject,
        key: String,
        section: ExchangeSection,
        serializer: KSerializer<D>,
        convert: (D) -> Outcome<T, EntryProblem>,
        idOf: (T) -> Long,
    ): Outcome<List<T>, ImportError> {
        val element = root[key] ?: return Outcome.Ok(emptyList())
        val entries = (element as? JsonArray) ?: return Outcome.Err(ImportError.NotABundle)
        if (entries.size > MAX_ENTRIES_PER_SECTION) return Outcome.Err(ImportError.TooMany(section))

        val result = ArrayList<T>(entries.size)
        entries.forEachIndexed { index, entry ->
            val dto = runCatching { json.decodeFromJsonElement(serializer, entry) }.getOrNull()
            val converted = if (dto == null) Outcome.Err(EntryProblem.BAD_STRUCTURE) else convert(dto)
            when (converted) {
                is Outcome.Err -> return Outcome.Err(ImportError.BadEntry(section, index + 1, converted.error))
                is Outcome.Ok -> result += converted.value
            }
        }
        return Outcome.Ok(result.distinctBy(idOf))
    }

    private fun <T> array(serializer: KSerializer<T>, items: List<T>): JsonElement =
        JsonArray(items.map { json.encodeToJsonElement(serializer, it) })
}
