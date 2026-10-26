package com.forgequest.app.data.plan

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** The coach's short take on a finished workout, shown under the summary and kept with the session. */
@Serializable
data class WorkoutDebrief(
    /** 4..10: a finished workout never scores below 4, showing up counts. */
    val score: Int,
    val summary: String,
    val highlights: List<String> = emptyList(),
    val watch: List<String> = emptyList(),
    val nextTime: List<String> = emptyList(),
) {
    companion object {
        const val MIN_SCORE = 4
        private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

        /**
         * Reads the model's JSON (bare or fenced, with chatter around it); null when it isn't usable. Forgiving about
         * shape, since fast models drift: a score of "8" or "8/10", and a single string where a list was asked for.
         */
        fun parse(text: String): WorkoutDebrief? {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            val obj = runCatching { json.parseToJsonElement(text.substring(start, end + 1)).jsonObject }.getOrNull() ?: return null
            val summary = obj.text("summary")?.trim().orEmpty()
            if (summary.isBlank()) return null
            val score = obj.text("score")?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() } ?: return null
            return WorkoutDebrief(
                score = score.coerceIn(MIN_SCORE, 10),
                summary = summary.take(240),
                highlights = obj.list("highlights").take(2),
                watch = obj.list("watch").take(1),
                nextTime = obj.list("nextTime", "next_time").take(1),
            )
        }

        private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

        private fun JsonObject.list(vararg keys: String): List<String> {
            val e: JsonElement = keys.firstNotNullOfOrNull { this[it] } ?: return emptyList()
            val items = when (e) {
                is JsonArray -> e.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                is JsonPrimitive -> listOfNotNull(e.contentOrNull)
                else -> emptyList()
            }
            return items.map(String::trim).filter(String::isNotBlank)
        }

        fun encode(d: WorkoutDebrief): String = json.encodeToString(serializer(), d)
        fun decode(text: String): WorkoutDebrief? = if (text.isBlank()) null else runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
    }
}
