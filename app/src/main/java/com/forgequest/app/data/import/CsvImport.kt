package com.forgequest.app.data.csvimport

import androidx.room.withTransaction
import com.forgequest.app.data.db.AppDatabase
import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.SessionEntity
import com.forgequest.app.data.db.SetEntity
import com.forgequest.app.data.seed.ExerciseAliases
import com.forgequest.app.data.seed.ExerciseNamesAr
import com.forgequest.app.domain.ExerciseKind
import com.forgequest.app.domain.Muscle
import com.forgequest.app.domain.SetType
import com.forgequest.app.domain.WeightUnit
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

enum class CsvSource(val displayName: String) {
    STRONG("Strong"),
    HEVY("Hevy"),
    FITNOTES("FitNotes"),
}

enum class MatchStatus { MATCHED, AMBIGUOUS, UNMATCHED }

data class ExerciseMatch(
    val rawName: String,
    val status: MatchStatus,
    val matchedId: String?,
    val candidates: List<String> = emptyList(),
)

sealed interface ExerciseMapping {
    data class UseExisting(val exerciseId: String) : ExerciseMapping
    data class CreateCustom(
        val name: String,
        val kind: ExerciseKind = ExerciseKind.WEIGHTED,
        val primary: Muscle = Muscle.CHEST,
    ) : ExerciseMapping
    data object Skip : ExerciseMapping
}

data class ParsedSetRow(
    val epochDay: Long,
    val workoutName: String,
    val rawExerciseName: String,
    val setIndex: Int,
    val setType: String,
    val weightKg: Float?,
    val reps: Int?,
    val seconds: Int?,
    val note: String,
)

data class CsvPreview(
    val source: CsvSource,
    val rows: List<ParsedSetRow>,
    val exerciseMatches: Map<String, ExerciseMatch>,
    val totalWorkouts: Int,
    val newSetsCount: Int,
    val duplicateSetsCount: Int,
    val existingDedupeKeys: Set<String>,
) {
    val unmatchedNames: List<String>
        get() = exerciseMatches.values
            .filter { it.status != MatchStatus.MATCHED }
            .map { it.rawName }
            .sorted()
}

data class CsvImportResult(
    val importedWorkouts: Int,
    val importedSets: Int,
    val skippedDuplicates: Int,
)

class CsvParseException(message: String) : IllegalArgumentException(message)

object CsvParser {

    fun dedupeKey(epochDay: Long, exerciseId: String, setIndex: Int): String =
        "$epochDay|$exerciseId|$setIndex"

