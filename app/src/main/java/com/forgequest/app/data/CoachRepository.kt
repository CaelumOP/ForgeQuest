package com.forgequest.app.data

import android.os.SystemClock
import android.util.Log
import com.forgequest.app.AppText
import com.forgequest.app.R
import com.forgequest.app.data.ai.AiError
import com.forgequest.app.data.ai.GeminiClient
import com.forgequest.app.data.ai.ModelStore
import com.forgequest.app.data.db.AppDatabase
import com.forgequest.app.data.db.ChatEntity
import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.SetEntity
import com.forgequest.app.data.plan.PlanDraft
import com.forgequest.app.data.plan.PlanHash
import com.forgequest.app.data.plan.PlanJson
import com.forgequest.app.data.plan.PlanPatch
import com.forgequest.app.data.plan.PlanPatches
import com.forgequest.app.data.plan.PlanValidator
import com.forgequest.app.data.prefs.SettingsRepository
import com.forgequest.app.domain.Dates
import com.forgequest.app.domain.Equipment
import com.forgequest.app.domain.ExerciseSwapEngine
import com.forgequest.app.domain.inferEquipmentFromText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.util.Locale

class CoachRepository(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val gemini: GeminiClient,
    private val models: ModelStore,
    private val progress: ProgressRepository,
    private val workouts: WorkoutRepository,
    private val health: com.forgequest.app.data.health.HealthRepository,
) {
    /** The system prompt, the plan format and the progress summary; the Gemini bridge reads the last two. */
    val prompts = CoachPromptBuilder(progress, health, db)
    private val chat = db.chat()
    private val chatMutex = Mutex()

    /** Who answered the last message and how fast, for the Coach menu ("Gemini · 0.8 s"). */
    private val _lastAnswer = MutableStateFlow<Pair<String, Long>?>(null)
    val lastAnswer: StateFlow<Pair<String, Long>?> = _lastAnswer

    val messages: Flow<List<ChatEntity>> = chat.observeAll()

    /** The reply currently streaming in, or null. The screen shows it as a live bubble. */
    private val _draft = MutableStateFlow<String?>(null)
    val draft: StateFlow<String?> = _draft

    suspend fun clear() = chatMutex.withLock { chat.clear() }

    /** Opens the connection to Google when the Coach tab opens, so the first message skips the handshake. */
    suspend fun warmUp() {
        if (!settings.isAiOn()) return // AI off: never contact Google on the person's behalf
        gemini.warmUp(settings.apiKey.value)
        suspendRunCatching { models.current() }
    }

    /**
     * [aboutWorkout]: the message is the debrief button's (see [debriefChatMessage]). It is never read as a plan request,
     * never changes the saved equipment, and is answered in the app language.
     */
    suspend fun send(text: String, aboutWorkout: Boolean = false) = chatMutex.withLock {
        val tapped = SystemClock.elapsedRealtime()
        val trimmed = text.trim()
        chat.insert(ChatEntity(role = "user", text = trimmed, createdAt = System.currentTimeMillis()))
        val rawRecent = chat.recent(14).reversed()
        val history = answeredHistory(rawRecent)

        // Persist any equipment explicitly mentioned in the user's message.
        val newlyInferredEq = if (aboutWorkout) null else inferEquipmentFromText(trimmed)
        if (newlyInferredEq != null && settings.current().equipment.isBlank()) {
            settings.update { it.copy(equipment = equipmentWith(it.equipment, newlyInferredEq)) }
        }

        val currentSettings = settings.current()
        val knownEquipment = currentSettings.configuredEquipment
            .ifEmpty { inferEquipmentFromText(currentSettings.coachNotes, trimmed).orEmpty() }

        val replyingToIntake = PlanIntent.isReplyToIntake(history, trimmed)
        val wantsPlan = !aboutWorkout && (wantsPlan(trimmed) || (replyingToIntake && knownEquipment.isNotEmpty()))
        val needsEquipmentIntake = wantsPlan && knownEquipment.isEmpty()
        val shouldEmitPlanJson = wantsPlan && !needsEquipmentIntake

        _draft.value = ""
        try {
            val system = prompts.coachSystemPrompt(
                replyLanguage = if (aboutWorkout && isArabicUi()) "Arabic" else languageOf(trimmed),
                plan = shouldEmitPlanJson,
                needsEquipmentIntake = needsEquipmentIntake,
                availableEquipment = knownEquipment,
            )
            var firstText = -1L
            val turns = buildAlternatingTurns(history)
            val onDelta: suspend (String) -> Unit = { delta ->
                if (firstText < 0) firstText = SystemClock.elapsedRealtime() - tapped
                _draft.value = (_draft.value ?: "") + delta
            }
            val reply = gemini.generateStreaming(
                apiKey = requireKey(),
                models = models(),
                system = system,
                turns = turns,
                // Full thinking for weekly plans; lowThinking (~1,024 budget) for normal coaching so the coach
                // actually reasons over Readiness, HRV, recovery and set history.
                lowThinking = !shouldEmitPlanJson,
                onDelta = onDelta,
            )
            _lastAnswer.value = "Gemini" to firstText
            Log.i(TAG, "send→first text ${firstText} ms, send→done ${SystemClock.elapsedRealtime() - tapped} ms via Gemini (plan=$shouldEmitPlanJson, intake=$needsEquipmentIntake)")
            saveReply(reply, knownEquipment) { problems ->
                // One silent repair round: the coach sees exactly what was wrong and resends only the JSON.
                _draft.value = PlanJson.strip(reply) + "\n\n_" + AppText.get(R.string.coach_fixing_plan) + "_"
                gemini.generate(
                    apiKey = requireKey(), models = models(), system = system,
                    turns = turns + ("model" to reply) + ("user" to repairRequest(problems)),
                    lowThinking = true,
                )
            }
        } catch (e: AiError.Partial) {
            if (PlanJson.strip(e.text).length > 40) saveReply(e.text, knownEquipment)
            saveError(e)
        } catch (e: AiError) {
            saveError(e)
        } finally {
            _draft.value = null
        }
    }

    /**
     * Drops error bubbles, old check-ins, and unanswered questions so the model only sees clean
     * user→coach exchanges and never re-answers old failed prompts. The newest message always stays.
     */
    internal fun answeredHistory(messages: List<ChatEntity>): List<ChatEntity> {
        val conversational = messages.filter { it.kind != "checkin" && it.kind != "gemini" }
        return conversational.filterIndexed { i, m ->
            when {
                m.kind == "error" -> false
                i == conversational.lastIndex -> true
                m.role == "user" -> conversational[i + 1].role == "coach" && conversational[i + 1].kind != "error"
                else -> true
            }
        }
    }

    /** Ensures turns strictly alternate user/model and end with a user turn. */
    private fun buildAlternatingTurns(history: List<ChatEntity>): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        for (m in history) {
            val role = if (m.role == "user") "user" else "model"
            val last = result.lastOrNull()
            if (last != null && last.first == role) {
                result[result.lastIndex] = role to (last.second + "\n\n" + m.text)
            } else {
                result += role to m.text
            }
        }
        while (result.isNotEmpty() && result.last().first != "user") {
            result.removeAt(result.lastIndex)
        }
        return result
    }

    /** True when the newest message is a failed reply, so the screen can buzz once. */
    suspend fun lastReplyFailed(): Boolean = chat.recent(1).firstOrNull()?.kind == "error"

    private suspend fun saveError(e: AiError) {
        val text = if (e is AiError.Timeout) AppText.get(R.string.ai_err_coach_timeout) else e.userMessage
        val s = settings.current()
        val detail = listOf(e.javaClass.simpleName, e.detail, "models ${s.liteModel} / ${s.fastModel}, app ${com.forgequest.app.BuildConfig.VERSION_NAME}")
            .filter { it.isNotBlank() }.joinToString("\n")
        chat.insert(ChatEntity(role = "coach", text = text, kind = "error", detail = detail, createdAt = System.currentTimeMillis()))
    }

    /** Result of reading a reply's plan or change block against the library and the current plan. */
    private class PlanCheck(val draft: PlanDraft?, val errors: List<String>, val unknownIds: Set<String>)

    private fun checkPlan(reply: String, library: Map<String, ExerciseEntity>, current: PlanDraft?): PlanCheck? {
        val currentHash = current?.let(PlanHash::of)
        PlanJson.extractPatch(reply)?.let { patch ->
            if (current == null) return PlanCheck(null, listOf("There is no current plan to change; send the full plan instead."), emptySet())
            val out = PlanPatches.apply(current, patch, library)
            return PlanCheck(out.draft, out.errors, emptySet())
        }
        PlanJson.extract(reply)?.let { draft ->
            val v = PlanValidator.validate(draft, library)
            return PlanCheck(v.draft.copy(basedOnHash = currentHash), v.errors, v.unknownIds)
        }
        return if (PlanJson.hasBrokenBlock(reply)) PlanCheck(null, listOf("The JSON block could not be parsed; check brackets, quotes and commas."), emptySet()) else null
    }

    private fun repairRequest(problems: List<String>) = buildString {
        appendLine("REPAIR REQUEST (automatic, from the app): your last plan JSON has these problems:")
        problems.take(12).forEach { appendLine("- $it") }
        append("Reply with ONLY the corrected ```json block (same shape as before), no other text.")
    }

    /**
     * Saves the coach's reply. A plan or change block is checked first; when it has problems and [repair] is
     * given, the coach is asked once to fix them. Ids that are still unknown become the closest library exercise
     * (noted under the reply), and a block that stays unreadable says so instead of silently vanishing.
     */
    private suspend fun saveReply(reply: String, availableEquipment: Set<Equipment>, repair: (suspend (List<String>) -> String)? = null) {
        val snap = progress.snapshot.first()
        val current = snap.plan?.toDraft()
        val first = checkPlan(reply, snap.exercises, current)
        val retried = if (first != null && first.errors.isNotEmpty() && repair != null) {
            suspendRunCatching { repair(first.errors) }.getOrNull()?.let { checkPlan(it, snap.exercises, current) }
        } else null
        val check = if (retried != null && first != null && (retried.draft != null || first.draft == null) && retried.errors.size <= first.errors.size) retried else first
        val extraNotes = mutableListOf<String>()
        var checked = check?.draft
        if (checked != null && check.unknownIds.isNotEmpty()) {
            val (replaced, swaps) = PlanValidator.replaceUnknown(checked, check.unknownIds, snap.exercises.values.toList(), availableEquipment)
            checked = replaced
            swaps.forEach { (old, new) ->
                extraNotes += if (new.isBlank()) AppText.get(R.string.coach_plan_dropped, old) else AppText.get(R.string.coach_plan_replaced, old, new)
            }
        }
        if (check != null && checked == null) extraNotes += AppText.get(R.string.coach_plan_unreadable)
        val patch = checked?.patch
        if (patch != null && current != null) extraNotes += describeChanges(patch, snap.exercises)
        val adaptedPlan = if (checked != null && availableEquipment.isNotEmpty()) {
            ExerciseSwapEngine.adaptPlanToEquipment(checked, snap.exercises, availableEquipment, dropUnfit = true).first
        } else {
            checked
        }
        val prose = PlanJson.strip(reply).ifBlank { AppText.get(R.string.coach_plan_fallback) }
        chat.insert(
            ChatEntity(
                role = "coach",
                text = if (extraNotes.isEmpty()) prose else prose + "\n\n" + extraNotes.joinToString("\n") { "• $it" },
                planJson = adaptedPlan?.let(PlanJson::encode),
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    enum class ApplyResult { APPLIED, STALE, FAILED }

    /**
     * Applies a coach plan card. If the plan was edited since the card was made, a change-only card is re-run on
     * the current plan; a full plan returns [ApplyResult.STALE] so the screen can ask first ([force] skips that).
     */
    suspend fun applyPlan(messageId: Long, force: Boolean = false): ApplyResult {
        val msg = chat.byId(messageId) ?: return ApplyResult.FAILED
        var plan = msg.planJson?.let(PlanJson::decode) ?: return ApplyResult.FAILED
        val snap = progress.snapshot.first()
        val current = snap.plan?.toDraft()
        if (!force && plan.basedOnHash != null && current != null && PlanHash.of(current) != plan.basedOnHash) {
            val patch = plan.patch ?: return ApplyResult.STALE
            plan = PlanPatches.apply(current, patch, snap.exercises).draft ?: return ApplyResult.STALE
        }
        val eq = settings.current().configuredEquipment
        val finalPlan = if (eq.isNotEmpty()) ExerciseSwapEngine.adaptPlanToEquipment(plan, snap.exercises, eq, dropUnfit = true).first else plan
        workouts.applyPlan(finalPlan.copy(basedOnHash = null, patch = null), "coach")
        chat.update(msg.copy(planApplied = true))
        return ApplyResult.APPLIED
    }

    /** The debrief's "Ask the coach" button shows: AI is on and a Gemini key is saved. */
    fun canDebrief(): Boolean = com.forgequest.app.data.prefs.chatIsTab(settings.isAiOn(), settings.apiKey.value)

    /** The message the Coach tab sends the moment it opens from the debrief button. The tab takes it and sets it back to null. */
    val pendingMessage = MutableStateFlow<String?>(null)

    /**
     * The first message of "Ask the coach about this workout": the workout's facts (each exercise against its previous
     * time, effort chips, notes, that day's readiness), the debrief the app calculated in plain English, and the
     * instruction to treat the numbers as fact. Blank when the workout is gone.
     */
    suspend fun debriefChatMessage(sessionId: Long): String {
        val s = settings.current()
        val unit = s.weightUnit
        val session = db.sessions().byId(sessionId) ?: return ""
        val sets = db.sessions().observeSets(sessionId).first().filter { it.done }
        if (sets.isEmpty()) return ""
        val snap = progress.freshSnapshot()
        val library = snap.exercises
        val name = { id: String -> library[id]?.name ?: id }
        val facts = buildString {
            appendLine("Workout: ${session.title}, ${LocalDate.ofEpochDay(session.epochDay)}, ${((session.finishedAt ?: System.currentTimeMillis()) - session.startedAt) / 60_000} min")
            appendLine("Body goal: ${s.nutritionGoal.name}")
            db.readiness().get(session.epochDay)?.let { appendLine("Readiness that day: recovery ${it.recovery}%, sleep ${it.sleepMin} min") }
            if (session.note.isNotBlank()) appendLine("Their note: ${session.note}")
            workouts.scores(snap, only = sessionId)[sessionId]?.let { r ->
                val parts = listOfNotNull(
                    if (r.plannedSets == null) "${r.doneSets} sets" else if (r.doneSets >= r.plannedSets) "All ${r.doneSets} sets" else "${r.doneSets} of ${r.plannedSets} sets",
                    if (r.compared > 0) "up on ${r.up} of ${r.compared}" else null,
                    if (r.prs > 0) "${r.prs} ${if (r.prs > 1) "PRs" else "PR"}" else null,
                )
                appendLine("Score (calculated by the app, do not change it): ${r.score}/10. ${parts.joinToString(", ")}.")
            }
            sets.groupBy { it.exerciseId }.forEach { (id, done) ->
                fun line(list: List<SetEntity>) = list.sortedBy { it.setIndex }.joinToString(", ") { st ->
                    listOfNotNull(
                        st.reps?.let { "$it reps" }, st.seconds?.let { "${it}s" }, st.weightKg?.takeIf { it > 0f }?.let { "${unit.number(it)}${unit.code}" },
                        st.effort.takeIf { it.isNotBlank() && it != "OK" }?.lowercase(), st.note.takeIf { it.isNotBlank() }?.let { "\"$it\"" },
                        if (st.isPr) "PR" else null,
                    ).joinToString(" ")
                }
                appendLine("- ${name(id)}: ${line(done)}")
                // Only workouts before this one, so a debrief asked for later from the journal compares the right way.
                val before = db.sessions().doneSetsBefore(id, session.epochDay, session.startedAt)
                val last = before.filter { it.sessionId == before.firstOrNull()?.sessionId }
                if (last.isNotEmpty()) appendLine("  last time: ${line(last)}")
            }
        }
        val calculated = workouts.debrief(snap, sessionId)?.let { d ->
            AppText.englishResources()?.let { res ->
                val lines = DebriefText.render(d, res, name) { "${unit.number(it)} ${unit.code}" }
                buildString {
                    appendLine("Debrief calculated by the app:")
                    appendLine(lines.headline)
                    (lines.wins + lines.watch + lines.next).forEach { appendLine("- $it") }
                    lines.sameRest?.let { appendLine("- $it") }
                }
            }
        }.orEmpty()
        val language = if (isArabicUi()) "formal Modern Standard Arabic (Western digits)" else "English"
        return buildString {
            append(facts)
            if (calculated.isNotBlank()) { appendLine(); append(calculated) }
            appendLine()
            appendLine("Treat these numbers and next-time targets as fact; don't recalculate or contradict them.")
            append("Give me your take in a few short lines: what went well, one thing to watch, and one tip. Reply in $language.")
        }
    }

    /** "Tuesday: Dips → Push-up" lines for a change-only card, in the app language. */
    private fun describeChanges(patch: PlanPatch, library: Map<String, ExerciseEntity>): List<String> {
        fun name(id: String?) = id?.let { PlanValidator.canonicalId(it, null, library) ?: it }?.let { library[it]?.name ?: it }.orEmpty()
        return patch.changes.map { c ->
            val day = Dates.dayName(c.dayOfWeek.coerceIn(1, 7))
            when (c.type) {
                "swap" -> AppText.get(R.string.coach_change_swap, day, name(c.exerciseId), name(c.newExerciseId))
                "add" -> AppText.get(R.string.coach_change_add, day, name(c.newExerciseId))
                "remove" -> AppText.get(R.string.coach_change_remove, day, name(c.exerciseId))
                else -> AppText.get(R.string.coach_change_edit, day, name(c.exerciseId))
            }
        }
    }

    /** Turns free text (pasted, or exported from Google Docs) into a plan. */
    suspend fun parsePlanText(text: String): PlanDraft {
        val eq = settings.current().configuredEquipment
        val system = """
            Convert the user's workout plan text into JSON. Keep their exercises, sets, reps and days.
            ${prompts.planSchemaInstructions(eq)}
            Reply ONLY with the JSON object, no other text.
        """.trimIndent()
        val raw = gemini.generate(requireKey(), models(), system, listOf("user" to text.take(20_000)), jsonOutput = true, lowThinking = true)
        return PlanJson.extract("```json\n$raw\n```") ?: throw AiError.Unreadable(R.string.ai_err_plan_text)
    }

    suspend fun setNotes(notes: String) = settings.update {
        val cleaned = notes.lines().map(String::trim).filter { l -> l.isNotBlank() }.joinToString("\n")
        it.copy(coachNotes = cleaned, equipment = equipmentWith(it.equipment, inferEquipmentFromText(cleaned)))
    }

    /** The saved equipment, or [inferred] when nothing is saved yet. */
    private fun equipmentWith(current: String, inferred: Collection<Equipment>?): String =
        if (current.isBlank() && inferred != null) inferred.joinToString(",") { it.name } else current

    private fun requireKey(): String = settings.apiKey.value.ifBlank { throw AiError.MissingKey }

    private suspend fun models(): List<String> {
        val m = models.current()
        // One fixed chain: the model picker was removed.
        return listOf(m.fast, GeminiClient.FALLBACK_STABLE_FLASH, m.lite)
    }

    /** A plan request, or an edit ("swap dips on Tuesday") that names a day or an exercise in the current plan. */
    private suspend fun wantsPlan(text: String): Boolean {
        val snap = progress.snapshot.first()
        val names = snap.plan?.days.orEmpty().flatMap { it.items }.map { it.exerciseId }.distinct()
            .flatMap { id -> listOfNotNull(id.replace('_', ' '), snap.exercises[id]?.name) }
        return PlanIntent.matches(text, names)
    }

    private fun isArabicUi() = Locale.getDefault().language == "ar"

    /** Script of the person's latest message, so the reply never drifts into another language. */
    internal fun languageOf(text: String): String = when {
        text.any { it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' } -> "Arabic"
        text.any { it.isLetter() && it.code < 0x250 } -> "English"
        else -> "the language of the user's latest message"
    }

    // ------------------------------------------------------------ Prompting

    /** The exact chat system prompt, for the debug latency bench. */
    internal suspend fun systemPromptForBench(latest: String): String {
        val eq = settings.current().configuredEquipment
        val wants = wantsPlan(latest)
        return prompts.coachSystemPrompt(languageOf(latest), plan = wants && eq.isNotEmpty(), needsEquipmentIntake = wants && eq.isEmpty(), availableEquipment = eq)
    }

    private companion object {
        const val TAG = "FQ-AI"
    }
}

/**
 * Asking to make or change a plan, not just mentioning one, plus detection for when the user is answering
 * the coach's equipment intake question.
 */
internal object PlanIntent {
    private val planWord = Regex("""\b(plan|program|programme|split|routine|schedule)s?\b""")
    private val action = Regex("""\b(make|build|create|write|design|give|new|change|update|adjust|modify|redo|swap|replace|generate|set up|turn|convert)\b""")
    private val arabicPlan = Regex("جدول|برنامج|خطة|خطه|روتين|سبليت")
    private val arabicAction = Regex("اعمل|سوي|سوّي|ابني|اكتب|صمم|غير|غيّر|عدل|عدّل|بدل|جديد|اعطني|عطني|ابغى|أبغى|بدي|عايز|حضر|جهز")

    private val editAction = Regex("""\b(swap|replace|remove|drop|add|switch|substitute|more|less|fewer|extra)\b""")
    private val arabicEdit = Regex("بدل|بدّل|استبدل|احذف|أضف|اضف|زد|قلل|أنقص|غير|غيّر")
    private val dayWord = Regex("""\b(mon|tue|wed|thu|fri|sat|sun)(day|s|sday|nesday|rsday|urday)?\b""")
    private val arabicDay = Regex("الاثنين|الإثنين|الثلاثاء|الأربعاء|الاربعاء|الخميس|الجمعة|السبت|الأحد|الاحد")

    /**
     * True for "make me a new plan" and also for edits like "swap dips on Tuesday" or "add more pull-ups":
     * an edit verb plus a day name or an exercise from the current plan ([planExercises], lowercase or not).
     */
    fun matches(text: String, planExercises: Collection<String> = emptyList()): Boolean {
        val t = text.lowercase()
        if ((planWord.containsMatchIn(t) || arabicPlan.containsMatchIn(t)) && (action.containsMatchIn(t) || arabicAction.containsMatchIn(t))) return true
        val edit = editAction.containsMatchIn(t) || arabicEdit.containsMatchIn(t)
        if (!edit) return false
        val mentionsDay = dayWord.containsMatchIn(t) || arabicDay.containsMatchIn(t)
        val mentionsExercise = planExercises.any { n -> n.length >= 3 && n.lowercase() in t }
        return mentionsDay || mentionsExercise
    }

    /** True when the preceding user turn asked for a plan, the coach asked an intake question, and the user just answered it. */
    fun isReplyToIntake(history: List<ChatEntity>, latestUserText: String): Boolean {
        if (history.size < 3) return false
        val prevCoach = history.getOrNull(history.lastIndex - 1) ?: return false
        val prevUser = history.getOrNull(history.lastIndex - 2) ?: return false
        if (prevCoach.role != "coach" || prevCoach.planJson != null) return false
        val coachAskedEquipment = Regex("""(equipment|gym|dumbbell|pull-?up|bodyweight|معدات|نادي|دمبل|عقلة|وزن الجسم)""", RegexOption.IGNORE_CASE)
            .containsMatchIn(prevCoach.text)
        return matches(prevUser.text) && coachAskedEquipment && latestUserText.isNotBlank()
    }
}