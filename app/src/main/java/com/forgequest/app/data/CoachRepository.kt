package com.forgequest.app.data

import android.os.SystemClock
import android.util.Log
import com.forgequest.app.AppText
import com.forgequest.app.R
import com.forgequest.app.data.ai.AiError
import com.forgequest.app.data.ai.GeminiClient
import com.forgequest.app.data.ai.GroqClient
import com.forgequest.app.data.ai.ModelStore
import com.forgequest.app.data.db.AppDatabase
import com.forgequest.app.data.db.ChatEntity
import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.SetEntity
import com.forgequest.app.data.plan.CoachMemory
import com.forgequest.app.data.plan.PlanDraft
import com.forgequest.app.data.plan.PlanHash
import com.forgequest.app.data.plan.PlanJson
import com.forgequest.app.data.plan.PlanPatch
import com.forgequest.app.data.plan.PlanPatches
import com.forgequest.app.data.plan.PlanValidator
import com.forgequest.app.data.plan.WorkoutDebrief
import com.forgequest.app.data.prefs.SettingsRepository
import com.forgequest.app.data.seed.ExerciseCatalog
import com.forgequest.app.domain.Badges
import com.forgequest.app.domain.Dates
import com.forgequest.app.domain.DayState
import com.forgequest.app.domain.Equipment
import com.forgequest.app.domain.ExerciseKind
import com.forgequest.app.domain.ExerciseSwapEngine
import com.forgequest.app.domain.inferEquipmentFromText
import com.forgequest.app.domain.Muscle
import com.forgequest.app.domain.Nutrition
import com.forgequest.app.domain.NutritionGoal
import com.forgequest.app.domain.Records
import com.forgequest.app.domain.Trends
import com.forgequest.app.domain.WeightTrend
import com.forgequest.app.domain.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class CoachRepository(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val gemini: GeminiClient,
    private val models: ModelStore,
    private val progress: ProgressRepository,
    private val workouts: WorkoutRepository,
    private val groq: GroqClient,
    private val health: com.forgequest.app.data.health.HealthRepository,
) {
    private val chat = db.chat()
    private val chatMutex = Mutex()

    /** Who answered the last message and how fast, for the Coach menu ("Groq · 0.8 s"). */
    private val _lastAnswer = MutableStateFlow<Pair<String, Long>?>(null)
    val lastAnswer: StateFlow<Pair<String, Long>?> = _lastAnswer

    val messages: Flow<List<ChatEntity>> = chat.observeAll()

    /** The reply currently streaming in, or null. The screen shows it as a live bubble. */
    private val _draft = MutableStateFlow<String?>(null)
    val draft: StateFlow<String?> = _draft

    suspend fun clear() = chatMutex.withLock { chat.clear() }

    /** Opens the connection to Google when the Coach tab opens, so the first message skips the handshake. */
    suspend fun warmUp() {
        gemini.warmUp(settings.apiKey.value)
        suspendRunCatching { models.current() }
    }

    suspend fun send(text: String) = chatMutex.withLock {
        val tapped = SystemClock.elapsedRealtime()
        val trimmed = text.trim()
        chat.insert(ChatEntity(role = "user", text = trimmed, createdAt = System.currentTimeMillis()))
        val rawRecent = chat.recent(14).reversed()
        val history = answeredHistory(rawRecent)

        // Persist any equipment explicitly mentioned in the user's message.
        val newlyInferredEq = inferEquipmentFromText(trimmed)
        if (newlyInferredEq != null && settings.current().equipment.isBlank()) {
            settings.update { it.copy(equipment = newlyInferredEq.joinToString(",") { e -> e.name }) }
        }

        val currentSettings = settings.current()
        val knownEquipment = currentSettings.configuredEquipment
            .ifEmpty { inferEquipmentFromText(currentSettings.coachNotes, trimmed).orEmpty() }

        val replyingToIntake = PlanIntent.isReplyToIntake(history, trimmed)
        val wantsPlan = wantsPlan(trimmed) || (replyingToIntake && knownEquipment.isNotEmpty())
        val needsEquipmentIntake = wantsPlan && knownEquipment.isEmpty()
        val shouldEmitPlanJson = wantsPlan && !needsEquipmentIntake

        _draft.value = ""
        try {
            val system = coachSystemPrompt(
                replyLanguage = languageOf(trimmed),
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
            var provider = "Gemini"
            val modelChoice = currentSettings.coachModel
            // Chat goes to Groq (Llama 3.3 70B) when there's a Groq key and Automatic is chosen; plans stay on Gemini.
            val viaGroq = !shouldEmitPlanJson && settings.groqKey.value.isNotBlank() && modelChoice == "auto"
            val fromGroq = if (!viaGroq) null else try {
                groq.streamChat(settings.groqKey.value, groqModel(), system, turns, onDelta = onDelta).also { provider = "Groq" }
            } catch (e: AiError) {
                if (e is AiError.Partial) throw e
                Log.w(TAG, "groq failed before any text, using Gemini: ${e.detail}")
                null
            }
            val useZeroThinking = !shouldEmitPlanJson && modelChoice == "lite"
            val reply = fromGroq ?: gemini.generateStreaming(
                apiKey = requireKey(),
                models = models(),
                system = system,
                turns = turns,
                // Full thinking for weekly plans; lowThinking (~1,024 budget) for normal coaching so the coach
                // actually reasons over Readiness, HRV, recovery and set history; zero thinking only if user pinned "lite".
                fast = useZeroThinking,
                lowThinking = !shouldEmitPlanJson && !useZeroThinking,
                onDelta = onDelta,
            )
            _lastAnswer.value = provider to firstText
            Log.i(TAG, "send→first text ${firstText} ms, send→done ${SystemClock.elapsedRealtime() - tapped} ms via $provider (plan=$shouldEmitPlanJson, intake=$needsEquipmentIntake)")
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
     * Drops error bubbles, background check-ins, and unanswered questions so the model only sees clean
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

    private suspend fun saveError(e: AiError) {
        val text = if (e is AiError.Timeout) AppText.get(R.string.ai_err_coach_timeout) else e.userMessage
        val s = settings.current()
        val detail = listOf(e.javaClass.simpleName, e.detail, "models ${s.liteModel} / ${s.fastModel}, app ${com.forgequest.app.BuildConfig.VERSION_NAME}")
            .filter { it.isNotBlank() }.joinToString("\n")
        chat.insert(ChatEntity(role = "coach", text = text, kind = "error", detail = detail, createdAt = System.currentTimeMillis()))
    }

    /** Result of reading a reply's plan or change block against the library and the current plan. */
    private class PlanCheck(val draft: PlanDraft?, val errors: List<String>, val unknownIds: Set<String>, val broken: Boolean)

    private fun checkPlan(reply: String, library: Map<String, ExerciseEntity>, current: PlanDraft?): PlanCheck? {
        val currentHash = current?.let(PlanHash::of)
        PlanJson.extractPatch(reply)?.let { patch ->
            if (current == null) return PlanCheck(null, listOf("There is no current plan to change; send the full plan instead."), emptySet(), true)
            val out = PlanPatches.apply(current, patch, library)
            return PlanCheck(out.draft, out.errors, emptySet(), out.draft == null)
        }
        PlanJson.extract(reply)?.let { draft ->
            val v = PlanValidator.validate(draft, library)
            return PlanCheck(v.draft.copy(basedOnHash = currentHash), v.errors, v.unknownIds, false)
        }
        return if (PlanJson.hasBrokenBlock(reply)) PlanCheck(null, listOf("The JSON block could not be parsed; check brackets, quotes and commas."), emptySet(), true) else null
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
        if (checked != null && check != null && check.unknownIds.isNotEmpty()) {
            val (replaced, swaps) = PlanValidator.replaceUnknown(checked, check.unknownIds, snap.exercises.values.toList(), availableEquipment)
            checked = replaced
            swaps.forEach { (old, new) ->
                extraNotes += if (new.isBlank()) AppText.get(R.string.coach_plan_dropped, old) else AppText.get(R.string.coach_plan_replaced, old, new)
            }
        }
        if (check != null && checked == null) extraNotes += AppText.get(R.string.coach_plan_unreadable)
        val patch = checked?.patch
        if (patch != null && current != null) extraNotes += describeChanges(current, patch, snap.exercises)
        val rawPlan = checked
        val notes = CoachMemory.extract(reply)
        if (notes.isNotEmpty()) {
            val inferredEq = inferEquipmentFromText(*notes.toTypedArray())
            settings.update { s ->
                val mergedNotes = CoachMemory.merge(s.coachNotes, notes)
                val updatedEq = if (s.equipment.isBlank() && inferredEq != null) {
                    inferredEq.joinToString(",") { it.name }
                } else {
                    s.equipment
                }
                s.copy(coachNotes = mergedNotes, equipment = updatedEq)
            }
        }
        val adaptedPlan = if (rawPlan != null && availableEquipment.isNotEmpty()) {
            val lib = progress.snapshot.first().exercises
            ExerciseSwapEngine.adaptPlanToEquipment(rawPlan, lib, availableEquipment, dropUnfit = true).first
        } else {
            rawPlan
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

    /** A debrief can be asked for at all: a Groq or Gemini key is saved. */
    fun canDebrief(): Boolean = settings.groqKey.value.isNotBlank() || settings.apiKey.value.isNotBlank()

    /**
     * The coach's short debrief of a finished workout: compares it with the last time each exercise was done before
     * it, the effort chips and notes, and that day's readiness. Gemini first when set (no thinking), Groq in JSON mode
     * when Gemini fails or its reply can't be read; [DEBRIEF_TIMEOUT_MS] overall. Null when offline, keyless
     * or unreadable: the screen then offers Try again. [onDemand] (asked for from the journal) ignores the setting.
     */
    suspend fun generateDebrief(sessionId: Long, onDemand: Boolean = false): WorkoutDebrief? = suspendRunCatching {
        val s = settings.current()
        if (!s.debriefEnabled && !onDemand) return@suspendRunCatching null
        if (!canDebrief()) return@suspendRunCatching null
        val groqKey = settings.groqKey.value
        val geminiKey = settings.apiKey.value
        val session = db.sessions().byId(sessionId) ?: return@suspendRunCatching null
        val sets = db.sessions().observeSets(sessionId).first().filter { it.done }
        if (sets.isEmpty()) return@suspendRunCatching null
        val library = progress.snapshot.first().exercises
        val payload = buildString {
            appendLine("Workout: ${session.title}, ${LocalDate.ofEpochDay(session.epochDay)}, ${((session.finishedAt ?: System.currentTimeMillis()) - session.startedAt) / 60_000} min")
            appendLine("Body goal: ${s.nutritionGoal.name}")
            db.readiness().get(session.epochDay)?.let { appendLine("Readiness that day: recovery ${it.recovery}%, sleep ${it.sleepMin} min") }
            if (session.note.isNotBlank()) appendLine("Their note: ${session.note}")
            sets.groupBy { it.exerciseId }.forEach { (id, done) ->
                val name = library[id]?.name ?: id
                fun line(list: List<SetEntity>) = list.sortedBy { it.setIndex }.joinToString(", ") { st ->
                    listOfNotNull(
                        st.reps?.let { "$it reps" }, st.seconds?.let { "${it}s" }, st.weightKg?.takeIf { it > 0f }?.let { "${it}kg" },
                        st.effort.takeIf { it.isNotBlank() && it != "OK" }?.lowercase(), st.note.takeIf { it.isNotBlank() }?.let { "\"$it\"" },
                        if (st.isPr) "PR" else null,
                    ).joinToString(" ")
                }
                appendLine("- $name: ${line(done)}")
                // Only workouts before this one, so a debrief asked for later from the journal compares the right way.
                val before = db.sessions().doneSetsBefore(id, session.epochDay, session.startedAt)
                val last = before.filter { it.sessionId == before.firstOrNull()?.sessionId }
                if (last.isNotEmpty()) appendLine("  last time: ${line(last)}")
            }
        }
        val language = if (java.util.Locale.getDefault().language == "ar") "formal Modern Standard Arabic (Western digits)" else "English"
        val system = """
            You are a friendly strength coach writing a 10-second debrief of a finished workout.
            Reply with ONLY a JSON object: {"score": 4-10, "summary": one short sentence, "highlights": [1-2 short items citing real numbers],
            "watch": [0-1 short item: a stall, a pain/hard note, or low readiness], "nextTime": [1 short, concrete tip]}.
            Finishing counts: never score below 4. Be specific, warm and brief. Write in $language.
        """.trimIndent()
        val turns = listOf("user" to payload)
        val debrief = withTimeoutOrNull(DEBRIEF_TIMEOUT_MS) {
            val fromGemini = if (geminiKey.isBlank()) null else suspendRunCatching { gemini.generate(geminiKey, models(), system, turns, jsonOutput = true, fast = true) }
                .onFailure { Log.w(TAG, "debrief: gemini failed, using Groq: ${it.message}") }.getOrNull()
                ?.let { text -> WorkoutDebrief.parse(text) ?: null.also { Log.w(TAG, "debrief: gemini reply unreadable: ${text.take(200)}") } }
            fromGemini ?: groqKey.takeIf { it.isNotBlank() }?.let { key ->
                val text = groq.streamChat(key, groqModel(), system, turns, jsonOutput = true) {}
                WorkoutDebrief.parse(text) ?: null.also { Log.w(TAG, "debrief: groq reply unreadable: ${text.take(200)}") }
            }
        } ?: return@suspendRunCatching null.also { Log.w(TAG, "debrief: no readable reply within $DEBRIEF_TIMEOUT_MS ms") }
        // Re-read the workout so a note added while the coach was writing isn't overwritten.
        debrief.also { d -> db.sessions().byId(sessionId)?.let { db.sessions().update(it.copy(debrief = WorkoutDebrief.encode(d))) } }
    }.onFailure { Log.w(TAG, "debrief failed: ${it.message}") }.getOrNull()

    /** "Tuesday: Dips → Push-up" lines for a change-only card, in the app language. */
    private fun describeChanges(base: PlanDraft, patch: PlanPatch, library: Map<String, ExerciseEntity>): List<String> {
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
            ${planSchemaInstructions(eq)}
            Reply ONLY with the JSON object, no other text.
        """.trimIndent()
        val raw = gemini.generate(requireKey(), models(), system, listOf("user" to text.take(20_000)), jsonOutput = true, lowThinking = true)
        return PlanJson.extract("```json\n$raw\n```") ?: throw AiError.Unreadable(R.string.ai_err_plan_text)
    }

    /** Once per week, a short review of last week. Returns true if one was written. */
    suspend fun weeklyCheckinIfDue(): Boolean {
        if (settings.apiKey.value.isBlank()) return false
        if (chatMutex.isLocked) return false
        return chatMutex.withLock {
            val thisWeek = Dates.weekStart(Dates.today())
            val s = settings.current()
            if (s.lastCheckinWeek >= thisWeek) return@withLock false
            val snap = progress.snapshot.first()
            val lastWeek = thisWeek - 7
            if (snap.sessions.none { it.epochDay in lastWeek until thisWeek }) return@withLock false
            settings.update { it.copy(lastCheckinWeek = thisWeek) }
            try {
                val system = coachSystemPrompt(replyLanguage = if (java.util.Locale.getDefault().language == "ar") "Arabic" else "English")
                val ask = listOf("user" to "Write my weekly check-in for last week: 3 short parts — what went well, what to push or deload this week, one nutrition/recovery tip. Under 180 words. No plan JSON, no MEMORY line.")
                val viaGroq = settings.groqKey.value.isNotBlank() && settings.current().coachModel == "auto"
                val reply = (if (viaGroq) suspendRunCatching { groq.streamChat(settings.groqKey.value, groqModel(), system, ask) {} }.getOrNull() else null)
                    ?: gemini.generate(requireKey(), models(), system, ask, lowThinking = true)
                chat.insert(ChatEntity(role = "coach", text = PlanJson.strip(reply), kind = "checkin", createdAt = System.currentTimeMillis()))
                true
            } catch (e: AiError) {
                settings.update { it.copy(lastCheckinWeek = thisWeek - 7) }
                false
            }
        }
    }

    suspend fun setNotes(notes: String) = settings.update {
        val cleaned = notes.lines().map(String::trim).filter { l -> l.isNotBlank() }.joinToString("\n")
        val inferred = inferEquipmentFromText(cleaned)
        it.copy(
            coachNotes = cleaned,
            equipment = if (it.equipment.isBlank() && inferred != null) inferred.joinToString(",") { e -> e.name } else it.equipment,
        )
    }

    private fun requireKey(): String = settings.apiKey.value.ifBlank { throw AiError.MissingKey }

    private suspend fun models(): List<String> {
        val m = models.current()
        return when (settings.current().coachModel) {
            "lite" -> listOf(m.lite, m.fast, GeminiClient.FALLBACK_STABLE_FLASH)
            "pro" -> listOf(m.smart, m.fast, GeminiClient.FALLBACK_STABLE_FLASH, m.lite)
            else -> listOf(m.fast, GeminiClient.FALLBACK_STABLE_FLASH, m.lite)
        }
    }

    suspend fun setCoachModel(choice: String) = settings.update { it.copy(coachModel = choice) }

    /** Checks a Groq key by reading its model list, then saves it with the fastest chat model it offers. */
    suspend fun saveGroqKey(key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) { settings.setGroqKey(""); return }
        val model = groq.pickModel(trimmed)
        settings.setGroqKey(trimmed)
        settings.update { it.copy(groqModel = model) }
    }

    private suspend fun groqModel(): String = settings.current().groqModel.ifBlank { GroqClient.FALLBACK_MODEL }

    /** A plan request, or an edit ("swap dips on Tuesday") that names a day or an exercise in the current plan. */
    private suspend fun wantsPlan(text: String): Boolean {
        val snap = progress.snapshot.first()
        val names = snap.plan?.days.orEmpty().flatMap { it.items }.map { it.exerciseId }.distinct()
            .flatMap { id -> listOfNotNull(id.replace('_', ' '), snap.exercises[id]?.name) }
        return PlanIntent.matches(text, names)
    }

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
        return coachSystemPrompt(languageOf(latest), plan = wants && eq.isNotEmpty(), needsEquipmentIntake = wants && eq.isEmpty(), availableEquipment = eq)
    }

    private suspend fun coachSystemPrompt(
        replyLanguage: String = "the language of the user's latest message",
        plan: Boolean = false,
        needsEquipmentIntake: Boolean = false,
        availableEquipment: Set<Equipment> = emptySet(),
    ): String = buildString {
        appendLine(CORE_RULES)
        appendLine()
        when {
            needsEquipmentIntake -> {
                appendLine("EQUIPMENT INTAKE REQUIRED: The user asked to build or change a workout plan, but we do not yet know what equipment they have access to.")
                appendLine("Do NOT output a ```json plan block in this reply. Instead, ask a concise, friendly 3-bullet intake question:")
                appendLine("1. What equipment do you have access to? (e.g., Full commercial gym, home dumbbells/barbell, pull-up bar & dip bars, doorway pull-up bar only, or pure bodyweight)")
                appendLine("2. How many days per week do you want to train? (if not already stated)")
                appendLine("3. What is your main goal right now? (muscle growth, strength, fat loss, or calisthenics skills)")
                appendLine("Tell them you will generate their ready-to-apply plan as soon as they answer.")
            }
            plan -> {
                appendLine("When the user asks to create or change their weekly plan, describe it briefly, then add the full plan as a ```json code block at the very end.")
                appendLine(planSchemaInstructions(availableEquipment, progress.snapshot.first().plan?.toDraft()))
            }
            else -> {
                appendLine("Never write plan JSON in this reply. If they want a new or changed weekly plan, outline it in a few lines and tell them to say \"make it my plan\" (in Arabic: \"اعمل لي الخطة\") to get one they can apply.")
            }
        }
        appendLine()
        appendLine(MEMORY_RULE)
        appendLine()
        append(progressSummary())
        appendLine()
        append("LANGUAGE: reply in $replyLanguage. Decide from the latest message only, never from earlier messages, the user's name or the data above.")
        if (replyLanguage == "Arabic") append(" Write formal Modern Standard Arabic (الفصحى), not dialect: natural Arabic phrasing, not translated English; use the Arabic comma (،) and question mark (؟). Keep exercise names in English in parentheses the first time, and write numbers as Western digits (0-9).")
    }

    fun planSchemaInstructions(availableEquipment: Set<Equipment> = emptySet(), current: PlanDraft? = null): String {
        val allowed = availableEquipment.ifEmpty { Equipment.entries.toSet() } + Equipment.BODYWEIGHT
        val allowedCatalog = ExerciseCatalog.all.filter { ex ->
            ExerciseSwapEngine.profileFor(ex).equipment in allowed
        }
        val ids = allowedCatalog.joinToString(", ") { it.id }
        val equipmentRule = if (availableEquipment.isNotEmpty() && !allowed.containsAll(Equipment.entries)) {
            "CRITICAL EQUIPMENT CONSTRAINT: The user ONLY has access to: ${allowed.joinToString(", ") { it.labelEn }}. You MUST ONLY program exercises that use this equipment. NEVER program unavailable machines, cables, or barbells."
        } else {
            "Match exercise selection to the user's available equipment."
        }
        return """
            Plan JSON shape: {"name": string, "description": string, "days": [{"dayOfWeek": 1-7 (1 = Monday), "title": string, "isRest": boolean, "note": string,
            "items": [{"exerciseId": string, "name": string, "kind": "REPS"|"HOLD"|"WEIGHTED", "muscles": [string], "block": string, "circuit": boolean,
            "sets": int, "repsMin": int|null, "repsMax": int|null, "seconds": int|null, "weightKg": number|null, "restSeconds": int, "note": string}]}]}
            Include all 7 days. $equipmentRule
            Prefer these equipment-compatible exercise ids: $ids.
            For an exercise not in that list, invent a snake_case id that strictly uses their available equipment and set name, kind and muscles (from: ${Muscle.entries.joinToString(", ") { it.name }}).
            Keep the JSON short: for ids from the list leave out name, kind and muscles; omit "note" unless needed; one-sentence description.
            Use "seconds" for holds, "repsMin"/"repsMax" for reps. Items with the same block and circuit=true are done round by round.
        """.trimIndent() + (current?.let { plan ->
            val lines = plan.days.sortedBy { it.dayOfWeek }.filter { it.items.isNotEmpty() }.joinToString("\n") { d ->
                "  ${d.dayOfWeek}: " + d.items.joinToString(", ") { i ->
                    i.exerciseId + " " + i.sets + "x" + (i.seconds?.let { "${it}s" } ?: listOfNotNull(i.repsMin, i.repsMax).distinct().joinToString("-"))
                }
            }
            "\n\n" + """
                SMALL CHANGES: when the user wants to adjust the current plan (swap, add or remove a few exercises, or change sets/reps/seconds/weight/rest) and not a whole new week, send ONLY the changes instead of the full plan, as a ```json block at the very end:
                {"changes": [{"type": "swap"|"add"|"remove"|"edit", "dayOfWeek": 1-7, "exerciseId": current id (swap/remove/edit), "newExerciseId": id (swap/add), "sets": int, "repsMin": int, "repsMax": int, "seconds": int, "weightKg": number, "restSeconds": int}]}
                Only include the fields that change. Use ids from the list above. Current plan (dayOfWeek: exerciseId setsxreps):
            """.trimIndent() + "\n" + lines
        }.orEmpty())
    }

    /**
     * What the coach knows about the user: goals, equipment, this week, plan, recovery, fatigue/deload status,
     * recent sessions, 4-week trends, records, nutrition, badges and remembered facts.
     */
    suspend fun progressSummary(): String {
        val snap = progress.snapshot.first()
        val s = snap.settings
        val today = Dates.today()
        val sb = StringBuilder("User data (today is ${LocalDate.ofEpochDay(today).format(DateTimeFormatter.ofPattern("EEEE, MMM d yyyy", Locale.ENGLISH))}):\n")
        if (s.name.isNotBlank()) sb.append("- Name: ${s.name}\n")

        // Body weight and trend
        snap.latestWeight?.let { latest ->
            val monthAgo = snap.weights.filter { it.epochDay <= today - 28 }.lastOrNull() ?: snap.weights.firstOrNull()
            val trend = monthAgo?.takeIf { it.epochDay < (snap.weights.lastOrNull()?.epochDay ?: today) }?.let { base ->
                val delta = latest - base.kg
                val days = (snap.weights.last().epochDay - base.epochDay).toInt()
                if (abs(delta) < 0.05f) " (steady over the last $days days)" else " (${if (delta > 0) "+" else "−"}${"%.1f".format(Locale.US, abs(delta))} kg over the last $days days)"
            }.orEmpty()
            sb.append("- Body weight: ${"%.1f".format(Locale.US, latest)} kg$trend\n")
        }
        val bodyMetrics = listOfNotNull(
            if (s.heightCm > 0f) "Height ${s.heightCm.toInt()} cm" else null,
            if (s.birthYear > 0) "age ${java.time.Year.now().value - s.birthYear}" else null,
            if (s.bodyFatPct in 4f..60f) "body fat ${"%.1f".format(Locale.US, s.bodyFatPct)}%" else null,
        )
        if (bodyMetrics.isNotEmpty()) sb.append("- ${bodyMetrics.joinToString(", ")}\n")
        if (s.weightUnit == WeightUnit.LB) sb.append("- Units: the user thinks in pounds. The data below is in kg; convert weights to lb when you mention them.\n")

        val eq = s.configuredEquipment
        if (eq.isNotEmpty()) {
            sb.append("- Available equipment: ${eq.joinToString(", ") { it.labelEn }}\n")
        } else {
            sb.append("- Available equipment: not specified yet (ask before building a workout plan)\n")
        }

        val target = if (s.weeklyTargetOverride > 0) s.weeklyTargetOverride else snap.plan?.trainingDays?.size ?: 0
        sb.append("- Goals: ${s.kcalGoal} kcal and ${s.proteinGoal} g protein per day; $target workouts per week\n")
        val goalText = when (s.nutritionGoal) {
            NutritionGoal.CUT -> "CUT (lose fat while keeping muscle and strength: keep protein high, aim to hold lifts steady, a moderate deficit)"
            NutritionGoal.MAINTAIN -> "MAINTAIN (keep body weight steady while getting stronger)"
            NutritionGoal.GAIN -> "GAIN (build muscle with a small surplus: push progressive overload)"
        }
        sb.append("- Body goal: $goalText\n")
        when (s.nutritionGoal) {
            NutritionGoal.CUT -> sb.append("- Cut pace: ${s.cutPace.name.lowercase()} (${s.cutPace.weeklyPct}% of body weight a week)\n")
            NutritionGoal.GAIN -> sb.append("- Gain pace: ${s.gainPace.name.lowercase()} (${s.gainPace.weeklyPct}% of body weight a week)\n")
            NutritionGoal.MAINTAIN -> Unit
        }
        when (Nutrition.weightTrendNote(s.nutritionGoal, s.goalSetDay, snap.weights.map { it.epochDay to it.kg }, today, s.cutPace, s.gainPace, s.bodyType.name == "FEMALE")) {
            WeightTrend.CUT_TOO_FAST -> sb.append("- Weight trend: losing faster than the chosen cut pace allows; suggest eating a little more\n")
            WeightTrend.GAIN_STALLED -> sb.append("- Weight trend: no gain for 3+ weeks on the gain goal; suggest a little more food\n")
            WeightTrend.GAIN_TOO_FAST -> sb.append("- Weight trend: gaining faster than the chosen pace; most of the extra is fat, suggest a little less food\n")
            WeightTrend.DIET_BREAK_DUE -> sb.append("- Diet break due: this cut has run its planned weeks; suggest 1-2 weeks at maintenance calories\n")
            null -> Unit
        }

        // This week
        val streak = snap.streak
        val missed = streak.days.withIndex().filter { it.value == DayState.MISSED }.map { Dates.dayName(it.index + 1) }
        val upcoming = streak.days.withIndex().filter { it.value == DayState.UPCOMING }.map { Dates.dayName(it.index + 1) }
        sb.append("- Level ${snap.level.level} (rank ${snap.level.rank.letter}, ${snap.level.rank.title}); weekly streak ${streak.weeks} (best ${streak.best})\n")
        if (streak.targetThisWeek == 0) {
            sb.append("- This week: ${streak.doneThisWeek} workouts done; they started mid-week, so the weekly target starts next Monday")
        } else {
            sb.append("- This week: ${streak.doneThisWeek} of ${streak.targetThisWeek} workouts done")
        }
        if (missed.isNotEmpty()) sb.append("; missed planned days: ${missed.joinToString(", ")}")
        if (upcoming.isNotEmpty()) sb.append("; still to come: ${upcoming.joinToString(", ")}")
        sb.append("\n")
        val recentTags = snap.dayTags.filterKeys { it >= today - 13 }.toSortedMap()
        if (recentTags.isNotEmpty()) {
            sb.append("- Excused days (last 2 weeks; never treat these as missed, be kind about them): ")
            sb.append(recentTags.entries.joinToString(", ") { (d, t) -> "${LocalDate.ofEpochDay(d).format(DateTimeFormatter.ofPattern("EEE MMM d", Locale.ENGLISH))} ${t.name.lowercase()}" })
            sb.append("\n")
        }
        val todayPlan = snap.plan?.day(Dates.todayDayOfWeek())
        sb.append("- Today: ")
        when {
            todayPlan == null || todayPlan.day.isRest -> sb.append("rest day")
            snap.workoutDoneToday -> sb.append("${todayPlan.day.title} (done)")
            else -> sb.append("${todayPlan.day.title} (not done yet)")
        }
        sb.append("\n")

        // Plan
        snap.plan?.let { plan ->
            sb.append("- Current plan: ${plan.plan.name} (source: ${plan.plan.source})\n")
            plan.days.forEach { d ->
                if (d.day.isRest) {
                    sb.append("  ${Dates.dayAbbrev(d.day.dayOfWeek)}: rest\n")
                } else {
                    val names = d.items.joinToString(", ") { item ->
                        val ex = snap.exercises[item.exerciseId]
                        val per = when {
                            ex?.kind == ExerciseKind.HOLD -> "${item.seconds ?: 30}s"
                            item.repsMin != null && item.repsMax != null && item.repsMax != item.repsMin -> "${item.repsMin}-${item.repsMax}"
                            item.repsMin != null -> "${item.repsMin}"
                            else -> ""
                        }
                        "${ex?.name ?: item.exerciseId}${if (per.isNotEmpty()) " ${item.sets}×$per" else ""}"
                    }
                    sb.append("  ${Dates.dayAbbrev(d.day.dayOfWeek)}: ${d.day.title}: $names\n")
                }
            }
        } ?: sb.append("- No plan set yet\n")

        // Muscle recovery
        val recovering = snap.recovery.entries.filter { it.value.fatigue >= 0.20f }.sortedByDescending { it.value.fatigue }.take(6)
        if (recovering.isNotEmpty()) {
            sb.append("- Muscle recovery: ${recovering.joinToString(", ") { "${it.key.label} (${((1f - it.value.fatigue) * 100).roundToInt()}% recovered, ready in ${it.value.hoursUntilReady} h)" }}\n")
        }

        // Fatigue, training load & plateau / deload analysis
        val readinessHistory = suspendRunCatching { db.readiness().all().takeLast(14) }.getOrDefault(emptyList())
        val loadReport = Trends.analyzeFatigueAndPlateaus(snap.sessions, snap.doneSets, snap.exercises, readinessHistory, today)
        if (snap.sessions.isNotEmpty()) {
            sb.append("- Training load & deload status: weekly set volume is ${loadReport.trainingLoadPct}% of 4-week average")
            if (loadReport.stalledLifts.isNotEmpty()) {
                sb.append("; stalled lifts (2+ weeks without progression): ${loadReport.stalledLifts.take(4).joinToString(", ") { "${it.exerciseName} (${it.sessionsCount} sessions)" }}")
            }
            if (loadReport.needsDeload) {
                sb.append("; DELOAD RECOMMENDED (${loadReport.deloadReasons.joinToString("; ")} — suggest reducing working sets by 35-40% or intensity by 10% for 1 week)")
            }
            sb.append("\n")
        }

        // Recent workouts
        val recent = snap.sessions.take(5)
        if (recent.isNotEmpty()) {
            sb.append("- Recent workouts (newest first):\n")
            recent.forEach { session ->
                val sets = snap.doneSets.filter { it.sessionId == session.id }
                val minutes = session.finishedAt?.let { ((it - session.startedAt) / 60_000).toInt() }
                val summary = sets.groupBy { it.exerciseId }.entries.take(7).joinToString("; ") { (id, list) ->
                    val ex = snap.exercises[id]
                    "${ex?.name ?: id} ${list.joinToString("/") { setLabel(ex?.kind, it) }}${if (list.any { it.isPr }) " PR" else ""}"
                }
                sb.append("  ${LocalDate.ofEpochDay(session.epochDay)} ${session.title}${minutes?.let { " ($it min)" } ?: ""}: $summary\n")
                val notes = sets.filter { it.note.isNotBlank() }.take(4).joinToString("; ") { "${snap.exercises[it.exerciseId]?.name ?: it.exerciseId}: ${it.note}" }
                if (notes.isNotEmpty()) sb.append("    notes: $notes\n")
                val felt = sets.filter { it.effort == "HARD" || it.effort == "PAIN" }.take(4)
                    .joinToString("; ") { "${snap.exercises[it.exerciseId]?.name ?: it.exerciseId} set ${it.setIndex + 1}: ${it.effort.lowercase()}" }
                if (felt.isNotEmpty()) sb.append("    felt: $felt\n")
            }
        }

        // 4-week trends for the exercises done most
        val sessionDay = snap.sessions.associate { it.id to it.epochDay }
        val byExercise = snap.doneSets.groupBy { it.exerciseId }
        val trends = byExercise.entries.sortedByDescending { it.value.size }.take(8).mapNotNull { (id, sets) ->
            val ex = snap.exercises[id] ?: return@mapNotNull null
            val old = sets.filter { (sessionDay[it.sessionId] ?: 0) in (today - 56)..(today - 21) }
            val new = sets.filter { (sessionDay[it.sessionId] ?: 0) > today - 21 }
            if (old.isEmpty() || new.isEmpty()) return@mapNotNull null
            val bestOld = old.maxBy { Records.score(ex.kind, it.reps, it.seconds, it.weightKg, ex.id) }
            val bestNew = new.maxBy { Records.score(ex.kind, it.reps, it.seconds, it.weightKg, ex.id) }
            "${ex.name} ${setLabel(ex.kind, bestOld)} → ${setLabel(ex.kind, bestNew)}"
        }
        if (trends.isNotEmpty()) sb.append("- Best set 3-8 weeks ago → best set in the last 3 weeks: ${trends.joinToString("; ")}\n")

        // Personal bests
        val bests = byExercise.entries.mapNotNull { (id, sets) ->
            val ex = snap.exercises[id] ?: return@mapNotNull null
            val best = sets.maxByOrNull { Records.score(ex.kind, it.reps, it.seconds, it.weightKg, ex.id) } ?: return@mapNotNull null
            Triple(ex.name, setLabel(ex.kind, best), sets.size)
        }.sortedByDescending { it.third }.take(12)
        if (bests.isNotEmpty()) sb.append("- Personal bests: ${bests.joinToString(", ") { "${it.first} ${it.second}" }}\n")

        // Nutrition
        val week = snap.nutritionByDay.filter { it.epochDay > today - 7 && it.epochDay < today }
        if (week.isNotEmpty()) {
            val hit = week.count { it.protein >= s.proteinGoal }
            sb.append("- Last 7 days nutrition: avg ${(week.sumOf { it.kcal }.toDouble() / week.size).roundToInt()} kcal, ${(week.sumOf { it.protein.toDouble() } / week.size).roundToInt()} g protein over ${week.size} logged days; protein goal hit on $hit of them\n")
        } else {
            sb.append("- No meals logged in the last 7 days\n")
        }
        sb.append("- Today so far: ${snap.today.kcal} kcal, ${snap.today.protein.roundToInt()} g protein")
        if (s.trackCarbs) sb.append(", ${snap.today.carbs.roundToInt()} g carbs (goal ${s.carbsGoal} g)")
        sb.append("\n")
        health.coachLine()?.let { sb.append(it) }

        // Badges
        val unlocked = snap.badges.sortedByDescending { it.unlockedAt }
        sb.append("- Badges: ${unlocked.size} of ${Badges.all.size} unlocked")
        if (unlocked.isNotEmpty()) sb.append("; latest: ${unlocked.take(3).mapNotNull { Badges.byId[it.id]?.title }.joinToString(", ")}")
        sb.append("\n")

        // Memory
        val notes = s.coachNotes.lines().filter { it.isNotBlank() }
        if (notes.isNotEmpty()) {
            sb.append("- Things to remember about the user:\n")
            notes.forEach { sb.append("  • $it\n") }
        }
        return sb.toString()
    }

    private fun setLabel(kind: ExerciseKind?, set: SetEntity): String = when (kind) {
        ExerciseKind.HOLD -> "${set.seconds}s" + (set.weightKg?.takeIf { it > 0f }?.let { "+${"%.1f".format(Locale.US, it).removeSuffix(".0")}kg" } ?: "")
        ExerciseKind.WEIGHTED -> "${set.reps}×${"%.1f".format(Locale.US, set.weightKg ?: 0f).removeSuffix(".0")}kg"
        else -> "${set.reps}" + (set.weightKg?.takeIf { it > 0f }?.let { "+${"%.1f".format(Locale.US, it).removeSuffix(".0")}kg" } ?: "")
    }

    private companion object {
        const val TAG = "FQ-AI"
        // Runs while the summary is already showing, so it can afford a slow network and a Gemini → Groq fallback.
        const val DEBRIEF_TIMEOUT_MS = 30_000L

        val CORE_RULES = """
            You are the head performance and nutrition coach inside ForgeQuest (acting as a high-precision Sports Science, Calisthenics & Gym Coach).
            - Synthesize, do not just parrot stats back: connect the dots between Fitbit Readiness (recovery %, HRV vs baseline, resting HR, sleep debt, strain target), muscle recovery hours, training load / deload status, recent set performance, and daily protein/calorie intake.
            - Give concrete, actionable prescriptions: when asked about today's session or progression, specify exact set/rep/hold/weight adjustments based on recovery state and their last logged sets. If a deload or lift plateau is flagged in the summary, proactively prescribe a structured deload (cutting sets by 35-40% for 1 week) or a biomechanical exercise variation.
            - Strict sports nutrition guardrails: NEVER prescribe a daily calorie target below ${Nutrition.MIN_SAFE_KCAL_MALE} kcal/day for men or ${Nutrition.MIN_SAFE_KCAL} for women (and never under 85% of their BMR), or a loss faster than 1% of body weight a week (a deficit over 30% of TDEE). Always maintain a protein floor of 1.6-2.2 g/kg and an essential dietary fat floor of at least 0.6-0.8 g/kg before allocating carbohydrates.
            - Keep normal replies focused and punchy (120-250 words), using **bold** and clean bullet points, and go deeper when the user asks for analysis or meal/workout breakdowns.
            - Never shame food choices; going over calories is fuel, not failure.
            - You cannot open links or watch videos. If the user shares a YouTube or other link, say so plainly, then point them to Workouts → Change plan → "Make a plan with Gemini", which turns a YouTube programme into a plan through the Gemini app.
        """.trimIndent()

        val MEMORY_RULE = """
            If the user tells you something lasting about themselves (an injury, their available equipment, their schedule, a food preference, a goal), finish your reply with one final line exactly like: MEMORY: <one short sentence>
            Only do this for new facts not already in "Things to remember" below. Never mention this mechanism.
        """.trimIndent()
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