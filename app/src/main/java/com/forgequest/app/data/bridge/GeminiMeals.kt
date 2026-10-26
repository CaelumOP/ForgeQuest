package com.forgequest.app.data.bridge

import android.content.Context
import com.forgequest.app.R
import com.forgequest.app.data.FoodScan
import com.forgequest.app.data.ScanItem
import com.forgequest.app.data.db.AppDatabase
import com.forgequest.app.data.db.FoodCategory
import com.forgequest.app.data.drive.DriveSync
import com.forgequest.app.domain.Dates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

/** One meal estimated by the owner's "ForgeQuest Food" Gem, from the meals Doc or shared/pasted text. */
@Serializable
data class GeminiMeal(
    val mealId: String = "",
    val date: String = "",
    val time: String = "",
    val dish: String = "Meal",
    /** The restaurant or shop it came from, when the person told the Gem or it was visible; empty otherwise. */
    val place: String = "",
    val items: List<ScanItem> = emptyList(),
    val handVisible: Boolean = false,
    val note: String = "",
) {
    fun toScan() = FoodScan(dish = dish, items = items, handVisible = handVisible, note = note)
    val kcal: Int get() = items.sumOf { it.kcal }

    /** The day it was eaten; today when missing, never in the future. */
    fun epochDay(): Long = (runCatching { LocalDate.parse(date.trim()).toEpochDay() }.getOrNull() ?: Dates.today()).coerceAtMost(Dates.today())

    fun localTime(): LocalTime? = runCatching { LocalTime.parse(time.trim().padStart(5, '0')) }.getOrNull()

    /** Breakfast/lunch/dinner/snacks from the time it was eaten, with the same hours as the scan screen. */
    fun category(): FoodCategory? = localTime()?.hour?.let { h ->
        when (h) {
            in 4..10 -> FoodCategory.BREAKFAST
            in 11..15 -> FoodCategory.LUNCH
            in 18..22 -> FoodCategory.DINNER
            else -> FoodCategory.SNACKS
        }
    }
}

object MealFormat {
    const val DOC = "ForgeQuest meals from Gemini"
    const val HEADER = "FORGEQUEST-MEAL v1"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    /**
     * Every meal in [text]: the blocks after each [HEADER] line in the Doc, or a single bare JSON answer that was
     * shared or copied from the Gem (or from any AI app using the scan prompt). Meals without food are dropped.
     */
    fun parseAll(raw: String): List<GeminiMeal> {
        // Docs can turn straight quotes into curly ones.
        val text = raw.replace('\u201C', '"').replace('\u201D', '"')
        val chunks = if (text.contains(HEADER)) text.split(HEADER).drop(1) else listOf(text)
        return chunks.mapNotNull { chunk ->
            val obj = firstJsonObject(chunk) ?: return@mapNotNull null
            runCatching { json.decodeFromString<GeminiMeal>(obj) }.getOrNull()?.takeIf { it.items.isNotEmpty() }
        }
    }

    fun parseOne(text: String): GeminiMeal? = parseAll(text).lastOrNull()

    fun encode(meals: List<GeminiMeal>): String = json.encodeToString(meals)
    fun decode(text: String): List<GeminiMeal> = runCatching { json.decodeFromString<List<GeminiMeal>>(text) }.getOrDefault(emptyList())

    /** Rebuilds one Doc block, for tidying the Doc. */
    fun block(meal: GeminiMeal): String = HEADER + "\n" + json.encodeToString(meal)

    /** The first balanced {...} in [text], skipping braces inside strings. Docs can wrap it in code fences or prose. */
    private fun firstJsonObject(text: String): String? {
        val start = text.indexOf('{').takeIf { it >= 0 } ?: return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val ch = text[i]
            when {
                escaped -> escaped = false
                ch == '\\' && inString -> escaped = true
                ch == '"' -> inString = !inString
                inString -> Unit
                ch == '{' -> depth++
                ch == '}' -> if (--depth == 0) return text.substring(start, i + 1)
            }
        }
        return null
    }
}

/**
 * Meals from the Gem that wait for the person to confirm them on the Food tab. Remembers every meal id it has seen
 * logged or dismissed, so the same meal arriving twice (shared, then synced from the Doc) is never offered again.
 */
class MealInbox(private val context: Context, private val db: AppDatabase, private val drive: DriveSync) {
    enum class Offer { ADDED, UPDATED, UNCHANGED, ALREADY_LOGGED }

    private val prefs = context.getSharedPreferences("gemini_meals", Context.MODE_PRIVATE)
    private val lock = Mutex()
    private val _pending = MutableStateFlow(MealFormat.decode(prefs.getString(KEY_PENDING, null).orEmpty()))
    val pending: StateFlow<List<GeminiMeal>> = _pending.asStateFlow()

