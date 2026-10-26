package com.forgequest.app.ui.food

import com.forgequest.app.ui.components.ChoiceChip
import com.forgequest.app.ui.components.FitSwitch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import com.forgequest.app.R
import com.forgequest.app.ui.localLabel
import com.forgequest.app.ui.titleRes

import androidx.compose.ui.res.pluralStringResource

import androidx.compose.ui.res.stringResource

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Search
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.forgequest.app.AppContainer
import com.forgequest.app.data.bridge.GeminiMeal
import com.forgequest.app.data.ProgressSnapshot
import com.forgequest.app.data.db.FoodCategory
import com.forgequest.app.data.db.FoodEntity
import com.forgequest.app.data.db.FoodPart
import com.forgequest.app.data.db.FoodUsage
import com.forgequest.app.data.db.withParts
import com.forgequest.app.data.FoodPick
import com.forgequest.app.domain.FoodSearch
import com.forgequest.app.data.db.MealEntity
import com.forgequest.app.data.db.SCANNED_NO_PHOTO
import com.forgequest.app.domain.Dates
import com.forgequest.app.ui.LocalSnackbar
import com.forgequest.app.ui.Routes
import com.forgequest.app.ui.appViewModel
import com.forgequest.app.ui.components.SegmentedTabs
import com.forgequest.app.ui.components.FitCard
import com.forgequest.app.ui.components.IconCircle
import com.forgequest.app.ui.components.PillButton
import com.forgequest.app.ui.components.ProgressLine
import com.forgequest.app.ui.components.NumberEntryDialog
import com.forgequest.app.ui.components.Stepper
import com.forgequest.app.ui.components.TonalPill
import com.forgequest.app.ui.theme.Fit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@OptIn(ExperimentalCoroutinesApi::class)
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

    /** A ticked food: how much, and the meal it's logged under. */
    data class Pick(val qty: Float, val category: FoodCategory)

    /** foodId → pick for items ticked but not yet logged. */
    val selection = MutableStateFlow<Map<Long, Pick>>(emptyMap())
    val message = MutableStateFlow<String?>(null)
    val apiKey: StateFlow<String> = app.settings.apiKey

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
    private fun mealFor(food: FoodEntity): FoodCategory = when {
        food.category == FoodCategory.DRINKS -> FoodCategory.DRINKS
        query.value.isNotBlank() -> category.value
        else -> food.category
    }

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
                if (badges.isNotEmpty()) app.str(R.string.badge_unlocked_suffix, app.str(badges.first().titleRes)) else ""
        }
    }

    fun save(food: FoodEntity) = viewModelScope.launch { app.food.saveFood(food) }
    fun delete(food: FoodEntity) = viewModelScope.launch { app.food.deleteFood(food.id); selection.update { it - food.id } }
    /** Deleted right away; [undo] puts it back with the same id. */
    val undo = MutableStateFlow<Pair<String, suspend () -> Unit>?>(null)

    fun deleteMeal(meal: MealEntity) = viewModelScope.launch {
        app.food.deleteMeal(meal.id)
        undo.value = app.str(R.string.food_removed, meal.name) to { app.food.restoreMeal(meal) }
    }

    fun runUndo() = viewModelScope.launch { undo.value?.second?.invoke(); undo.value = null }

    fun setPhotoFromUri(food: FoodEntity, uri: Uri) = viewModelScope.launch {
        runCatching { app.food.importPhoto(uri) }.onSuccess { app.food.saveFood(food.copy(photoPath = it)) }
            .onFailure { message.value = app.str(R.string.food_photo_failed) }
    }

    fun setPhotoFromBitmap(food: FoodEntity, bitmap: Bitmap) = viewModelScope.launch {
        app.food.saveFood(food.copy(photoPath = app.food.saveBitmap(bitmap)))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodScreen(nav: NavHostController) {
    val vm = appViewModel { FoodViewModel(it) }
    val foods by vm.foods.collectAsState()
    val meals by vm.meals.collectAsState()
    val snap by vm.snapshot.collectAsState()
    val category by vm.category.collectAsState()
    val selection by vm.selection.collectAsState()
    val message by vm.message.collectAsState()
    val snackbar = LocalSnackbar.current
    val c = Fit.colors
    var editing by remember { mutableStateOf<FoodEntity?>(null) }
    var photoFor by remember { mutableStateOf<FoodEntity?>(null) }

    val undo by vm.undo.collectAsState()
    val geminiMeals by vm.geminiMeals.collectAsState()
    val apiKey by vm.apiKey.collectAsState()
    val day by vm.day.collectAsState()
    var confirmDeleteFood by remember { mutableStateOf<FoodEntity?>(null) }
    var quickLog by remember { mutableStateOf(false) }
    val isToday = day == Dates.today()
    LaunchedEffect(Unit) { vm.onShown() }

    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value = null } }
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(undo) {
        undo?.let { (text, _) ->
            val result = snackbar.showSnackbar(text, actionLabel = undoLabel, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) vm.runUndo() else if (vm.undo.value?.first == text) vm.undo.value = null
        }
    }

    val query by vm.query.collectAsState()
    val usage by vm.usage.collectAsState()
    var partsFor by remember { mutableStateOf<FoodEntity?>(null) }
    val focus = LocalFocusManager.current
    val inCategory = foods.filter { it.category == category }
    val searching = query.isNotBlank()
    // Best match first; among equally good matches, pinned foods, then the ones you log most and most lately.
    val results = remember(query, foods, usage) {
        if (!searching) emptyList()
        else foods.map { it to FoodSearch.score(query, it.name, it.place) }.filter { it.second > 0f }
            .sortedWith(
                compareByDescending<Pair<FoodEntity, Float>> { it.second }.thenByDescending { it.first.pinned }
                    .thenByDescending { usage[it.first.id]?.times ?: 0 }.thenByDescending { usage[it.first.id]?.lastAt ?: 0L },
            ).map { it.first }
    }
    val places = remember(foods) { foods.map { it.place }.filter { it.isNotBlank() }.distinct() }
    // Restaurant shortcuts: the ones used most lately first.
    val recentPlaces = remember(foods, usage) {
        foods.filter { it.place.isNotBlank() }.groupBy { it.place }
            .map { (p, fs) -> p to fs.maxOf { usage[it.id]?.lastAt ?: it.createdAt } }
            .sortedByDescending { it.second }.map { it.first }.take(6)
    }
    val shown = if (searching) results else inCategory
    val selectedFoods = foods.filter { it.id in selection }
    val selKcal = selectedFoods.sumOf { (it.kcal * (selection[it.id]?.qty ?: 1f)).toDouble() }.roundToInt()
    val selProtein = selectedFoods.sumOf { (it.protein * (selection[it.id]?.qty ?: 1f)).toDouble() }.roundToInt()

    Box(Modifier.fillMaxSize().background(c.background)) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = if (selection.isEmpty()) 24.dp else 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.food_title), style = MaterialTheme.typography.headlineMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
                    PillButton(stringResource(R.string.food_scan_meal), onClick = { nav.navigate(Routes.SCAN) }, icon = Icons.Rounded.CameraAlt)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(44.dp).clip(CircleShape).background(c.cardHigh).clickable {
                        editing = FoodEntity(name = "", category = category, kcal = 0, protein = 0f, createdAt = System.currentTimeMillis())
                    }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.food_add), tint = c.textPrimary)
                    }
                }
            }
            item {
                DayPicker(day, isToday, onPrev = { vm.shiftDay(-1) }, onNext = { vm.shiftDay(1) }, onToday = vm::goToToday)
            }
            if (geminiMeals.isNotEmpty()) item { GeminiInbox(geminiMeals, onOpen = vm::reviewMeal, onDismiss = { vm.dismissMeal(it) }) }
            snap?.let { s -> item { DayTotals(s, day, meals) } }
            if (isToday) snap?.let { s -> item { WeekCard(s) } }
            item {
                SearchField(query, onChange = { vm.query.value = it }, onDone = { focus.clearFocus() })
            }
            if (!searching && recentPlaces.isNotEmpty()) {
                item { RestaurantShortcuts(recentPlaces, onPick = { vm.query.value = it; focus.clearFocus() }) }
            }
            item {
                val withSelection = selection.values.map { FoodCategory.entries.indexOf(it.category) }.toSet()
                SegmentedTabs(
                    FoodCategory.entries.map { it.localLabel() }, FoodCategory.entries.indexOf(category),
                    onSelect = { vm.category.value = FoodCategory.entries[it] }, dots = withSelection,
                )
            }
            if (searching) {
                item {
                    Text(
                        if (results.isEmpty()) stringResource(R.string.food_search_none, query.trim()) else stringResource(R.string.food_search_logs_as, category.localLabel()),
                        style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                if (results.isEmpty()) {
                    item {
                        TonalPill(stringResource(R.string.food_search_add, query.trim()), onClick = {
                            editing = FoodEntity(name = query.trim().take(48), category = category, kcal = 0, protein = 0f, createdAt = System.currentTimeMillis())
                        }, icon = Icons.Rounded.Add)
                    }
                }
            } else if (inCategory.isEmpty()) {
                item {
                    FitCard {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            IconCircle(Icons.Rounded.Restaurant, c.teal, c.tealTrack, 56.dp)
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.food_none_in_category, category.localLabel()), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(R.string.food_empty_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(14.dp))
                            TonalPill(stringResource(R.string.food_add_a_food), onClick = {
                                editing = FoodEntity(name = "", category = category, kcal = 0, protein = 0f, createdAt = System.currentTimeMillis())
                            }, icon = Icons.Rounded.Add)
                        }
                    }
                }
            }
            // Saved foods are logged by ticking them; say so while nothing is ticked.
            if (!searching && inCategory.isNotEmpty() && selection.isEmpty()) {
                item { Text(stringResource(R.string.food_tick_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(horizontal = 4.dp)) }
            }
            items(shown, key = { it.id }) { food ->
                FoodRow(
                    food = food,
                    qty = selection[food.id]?.qty,
                    onToggle = { vm.toggle(food) },
                    onQty = { vm.setQty(food, it) },
                    onPhoto = { photoFor = food },
                    onEdit = { editing = food },
                    onParts = if (food.parts.isNotEmpty()) ({ partsFor = food }) else null,
                )
            }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isToday) stringResource(R.string.food_logged_today) else stringResource(R.string.food_logged_that_day), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
                    TonalPill(stringResource(R.string.food_quick_log), onClick = { quickLog = true }, icon = Icons.Rounded.Add)
                }
            }
            if (meals.isEmpty()) {
                item { Text(if (isToday) stringResource(R.string.food_nothing_today) else stringResource(R.string.food_nothing_that_day), style = MaterialTheme.typography.bodySmall, color = c.textSecondary) }
            }
            items(meals, key = { "m${it.id}" }) { meal -> MealRow(meal, onDelete = { vm.deleteMeal(meal) }) }
        }

        AnimatedVisibility(
            selection.isNotEmpty(),
            enter = slideInVertically { it }, exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Box(Modifier.fillMaxWidth().background(c.background).padding(16.dp)) {
                PillButton(
                    pluralStringResource(R.plurals.food_log_selection, selection.size, selection.size, selKcal, selProtein),
                    onClick = vm::logSelected,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (quickLog) {
        val recentOneOffs by vm.recentOneOffs.collectAsState()
        QuickLogSheet(category, recent = recentOneOffs, carbsOn = snap?.settings?.trackCarbs == true, estimate = if (apiKey.isNotBlank()) vm::estimate else null, onLog = { n, cat, k, p, cb -> vm.logOnce(n, cat, k, p, cb); quickLog = false }, onDismiss = { quickLog = false })
    }
    partsFor?.let { food ->
        PartsSheet(food.name, food.parts, carbsOn = snap?.settings?.trackCarbs == true, onSave = { vm.saveParts(food, it); partsFor = null }, onDismiss = { partsFor = null })
    }
    editing?.let { food ->
        FoodEditorSheet(food, places, carbsOn = snap?.settings?.trackCarbs == true, estimate = if (apiKey.isNotBlank()) vm::estimate else null, onSave = { vm.save(it); editing = null }, onDelete = if (food.id != 0L) ({ confirmDeleteFood = food; editing = null }) else null, onDismiss = { editing = null })
    }
    confirmDeleteFood?.let { food ->
        AlertDialog(
            onDismissRequest = { confirmDeleteFood = null },
            containerColor = c.card,
            title = { Text(stringResource(R.string.food_delete_title, food.name), color = c.textPrimary) },
            text = { Text(stringResource(R.string.food_delete_body), color = c.textSecondary) },
            confirmButton = { TextButton(onClick = { vm.delete(food); confirmDeleteFood = null }) { Text(stringResource(R.string.action_delete), color = c.coral) } },
            dismissButton = { TextButton(onClick = { confirmDeleteFood = null }) { Text(stringResource(R.string.action_cancel), color = c.textSecondary) } },
        )
    }
    photoFor?.let { food ->
        PhotoSourceSheet(
            onUri = { vm.setPhotoFromUri(food, it); photoFor = null },
            onBitmap = { vm.setPhotoFromBitmap(food, it); photoFor = null },
            onRemove = if (food.photoPath != null) ({ vm.save(food.copy(photoPath = null)); photoFor = null }) else null,
            onDismiss = { photoFor = null },
        )
    }
}

/** Meals from the Gemini Gem waiting for a tap: open one to check it on the review sheet, or dismiss it. */
@Composable
private fun GeminiInbox(meals: List<GeminiMeal>, onOpen: (GeminiMeal) -> Unit, onDismiss: (GeminiMeal) -> Unit) {
    val c = Fit.colors
    FitCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = c.purple, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.meals_inbox_title, meals.size), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.meals_inbox_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        Spacer(Modifier.height(8.dp))
        meals.forEach { meal ->
            val day = meal.epochDay()
            val whenLabel = when (day) {
                Dates.today() -> stringResource(R.string.day_today)
                Dates.today() - 1 -> stringResource(R.string.day_yesterday)
                else -> LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern(stringResource(R.string.home_date_pattern)))
            } + (meal.localTime()?.let { " · " + it.format(DateTimeFormatter.ofPattern("HH:mm")) } ?: "")
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(16.dp)).background(c.cardHigh)
                    .clickable { onOpen(meal) }.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(meal.dish, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(whenLabel, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
                Text(stringResource(R.string.food_kcal, meal.kcal), style = MaterialTheme.typography.labelLarge, color = c.blue)
                IconButton(onClick = { onDismiss(meal) }) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.meals_dismiss), tint = c.textSecondary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun DayPicker(day: Long, isToday: Boolean, onPrev: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    val c = Fit.colors
    val date = LocalDate.ofEpochDay(day)
    val label = when {
        isToday -> stringResource(R.string.day_today)
        day == Dates.today() - 1 -> stringResource(R.string.day_yesterday)
        else -> date.format(DateTimeFormatter.ofPattern(stringResource(R.string.home_date_pattern)))
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = stringResource(R.string.cd_previous_day), tint = c.textPrimary) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            if (!isToday) {
                Text(stringResource(R.string.food_back_to_today), style = MaterialTheme.typography.labelMedium, color = c.teal, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onToday).padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
        IconButton(onClick = onNext, enabled = !isToday) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = stringResource(R.string.cd_next_day), tint = if (isToday) c.divider else c.textPrimary)
        }
    }
}

