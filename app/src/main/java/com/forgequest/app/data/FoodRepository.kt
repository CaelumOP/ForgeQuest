package com.forgequest.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.forgequest.app.data.ai.AiError
import com.forgequest.app.data.ai.GeminiClient
import com.forgequest.app.data.db.AppDatabase
import com.forgequest.app.data.db.FoodCategory
import com.forgequest.app.data.db.FoodEntity
import com.forgequest.app.data.db.MealEntity
import com.forgequest.app.data.prefs.SettingsRepository
import com.forgequest.app.domain.Dates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
)

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
) {
    private val dao = db.food()
    private companion object { const val MAX_EDGE = 1280 }
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    val foods: Flow<List<FoodEntity>> = dao.observeFoods()
    fun mealsOn(day: Long): Flow<List<MealEntity>> = dao.observeMeals(day)

    suspend fun saveFood(food: FoodEntity): Long = dao.upsertFood(food)
    suspend fun deleteFood(id: Long) = dao.deleteFood(id)
    suspend fun deleteMeal(id: Long) = dao.deleteMeal(id)
    suspend fun restoreMeal(meal: MealEntity) = dao.restoreMeal(meal)

    suspend fun logFoods(selection: List<Pair<FoodEntity, Float>>, epochDay: Long = Dates.today()) {
        val now = System.currentTimeMillis()
        dao.insertMeals(selection.map { (food, qty) ->
            MealEntity(
                epochDay = epochDay, loggedAt = now, name = food.name, category = food.category,
                kcal = (food.kcal * qty).toInt(), protein = food.protein * qty, quantity = qty,
                photoPath = food.photoPath, foodId = food.id,
            )
        })
    }

    suspend fun logScannedMeal(name: String, category: FoodCategory, kcal: Int, protein: Float, photoPath: String?) =
        logOnce(name, category, kcal, protein, photoPath, Dates.today())

    /** One meal that isn't saved to the food list: a restaurant plate, a snack you won't repeat. */
    suspend fun logOnce(name: String, category: FoodCategory, kcal: Int, protein: Float, photoPath: String? = null, epochDay: Long = Dates.today()) {
        dao.insertMeals(listOf(
            MealEntity(
                epochDay = epochDay, loggedAt = System.currentTimeMillis(), name = name.trim(), category = category,
                kcal = kcal, protein = protein, photoPath = photoPath,
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
            ?: throw AiError.Unreadable("Couldn't open that photo.")
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
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw AiError.Unreadable("Couldn't read that photo.")
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_EDGE) }
        val decoded = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options) ?: throw AiError.Unreadable("Couldn't read that photo.")
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

    // ------------------------------------------------------------ AI scan

    suspend fun scan(photoPath: String): FoodScan {
        val key = settings.apiKey.value
        if (key.isBlank()) throw AiError.MissingKey
        val s = settings.current()
        val image = withContext(Dispatchers.IO) { File(photoPath).readBytes() }
        val handHint = if (s.handLengthCm > 0f) {
            "If the user's hand is visible, use it as a size reference: hand length ${s.handLengthCm} cm, palm width ${s.palmWidthCm} cm."
        } else {
            "If a hand, fork, phone or standard plate is visible, use it as a size reference."
        }
        val system = """
            You estimate nutrition from meal photos for a fitness app. Be realistic, not optimistic.
            $handHint
            List each distinct food separately with estimated cooked grams, kcal and grams of protein.
            Include visible sauces and oil in the items. Use short, plain food names (e.g. "Grilled chicken", "White rice").
            If the photo shows no food, return an empty items list.
            Reply ONLY with JSON: {"dish": string, "items": [{"name": string, "grams": int, "kcal": int, "protein": number}], "handVisible": boolean, "note": string}
            "note" is one short sentence about anything uncertain, or empty.
        """.trimIndent()
        val raw = gemini.generate(key, listOf(s.fastModel), system, listOf("user" to "Estimate this meal."), image = image, jsonOutput = true, fast = true)
        val scan = runCatching { json.decodeFromString<FoodScan>(extractJson(raw)) }.getOrNull()
            ?: throw AiError.Unreadable("Couldn't read the meal from that photo. Try again with the plate fully in frame.")
        if (scan.items.isEmpty()) throw AiError.Unreadable("No food found in that photo. Try again with the plate in the middle of the frame.")
        return scan
    }

    private fun extractJson(raw: String): String {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        return if (start >= 0 && end > start) raw.substring(start, end + 1) else raw
    }
}
