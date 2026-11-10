package com.forgequest.app.ui.food

import com.forgequest.app.R
import com.forgequest.app.ui.titleRes

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forgequest.app.AppContainer
import com.forgequest.app.data.bridge.GeminiMeal
import com.forgequest.app.data.ProgressSnapshot
import com.forgequest.app.data.db.FoodCategory
import com.forgequest.app.data.db.FoodEntity
import com.forgequest.app.data.db.FoodPart
import com.forgequest.app.data.db.FoodUsage
import com.forgequest.app.data.db.withParts
import com.forgequest.app.data.FoodPick
import com.forgequest.app.data.db.MealEntity
import com.forgequest.app.domain.Dates
import com.forgequest.app.ui.components.UndoState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class FoodViewModel(val app: AppContainer) : ViewModel() {
    val foods: StateFlow<List<FoodEntity>> = app.food.foods.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recentOneOffs: StateFlow<List<MealEntity>> = app.db.food().observeRecentOneOffs().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    /** The day being viewed and logged to. Follows the real date until the person steps away from today. */
    val day = MutableStateFlow(Dates.today())
    private var followToday = true
    val meals: StateFlow<List<MealEntity>> = day.flatMapLatest { app.food.mealsOn(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val snapshot: StateFlow<ProgressSnapshot?> = app.progress.snapshot.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val category = MutableStateFlow(app.food.categoryForNow())

    /** How often each saved food was logged, so search puts your usual foods first. */
    val usage: StateFlow<Map<Long, FoodUsage>> = app.food.usage.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    /** What's typed in the search box; blank shows the open tab's foods. */
    val query = MutableStateFlow("")
    val catalogReady = app.catalog.ready
    /** Show the food-list matches with the most protein first. */
    val sortProtein = MutableStateFlow(false)

    /** Matches from the built-in food list for what's typed (nothing for a blank box). */
    val catalogResults: StateFlow<List<com.forgequest.app.domain.CatalogFood>> =
        combine(query.debounce(150), sortProtein) { q, byProtein -> q to byProtein }
            .mapLatest { (q, byProtein) -> app.catalog.search(q.trim(), 12, byProtein) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Logs [amount] of a food-list item to the open day, and keeps it as the person's own food so it's theirs next time. */
    fun logCatalog(item: com.forgequest.app.domain.CatalogFood, amount: com.forgequest.app.domain.CatalogAmount, unitLabel: String, barcode: String = "") = viewModelScope.launch {
        val each = amount.gramsEach
        val name = item.name(com.forgequest.app.ui.isArabic()).take(48)
        val serving = if (each == 1f) "" else "$unitLabel (${com.forgequest.app.domain.Catalog.formatNumber(each)} g)"
        val qty = if (each == 1f) 1f else amount.count
        val grams = if (each == 1f) amount.count else each
        val food = FoodEntity(
            name = name, category = category.value, kcal = com.forgequest.app.domain.Catalog.kcalFor(item, grams),
            protein = com.forgequest.app.domain.Catalog.proteinFor(item, grams), carbs = com.forgequest.app.domain.Catalog.carbsFor(item, grams),
            serving = if (each == 1f) "${com.forgequest.app.domain.Catalog.formatNumber(grams)} g" else serving,
            createdAt = System.currentTimeMillis(), place = item.brand(com.forgequest.app.ui.isArabic()), barcode = barcode,
        )
        val same = foods.value.firstOrNull { it.name == food.name && it.serving == food.serving && it.kcal == food.kcal }
        // The same food saved before without its barcode picks it up now, so the next scan finds it.
        val saved = if (same != null && barcode.isNotBlank() && same.barcode != barcode) same.copy(barcode = barcode).also { app.food.saveFood(it) } else same
        val id = saved?.id ?: app.food.saveFood(food)
        app.food.logFoods(listOf(FoodPick((saved ?: food).copy(id = id), qty, mealFor(food), food.name)), day.value)
        app.haptics.setDone()
        val badges = app.progress.evaluateBadges()
        message.value = app.str(R.string.food_logged_name, food.name) +
            if (badges.isNotEmpty() && !app.settings.current().justLog) app.str(R.string.badge_unlocked_suffix, app.str(badges.first().titleRes)) else ""
    }

    /** A scanned barcode that is already one of the person's foods: logs one of it to the open day. */
    fun logOwn(food: FoodEntity) = viewModelScope.launch {
        app.food.logFoods(listOf(FoodPick(food, 1f, mealFor(food), food.name)), day.value)
        app.haptics.setDone()
        val badges = app.progress.evaluateBadges()
        message.value = app.str(R.string.food_logged_name, food.name) +
            if (badges.isNotEmpty() && !app.settings.current().justLog) app.str(R.string.badge_unlocked_suffix, app.str(badges.first().titleRes)) else ""
    }

    /** A ticked food: how much, and the meal it's logged under. */
    data class Pick(val qty: Float, val category: FoodCategory)

    /** foodId → pick for items ticked but not yet logged. */
    val selection = MutableStateFlow<Map<Long, Pick>>(emptyMap())
    val message = MutableStateFlow<String?>(null)
    val apiKey: StateFlow<String> = app.settings.apiKey
    val aiOn: StateFlow<Boolean> = app.settings.aiOn

    suspend fun estimate(text: String): com.forgequest.app.data.FoodScan = app.food.estimate(text)

    /** Called whenever the screen is shown, so a tab left open overnight moves on to the new day. */
    fun onShown() {
        if (followToday) day.value = Dates.today()
        syncGeminiMeals()
    }

    // ------------------------------------------------------------ Meals from the Gemini Gem

    val geminiMeals: StateFlow<List<GeminiMeal>> = app.meals.pending

    /** Quietly checks the Gem's meals Doc; at most every two minutes, and never asks to sign in. */
    private fun syncGeminiMeals() = viewModelScope.launch {
        if (!app.settings.current().geminiMealsEnabled) return@launch
        val now = System.currentTimeMillis()
        if (now - lastMealSync < 2 * 60 * 1000L) return@launch
        lastMealSync = now
        val token = app.drive.silentToken() ?: return@launch
        com.forgequest.app.data.suspendRunCatching { app.meals.sync(token) }
    }

    fun reviewMeal(meal: GeminiMeal) { app.meals.review.value = meal }

    fun dismissMeal(meal: GeminiMeal) = viewModelScope.launch { app.meals.markHandled(meal.mealId) }

    private companion object {
        var lastMealSync = 0L
    }

    fun shiftDay(delta: Int) {
        val next = (day.value + delta).coerceAtMost(Dates.today())
        day.value = next
        followToday = next == Dates.today()
        app.haptics.tick()
    }

    fun goToToday() { followToday = true; day.value = Dates.today() }

    fun logOnce(name: String, category: FoodCategory, kcal: Int, protein: Float, carbs: Float = 0f) = viewModelScope.launch {
        app.food.logOnce(name, category, kcal, protein, epochDay = day.value, carbs = carbs)
        app.haptics.setDone()
        app.progress.evaluateBadges()
        message.value = app.str(R.string.food_logged_name, name)
    }

    /**
     * The meal a ticked food is logged under: drinks always under Drinks; a search result under the open tab (which opens
     * on the meal for the time of day); otherwise the tab it's listed in.
     */
    private fun mealFor(food: FoodEntity): FoodCategory =
        if (food.category == FoodCategory.DRINKS) FoodCategory.DRINKS else category.value

    fun toggle(food: FoodEntity) {
        app.haptics.tick()
        selection.update { if (food.id in it) it - food.id else it + (food.id to Pick(1f, mealFor(food))) }
    }

    fun setQty(food: FoodEntity, qty: Float) {
        app.haptics.tick()
        selection.update { if (qty <= 0f) it - food.id else it + (food.id to Pick(qty, it[food.id]?.category ?: mealFor(food))) }
    }

    /** The name in the day's log, saying which parts were left out: "Chicken shawarma, no Ketchup". */
    private fun logName(food: FoodEntity): String {
        val off = food.parts.filter { !it.on }.map { it.name }
        return if (off.isEmpty()) food.name else app.str(R.string.food_without, food.name, off.joinToString(app.str(R.string.food_list_sep)))
    }

    /** Saves which parts are ticked (remembered for next time); the food's totals follow them. */
    fun saveParts(food: FoodEntity, parts: List<FoodPart>) = viewModelScope.launch { app.food.saveFood(food.withParts(parts)) }

    fun logSelected() {
        val foodsById = foods.value.associateBy { it.id }
        val picked = selection.value.mapNotNull { (id, p) -> foodsById[id]?.let { FoodPick(it, p.qty, p.category, logName(it)) } }
        if (picked.isEmpty()) return
        viewModelScope.launch {
            app.food.logFoods(picked, day.value)
            selection.value = emptyMap()
            app.haptics.setDone()
            val badges = app.progress.evaluateBadges()
            message.value = app.plural(R.plurals.food_logged_items, picked.size, picked.size) +
                if (badges.isNotEmpty() && !app.settings.current().justLog) app.str(R.string.badge_unlocked_suffix, app.str(badges.first().titleRes)) else ""
        }
    }

    fun save(food: FoodEntity) = viewModelScope.launch { app.food.saveFood(food) }
    fun delete(food: FoodEntity) = viewModelScope.launch { app.food.deleteFood(food.id); selection.update { it - food.id } }
    /** Deleted right away; [undo] puts it back with the same id. */
    val undo = UndoState(viewModelScope)

    fun deleteMeal(meal: MealEntity) = viewModelScope.launch {
        app.food.deleteMeal(meal.id)
        undo.offer(app.str(R.string.food_removed, meal.name)) { app.food.restoreMeal(meal) }
    }

    /** Copies a logged meal to the open day, with undo. */
    fun logAgain(meal: MealEntity) = viewModelScope.launch {
        val id = app.food.logAgain(meal, day.value)
        app.haptics.setDone()
        app.progress.evaluateBadges()
        undo.offer(app.str(R.string.food_logged_name, meal.name)) { app.food.deleteMeal(id) }
    }

    fun setPhotoFromUri(food: FoodEntity, uri: Uri) = viewModelScope.launch {
        runCatching { app.food.importPhoto(uri) }.onSuccess { app.food.saveFood(food.copy(photoPath = it)) }
            .onFailure { message.value = app.str(R.string.food_photo_failed) }
    }

    fun setPhotoFromBitmap(food: FoodEntity, bitmap: Bitmap) = viewModelScope.launch {
        app.food.saveFood(food.copy(photoPath = app.food.saveBitmap(bitmap)))
    }
}