@Composable
private fun DayTotals(s: ProgressSnapshot, day: Long, meals: List<MealEntity>) {
    val c = Fit.colors
    val kcal = meals.sumOf { it.kcal }
    val protein = meals.sumOf { it.protein.toDouble() }.toFloat()
    val over = kcal > s.settings.kcalGoal
    FitCard(padding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)) {
        Row {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.food_protein_of_goal, protein.roundToInt(), s.settings.proteinGoal), style = MaterialTheme.typography.titleSmall, color = c.teal)
                Text(stringResource(R.string.label_protein), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                Spacer(Modifier.height(6.dp))
                ProgressLine(protein / s.settings.proteinGoal.coerceAtLeast(1), c.teal, c.tealTrack, height = 5.dp)
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text("⁦%,d / %,d⁩".format(kcal, s.settings.kcalGoal), style = MaterialTheme.typography.titleSmall, color = if (over) c.gold else c.blue)
                Text(if (over) stringResource(R.string.food_calories_surplus) else stringResource(R.string.home_legend_calories), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                Spacer(Modifier.height(6.dp))
                ProgressLine(kcal.toFloat() / s.settings.kcalGoal.coerceAtLeast(1), if (over) c.gold else c.blue, if (over) c.goldTrack else c.blueTrack, height = 5.dp)
            }
        }
        if (s.settings.trackCarbs) {
            val carbs = meals.sumOf { it.carbs.toDouble() }.toFloat()
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.food_carbs_of_goal, carbs.roundToInt(), s.settings.carbsGoal), style = MaterialTheme.typography.titleSmall, color = c.purple)
            Text(stringResource(R.string.label_carbs), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
            Spacer(Modifier.height(6.dp))
            ProgressLine(carbs / s.settings.carbsGoal.coerceAtLeast(1), c.purple, c.purpleTrack, height = 5.dp)
        }
    }
}

