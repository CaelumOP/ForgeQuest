package com.forgequest.app.data

import com.forgequest.app.R
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.forgequest.app.data.ai.AiError
import com.forgequest.app.data.ai.GeminiClient
import com.forgequest.app.data.ai.ModelStore
import com.forgequest.app.data.db.AppDatabase
import com.forgequest.app.data.db.FoodCategory
import com.forgequest.app.data.db.FoodEntity
import com.forgequest.app.data.db.FoodUsage
import com.forgequest.app.data.db.MealEntity
import com.forgequest.app.data.prefs.SettingsRepository
import com.forgequest.app.domain.Dates
import com.forgequest.app.domain.FoodSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalTime

@Serializable
data class ScanItem(
    val name: String,
    val grams: Int = 0,
    val kcal: Int = 0,
    val protein: Float = 0f,
    val carbs: Float = 0f,
)

/** A saved food ticked to log: how much, under which meal, and its name in the day's log ("Chicken shawarma, no Ketchup"). */
data class FoodPick(val food: FoodEntity, val qty: Float, val category: FoodCategory, val name: String = food.name)

@Serializable
data class FoodScan(
    val dish: String = "Meal",
    val items: List<ScanItem> = emptyList(),
    val handVisible: Boolean = false,
    val note: String = "",
)

class FoodRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val gemini: GeminiClient,
    private val models: ModelStore,
) {
    private val dao = db.food()
    private companion object { const val MAX_EDGE = 1280 }
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    val foods: Flow<List<FoodEntity>> = dao.observeFoods()
    fun mealsOn(day: Long): Flow<List<MealEntity>> = dao.observeMeals(day)

    suspend fun saveFood(food: FoodEntity): Long = dao.upsertFood(food)
    suspend fun deleteFood(id: Long) = dao.deleteFood(id)

    /** How often each saved food was logged, by food id. */
    val usage: Flow<Map<Long, FoodUsage>> = dao.observeFoodUsage().map { list -> list.associateBy { it.foodId } }

    /**
     * Saves a scanned or Gemini meal to My foods. The same food at the same restaurant (spelling variants and venue words
     * like كفتيريا or مطعم aside) is updated in place instead of saved again; it keeps its id, photo and pin.
     */
    suspend fun saveMeal(food: FoodEntity): Long {
        val all = dao.allFoods()
        val places = all.map { it.place }.filter { it.isNotBlank() }.distinct() + food.place
        val existing = all.firstOrNull { FoodSearch.sameFood(it.name, it.place, food.name, food.place, places) }
            ?: return dao.upsertFood(food)
        dao.upsertFood(
            food.copy(
                id = existing.id, createdAt = existing.createdAt, pinned = existing.pinned || food.pinned,
                photoPath = food.photoPath ?: existing.photoPath, place = food.place.ifBlank { existing.place },
            )
        )
        return existing.id
    }
    suspend fun deleteMeal(id: Long) = dao.deleteMeal(id)
    suspend fun restoreMeal(meal: MealEntity) = dao.restoreMeal(meal)

    /** Logs saved foods, each with its amount (1 = one serving), meal and the name to show in the day's log. */
    suspend fun logFoods(picks: List<FoodPick>, epochDay: Long = Dates.today()) {
        val now = System.currentTimeMillis()
        dao.insertMeals(picks.map { (food, qty, category, name) ->
            MealEntity(
                epochDay = epochDay, loggedAt = now, name = name, category = category,
                kcal = (food.kcal * qty).toInt(), protein = food.protein * qty, quantity = qty, carbs = food.carbs * qty,
                photoPath = food.photoPath, foodId = food.id,
            )
        })
    }

    suspend fun logScannedMeal(name: String, category: FoodCategory, kcal: Int, protein: Float, photoPath: String?, carbs: Float = 0f) =
        logOnce(name, category, kcal, protein, photoPath, Dates.today(), carbs)

    /** One meal that isn't saved to the food list: a restaurant plate, a snack you won't repeat. */
    suspend fun logOnce(name: String, category: FoodCategory, kcal: Int, protein: Float, photoPath: String? = null, epochDay: Long = Dates.today(), carbs: Float = 0f) {
        dao.insertMeals(listOf(
            MealEntity(
                epochDay = epochDay, loggedAt = System.currentTimeMillis(), name = name.trim(), category = category,
                kcal = kcal, protein = protein, photoPath = photoPath, carbs = carbs,
            )
        ))
    }

    /** Category that fits the current time of day, so the right tab opens first. */
    fun categoryForNow(): FoodCategory = when (LocalTime.now().hour) {
        in 4..10 -> FoodCategory.BREAKFAST
        in 11..15 -> FoodCategory.LUNCH
        in 16..17 -> FoodCategory.SNACKS
        in 18..22 -> FoodCategory.DINNER
        else -> FoodCategory.SNACKS
    }

    // ------------------------------------------------------------ Photos

    /** Copies a picked image into app storage, downscaled and upright. Returns the file path. */
    suspend fun importPhoto(uri: Uri): String = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw AiError.Unreadable(R.string.photo_err_open)
        savePhoto(bytes)
    }

    /** Saves a small in-memory picture (e.g. a quick thumbnail shot) as a JPEG. */
    suspend fun saveBitmap(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        val file = File(dir, "food_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        file.absolutePath
    }

    /**
     * Saves camera or gallery JPEG bytes, downscaled to 1280 px and rotated upright.
     * The image is subsampled while decoding, so a 48 MP photo never becomes a 190 MB bitmap in memory.
     */
    suspend fun savePhoto(jpeg: ByteArray): String = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw AiError.Unreadable(R.string.photo_err_read)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_EDGE) }
        val decoded = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options) ?: throw AiError.Unreadable(R.string.photo_err_read)
        val rotation = runCatching { ExifInterface(jpeg.inputStream()).rotationDegrees }.getOrDefault(0)
        val scale = minOf(1f, MAX_EDGE.toFloat() / maxOf(decoded.width, decoded.height))
        val matrix = Matrix().apply { postScale(scale, scale); postRotate(rotation.toFloat()) }
        val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (upright !== decoded) decoded.recycle()
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        val file = File(dir, "meal_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        upright.recycle()
        file.absolutePath
    }

    /** Largest power-of-two subsampling that still leaves the longest edge at or above [maxEdge]. */
    private fun sampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= maxEdge) sample *= 2
        return sample
    }

    /** Deletes one photo file, e.g. after a retake or a failed scan. */
    suspend fun discardPhoto(path: String?) = withContext(Dispatchers.IO) { path?.let { File(it).takeIf { f -> f.parentFile?.name == "photos" }?.delete() } }

    /**
     * Removes photo files no food or meal refers to any more (retakes, failed scans, deleted meals).
     * Files younger than an hour are left alone in case a scan is still in progress.
     */
    suspend fun deleteOrphanPhotos() = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "photos")
        if (!dir.isDirectory) return@withContext
        val referenced = (dao.allFoods().mapNotNull { it.photoPath } + dao.allMeals().mapNotNull { it.photoPath }).toSet()
        val cutoff = System.currentTimeMillis() - 60 * 60 * 1000
        dir.listFiles().orEmpty().filter { it.absolutePath !in referenced && it.lastModified() < cutoff }.forEach { it.delete() }
    }

    // ------------------------------------------------------------ AI scan

    suspend fun scan(photoPath: String, userHint: String = ""): FoodScan {
        val key = settings.apiKey.value
        if (key.isBlank()) throw AiError.MissingKey
        val image = withContext(Dispatchers.IO) { File(photoPath).readBytes() }
        val m = models.current()
        val userPrompt = if (userHint.isBlank()) {
            "Estimate this meal with realistic cooked weights and macro math."
        } else {
            "Estimate this meal. User notes/context: $userHint"
        }
        // Route meal scans to full Flash (m.fast) first with lowThinking = true (~1024 thinking budget)
        // so the model calculates spatial geometry, density, and macro math before writing JSON.
        val raw = gemini.generate(
            apiKey = key,
            models = listOf(m.fast, GeminiClient.FALLBACK_STABLE_FLASH, m.lite),
            system = scanInstructions(),
            turns = listOf("user" to userPrompt),
            image = image,
            jsonOutput = true,
            fast = false,
            lowThinking = true,
        )
        val scan = runCatching { json.decodeFromString<FoodScan>(extractJson(raw)) }.getOrNull()
            ?: throw AiError.Unreadable(R.string.scan_err_unreadable)
        if (scan.items.isEmpty()) throw AiError.Unreadable(R.string.scan_err_no_food)
        return scan
    }

    /** Calories and macros for a typed food ("chicken and rice"), for people who don't know the numbers. */
    suspend fun estimate(description: String): FoodScan {
        val key = settings.apiKey.value
        if (key.isBlank()) throw AiError.MissingKey
        val m = models.current()
        val raw = gemini.generate(
            apiKey = key,
            models = listOf(m.fast, GeminiClient.FALLBACK_STABLE_FLASH, m.lite),
            system = scanInstructions(),
            turns = listOf("user" to "There is no photo. Estimate one typical home-sized serving of what the user typed: ${description.take(200)}"),
            jsonOutput = true,
            lowThinking = true,
        )
        val scan = runCatching { json.decodeFromString<FoodScan>(extractJson(raw)) }.getOrNull()
            ?: throw AiError.Unreadable(R.string.scan_err_unreadable)
        if (scan.items.isEmpty()) throw AiError.Unreadable(R.string.scan_err_no_food)
        return scan
    }

    private suspend fun scanInstructions(): String {
        val s = settings.current()
        val handHint = if (s.handLengthCm > 0f) {
            "If the user's hand is visible, use it as an exact spatial ruler: hand length = ${s.handLengthCm} cm, palm width = ${s.palmWidthCm} cm."
        } else {
            "If a hand, standard fork (~19 cm), spoon, mug, or dinner plate (~26 cm rim) is visible, use it as a geometric scale reference."
        }
        return """
            You are an expert sports nutritionist estimating macros from meal photos for a fitness app. Be realistic, never optimistic.
            1. $handHint
            2. Identify each distinct food component separately, including visible cooking oil, ghee, butter, sauces, or dressings.
            3. Recognize Saudi / Middle Eastern and home-cooked items accurately (e.g., Kabsa, Mandi, Saleeg, Mutabbaq, Shawarma, Paratha flatbread ~260-300 kcal per 80g piece, eggs, cheese, laban, dates, cappuccino/mocha) as well as standard gym foods.
            4. Anchor your math on standard cooked nutritional densities per 100g (e.g. cooked chicken breast ~165 kcal & 31g protein; cooked rice ~130 kcal & 2.7g protein & 28g carbs; cooked lean beef ~250 kcal & 26g protein; whole egg ~72 kcal & 6.3g protein; olive oil/ghee ~900 kcal per 100g).
            5. Before emitting each item, verify internally that kcal roughly equals (protein * 4 + carbs * 4 + estimated_fat * 9).
            6. Use short, plain food names (e.g. "Grilled chicken breast", "Basmati rice", "Paratha flatbread").
            7. If the photo shows no food or drink, return an empty items list.
            Reply ONLY with JSON: {"dish": string, "items": [{"name": string, "grams": int, "kcal": int, "protein": number, "carbs": number}], "handVisible": boolean, "note": string}
            "note" is one short sentence about portion/cooking assumptions or anything uncertain, or empty.
        """.trimIndent()
    }

    /** The same instructions as the in-app scan, worded for pasting into another AI app (Gemini, ChatGPT…) with the photo. */
    suspend fun outsidePrompt(): String = scanInstructions() + "\n\n" + """
        Estimate the meal in the attached photo.
        Put the JSON in a single code block with nothing before or after it, so it can be copied in one tap and pasted into the ForgeQuest app.
    """.trimIndent()

    /** Reads an answer copied from another AI app. Null when it isn't a meal estimate in the expected format. */
    fun parseScan(text: String): FoodScan? =
        runCatching { json.decodeFromString<FoodScan>(extractJson(text)) }.getOrNull()?.takeIf { it.items.isNotEmpty() }

    /** A shareable copy of a meal photo for other apps; the private file itself stays out of reach. */
    suspend fun shareablePhoto(photoPath: String): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        dir.listFiles().orEmpty().forEach { it.delete() }
        val file = File(dir, "meal.jpg")
        File(photoPath).copyTo(file, overwrite = true)
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    private fun extractJson(raw: String): String {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        return if (start >= 0 && end > start) raw.substring(start, end + 1) else raw
    }
}
