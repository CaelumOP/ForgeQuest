package com.forgequest.app.data.seed

import com.forgequest.app.data.db.ExerciseEntity
import java.text.Normalizer

/**
 * Cross-language, Arabic-normalised search helper for the exercise picker and library.
 * Matches EN name, AR name, aliases (stored + built-in), and id regardless of UI language.
 */
object ExerciseSearch {
    private val MARKS = Regex("\\p{Mn}+")
    private val NON_ALNUM = Regex("[^\\p{L}\\p{N}]+")

    private val enNameById: Map<String, String> by lazy {
        ExerciseCatalog.all.associate { it.id to it.name }
    }

    /** Lowercase, strip tashkeel/accents, unify أ/إ/آ->ا, ة->ه, ى->ي, and normalize separators to spaces. */
    fun normalize(s: String): String {
        val decomposed = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(MARKS, "")
        val sb = StringBuilder(decomposed.length)
        for (ch in decomposed) {
            when (ch) {
                'ٱ' -> sb.append('ا')
                'ة' -> sb.append('ه')
                'ى', 'ی', 'ې' -> sb.append('ي')
                'ک' -> sb.append('ك')
                'ء', 'ـ', '\'', '’', '`' -> Unit
                in '٠'..'٩' -> sb.append('0' + (ch - '٠'))
                in '۰'..'۹' -> sb.append('0' + (ch - '۰'))
                else -> sb.append(ch)
            }
        }
        return sb.toString().replace(NON_ALNUM, " ").trim()
    }

    fun matches(exercise: ExerciseEntity, query: String): Boolean = score(exercise, query) > 0

    fun filter(exercises: List<ExerciseEntity>, query: String): List<ExerciseEntity> {
        val normQuery = normalize(query)
        if (normQuery.isEmpty()) return exercises
        return exercises
            .mapNotNull { ex -> val s = scoreNormalized(ex, normQuery); if (s > 0) ex to s else null }
            .sortedWith(
                compareByDescending<Pair<ExerciseEntity, Int>> { it.second }
                    .thenBy { it.first.name.length }
                    .thenBy { it.first.name.lowercase() }
            )
            .map { it.first }
    }

    fun score(exercise: ExerciseEntity, query: String): Int {
        val normQuery = normalize(query)
        if (normQuery.isEmpty()) return 1
        return scoreNormalized(exercise, normQuery)
    }

    private fun scoreNormalized(exercise: ExerciseEntity, normQuery: String): Int {
        val tokens = normQuery.split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return 1

        val currentName = normalize(exercise.name)
        val enName = enNameById[exercise.id]?.let(::normalize).orEmpty()
        val arName = ExerciseNamesAr.byId[exercise.id]?.let(::normalize).orEmpty()
        val idNorm = normalize(exercise.id)
        val aliasesNorm = buildString {
            if (exercise.aliases.isNotBlank()) append(normalize(exercise.aliases)).append(' ')
            ExerciseAliases.byId[exercise.id]?.let { append(normalize(it)) }
        }.trim()

        val combined = listOf(currentName, enName, arName, aliasesNorm, idNorm)
            .filter { it.isNotEmpty() }
            .joinToString(" ")

        if (!tokens.all { combined.contains(it) }) return 0

        val hasWordStart = listOf(currentName, enName, arName).any { name ->
            name.contains(" $normQuery")
        }
        val exactAlias = aliasesNorm.split(' ').any { it == normQuery }

        return when {
            currentName == normQuery || enName == normQuery || arName == normQuery || idNorm == normQuery -> 100
            currentName.startsWith(normQuery) || enName.startsWith(normQuery) || arName.startsWith(normQuery) -> if (exactAlias) 85 else 80
            hasWordStart -> 70
            currentName.contains(normQuery) || enName.contains(normQuery) || arName.contains(normQuery) -> 60
            exactAlias -> 50
            else -> 30
        }
    }
}
