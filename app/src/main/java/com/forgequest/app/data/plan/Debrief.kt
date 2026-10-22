package com.forgequest.app.data.plan

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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

        /** Reads the model's JSON (bare or fenced, with chatter around it); null when it isn't usable. */
        fun parse(text: String): WorkoutDebrief? {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            val raw = runCatching { json.decodeFromString(serializer(), text.substring(start, end + 1)) }.getOrNull() ?: return null
            if (raw.summary.isBlank()) return null
            return raw.copy(
                score = raw.score.coerceIn(MIN_SCORE, 10),
                summary = raw.summary.trim().take(240),
                highlights = raw.highlights.map(String::trim).filter(String::isNotBlank).take(2),
                watch = raw.watch.map(String::trim).filter(String::isNotBlank).take(1),
                nextTime = raw.nextTime.map(String::trim).filter(String::isNotBlank).take(1),
            )
        }

        fun encode(d: WorkoutDebrief): String = json.encodeToString(serializer(), d)
        fun decode(text: String): WorkoutDebrief? = if (text.isBlank()) null else runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
    }
}