/** The last 7 days at a glance: one protein bar per day against the goal, and the averages. */
@Composable
private fun WeekCard(s: ProgressSnapshot) {
    val c = Fit.colors
    val today = Dates.today()
    val days = (6 downTo 0).map { offset -> today - offset }
    val byDay = s.nutritionByDay.associateBy { it.epochDay }
    val logged = days.mapNotNull { byDay[it] }
    if (logged.isEmpty()) return
    val avgKcal = logged.sumOf { it.kcal } / logged.size
    val avgProtein = (logged.sumOf { it.protein.toDouble() } / logged.size).roundToInt()
    val hits = logged.count { it.protein >= s.settings.proteinGoal }
    FitCard(padding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.food_last_7_days), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                Text(
                    pluralStringResource(R.plurals.food_week_summary, logged.size, "%,d".format(avgKcal), avgProtein, hits, logged.size) +
                        if (s.settings.trackCarbs) stringResource(R.string.food_week_carbs, (logged.sumOf { it.carbs.toDouble() } / logged.size).roundToInt()) else "",
                    style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            days.forEach { d ->
                val n = byDay[d]
                val ratio = ((n?.protein ?: 0f) / s.settings.proteinGoal.coerceAtLeast(1)).coerceIn(0f, 1f)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(32.dp).clip(RoundedCornerShape(6.dp)).background(c.tealTrack), contentAlignment = Alignment.BottomCenter) {
                        if (n != null) Box(Modifier.fillMaxWidth().height((32 * ratio).dp.coerceAtLeast(if (ratio > 0f) 3.dp else 0.dp)).background(if (n.protein >= s.settings.proteinGoal) c.teal else c.teal.copy(alpha = 0.55f)))
                    }
                    Text(Dates.shortDayLetters[Dates.dayOfWeek(d) - 1], style = MaterialTheme.typography.labelSmall, color = if (d == today) c.textPrimary else c.textSecondary)
                }
            }
        }
    }
}

