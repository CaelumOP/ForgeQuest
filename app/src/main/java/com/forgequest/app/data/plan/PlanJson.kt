package com.forgequest.app.data.plan

import kotlinx.serialization.json.Json

/** Finds and strips the ```json plan block the coach appends to a reply. */
object PlanJson {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    private val fenced = Regex("""```(?:json)?\s*(\{[\s\S]*?\})\s*```""")

    fun extract(text: String): PlanDraft? {
        val block = fenced.findAll(text).map { it.groupValues[1] }.lastOrNull { "\"days\"" in it } ?: return null
        return runCatching { json.decodeFromString(PlanDraft.serializer(), block) }.getOrNull()
            ?.takeIf { it.days.isNotEmpty() }
            ?.let { draft -> draft.copy(days = draft.days.filter { it.dayOfWeek in 1..7 }.distinctBy { it.dayOfWeek }) }
    }

    fun strip(text: String): String = text.replace(fenced, "").trim()

    fun encode(plan: PlanDraft): String = json.encodeToString(PlanDraft.serializer(), plan)
    fun decode(text: String): PlanDraft? = runCatching { json.decodeFromString(PlanDraft.serializer(), text) }.getOrNull()
}
