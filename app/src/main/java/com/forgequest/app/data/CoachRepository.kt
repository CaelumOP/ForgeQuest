package com.forgequest.app.data

import com.forgequest.app.data.ai.AiError
import com.forgequest.app.data.ai.GeminiClient
import com.forgequest.app.data.db.AppDatabase
import com.forgequest.app.data.db.ChatEntity
import com.forgequest.app.data.plan.PlanDraft
import com.forgequest.app.data.plan.PlanJson
import com.forgequest.app.data.prefs.SettingsRepository
import com.forgequest.app.data.seed.ExerciseCatalog
import com.forgequest.app.domain.Dates
import com.forgequest.app.domain.ExerciseKind
import com.forgequest.app.domain.Muscle
import com.forgequest.app.domain.Records
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class CoachRepository(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val gemini: GeminiClient,
    private val progress: ProgressRepository,
    private val workouts: WorkoutRepository,
) {
    private val chat = db.chat()

    val messages: Flow<List<ChatEntity>> = chat.observeAll()

    suspend fun clear() = chat.clear()

    suspend fun send(text: String) {
        chat.insert(ChatEntity(role = "user", text = text.trim(), createdAt = System.currentTimeMillis()))
        val history = chat.recent(12).reversed().filter { it.kind != "error" }
        val wantsPlan = PLAN_WORDS.containsMatchIn(text.lowercase())
        try {
            val reply = gemini.generate(
                apiKey = requireKey(),
                models = models(smart = wantsPlan),
                system = coachSystemPrompt(),
                turns = history.map { (if (it.role == "user") "user" else "model") to it.text },
            )
            val plan = PlanJson.extract(reply)
            chat.insert(
                ChatEntity(
                    role = "coach",
                    text = PlanJson.strip(reply).ifBlank { "Here's your plan. Review it below." },
                    planJson = plan?.let(PlanJson::encode),
                    createdAt = System.currentTimeMillis(),
                )
            )
        } catch (e: AiError) {
            chat.insert(ChatEntity(role = "coach", text = e.userMessage, kind = "error", createdAt = System.currentTimeMillis()))
        }
    }

    suspend fun applyPlan(messageId: Long): Boolean {
        val msg = chat.byId(messageId) ?: return false
        val plan = msg.planJson?.let(PlanJson::decode) ?: return false
        workouts.applyPlan(plan, "coach")
        chat.update(msg.copy(planApplied = true))
        return true
    }

    /** Turns free text (pasted, or exported from Google Docs) into a plan. */
    suspend fun parsePlanText(text: String): PlanDraft {
        val system = """
            Convert the user's workout plan text into JSON. Keep their exercises, sets, reps and days.
            ${planSchemaInstructions()}
            Reply ONLY with the JSON object, no other text.
        """.trimIndent()
        val raw = gemini.generate(requireKey(), models(smart = true), system, listOf("user" to text.take(20_000)), jsonOutput = true)
        return PlanJson.extract("```json\n$raw\n```") ?: throw AiError.Unreadable("Couldn't turn that text into a plan. Make sure it lists days and exercises.")
    }

    /** Once per week, a short review of last week. Returns true if one was written. */
    suspend fun weeklyCheckinIfDue(): Boolean {
        if (settings.apiKey.value.isBlank()) return false
        val thisWeek = Dates.weekStart(Dates.today())
        val s = settings.current()
        if (s.lastCheckinWeek >= thisWeek) return false
        val snap = progress.snapshot.first()
        val lastWeek = thisWeek - 7
        if (snap.sessions.none { it.epochDay in lastWeek until thisWeek }) return false
        settings.update { it.copy(lastCheckinWeek = thisWeek) }
        return try {
            val reply = gemini.generate(
                requireKey(), models(smart = false), coachSystemPrompt(),
                listOf("user" to "Write my weekly check-in for last week: 3 short parts — what went well, what to push this week, one nutrition tip. Under 120 words. No plan JSON."),
            )
            chat.insert(ChatEntity(role = "coach", text = PlanJson.strip(reply), kind = "checkin", createdAt = System.currentTimeMillis()))
            true
        } catch (e: AiError) {
            settings.update { it.copy(lastCheckinWeek = thisWeek - 7) }
            false
        }
    }

    private fun requireKey(): String = settings.apiKey.value.ifBlank { throw AiError.MissingKey }

    /** Pro first for plans (it often has no free quota, so Flash takes over), Flash first for chat. */
    private suspend fun models(smart: Boolean): List<String> {
        val s = settings.current()
        return if (smart) listOf(s.smartModel, s.fastModel) else listOf(s.fastModel)
    }

    // ------------------------------------------------------------ Prompting

    private suspend fun coachSystemPrompt(): String = """
        You are the coach inside ForgeQuest, a workout and nutrition app. The user trains calisthenics and/or gym.
        Be warm, direct and specific. Use their data below. Keep answers short (under 180 words) unless they ask for detail.
        Use simple Markdown: **bold**, short bullet lists, at most one small heading. No tables.
        Never shame food choices; going over calories is fuel, not failure.

        When the user asks to create or change their weekly plan, describe it briefly, then add the full plan as a
        ```json code block at the very end.
        ${planSchemaInstructions()}

        ${progressSummary()}
    """.trimIndent()

    private fun planSchemaInstructions(): String {
        val ids = ExerciseCatalog.all.joinToString(", ") { it.id }
        return """
            Plan JSON shape: {"name": string, "description": string, "days": [{"dayOfWeek": 1-7 (1 = Monday), "title": string, "isRest": boolean, "note": string,
            "items": [{"exerciseId": string, "name": string, "kind": "REPS"|"HOLD"|"WEIGHTED", "muscles": [string], "block": string, "circuit": boolean,
            "sets": int, "repsMin": int|null, "repsMax": int|null, "seconds": int|null, "weightKg": number|null, "restSeconds": int, "note": string}]}]}
            Include all 7 days. Prefer these exercise ids: $ids.
            For an exercise not in that list, invent a snake_case id and set name, kind and muscles (from: ${Muscle.entries.joinToString(", ") { it.name }}).
            Use "seconds" for holds, "repsMin"/"repsMax" for reps. Items with the same block and circuit=true are done round by round.
        """.trimIndent()
    }

    /** Compact snapshot of the user's training and nutrition (well under 800 tokens). */
    private suspend fun progressSummary(): String {
        val snap = progress.snapshot.first()
        val s = snap.settings
        val sb = StringBuilder("User data:\n")
        if (s.name.isNotBlank()) sb.append("- Name: ${s.name}\n")
        snap.latestWeight?.let { sb.append("- Body weight: $it kg\n") }
        sb.append("- Goals: ${s.kcalGoal} kcal, ${s.proteinGoal} g protein per day\n")
        sb.append("- Level ${snap.level.level}, rank ${snap.level.rank.letter}; weekly streak ${snap.streak.weeks} weeks; this week ${snap.streak.doneThisWeek}/${snap.streak.targetThisWeek} workouts\n")
        snap.plan?.let { plan ->
            sb.append("- Current plan: ${plan.plan.name}\n")
            plan.days.forEach { d ->
                if (d.day.isRest) {
                    sb.append("  ${Dates.dayName(d.day.dayOfWeek)}: rest\n")
                } else {
                    val names = d.items.joinToString(", ") { snap.exercises[it.exerciseId]?.name ?: it.exerciseId }
                    sb.append("  ${Dates.dayName(d.day.dayOfWeek)}: ${d.day.title} — $names\n")
                }
            }
        }
        val recent = snap.sessions.take(5)
        if (recent.isNotEmpty()) {
            sb.append("- Recent workouts:\n")
            recent.forEach { session ->
                val sets = snap.doneSets.filter { it.sessionId == session.id }
                val summary = sets.groupBy { it.exerciseId }.entries.take(6).joinToString("; ") { (id, list) ->
                    val ex = snap.exercises[id]
                    val parts = list.joinToString("/") { set ->
                        when (ex?.kind) {
                            ExerciseKind.HOLD -> "${set.seconds}s"
                            ExerciseKind.WEIGHTED -> "${set.reps}×${set.weightKg}kg"
                            else -> "${set.reps}"
                        }
                    }
                    "${ex?.name ?: id} $parts"
                }
                sb.append("  ${java.time.LocalDate.ofEpochDay(session.epochDay)} ${session.title}: $summary\n")
            }
        }
        val bests = snap.doneSets.groupBy { it.exerciseId }.mapNotNull { (id, sets) ->
            val ex = snap.exercises[id] ?: return@mapNotNull null
            val best = sets.maxByOrNull { Records.score(ex.kind, it.reps, it.seconds, it.weightKg) } ?: return@mapNotNull null
            when (ex.kind) {
                ExerciseKind.HOLD -> "${ex.name} ${best.seconds}s"
                ExerciseKind.WEIGHTED -> "${ex.name} ${best.reps}×${best.weightKg}kg"
                ExerciseKind.REPS -> "${ex.name} ${best.reps} reps"
            }
        }.take(12)
        if (bests.isNotEmpty()) sb.append("- Personal bests: ${bests.joinToString(", ")}\n")
        val week = snap.nutritionByDay.filter { it.epochDay > Dates.today() - 7 }
        if (week.isNotEmpty()) {
            sb.append("- Last 7 days nutrition (avg): ${week.sumOf { it.kcal } / week.size} kcal, ${(week.sumOf { it.protein.toDouble() } / week.size).toInt()} g protein over ${week.size} logged days\n")
        }
        sb.append("- Today so far: ${snap.today.kcal} kcal, ${snap.today.protein.toInt()} g protein\n")
        return sb.toString()
    }

    private companion object {
        val PLAN_WORDS = Regex("plan|program|programme|split|routine|schedule|week|جدول|برنامج")
    }
}