/** Search over your own saved foods and their restaurants (FoodSearch: spelling variants, Arabic/English, typos). */
@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, onDone: () -> Unit) {
    val c = Fit.colors
    OutlinedTextField(
        query, { onChange(it.take(48).replace("\n", "")) },
        placeholder = { Text(stringResource(R.string.food_search_hint), color = c.textSecondary) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = c.textSecondary) },
        trailingIcon = if (query.isNotEmpty()) ({
            IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.food_search_clear), tint = c.textSecondary) }
        }) else null,
        singleLine = true, shape = RoundedCornerShape(24.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onDone() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.teal, unfocusedBorderColor = c.divider, focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary,
            focusedContainerColor = c.card, unfocusedContainerColor = c.card,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** One-tap restaurant shortcuts under the search box: a tap shows that restaurant's foods. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RestaurantShortcuts(places: List<String>, onPick: (String) -> Unit) {
    val c = Fit.colors
    Column {
        Text(stringResource(R.string.food_restaurants), style = MaterialTheme.typography.labelLarge, color = c.textSecondary, modifier = Modifier.padding(horizontal = 4.dp))
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            places.forEach { p -> ChoiceChip(p, false, { onPick(p) }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickLogSheet(initial: FoodCategory, recent: List<MealEntity>, carbsOn: Boolean, estimate: (suspend (String) -> com.forgequest.app.data.FoodScan)?, onLog: (String, FoodCategory, Int, Float, Float) -> Unit, onDismiss: () -> Unit) {
    var carbs by remember { mutableStateOf("") }
    val c = Fit.colors
    var name by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(initial) }
    val fieldColors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.teal, unfocusedBorderColor = c.divider, focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary, focusedLabelColor = c.teal, unfocusedLabelColor = c.textSecondary)
    ModalBottomSheet(sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), onDismissRequest = onDismiss, containerColor = c.card) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text(stringResource(R.string.food_quick_log), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
            Text(stringResource(R.string.food_quick_log_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            // Recent one-offs fill the form in one tap (MacroFactor and MyFitnessPal lead with recents).
            if (recent.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.food_recent), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    recent.forEach { m ->
                        ChoiceChip("${m.name} · ${m.kcal}", m.name == name, {
                            name = m.name; kcal = m.kcal.toString(); protein = m.protein.toString().removeSuffix(".0"); carbs = if (m.carbs > 0f) m.carbs.toString().removeSuffix(".0") else ""
                        })
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(name, { name = it.take(48) }, label = { Text(stringResource(R.string.food_what_was_it)) }, singleLine = true, shape = RoundedCornerShape(16.dp), colors = fieldColors, modifier = Modifier.fillMaxWidth())
            EstimateButton(name, estimate) { k, p, cb -> kcal = k.toString(); protein = p; carbs = cb }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(kcal, { kcal = it.filter(Char::isDigit).take(5) }, label = { Text(stringResource(R.string.home_legend_calories)) }, singleLine = true, shape = RoundedCornerShape(16.dp), colors = fieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                OutlinedTextField(protein, { protein = it.filter { ch -> ch.isDigit() || ch == '.' }.take(5) }, label = { Text(stringResource(R.string.home_legend_protein)) }, singleLine = true, shape = RoundedCornerShape(16.dp), colors = fieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            }
            if (carbsOn) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(carbs, { carbs = it.filter { ch -> ch.isDigit() || ch == '.' }.take(5) }, label = { Text(stringResource(R.string.label_carbs_g)) }, singleLine = true, shape = RoundedCornerShape(16.dp), colors = fieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(14.dp))
            SegmentedTabs(FoodCategory.entries.map { it.localLabel() }, FoodCategory.entries.indexOf(category), { category = FoodCategory.entries[it] })
            Spacer(Modifier.height(16.dp))
            PillButton(
                stringResource(R.string.food_log_it),
                onClick = { onLog(name.trim(), category, kcal.toIntOrNull() ?: 0, protein.toFloatOrNull() ?: 0f, carbs.toFloatOrNull() ?: 0f) },
                enabled = name.isNotBlank() && kcal.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FoodRow(food: FoodEntity, qty: Float?, onToggle: () -> Unit, onQty: (Float) -> Unit, onPhoto: () -> Unit, onEdit: () -> Unit, onParts: (() -> Unit)?) {
    val c = Fit.colors
    val selected = qty != null
    val bg by animateColorAsState(if (selected) c.tealTrack else c.card, label = "bg")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(bg)
            .combinedClickable(onClick = onToggle, onLongClick = onEdit, onClickLabel = stringResource(if (selected) R.string.cd_deselect else R.string.cd_select), onLongClickLabel = stringResource(R.string.cd_edit))
            .semantics { this.selected = selected }
            .padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(if (selected) c.teal else c.cardHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.cd_selected), tint = c.onTeal, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(c.cardHigh).clickable(onClick = onPhoto), contentAlignment = Alignment.Center) {
            if (food.photoPath != null) {
                AsyncImage(model = File(food.photoPath), contentDescription = food.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Rounded.PhotoCamera, contentDescription = stringResource(R.string.cd_add_photo), tint = c.textSecondary, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (food.pinned) {
                    Icon(Icons.Rounded.PushPin, contentDescription = stringResource(R.string.cd_pinned), tint = c.gold, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(food.name, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (food.place.isNotBlank()) {
                Text(food.place, style = MaterialTheme.typography.bodySmall, color = c.textPrimary.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                listOfNotNull(stringResource(R.string.food_kcal, food.kcal), stringResource(R.string.food_g_protein, food.protein.roundToInt()), food.serving.ifBlank { null }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (onParts != null) {
                Text(
                    partsCount(food.parts) + " · " + stringResource(R.string.food_parts_change),
                    style = MaterialTheme.typography.labelMedium, color = c.teal, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onParts).padding(vertical = 4.dp),
                )
            }
        }
        if (qty != null) {
            var typing by remember { mutableStateOf(false) }
            Stepper(
                if (qty % 1f == 0f) "${qty.toInt()}×" else "${qty}×",
                // Whole servings, with a half serving as the smallest step.
                onMinus = { onQty(if (qty > 1f) qty - 1f else if (qty == 1f) 0.5f else 0f) },
                onPlus = { onQty(if (qty < 1f) 1f else (qty + 1f).coerceAtMost(20f)) },
                minValueWidth = 40.dp,
                onValueClick = { typing = true },
            )
            if (typing) {
                NumberEntryDialog(
                    title = stringResource(R.string.food_servings_of, food.name), initial = qty, unit = "×", decimals = 2, min = 0.25f, max = 20f,
                    onDismiss = { typing = false }, onConfirm = { typing = false; onQty(it) },
                )
            }
        }
    }
}

@Composable
private fun MealRow(meal: MealEntity, onDelete: () -> Unit) {
    val c = Fit.colors
    val time = Instant.ofEpochMilli(meal.loggedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.card).padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (meal.photoPath != null && meal.photoPath != SCANNED_NO_PHOTO) {
            AsyncImage(File(meal.photoPath), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)))
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                (if (meal.quantity != 1f) "${if (meal.quantity % 1f == 0f) meal.quantity.toInt() else meal.quantity}× " else "") + meal.name,
                style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text("$time · ${meal.category.localLabel()}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(stringResource(R.string.food_kcal, meal.kcal), style = MaterialTheme.typography.labelLarge, color = c.blue)
            Text(stringResource(R.string.food_g, meal.protein.roundToInt()), style = MaterialTheme.typography.labelMedium, color = c.teal)
        }
        IconButton(onClick = onDelete) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_remove), tint = c.textSecondary, modifier = Modifier.size(18.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoSourceSheet(onUri: (Uri) -> Unit, onBitmap: (Bitmap) -> Unit, onRemove: (() -> Unit)?, onDismiss: () -> Unit) {
    val c = Fit.colors
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) onUri(uri) else onDismiss() }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp -> if (bmp != null) onBitmap(bmp) else onDismiss() }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.card) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text(stringResource(R.string.food_photo), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
            Spacer(Modifier.height(12.dp))
            SheetAction(Icons.Rounded.PhotoLibrary, stringResource(R.string.photo_choose_gallery)) { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            SheetAction(Icons.Rounded.PhotoCamera, stringResource(R.string.photo_take)) { camera.launch(null) }
            if (onRemove != null) SheetAction(Icons.Rounded.Close, stringResource(R.string.photo_remove), onRemove)
        }
    }
}

@Composable
private fun SheetAction(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    val c = Fit.colors
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 14.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = c.teal)
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoodEditorSheet(food: FoodEntity, places: List<String>, carbsOn: Boolean, estimate: (suspend (String) -> com.forgequest.app.data.FoodScan)?, onSave: (FoodEntity) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var carbs by remember { mutableStateOf(if (food.carbs == 0f) "" else food.carbs.toString().removeSuffix(".0")) }
    val c = Fit.colors
    var name by remember { mutableStateOf(food.name) }
    var kcal by remember { mutableStateOf(if (food.kcal == 0) "" else food.kcal.toString()) }
    var protein by remember { mutableStateOf(if (food.protein == 0f) "" else food.protein.toString().removeSuffix(".0")) }
    var serving by remember { mutableStateOf(food.serving) }
    var category by remember { mutableStateOf(food.category) }
    var pinned by remember { mutableStateOf(food.pinned) }
    var place by remember { mutableStateOf(food.place) }
    var parts by remember { mutableStateOf(food.parts) }
    var editParts by remember { mutableStateOf(false) }
    val fieldColors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.teal, unfocusedBorderColor = c.divider, focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary, focusedLabelColor = c.teal, unfocusedLabelColor = c.textSecondary)
    ModalBottomSheet(sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), onDismissRequest = onDismiss, containerColor = c.card) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text(if (food.id == 0L) stringResource(R.string.food_add_title) else stringResource(R.string.food_edit_title), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(name, { name = it.take(48) }, label = { Text(stringResource(R.string.label_name)) }, singleLine = true, shape = RoundedCornerShape(16.dp), colors = fieldColors, modifier = Modifier.fillMaxWidth())
            if (parts.isNotEmpty()) {
                // With parts, the numbers come from the ticked parts: change a part's grams instead of typing totals.
                val on = parts.filter { it.on }
                Spacer(Modifier.height(10.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.cardHigh).clickable { editParts = true }.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        listOf(stringResource(R.string.food_kcal, on.sumOf { it.kcal }), stringResource(R.string.food_g_protein, on.sumOf { it.protein.toDouble() }.roundToInt())).joinToString(" · "),
                        style = MaterialTheme.typography.bodyLarge, color = c.textPrimary,
                    )
                    Text(partsCount(parts) + " · " + stringResource(R.string.food_parts_change), style = MaterialTheme.typography.labelMedium, color = c.teal)
                }
            } else {
                EstimateButton(name, estimate) { k, p, cb -> kcal = k.toString(); protein = p; carbs = cb }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(kcal, { kcal = it.filter(Char::isDigit).take(5) }, label = { Text(stringResource(R.string.home_legend_calories)) }, singleLine = true, shape = RoundedCornerShape(16.dp), colors = fieldColors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(protein, { protein = it.filter { ch -> ch.isDigit() || ch == '.' }.take(5) }, label = { Text(stringResource(R.string.home_legend_protein)) }, singleLine = true, shape = RoundedCornerShape(16.dp), colors = fieldColors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
                if (carbsOn) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(carbs, { carbs = it.filter { ch -> ch.isDigit() || ch == '.' }.take(5) }, label = { Text(stringResource(R.string.label_carbs_g)) }, singleLine = true, shape = RoundedCornerShape(16.dp), colors = fieldColors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(10.dp))
            PlaceField(place, { place = it }, places)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(serving, { serving = it.take(32) }, label = { Text(stringResource(R.string.food_serving_hint)) }, singleLine = true, shape = RoundedCornerShape(16.dp), colors = fieldColors, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            SegmentedTabs(FoodCategory.entries.map { it.localLabel() }, FoodCategory.entries.indexOf(category), { category = FoodCategory.entries[it] })
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.food_pin_top), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
                FitSwitch(pinned, { pinned = it })
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete), color = c.coral) }
                Spacer(Modifier.weight(1f))
                PillButton(
                    stringResource(R.string.action_save),
                    onClick = {
                        onSave(
                            food.copy(
                                name = name.trim(), kcal = kcal.toIntOrNull() ?: 0, protein = protein.toFloatOrNull() ?: 0f, carbs = carbs.toFloatOrNull() ?: 0f,
                                serving = serving.trim(), category = category, pinned = pinned, place = place.trim(),
                            ).withParts(parts)
                        )
                    },
                    enabled = name.isNotBlank() && (kcal.isNotBlank() || parts.any { it.on }),
                )
            }
        }
    }
    if (editParts) {
        PartsSheet(name.ifBlank { food.name }, parts, carbsOn, onSave = { parts = it; editParts = false }, onDismiss = { editParts = false })
    }
}

/**
 * Fills calories, protein and carbs from the food's name, so nobody has to know them to log a meal. Without an AI key
 * it says where the numbers can come from instead.
 */
@Composable
private fun EstimateButton(name: String, estimate: (suspend (String) -> com.forgequest.app.data.FoodScan)?, onResult: (Int, String, String) -> Unit) {
    val c = Fit.colors
    if (estimate == null) {
        Text(stringResource(R.string.food_estimate_needs_key), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 6.dp))
        return
    }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // What the guess was based on, so a wrong match ("2 scrambled eggs" read as a bigger meal) is easy to spot.
    var basis by remember { mutableStateOf<String?>(null) }
    val fallback = stringResource(R.string.scan_err_unreadable)
    TextButton(
        onClick = {
            busy = true
            error = null
            scope.launch {
                runCatching { estimate(name.trim()) }
                    .onSuccess { scan ->
                        basis = scan.items.joinToString(", ") { if (it.grams > 0) "${it.name} (${it.grams} g)" else it.name }
                        fun num(v: Float) = (kotlin.math.round(v * 10) / 10).toString().removeSuffix(".0")
                        onResult(scan.items.sumOf { it.kcal }, num(scan.items.map { it.protein }.sum()), num(scan.items.map { it.carbs }.sum()))
                    }
                    .onFailure { e -> error = (e as? com.forgequest.app.LocalizedException)?.userMessage ?: fallback }
                busy = false
            }
        },
        enabled = name.isNotBlank() && !busy,
    ) {
        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = c.purple, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(if (busy) R.string.food_estimating else R.string.food_estimate), color = c.purple)
    }
    error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.coral) }
    basis?.let { Text(stringResource(R.string.food_estimate_basis, it), style = MaterialTheme.typography.bodySmall, color = c.textSecondary) }
}