    /**
     * Parses RFC-4180 CSV text (supporting quoted newlines, escaped quotes `""`, UTF-8 BOM, and `,` or `;`).
     */
    fun parseTable(rawText: String): List<List<String>> {
        val text = rawText.removePrefix("\uFEFF")
        if (text.isBlank()) return emptyList()
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        val delimiter = detectDelimiter(firstLine)
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        cell.append('"')
                        i += 2
                        continue
                    } else {
                        inQuotes = false
                    }
                } else {
                    cell.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> inQuotes = true
                    delimiter -> {
                        row += cell.toString()
                        cell.setLength(0)
                    }
                    '\r' -> {
                        if (i + 1 < text.length && text[i + 1] == '\n') i++
                        row += cell.toString()
                        cell.setLength(0)
                        if (row.any { it.isNotBlank() }) rows += row.toList()
                        row.clear()
                    }
                    '\n' -> {
                        row += cell.toString()
                        cell.setLength(0)
                        if (row.any { it.isNotBlank() }) rows += row.toList()
                        row.clear()
                    }
                    else -> cell.append(ch)
                }
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row += cell.toString()
            if (row.any { it.isNotBlank() }) rows += row.toList()
        }
        return rows
    }

    private fun detectDelimiter(headerLine: String): Char {
        var commas = 0
        var semis = 0
        var quoted = false
        for (ch in headerLine) {
            if (ch == '"') quoted = !quoted
            else if (!quoted) {
                if (ch == ',') commas++
                else if (ch == ';') semis++
            }
        }
        return if (semis > commas) ';' else ','
    }

    fun detectSource(headers: List<String>): CsvSource {
        val norm = headers.map { it.trim().lowercase(Locale.ROOT) }.toSet()
        return when {
            "exercise_title" in norm && ("start_time" in norm || "set_index" in norm) -> CsvSource.HEVY
            "exercise name" in norm && ("set order" in norm || "workout name" in norm) -> CsvSource.STRONG
            "exercise" in norm && "date" in norm && ("weight unit" in norm || "weight (kgs)" in norm || "weight (lbs)" in norm || "category" in norm) -> CsvSource.FITNOTES
            else -> throw CsvParseException("Unrecognized CSV header format")
        }
    }

    fun parseRows(csvText: String): Pair<CsvSource, List<ParsedSetRow>> {
        val table = parseTable(csvText)
        if (table.size < 2) throw CsvParseException("CSV has no data rows")
        val headers = table.first().map { it.trim() }
        val source = detectSource(headers)
        val col = headers.withIndex().associate { (idx, h) -> h.lowercase(Locale.ROOT) to idx }
        fun cell(row: List<String>, vararg keys: String): String {
            for (k in keys) {
                val idx = col[k]
                if (idx != null && idx < row.size) return row[idx].trim()
            }
            return ""
        }

        val out = mutableListOf<ParsedSetRow>()
        val groupCounters = mutableMapOf<String, Int>()
        val dataRows = table.drop(1)

        when (source) {
            CsvSource.STRONG -> {
                val usesLbsHeader = headers.any { it.contains("(lbs", ignoreCase = true) || it.contains("(lb)", ignoreCase = true) }
                for (r in dataRows) {
                    val setOrderRaw = cell(r, "set order")
                    if (setOrderRaw.equals("Rest Timer", ignoreCase = true) || setOrderRaw.equals("Note", ignoreCase = true)) continue
                    val exName = cell(r, "exercise name")
                    if (exName.isBlank()) continue
                    val epochDay = parseEpochDay(cell(r, "date")) ?: continue
                    val workoutName = cell(r, "workout name").ifBlank { "Strong" }
                    val groupKey = "$epochDay|$workoutName|$exName"
                    val seqIdx = groupCounters[groupKey] ?: 0
                    groupCounters[groupKey] = seqIdx + 1
                    val rawWeight = cell(r, "weight", "weight (kg)", "weight (lbs)", "weight (lb)").toFloatOrNull()
                    val weightKg = rawWeight?.takeIf { it > 0f }?.let { if (usesLbsHeader) WeightUnit.LB.toKg(it) else it }
                    val reps = cell(r, "reps").toFloatOrNull()?.roundToInt()?.takeIf { it > 0 }
                    val seconds = parseDurationSeconds(cell(r, "seconds"))
                    val setType = mapStrongSetType(setOrderRaw)
                    out += ParsedSetRow(
                        epochDay = epochDay,
                        workoutName = workoutName,
                        rawExerciseName = exName,
                        setIndex = seqIdx,
                        setType = setType,
                        weightKg = weightKg,
                        reps = reps,
                        seconds = seconds,
                        note = cell(r, "notes"),
                    )
                }
            }
            CsvSource.HEVY -> {
                for (r in dataRows) {
                    val exName = cell(r, "exercise_title")
                    if (exName.isBlank()) continue
                    val epochDay = parseEpochDay(cell(r, "start_time")) ?: continue
                    val workoutName = cell(r, "title").ifBlank { "Hevy" }
                    val groupKey = "$epochDay|$workoutName|$exName"
                    val seqIdx = groupCounters[groupKey] ?: 0
                    groupCounters[groupKey] = seqIdx + 1
                    val rawKg = cell(r, "weight_kg").toFloatOrNull()?.takeIf { it > 0f }
                    val rawLbs = cell(r, "weight_lbs").toFloatOrNull()?.takeIf { it > 0f }
                    val weightKg = rawKg ?: rawLbs?.let { WeightUnit.LB.toKg(it) }
                    val reps = cell(r, "reps").toFloatOrNull()?.roundToInt()?.takeIf { it > 0 }
                    val seconds = parseDurationSeconds(cell(r, "duration_seconds"))
                    val setType = mapHevySetType(cell(r, "set_type"))
                    out += ParsedSetRow(
                        epochDay = epochDay,
                        workoutName = workoutName,
                        rawExerciseName = exName,
                        setIndex = seqIdx,
                        setType = setType,
                        weightKg = weightKg,
                        reps = reps,
                        seconds = seconds,
                        note = cell(r, "exercise_notes"),
                    )
                }
            }
            CsvSource.FITNOTES -> {
                val hasLbsHeader = headers.any { it.contains("(lbs", ignoreCase = true) || it.contains("(lb)", ignoreCase = true) }
                for (r in dataRows) {
                    val exName = cell(r, "exercise")
                    if (exName.isBlank()) continue
                    val epochDay = parseEpochDay(cell(r, "date")) ?: continue
                    val category = cell(r, "category")
                    val workoutName = if (category.isNotBlank()) "FitNotes · $category" else "FitNotes"
                    val groupKey = "$epochDay|$exName"
                    val seqIdx = groupCounters[groupKey] ?: 0
                    groupCounters[groupKey] = seqIdx + 1
                    val rawWeight = cell(r, "weight", "weight (kgs)", "weight (kg)", "weight (lbs)", "weight (lb)").toFloatOrNull()
                    val unitRaw = cell(r, "weight unit").lowercase(Locale.ROOT)
                    val isLb = hasLbsHeader || unitRaw == "lb" || unitRaw == "lbs"
                    val weightKg = rawWeight?.takeIf { it > 0f }?.let { if (isLb) WeightUnit.LB.toKg(it) else it }
                    val reps = cell(r, "reps").toFloatOrNull()?.roundToInt()?.takeIf { it > 0 }
                    val seconds = parseDurationSeconds(cell(r, "time"))
                    out += ParsedSetRow(
                        epochDay = epochDay,
                        workoutName = workoutName,
                        rawExerciseName = exName,
                        setIndex = seqIdx,
                        setType = SetType.WORKING.name,
                        weightKg = weightKg,
                        reps = reps,
                        seconds = seconds,
                        note = "",
                    )
                }
            }
        }
        if (out.isEmpty()) throw CsvParseException("No importable set rows found")
        return source to out
    }

    /**
     * Timezone-safe calendar date parser:
     * - `YYYY-MM-DD...` without explicit UTC offset extracts the calendar date directly (no midnight timezone shift).
     * - `DD MMM YYYY...` (Hevy) extracts the calendar date directly.
     * - Explicit ISO offset (`...Z` or `+HH:MM`) converts via [ZoneId.systemDefault].
     */
    fun parseEpochDay(raw: String, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        val explicitOffset = Regex(""".*(?:Z|[+-]\d{2}:?\d{2})$""").matches(s)
        if (explicitOffset) {
            runCatching { return Instant.parse(s).atZone(zone).toLocalDate().toEpochDay() }
        }
        Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})""").find(s)?.let { m ->
            val y = m.groupValues[1].toInt()
            val mo = m.groupValues[2].toInt()
            val d = m.groupValues[3].toInt()
            return runCatching { LocalDate.of(y, mo, d).toEpochDay() }.getOrNull()
        }
        Regex("""^(\d{1,2})\s+([A-Za-z]{3,9})\s+(\d{4})""").find(s)?.let { m ->
            val d = m.groupValues[1].toInt()
            val mo = parseMonth(m.groupValues[2]) ?: return null
            val y = m.groupValues[3].toInt()
            return runCatching { LocalDate.of(y, mo, d).toEpochDay() }.getOrNull()
        }
        return null
    }

    private fun parseMonth(name: String): Month? = when (name.take(3).lowercase(Locale.ROOT)) {
        "jan" -> Month.JANUARY
        "feb" -> Month.FEBRUARY
        "mar" -> Month.MARCH
        "apr" -> Month.APRIL
        "may" -> Month.MAY
        "jun" -> Month.JUNE
        "jul" -> Month.JULY
        "aug" -> Month.AUGUST
        "sep" -> Month.SEPTEMBER
        "oct" -> Month.OCTOBER
        "nov" -> Month.NOVEMBER
        "dec" -> Month.DECEMBER
        else -> null
    }

    private fun parseDurationSeconds(raw: String): Int? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        s.toFloatOrNull()?.roundToInt()?.let { return it.takeIf { v -> v > 0 } }
        val parts = s.split(':').mapNotNull { it.trim().toIntOrNull() }
        return when (parts.size) {
            2 -> (parts[0] * 60 + parts[1]).takeIf { it > 0 }
            3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]).takeIf { it > 0 }
            else -> null
        }
    }

    private fun mapStrongSetType(order: String): String = when (order.trim().uppercase(Locale.ROOT)) {
        "W" -> SetType.WARMUP.name
        "D" -> SetType.DROPSET.name
        "F" -> SetType.FAILURE.name
        else -> SetType.WORKING.name
    }

    private fun mapHevySetType(raw: String): String = when (raw.trim().lowercase(Locale.ROOT)) {
        "warmup", "warm_up", "warm-up" -> SetType.WARMUP.name
        "dropset", "drop_set", "drop" -> SetType.DROPSET.name
        "failure", "to_failure" -> SetType.FAILURE.name
        "rest_pause", "restpause" -> SetType.RESTPAUSE.name
        else -> SetType.WORKING.name
    }
}

object CsvExerciseMatcher {

    private val COMMON_ALIASES: Map<String, String> = mapOf(
        "bench press" to "bench_press",
        "barbell bench press" to "bench_press",
        "flat barbell bench press" to "bench_press",
        "incline bench press" to "incline_bench_press",
        "incline barbell bench press" to "incline_bench_press",
        "incline dumbbell press" to "incline_db_press",
        "dumbbell bench press" to "db_bench_press",
        "squat" to "back_squat",
        "squat bodyweight" to "squat",
        "barbell squat" to "back_squat",
        "back squat" to "back_squat",
        "front squat" to "front_squat",
        "deadlift" to "deadlift",
        "conventional deadlift" to "deadlift",
        "romanian deadlift" to "romanian_deadlift",
        "rdl" to "romanian_deadlift",
        "overhead press" to "overhead_press",
        "ohp" to "overhead_press",
        "military press" to "overhead_press",
        "bent over row" to "barbell_row",
        "barbell row" to "barbell_row",
        "lat pulldown" to "lat_pulldown",
        "seated row" to "seated_cable_row",
        "seated cable row" to "seated_cable_row",
        "pull up" to "pull_up",
        "pullup" to "pull_up",
        "chin up" to "chin_up",
        "chinup" to "chin_up",
        "dip" to "dip",
        "dips" to "dip",
        "push up" to "push_up",
        "pushup" to "push_up",
        "bicep curl" to "bicep_curl",
        "dumbbell bicep curl" to "bicep_curl",
        "hammer curl" to "hammer_curl",
        "triceps pushdown" to "tricep_pushdown",
        "tricep pushdown" to "tricep_pushdown",
        "skullcrusher" to "skull_crusher",
        "skull crusher" to "skull_crusher",
        "lateral raise" to "lateral_raise",
        "face pull" to "face_pull",
        "leg press" to "leg_press",
        "leg extension" to "leg_extension",
        "leg curl" to "leg_curl",
        "seated leg curl" to "seated_leg_curl",
        "hip thrust" to "hip_thrust",
        "calf raise" to "calf_raise",
        "standing calf raise" to "standing_calf_raise",
        "plank" to "plank",
        "crunch" to "crunch",
    )

    fun normalize(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw.lowercase(Locale.ROOT)) {
            when {
                ch in 'a'..'z' || ch in '0'..'9' -> sb.append(ch)
                ch in '\u0621'..'\u064A' -> {
                    val mapped = when (ch) {
                        'أ', 'إ', 'آ' -> 'ا'
                        'ى' -> 'ي'
                        'ة' -> 'ه'
                        else -> ch
                    }
                    sb.append(mapped)
                }
                ch == '\u064B' || ch == '\u064C' || ch == '\u064D' || ch == '\u064E' ||
                    ch == '\u064F' || ch == '\u0650' || ch == '\u0651' || ch == '\u0652' -> Unit
                else -> sb.append(' ')
            }
        }
        return sb.toString().trim().replace(Regex("\\s+"), " ")
    }

    private fun stripEquipmentTag(raw: String): String =
        raw.replace(Regex("""\s*\([^)]*\)\s*$"""), "").trim()

    fun matchAll(rawNames: Collection<String>, library: List<ExerciseEntity>): Map<String, ExerciseMatch> {
        val exactIndex = mutableMapOf<String, MutableSet<String>>()
        val idIndex = mutableMapOf<String, MutableSet<String>>()
        fun addKey(index: MutableMap<String, MutableSet<String>>, key: String, id: String) {
            val n = normalize(key)
            if (n.isNotEmpty()) index.getOrPut(n) { linkedSetOf() }.add(id)
        }
        val byId = library.associateBy { it.id }
        for (ex in library) {
            addKey(idIndex, ex.id.replace('_', ' '), ex.id)
            addKey(exactIndex, ex.name, ex.id)
            ExerciseNamesAr.byId[ex.id]?.let { addKey(exactIndex, it, ex.id) }
            val aliasesRaw = ex.aliases.ifBlank { ExerciseAliases.byId[ex.id].orEmpty() }
            if (aliasesRaw.isNotBlank()) {
                aliasesRaw.split(',', '|', ';').forEach { addKey(exactIndex, it, ex.id) }
            }
        }

        return rawNames.distinct().associateWith { raw ->
            val normFull = normalize(raw)
            val normStripped = normalize(stripEquipmentTag(raw))
            val direct = exactIndex[normFull]?.toList().orEmpty()
            val aliasHit = (COMMON_ALIASES[normFull] ?: COMMON_ALIASES[normStripped])?.takeIf { it in byId }
            val idHits = idIndex[normFull]?.toList().orEmpty()
            val strippedHits = exactIndex[normStripped]?.toList()?.takeIf { it.isNotEmpty() }
                ?: idIndex[normStripped]?.toList().orEmpty()
            when {
                direct.size == 1 -> ExerciseMatch(raw, MatchStatus.MATCHED, direct.first())
                direct.size > 1 -> ExerciseMatch(raw, MatchStatus.AMBIGUOUS, null, direct)
                aliasHit != null -> ExerciseMatch(raw, MatchStatus.MATCHED, aliasHit)
                idHits.size == 1 -> ExerciseMatch(raw, MatchStatus.MATCHED, idHits.first())
                idHits.size > 1 -> ExerciseMatch(raw, MatchStatus.AMBIGUOUS, null, idHits)
                strippedHits.size == 1 -> ExerciseMatch(raw, MatchStatus.MATCHED, strippedHits.first())
                strippedHits.size > 1 -> ExerciseMatch(raw, MatchStatus.AMBIGUOUS, null, strippedHits)
                else -> ExerciseMatch(raw, MatchStatus.UNMATCHED, null)
            }
        }
    }
}

class CsvImporter(private val db: AppDatabase) {

    suspend fun preview(csvText: String): CsvPreview {
        val (source, rows) = CsvParser.parseRows(csvText)
        val library = db.exercises().all()
        val matches = CsvExerciseMatcher.matchAll(rows.map { it.rawExerciseName }, library)
        val existingKeys = loadExistingDedupeKeys()

        val seenInBatch = HashSet<String>()
        var newSets = 0
        var dupes = 0
        val newWorkoutKeys = HashSet<String>()

        for (r in rows) {
            val matchedId = matches[r.rawExerciseName]?.matchedId
            if (matchedId != null) {
                val key = CsvParser.dedupeKey(r.epochDay, matchedId, r.setIndex)
                if (key in existingKeys || !seenInBatch.add(key)) {
                    dupes++
                    continue
                }
            }
            newSets++
            newWorkoutKeys += "${r.epochDay}|${r.workoutName}"
        }

        return CsvPreview(
            source = source,
            rows = rows,
            exerciseMatches = matches,
            totalWorkouts = newWorkoutKeys.size,
            newSetsCount = newSets,
            duplicateSetsCount = dupes,
            existingDedupeKeys = existingKeys,
        )
    }

    suspend fun importTransactional(
        preview: CsvPreview,
        mappings: Map<String, ExerciseMapping>,
    ): CsvImportResult = db.withTransaction {
        val existingKeys = loadExistingDedupeKeys().toMutableSet()
        val customIds = mutableMapOf<String, String>()

        for ((rawName, mapping) in mappings) {
            if (mapping is ExerciseMapping.CreateCustom) {
                val cleanName = mapping.name.ifBlank { rawName }
                val id = uniqueCustomId(cleanName, db.exercises().all().map { it.id }.toSet())
                db.exercises().upsert(
                    ExerciseEntity(
                        id = id,
                        name = cleanName,
                        kind = mapping.kind,
                        primary = listOf(mapping.primary),
                        secondary = emptyList(),
                        custom = true,
                    ),
                )
                customIds[rawName] = id
            }
        }

        fun resolveExerciseId(rawName: String): String? {
            return when (val m = mappings[rawName]) {
                is ExerciseMapping.UseExisting -> m.exerciseId
                is ExerciseMapping.CreateCustom -> customIds[rawName]
                is ExerciseMapping.Skip -> null
                null -> preview.exerciseMatches[rawName]?.matchedId
            }
        }

        var skippedDupes = 0
        val acceptedByWorkout = linkedMapOf<Pair<Long, String>, MutableList<Pair<String, ParsedSetRow>>>()
        for (r in preview.rows) {
            val exId = resolveExerciseId(r.rawExerciseName) ?: continue
            val key = CsvParser.dedupeKey(r.epochDay, exId, r.setIndex)
            if (!existingKeys.add(key)) {
                skippedDupes++
                continue
            }
            acceptedByWorkout.getOrPut(r.epochDay to r.workoutName) { mutableListOf() }.add(exId to r)
        }

        var importedWorkouts = 0
        var importedSets = 0
        val zone = ZoneId.systemDefault()

        for ((workoutKey, items) in acceptedByWorkout) {
            if (items.isEmpty()) continue
            val (epochDay, title) = workoutKey
            val noonMs = LocalDate.ofEpochDay(epochDay).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
            val sessionId = db.sessions().insert(
                SessionEntity(
                    planDayId = null,
                    title = title,
                    epochDay = epochDay,
                    startedAt = noonMs,
                    finishedAt = noonMs + items.size * 120_000L,
                ),
            )
            var itemPos = 0
            val posByExercise = mutableMapOf<String, Int>()
            val entities = items.map { (exId, r) ->
                val pos = posByExercise.getOrPut(exId) { itemPos++ }
                val target = when {
                    r.seconds != null -> "${r.seconds} s"
                    r.reps != null -> "${r.reps}"
                    else -> ""
                }
                SetEntity(
                    sessionId = sessionId,
                    exerciseId = exId,
                    itemPosition = pos,
                    block = if (r.setType == SetType.WARMUP.name) "Warm-up" else "Main",
                    circuit = false,
                    setIndex = r.setIndex,
                    targetLabel = target,
                    reps = r.reps,
                    seconds = r.seconds,
                    weightKg = r.weightKg?.let { (it * 100f).roundToInt() / 100f },
                    done = true,
                    isPr = false,
                    restSeconds = 60,
                    note = r.note,
                    doneAt = noonMs,
                    setType = r.setType,
                )
            }
            db.sessions().insertSets(entities)
            importedWorkouts++
            importedSets += entities.size
        }

        CsvImportResult(importedWorkouts, importedSets, skippedDupes)
    }

    private suspend fun loadExistingDedupeKeys(): Set<String> {
        val sessions = db.backup().sessions().filter { it.finishedAt != null }.associateBy { it.id }
        if (sessions.isEmpty()) return emptySet()
        val keys = HashSet<String>()
        for (set in db.backup().sets()) {
            val session = sessions[set.sessionId] ?: continue
            keys += CsvParser.dedupeKey(session.epochDay, set.exerciseId, set.setIndex)
        }
        return keys
    }

    private fun uniqueCustomId(name: String, existingIds: Set<String>): String {
        val slug = name.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "custom_import" }
        var candidate = "custom_$slug"
        var n = 2
        while (candidate in existingIds) {
            candidate = "custom_${slug}_$n"
            n++
        }
        return candidate
    }
}