    /** A meal to open on the scan screen's review sheet (from the inbox, a share or a paste). */
    val review = MutableStateFlow<GeminiMeal?>(null)

    private fun handled(): Set<String> = prefs.getStringSet(KEY_HANDLED, emptySet()).orEmpty()

    /** Puts [meal] in the inbox unless it was already logged. A meal with a known id replaces the waiting copy (a correction). */
    suspend fun offer(meal: GeminiMeal): Offer = lock.withLock {
        // Without an id, one is made from the content so re-reading the same block finds the same meal.
        val withId = if (meal.mealId.isBlank()) meal.copy(mealId = "h" + "${meal.date}|${meal.time}|${meal.dish}|${meal.kcal}".hashCode().toUInt().toString(36)) else meal
        if (withId.mealId in handled() || alreadyLogged(withId)) return@withLock Offer.ALREADY_LOGGED
        val list = _pending.value
        if (withId in list) return@withLock Offer.UNCHANGED
        val updated = list.any { it.mealId == withId.mealId }
        save(if (updated) list.map { if (it.mealId == withId.mealId) withId else it } else list + withId)
        if (updated) Offer.UPDATED else Offer.ADDED
    }

    /** The inbox copy of [meal] (which may have been given an id), to open for review. */
    fun stored(meal: GeminiMeal): GeminiMeal =
        _pending.value.lastOrNull { it.mealId == meal.mealId || (it.dish == meal.dish && it.items == meal.items) } ?: meal

    /** The meal was logged or thrown away: drop it from the inbox and never offer it again. */
    suspend fun markHandled(mealId: String) = lock.withLock {
        if (mealId.isBlank()) return@withLock
        prefs.edit().putStringSet(KEY_HANDLED, (handled() + mealId).toList().takeLast(MAX_HANDLED).toSet()).apply()
        save(_pending.value.filterNot { it.mealId == mealId })
    }

    /**
     * Fallback for a meal also logged by hand or by another route: same day, calories within 5 %, and eaten within
     * 2 hours of it (or, without a time, the same name).
     */
    private suspend fun alreadyLogged(meal: GeminiMeal): Boolean {
        val kcal = meal.kcal
        if (kcal <= 0) return false
        val day = meal.epochDay()
        val eatenAt = meal.localTime()?.let { LocalDate.ofEpochDay(day).atTime(it).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
        return db.food().observeMeals(day).first().any { logged ->
            val closeKcal = abs(logged.kcal - kcal) <= kcal * 0.05
            val sameName = logged.name.trim().equals(meal.dish.trim(), ignoreCase = true)
            val closeTime = eatenAt != null && abs(logged.loggedAt - eatenAt) <= 2 * 60 * 60 * 1000L
            closeKcal && (closeTime || (eatenAt == null && sameName))
        }
    }

    private fun save(list: List<GeminiMeal>) {
        _pending.value = list
        prefs.edit().putString(KEY_PENDING, MealFormat.encode(list)).apply()
    }

    /**
     * Reads the meals Doc and puts new meals in the inbox. Creates the Doc on first use, since the app can only read
     * Docs it created. Returns how many meals were added or updated.
     */
    suspend fun sync(token: String): Int {
        val since = prefs.getLong(KEY_DOC_MODIFIED, 0)
        val doc = drive.readDoc(token, MealFormat.DOC, since, context.getString(R.string.meals_doc_placeholder)) ?: return 0
        val meals = MealFormat.parseAll(doc.text)
        var count = 0
        meals.forEach { val r = offer(it); if (r == Offer.ADDED || r == Offer.UPDATED) count++ }
        prefs.edit().putLong(KEY_DOC_MODIFIED, doc.modifiedAt).apply()
        tidy(token, doc, meals)
        return count
    }

    /** Keeps the Doc small: once it holds more than [TIDY_AFTER] meals, rewrite it with only the last 14 days. */
    private suspend fun tidy(token: String, doc: DriveSync.DocText, meals: List<GeminiMeal>) {
        if (meals.size <= TIDY_AFTER) return
        val keepFrom = Dates.today() - 14
        val kept = meals.filter { it.epochDay() >= keepFrom }
        val text = context.getString(R.string.meals_doc_placeholder) + "\n\n" + kept.joinToString("\n\n") { MealFormat.block(it) }
        com.forgequest.app.data.suspendRunCatching { drive.upsertTextDoc(token, MealFormat.DOC, text, doc.id) }
    }

    private companion object {
        const val KEY_PENDING = "pending"
        const val KEY_HANDLED = "handled"
        const val KEY_DOC_MODIFIED = "doc_modified"
        const val MAX_HANDLED = 400
        const val TIDY_AFTER = 40
    }
}
