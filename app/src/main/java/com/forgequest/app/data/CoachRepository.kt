package com.forgequest.app.data

import com.forgequest.app.AppText
import com.forgequest.app.R
import com.forgequest.app.data.ai.AiError
import com.forgequest.app.data.ai.GeminiClient
import com.forgequest.app.data.ai.ModelStore
import com.forgequest.app.data.db.AppDatabase
import com.forgequest.app.data.db.ChatEntity
import com.forgequest.app.data.db.SetEntity
import com.forgequest.app.data.plan.CoachMemory
import com.forgequest.app.data.plan.PlanDraft
import com.forgequest.app.data.plan.PlanJson
import com.forgequest.app.data.prefs.SettingsRepository
import com.forgequest.app.data.seed.ExerciseCatalog
import com.forgequest.app.domain.Badges
import com.forgequest.app.domain.Dates
import com.forgequest.app.domain.DayState
import com.forgequest.app.domain.ExerciseKind
import com.forgequest.app.domain.Muscle
import com.forgequest.app.domain.Records
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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
) {
    private val chat = db.chat()

    val messages: Flow<List<ChatEntity>> = chat.observeAll()

    /** The reply currently streaming in, or null. The screen shows it as a live bubble. */
    private val _draft = MutableStateFlow<String?>(null)
    val draft: StateFlow<String?> = _draft

    suspend fun clear() = chat.clear()

    /** Opens the connection to Google when the Coach tab opens, so the first message skips the handshake. */
    suspend fun warmUp() = gemini.warmUp(settings.apiKey.value)

    suspend fun send(text: String) {
        val tapped = SystemClock.elapsedRealtime()
        chat.insert(ChatEntity(role = "user", text = text.trim(), createdAt = System.currentTimeMillis()))
        val history = answeredHistory(chat.recent(12).reversed())
        val wantsPlan = wantsPlan(text)
        _draft.value = ""
        try {
            val system = coachSystemPrompt(replyLanguage = languageOf(text), plan = wantsPlan)
            var firstText = -1L
            val reply = gemini.generateStreaming(
                apiKey = requireKey(),
                models = models(),
                system = system,
                turns = history.map { (if (it.role == "user") "user" else "model") to it.text },
                // Plans deserve the model's thinking; everything else should land in a second or two.
                fast = !wantsPlan,
            ) { delta ->
                if (firstText < 0) firstText = SystemClock.elapsedRealtime() - tapped
                _draft.value = (_draft.value ?: "") + delta
            }
            Log.i(TAG, "send→first text ${firstText} ms, send→done ${SystemClock.elapsedRealtime() - tapped} ms (plan=$wantsPlan)")
            saveReply(reply)
        } catch (e: AiError.Partial) {
            if (PlanJson.strip(e.text).length > 40) saveReply(e.text)
            saveError(e)
        } catch (e: AiError) {
            saveError(e)
        } finally {
            _draft.value = null
        }
    }

    /**
     * Drops error bubbles and the questions that got one instead of an answer; otherwise the model answers
     * every failed question again on top of the new one. The newest message always stays.
     */
    internal fun answeredHistory(messages: List<ChatEntity>): List<ChatEntity> = messages.filterIndexed { i, m ->
        when {
            m.kind == "error" -> false
            i == messages.lastIndex -> true
            m.role == "user" -> messages[i + 1].role == "coach" && messages[i + 1].kind != "error"
            else -> true
        }
    }

    private suspend fun saveError(e: AiError) {
        val text = if (e is AiError.Timeout) AppText.get(R.string.ai_err_coach_timeout) else e.userMessage
        val s = settings.current()
        val detail = listOf(e.javaClass.simpleName, e.detail, "models ${s.liteModel} / ${s.fastModel}, app ${com.forgequest.app.BuildConfig.VERSION_NAME}")
            .filter { it.isNotBlank() }.joinToString("\n")
        chat.insert(ChatEntity(role = "coach", text = text, kind = "error", detail = detail, createdAt = System.currentTimeMillis()))
    }

    private suspend fun saveReply(reply: String) {
        val plan = PlanJson.extract(reply)
        val notes = CoachMemory.extract(reply)
        if (notes.isNotEmpty()) settings.update { it.copy(coachNotes = CoachMemory.merge(it.coachNotes, notes)) }
        chat.insert(
            ChatEntity(
                role = "coach",
                text = PlanJson.strip(reply).ifBlank { AppText.get(R.string.coach_plan_fallback) },
                planJson = plan?.let(PlanJson::encode),
                createdAt = System.currentTimeMillis(),
            )
        )
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
        val raw = gemini.generate(requireKey(), models(), system, listOf("user" to text.take(20_000)), jsonOutput = true)
        return PlanJson.extract("```json\n$raw\n```") ?: throw AiError.Unreadable(R.string.ai_err_plan_text)
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
                requireKey(), models(), coachSystemPrompt(replyLanguage = if (java.util.Locale.getDefault().language == "ar") "Arabic" else "English"),
                listOf("user" to "Write my weekly check-in for last week: 3 short parts — what went well, what to push this week, one nutrition tip. Under 120 words. No plan JSON, no MEMORY line."),
                fast = true,
            )
            chat.insert(ChatEntity(role = "coach", text = PlanJson.strip(reply), kind = "checkin", createdAt = System.currentTimeMillis()))
            true
        } catch (e: AiError) {
            settings.update { it.copy(lastCheckinWeek = thisWeek - 7) }
            false
        }
    }

    suspend fun setNotes(notes: String) = settings.update { it.copy(coachNotes = notes.lines().map(String::trim).filter { l -> l.isNotBlank() }.joinToString("\n")) }

    private fun requireKey(): String = settings.apiKey.value.ifBlank { throw AiError.MissingKey }

    /**
     * Measured on the emulator (18 Oct 2026, free key, ~1,800-token prompt): Flash-Lite with thinking off shows
     * its first words in 0.6-1.5 s and writes a whole week's plan, thinking on, in ~4.5 s. Flash with thinking off
     * took 2-65 s to say anything (queued free tier) and gemini-3.8-flash allows only 20 requests a day; with
     * thinking on, a plan took 37-80 s. Pro takes one to two minutes. So Flash-Lite answers everything and
     * Flash only steps in when Lite is rate limited, busy or stalled.
     */
    private suspend fun models(): List<String> {
        val m = models.current()
        return listOf(m.lite, m.fast)
    }

    private fun wantsPlan(text: String) = PlanIntent.matches(text)

    /** Script of the person's latest message, so the reply never drifts into another language. */
    internal fun languageOf(text: String): String = when {
        text.any { it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' } -> "Arabic"
        text.any { it.isLetter() && it.code < 0x250 } -> "English"
        else -> "the language of the user's latest message"
    }

    // ------------------------------------------------------------ Prompting

    /** The exact chat system prompt, for the debug latency bench. */
    internal suspend fun systemPromptForBench(latest: String): String = coachSystemPrompt(languageOf(latest), wantsPlan(latest))

    /**
     * Stable instructions first and the per-message language rule last, so consecutive requests share the
     * longest possible prefix (Gemini's implicit cache) and the rule sits closest to the reply. The plan schema,
     * with every exercise id, is about 600 tokens and only goes in when the person asked for a plan.
     */
    private suspend fun coachSystemPrompt(replyLanguage: String = "the language of the user's latest message", plan: Boolean = false): String = buildString {
        appendLine(CORE_RULES)
        appendLine()
        if (plan) {
            appendLine("When the user asks to create or change their weekly plan, describe it briefly, then add the full plan as a ```json code block at the very end.")
            appendLine(planSchemaInstructions())
        } else {
            appendLine("Never write plan JSON in this reply. If they want a new or changed weekly plan, outline it in a few lines and tell them to say \"make it my plan\" (in Arabic: \"اعمل لي الخطة\") to get one they can apply.")
        }
        appendLine()
        appendLine(MEMORY_RULE)
        appendLine()
        append(progressSummary())
        appendLine()
        append("LANGUAGE: reply in $replyLanguage. Decide from the latest message only, never from earlier messages, the user's name or the data above.")
        if (replyLanguage == "Arabic") append(" Keep exercise names in English in parentheses the first time, and write numbers as Western digits (0-9).")
    }

    private fun planSchemaInstructions(): String {
        val ids = ExerciseCatalog.all.joinToString(", ") { it.id }
        return """
            Plan JSON shape: {"name": string, "description": string, "days": [{"dayOfWeek": 1-7 (1 = Monday), "title": string, "isRest": boolean, "note": string,
            "items": [{"exerciseId": string, "name": string, "kind": "REPS"|"HOLD"|"WEIGHTED", "muscles": [string], "block": string, "circuit": boolean,
            "sets": int, "repsMin": int|null, "repsMax": int|null, "seconds": int|null, "weightKg": number|null, "restSeconds": int, "note": string}]}]}
            Include all 7 days. Prefer these exercise ids: $ids.
            For an exercise not in that list, invent a snake_case id and set name, kind and muscles (from: ${Muscle.entries.joinToString(", ") { it.name }}).
            Keep the JSON short: for ids from the list leave out name, kind and muscles; omit "note" unless needed; one-sentence description.
            Use "seconds" for holds, "repsMin"/"repsMax" for reps. Items with the same block and circuit=true are done round by round.
        """.trimIndent()
    }

    /**
     * What the coach knows about the user: goals, this week, plan, recovery, recent sessions, 4-week trends,
     * records, nutrition, badges and remembered facts. Lists are capped so the whole thing stays around 1,000 tokens.
     */
    private suspend fun progressSummary(): String {
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
        if (s.unit == "lb") sb.append("- Units: the user thinks in pounds. The data below is in kg; convert weights to lb when you mention them.\n")
        val target = if (s.weeklyTargetOverride > 0) s.weeklyTargetOverride else snap.plan?.trainingDays?.size ?: 0
        sb.append("- Goals: ${s.kcalGoal} kcal and ${s.proteinGoal} g protein per day; $target workouts per week\n")

        // This week
        val streak = snap.streak
        val missed = streak.days.withIndex().filter { it.value == DayState.MISSED }.map { Dates.dayName(it.index + 1) }
        val upcoming = streak.days.withIndex().filter { it.value == DayState.UPCOMING }.map { Dates.dayName(it.index + 1) }
        sb.append("- Level ${snap.level.level} (rank ${snap.level.rank.letter}, ${snap.level.rank.title}); weekly streak ${streak.weeks} (best ${streak.best})\n")
        if (streak.targetThisWeek == 0) {
            // The week they joined has no target (planned days had already passed); "1 of 0" confused the model.
            sb.append("- This week: ${streak.doneThisWeek} workouts done; they started mid-week, so the weekly target starts next Monday")
        } else {
            sb.append("- This week: ${streak.doneThisWeek} of ${streak.targetThisWeek} workouts done")
        }
        if (missed.isNotEmpty()) sb.append("; missed planned days: ${missed.joinToString(", ")}")
        if (upcoming.isNotEmpty()) sb.append("; still to come: ${upcoming.joinToString(", ")}")
        sb.append("\n")
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
                    sb.append("  ${Dates.dayName(d.day.dayOfWeek).take(3)}: rest\n")
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
                    sb.append("  ${Dates.dayName(d.day.dayOfWeek).take(3)}: ${d.day.title}: $names\n")
                }
            }
        } ?: sb.append("- No plan set yet\n")

        // Recovery
        val recovering = snap.recovery.entries.filter { it.value.fatigue >= 0.33f }.sortedByDescending { it.value.fatigue }.take(6)
        if (recovering.isNotEmpty()) {
            sb.append("- Still recovering: ${recovering.joinToString(", ") { "${it.key.label} (ready in ${it.value.hoursUntilReady} h)" }}\n")
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
            val bestOld = old.maxBy { Records.score(ex.kind, it.reps, it.seconds, it.weightKg) }
            val bestNew = new.maxBy { Records.score(ex.kind, it.reps, it.seconds, it.weightKg) }
            "${ex.name} ${setLabel(ex.kind, bestOld)} → ${setLabel(ex.kind, bestNew)}"
        }
        if (trends.isNotEmpty()) sb.append("- Best set 3-8 weeks ago → best set in the last 3 weeks: ${trends.joinToString("; ")}\n")

        // Personal bests
        val bests = byExercise.entries.mapNotNull { (id, sets) ->
            val ex = snap.exercises[id] ?: return@mapNotNull null
            val best = sets.maxByOrNull { Records.score(ex.kind, it.reps, it.seconds, it.weightKg) } ?: return@mapNotNull null
            Triple(ex.name, setLabel(ex.kind, best), sets.size)
        }.sortedByDescending { it.third }.take(12)
        if (bests.isNotEmpty()) sb.append("- Personal bests: ${bests.joinToString(", ") { "${it.first} ${it.second}" }}\n")

        // Nutrition
        val week = snap.nutritionByDay.filter { it.epochDay > today - 7 && it.epochDay < today }
        if (week.isNotEmpty()) {
            val hit = week.count { it.protein >= s.proteinGoal }
            sb.append("- Last 7 days nutrition: avg ${week.sumOf { it.kcal } / week.size} kcal, ${(week.sumOf { it.protein.toDouble() } / week.size).toInt()} g protein over ${week.size} logged days; protein goal hit on $hit of them\n")
        } else {
            sb.append("- No meals logged in the last 7 days\n")
        }
        sb.append("- Today so far: ${snap.today.kcal} kcal, ${snap.today.protein.roundToInt()} g protein\n")

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
        ExerciseKind.HOLD -> "${set.seconds}s"
        ExerciseKind.WEIGHTED -> "${set.reps}×${"%.1f".format(Locale.US, set.weightKg ?: 0f).removeSuffix(".0")}kg"
        else -> "${set.reps}"
    }

    private companion object {
        const val TAG = "FQ-AI"

        val CORE_RULES = """
            You are the coach inside ForgeQuest, a workout and nutrition app. The user trains calisthenics and/or gym.
            Be warm, direct and specific. Use their data below: quote their real numbers, name the exercises they actually do, notice trends, and point out what they missed or improved.
            Keep answers short (under 180 words) unless they ask for detail. Use simple Markdown: **bold**, short bullet lists, at most one small heading. No tables.
            Never shame food choices; going over calories is fuel, not failure.
        """.trimIndent()

        val MEMORY_RULE = """
            If the user tells you something lasting about themselves (an injury, their equipment, their schedule, a food preference, a goal), finish your reply with one final line exactly like: MEMORY: <one short sentence>
            Only do this for new facts not already in "Things to remember" below. Never mention this mechanism.
        """.trimIndent()
    }
}

/**
 * Asking to make or change a plan, not just mentioning one. The old check matched "plan" anywhere, so
 * "explain" or "how's my plan going?" switched thinking on and left the person waiting 30+ seconds.
 */
internal object PlanIntent {
    private val planWord = Regex("""\b(plan|program|programme|split|routine|schedule)s?\b""")
    private val action = Regex("""\b(make|build|create|write|design|give|new|change|update|adjust|modify|redo|swap|replace|generate|set up|turn|convert)\b""")
    private val arabicPlan = Regex("جدول|برنامج|خطة|خطه|روتين|سبليت")
    private val arabicAction = Regex("اعمل|سوي|سوّي|ابني|اكتب|صمم|غير|غيّر|عدل|عدّل|بدل|جديد|اعطني|عطني|ابغى|أبغى|بدي|عايز|حضر|جهز")

    fun matches(text: String): Boolean {
        val t = text.lowercase()
        return (planWord.containsMatchIn(t) || arabicPlan.containsMatchIn(t)) && (action.containsMatchIn(t) || arabicAction.containsMatchIn(t))
    }
}
