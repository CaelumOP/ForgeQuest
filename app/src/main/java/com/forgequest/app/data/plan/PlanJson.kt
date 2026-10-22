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

    /** A small-edit block: ```json {"changes": [...]} ```, or null. */
    fun extractPatch(text: String): PlanPatch? {
        val block = fenced.findAll(text).map { it.groupValues[1] }.lastOrNull { "\"changes\"" in it } ?: return null
        return runCatching { json.decodeFromString(PlanPatch.serializer(), block) }.getOrNull()?.takeIf { it.changes.isNotEmpty() }
    }

    /** True when the reply carries a plan or change block that could not be read: worth one repair round. */
    fun hasBrokenBlock(text: String): Boolean {
        val blocks = Regex("""```(?:json)?\s*([\s\S]*?)```""").findAll(text).map { it.groupValues[1] }.toList()
        return blocks.any { "\"days\"" in it || "\"changes\"" in it } && extract(text) == null && extractPatch(text) == null
    }

    /** A pasted plan that is already JSON (with or without a ```json fence), so it needs no AI round trip. */
    fun parsePasted(text: String): PlanDraft? {
        val t = text.trim()
        val body = fenced.find(t)?.groupValues?.get(1) ?: t.takeIf { it.startsWith("{") && it.endsWith("}") } ?: return null
        return runCatching { json.decodeFromString(PlanDraft.serializer(), body) }.getOrNull()
            ?.takeIf { it.days.isNotEmpty() }
            ?.let { draft -> draft.copy(days = draft.days.filter { it.dayOfWeek in 1..7 }.distinctBy { it.dayOfWeek }) }
    }

    fun strip(text: String): String = CoachMemory.strip(text.replace(fenced, "")).trim()

    /**
     * What to show while a reply is still streaming in: an unfinished ```json block (the plan the coach appends
     * at the end) and any MEMORY line are hidden, so the person sees only the prose.
     */
    fun stripPartial(text: String): String {
        val cleaned = strip(text)
        val fenceCount = Regex("```").findAll(cleaned).count()
        val cut = if (fenceCount % 2 == 1) cleaned.substring(0, cleaned.lastIndexOf("```")) else cleaned
        return CoachMemory.strip(cut).trimEnd()
    }

    fun encode(plan: PlanDraft): String = json.encodeToString(PlanDraft.serializer(), plan)
    fun decode(text: String): PlanDraft? = runCatching { json.decodeFromString(PlanDraft.serializer(), text) }.getOrNull()
}

/**
 * The coach ends a reply with "MEMORY: <fact>" when the person shares something lasting (an injury, their
 * equipment, a preference). The app stores those lines and feeds them back in every later conversation.
 */
object CoachMemory {
    private val line = Regex("""(?im)^\s*\*{0,2}MEMORY:\*{0,2}\s*(.+?)\s*$""")
    const val MAX_NOTES = 15

    fun extract(text: String): List<String> = line.findAll(text).map { it.groupValues[1].trim().trimEnd('.') }.filter { it.isNotBlank() }.toList()

    fun strip(text: String): String = text.replace(line, "").trimEnd()

    /** Adds [new] to [existing] (one note per line), skipping duplicates and keeping the newest [MAX_NOTES]. */
    fun merge(existing: String, new: List<String>): String {
        val notes = existing.lines().map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
        for (n in new) if (notes.none { it.equals(n, ignoreCase = true) }) notes += n
        return notes.takeLast(MAX_NOTES).joinToString("\n")
    }
}
