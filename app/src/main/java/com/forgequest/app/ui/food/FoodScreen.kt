package com.forgequest.app.ui.food

import androidx.compose.ui.semantics.Role
import com.forgequest.app.ui.theme.ForgeSpace
import com.forgequest.app.ui.theme.ForgeShape
import com.forgequest.app.ui.theme.tabular
import com.forgequest.app.ui.theme.itemMotion
import com.forgequest.app.domain.Nutrition
import com.forgequest.app.ui.components.ChoiceChip
import com.forgequest.app.ui.components.FitSwitch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import com.forgequest.app.R
import com.forgequest.app.ui.localLabel

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Search
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ModalBottomSheet
import com.forgequest.app.ui.components.rememberFullSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.forgequest.app.data.bridge.GeminiMeal
import com.forgequest.app.data.ProgressSnapshot
import com.forgequest.app.data.db.FoodCategory
import com.forgequest.app.data.db.FoodEntity
import com.forgequest.app.data.db.withParts
import com.forgequest.app.domain.FoodLists
import com.forgequest.app.domain.FoodSearch
import com.forgequest.app.data.db.MealEntity
import com.forgequest.app.data.db.SCANNED_NO_PHOTO
import com.forgequest.app.domain.Dates
import com.forgequest.app.ui.LocalSnackbar
import com.forgequest.app.ui.Routes
import com.forgequest.app.ui.appViewModel
import com.forgequest.app.ui.components.SegmentedTabs
import com.forgequest.app.ui.components.FitCard
import com.forgequest.app.ui.components.UndoSnackbarEffect
import com.forgequest.app.ui.components.IconCircle
import com.forgequest.app.ui.components.PillButton
import com.forgequest.app.ui.components.ProgressLine
import com.forgequest.app.ui.components.NumberEntryDialog
import com.forgequest.app.ui.components.Stepper
import com.forgequest.app.ui.components.SectionTitle
import com.forgequest.app.ui.components.TonalPill
import com.forgequest.app.ui.components.SwipeToDelete
import com.forgequest.app.ui.theme.Fit
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** How many logged meals show before "Show all". */
private const val MEALS_SHOWN = 3

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
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard = com.forgequest.app.ui.components.rememberTextClipboard()
    var editing by remember { mutableStateOf<FoodEntity?>(null) }
    var photoFor by remember { mutableStateOf<FoodEntity?>(null) }

    val geminiMeals by vm.geminiMeals.collectAsState()
    val apiKey by vm.apiKey.collectAsState()
    val aiOn by vm.aiOn.collectAsState()
    val day by vm.day.collectAsState()
    var confirmDeleteFood by remember { mutableStateOf<FoodEntity?>(null) }
    var quickLog by remember { mutableStateOf(false) }
    var catalogPick by remember { mutableStateOf<com.forgequest.app.domain.CatalogFood?>(null) }
    var scanningBarcode by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var mealMenu by remember { mutableStateOf(false) }
    var searchFocused by remember { mutableStateOf(false) }
    var showWeek by rememberSaveable { mutableStateOf(false) }
    var showAllMeals by rememberSaveable(day) { mutableStateOf(false) }
    val newestMeals = remember(meals) { meals.asReversed() }
    val catalogResults by vm.catalogResults.collectAsState()
    val isToday = day == Dates.today()
    LaunchedEffect(Unit) { vm.onShown() }

    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value = null } }
    UndoSnackbarEffect(vm.undo)

    val query by vm.query.collectAsState()
    val usage by vm.usage.collectAsState()
    val catalogReady by vm.catalogReady.collectAsState()
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
    val recent = remember(foods, usage, category) { FoodLists.recent(foods, usage, category) }
    val selectedFoods = foods.filter { it.id in selection }
    // The chip tray: this meal's pinned and most-logged foods, then what was logged lately under other meals.
    val quickPicks = remember(foods, usage, category, recent) {
        (inCategory.sortedWith(compareByDescending<FoodEntity> { it.pinned }.thenByDescending { usage[it.id]?.times ?: 0 }.thenByDescending { usage[it.id]?.lastAt ?: 0L }).take(8) + recent)
            .distinctBy { it.id }.take(12)
    }
    var browseAll by rememberSaveable(category) { mutableStateOf(false) }
    // Each food rounded on its own, as logFoods stores it, so this is what the day's total gains.
    val selKcal = selectedFoods.sumOf { Nutrition.scaledKcal(it.kcal, selection[it.id]?.qty ?: 1f) }
    val selProtein = selectedFoods.sumOf { (it.protein * (selection[it.id]?.qty ?: 1f)).toDouble() }.roundToInt()

    Box(Modifier.fillMaxSize().background(c.background)) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = ForgeSpace.Gutter, end = ForgeSpace.Gutter, bottom = if (selection.isEmpty()) 24.dp else 96.dp),
            verticalArrangement = Arrangement.spacedBy(ForgeSpace.CardGap),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.food_title), style = MaterialTheme.typography.headlineMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
                    // One menu for the occasional things; scanning lives inside the search field.
                    Box {
                        IconButton(onClick = { moreMenu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.cd_more), tint = c.textSecondary) }
                        DropdownMenu(moreMenu, { moreMenu = false }, containerColor = c.cardHigh) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.food_add), color = c.textPrimary) },
                                leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, tint = c.textSecondary) },
                                onClick = {
                                    moreMenu = false
                                    editing = FoodEntity(name = "", category = category, kcal = 0, protein = 0f, createdAt = System.currentTimeMillis())
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.food_quick_log), color = c.textPrimary) },
                                leadingIcon = { Icon(Icons.Rounded.Restaurant, contentDescription = null, tint = c.textSecondary) },
                                onClick = { moreMenu = false; quickLog = true },
                            )
                            // The Gemini app round trip, open with the coach off: copy the skill once, then paste each meal answer.
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.food_paste_meal), color = c.textPrimary) },
                                leadingIcon = { Icon(Icons.Rounded.ContentPaste, contentDescription = null, tint = c.textSecondary) },
                                onClick = { moreMenu = false; clipboard.readThen { vm.pasteMeal(it) } },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.food_copy_skill), color = c.textPrimary) },
                                leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = c.textSecondary) },
                                onClick = {
                                    moreMenu = false
                                    clipboard.copy(com.forgequest.app.ui.components.SkillPrompts.food(context, snap?.settings?.handLengthCm ?: 0f, snap?.settings?.palmWidthCm ?: 0f))
                                    vm.message.value = context.getString(R.string.skill_copied)
                                },
                            )
                            if (isToday) DropdownMenuItem(
                                text = { Text(stringResource(R.string.food_last_7_days), color = c.textPrimary) },
                                leadingIcon = { Icon(Icons.Rounded.Check, contentDescription = null, tint = if (showWeek) c.teal else androidx.compose.ui.graphics.Color.Transparent) },
                                onClick = { moreMenu = false; showWeek = !showWeek },
                            )
                        }
                    }
                }
            }
            item {
                DayPicker(day, isToday, onPrev = { vm.shiftDay(-1) }, onNext = { vm.shiftDay(1) }, onToday = vm::goToToday)
            }
            if (geminiMeals.isNotEmpty()) item { GeminiInbox(geminiMeals, onOpen = vm::reviewMeal, onDismiss = { vm.dismissMeal(it) }) }
            snap?.let { s -> item { DayTotals(s, meals) } }
            if (isToday && showWeek) snap?.let { s -> item { WeekCard(s) } }
            // What is already logged sits right under the totals it adds up to: the latest three, the rest one tap away.
            item {
                SectionTitle(if (isToday) stringResource(R.string.food_logged_today) else stringResource(R.string.food_logged_that_day))
            }
            if (meals.isEmpty()) {
                item { Text(if (isToday) stringResource(R.string.food_nothing_today) else stringResource(R.string.food_nothing_that_day), style = MaterialTheme.typography.bodySmall, color = c.textSecondary) }
            }
            items(if (showAllMeals) newestMeals else newestMeals.take(MEALS_SHOWN), key = { "m${it.id}" }) { meal ->
                SwipeToDelete(onDelete = { vm.deleteMeal(meal) }, modifier = itemMotion()) {
                    MealRow(meal, onAgain = { vm.logAgain(meal) }, onDelete = { vm.deleteMeal(meal) })
                }
            }
            if (meals.size > MEALS_SHOWN) {
                item {
                    TextButton(onClick = { showAllMeals = !showAllMeals }) {
                        Text(if (showAllMeals) stringResource(R.string.food_show_less) else stringResource(R.string.food_show_all, meals.size), color = c.teal)
                    }
                }
            }
            item {
                com.forgequest.app.ui.components.CoachMarkBox("coach_food", R.string.coach_food_t, if (aiOn) R.string.coach_food_b else R.string.coach_food_b_noai, below = true) {
                    SearchField(
                        query, onChange = { vm.query.value = it }, onDone = { focus.clearFocus() },
                        onScan = if (aiOn) ({ nav.navigate(Routes.SCAN) }) else null, onBarcode = { scanningBarcode = true }, onFocus = { searchFocused = it },
                    )
                }
            }
            // Restaurant shortcuts appear the moment the user is about to search.
            if (searchFocused && !searching && recentPlaces.isNotEmpty()) {
                item { RestaurantShortcuts(recentPlaces, onPick = { vm.query.value = it; focus.clearFocus() }) }
            }
            item {
                val withSelection = selection.values.map { it.category }.toSet()
                Box {
                    TonalPill(stringResource(R.string.food_meal_chip, category.localLabel()), onClick = { mealMenu = true }, icon = Icons.Rounded.ArrowDropDown)
                    DropdownMenu(mealMenu, { mealMenu = false }, containerColor = c.cardHigh) {
                        FoodCategory.entries.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.localLabel(), color = c.textPrimary) },
                                leadingIcon = { Icon(Icons.Rounded.Check, contentDescription = null, tint = if (cat == category) c.teal else androidx.compose.ui.graphics.Color.Transparent) },
                                trailingIcon = if (cat in withSelection) ({ Box(Modifier.size(8.dp).clip(CircleShape).background(c.teal)) }) else null,
                                onClick = { mealMenu = false; vm.category.value = cat },
                            )
                        }
                    }
                }
            }
            if (searching) {
                item {
                    Text(
                        when {
                            results.isNotEmpty() || catalogResults.isNotEmpty() -> stringResource(R.string.food_search_logs_as, category.localLabel())
                            !catalogReady -> stringResource(R.string.food_list_loading)
                            else -> stringResource(R.string.food_search_none, query.trim())
                        },
                        style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            } else if (inCategory.isEmpty() && recent.isEmpty()) {
                item {
                    FitCard {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            IconCircle(Icons.Rounded.Restaurant, c.teal, c.tealTrack, 56.dp)
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.food_none_in_category, category.localLabel()), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(R.string.food_empty_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
            if (searching) {
                items(results, key = { it.id }) { food ->
                    Box(itemMotion()) {
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
                }
            } else {
                // The usual foods as one row of chips: a tap ticks one. The long lists stay folded until asked for.
                if (quickPicks.isNotEmpty()) {
                    item { QuickPickChips(quickPicks, selection.keys, onToggle = { vm.toggle(it) }) }
                }
                // Ticked foods get their full row, which is where the amount is changed.
                items(selectedFoods, key = { it.id }) { food ->
                    Box(itemMotion()) {
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
                }
                if (inCategory.isNotEmpty()) {
                    if (browseAll || searchFocused) {
                        items(inCategory.filter { it.id !in selection }, key = { "b${it.id}" }) { food ->
                            CompactFoodRow(food, onToggle = { vm.toggle(food) }, onEdit = { editing = food })
                        }
                    } else {
                        item { TonalPill(stringResource(R.string.food_browse_all, inCategory.size), onClick = { browseAll = true }, icon = Icons.Rounded.ArrowDropDown) }
                    }
                }
            }
            if (searching && catalogResults.isNotEmpty()) {
                item {
                    val byProtein by vm.sortProtein.collectAsState()
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.food_from_list), style = MaterialTheme.typography.titleSmall, color = c.textPrimary, modifier = Modifier.weight(1f).padding(start = 4.dp))
                        FilterChip(selected = byProtein, onClick = { vm.sortProtein.value = !byProtein }, label = { Text(stringResource(R.string.food_sort_protein)) })
                    }
                }
                items(catalogResults, key = { "c${it.id}" }) { item -> CatalogRow(item, onClick = { focus.clearFocus(); catalogPick = item }) }
            }
            if (searching && results.isEmpty()) {
                item {
                    TonalPill(stringResource(R.string.food_search_add, query.trim()), onClick = {
                        editing = FoodEntity(name = query.trim().take(48), category = category, kcal = 0, protein = 0f, createdAt = System.currentTimeMillis())
                    }, icon = Icons.Rounded.Add)
                }
            }
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

    if (scanningBarcode) {
        BarcodeScanScreen(onClose = { scanningBarcode = false }, onOwn = { vm.logOwn(it); scanningBarcode = false }, onLog = { food, amount, label, code -> vm.logCatalog(food, amount, label, code); scanningBarcode = false })
    }
    catalogPick?.let { item ->
        CatalogAmountSheet(item, onLog = { amount, label -> vm.logCatalog(item, amount, label); catalogPick = null; vm.query.value = "" }, onDismiss = { catalogPick = null })
    }
    if (quickLog) {
        val recentOneOffs by vm.recentOneOffs.collectAsState()
        QuickLogSheet(category, recent = recentOneOffs, carbsOn = snap?.settings?.trackCarbs == true, estimate = if (aiOn && apiKey.isNotBlank()) vm::estimate else null, onLog = { n, cat, k, p, cb -> vm.logOnce(n, cat, k, p, cb); quickLog = false }, onDismiss = { quickLog = false })
    }
    partsFor?.let { food ->
        PartsSheet(food.name, food.parts, carbsOn = snap?.settings?.trackCarbs == true, onSave = { vm.saveParts(food, it); partsFor = null }, onDismiss = { partsFor = null })
    }
    editing?.let { food ->
        FoodEditorSheet(food, places, carbsOn = snap?.settings?.trackCarbs == true, estimate = if (aiOn && apiKey.isNotBlank()) vm::estimate else null, onSave = { vm.save(it); editing = null }, onDelete = if (food.id != 0L) ({ confirmDeleteFood = food; editing = null }) else null, onPhoto = if (food.id != 0L) ({ photoFor = food; editing = null }) else null, onDismiss = { editing = null })
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
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = c.teal, modifier = Modifier.size(20.dp))
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
                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(ForgeShape.Md).background(c.cardHigh)
                    .clickable { onOpen(meal) }.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
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
        // The whole centre is the "back to today" target on another day, not just the small teal line.
        Column(
            Modifier.weight(1f).clip(ForgeShape.Sm).clickable(enabled = !isToday, role = Role.Button, onClick = onToday).heightIn(min = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            if (!isToday) {
                Text(stringResource(R.string.food_back_to_today), style = MaterialTheme.typography.labelMedium, color = c.teal, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
            }
        }
        IconButton(onClick = onNext, enabled = !isToday) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = stringResource(R.string.cd_next_day), tint = if (isToday) c.divider else c.textPrimary)
        }
    }
}

@Composable
private fun DayTotals(s: ProgressSnapshot, meals: List<MealEntity>) {
    val c = Fit.colors
    val kcal = com.forgequest.app.ui.components.tweenedInt(meals.sumOf { it.kcal })
    val protein = meals.sumOf { it.protein.toDouble() }.toFloat()
    val proteinShown = com.forgequest.app.ui.components.tweenedInt(protein.roundToInt())
    val over = kcal > s.settings.kcalGoal
    FitCard {
        Row {
            Column(Modifier.weight(1f)) {
                val proteinLeft = (s.settings.proteinGoal - proteinShown).coerceAtLeast(0)
                Text(
                    if (proteinLeft > 0) stringResource(R.string.food_protein_left, proteinLeft) else stringResource(R.string.food_protein_met),
                    style = MaterialTheme.typography.titleMedium.tabular(), color = c.gold,
                )
                Text("⁦%d / %d⁩ ".format(proteinShown, s.settings.proteinGoal) + stringResource(R.string.food_unit_g_short), style = MaterialTheme.typography.labelSmall.tabular(), color = c.textSecondary)
                Spacer(Modifier.height(8.dp))
                ProgressLine(protein / s.settings.proteinGoal.coerceAtLeast(1), c.gold, c.goldTrack, height = 5.dp)
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                val diff = "⁦%,d⁩".format(kotlin.math.abs(s.settings.kcalGoal - kcal))
                Text(
                    if (over) stringResource(R.string.food_kcal_over, diff) else stringResource(R.string.food_kcal_left, diff),
                    style = MaterialTheme.typography.titleMedium.tabular(), color = if (over) c.coral else c.blue,
                )
                Text("⁦%,d / %,d⁩".format(kcal, s.settings.kcalGoal), style = MaterialTheme.typography.labelSmall.tabular(), color = c.textSecondary)
                Spacer(Modifier.height(8.dp))
                ProgressLine(kcal.toFloat() / s.settings.kcalGoal.coerceAtLeast(1), if (over) c.coral else c.blue, if (over) c.coralTrack else c.blueTrack, height = 5.dp)
            }
        }
        if (s.settings.trackCarbs) {
            val carbs = meals.sumOf { it.carbs.toDouble() }.toFloat()
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.food_carbs_of_goal, carbs.roundToInt(), s.settings.carbsGoal), style = MaterialTheme.typography.titleSmall.tabular(), color = c.purple)
            Text(stringResource(R.string.label_carbs), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
            Spacer(Modifier.height(8.dp))
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
    FitCard {
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            days.forEach { d ->
                val n = byDay[d]
                val ratio = ((n?.protein ?: 0f) / s.settings.proteinGoal.coerceAtLeast(1)).coerceIn(0f, 1f)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(32.dp).clip(ForgeShape.Sm).background(c.tealTrack), contentAlignment = Alignment.BottomCenter) {
                        if (n != null) Box(Modifier.fillMaxWidth().height((32 * ratio).dp.coerceAtLeast(if (ratio > 0f) 3.dp else 0.dp)).background(if (n.protein >= s.settings.proteinGoal) c.teal else c.teal.copy(alpha = 0.55f)))
                    }
                    // At 200% text a 3-letter day no longer fits a seventh of the card: shrink it instead of cutting it.
                    Text(
                        Dates.dayShort(Dates.dayOfWeek(d)), maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall,
                        color = if (d == today) c.textPrimary else c.textSecondary,
                        autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = MaterialTheme.typography.labelSmall.fontSize),
                    )
                }
            }
        }
    }
}

/** Search over your own saved foods and their restaurants (FoodSearch: spelling variants, Arabic/English, typos). */
@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, onDone: () -> Unit, onScan: (() -> Unit)?, onBarcode: () -> Unit, onFocus: (Boolean) -> Unit) {
    val c = Fit.colors
    OutlinedTextField(
        query, { onChange(it.take(48).replace("\n", "")) },
        placeholder = { Text(stringResource(R.string.food_search_hint), color = c.textSecondary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = c.textSecondary) },
        trailingIcon = if (query.isNotEmpty()) ({
            IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.food_search_clear), tint = c.textSecondary) }
        }) else ({
            Row {
                if (onScan != null) IconButton(onClick = onScan) { Icon(Icons.Rounded.CameraAlt, contentDescription = stringResource(R.string.food_scan_meal), tint = c.teal) }
                IconButton(onClick = onBarcode) { Icon(Icons.Rounded.QrCodeScanner, contentDescription = stringResource(R.string.barcode_scan), tint = c.textSecondary) }
            }
        }),
        singleLine = true, shape = ForgeShape.Lg,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onDone() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.teal, unfocusedBorderColor = c.divider, focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary,
            focusedContainerColor = c.card, unfocusedContainerColor = c.card,
        ),
        modifier = Modifier.fillMaxWidth().onFocusChanged { onFocus(it.isFocused) },
    )
}

/** One-tap restaurant shortcuts under the search box: a tap shows that restaurant's foods. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RestaurantShortcuts(places: List<String>, onPick: (String) -> Unit) {
    val c = Fit.colors
    Column {
        Text(stringResource(R.string.food_restaurants), style = MaterialTheme.typography.labelLarge, color = c.textSecondary, modifier = Modifier.padding(horizontal = 4.dp))
        Spacer(Modifier.height(8.dp))
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
    ModalBottomSheet(sheetState = rememberFullSheetState(), onDismissRequest = onDismiss, containerColor = c.card) {
        Column(Modifier.padding(horizontal = ForgeSpace.Gutter).padding(bottom = 32.dp)) {
            Text(stringResource(R.string.food_quick_log), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
            Text(stringResource(R.string.food_quick_log_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            // Recent one-offs fill the form in one tap (MacroFactor and MyFitnessPal lead with recents).
            if (recent.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.food_recent), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    recent.forEach { m ->
                        ChoiceChip("${m.name} · ${m.kcal}", m.name == name, {
                            name = m.name; kcal = m.kcal.toString(); protein = m.protein.toString().removeSuffix(".0"); carbs = if (m.carbs > 0f) m.carbs.toString().removeSuffix(".0") else ""
                        })
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(name, { name = it.take(48) }, label = { Text(stringResource(R.string.food_what_was_it)) }, singleLine = true, shape = ForgeShape.Md, colors = fieldColors, modifier = Modifier.fillMaxWidth())
            EstimateButton(name, estimate) { k, p, cb -> kcal = k.toString(); protein = p; carbs = cb }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(kcal, { kcal = it.filter(Char::isDigit).take(5) }, label = { Text(stringResource(R.string.home_legend_calories)) }, singleLine = true, shape = ForgeShape.Md, colors = fieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                OutlinedTextField(protein, { protein = it.filter { ch -> ch.isDigit() || ch == '.' }.take(5) }, label = { Text(stringResource(R.string.home_legend_protein)) }, singleLine = true, shape = ForgeShape.Md, colors = fieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            }
            if (carbsOn) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(carbs, { carbs = it.filter { ch -> ch.isDigit() || ch == '.' }.take(5) }, label = { Text(stringResource(R.string.label_carbs_g)) }, singleLine = true, shape = ForgeShape.Md, colors = fieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(12.dp))
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

/** The usual foods as chips in one scrolling row; a tap ticks or unticks one. */
@Composable
private fun QuickPickChips(foods: List<FoodEntity>, selected: Set<Long>, onToggle: (FoodEntity) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(foods, key = { it.id }) { food -> ChoiceChip(food.name, selected = food.id in selected, onClick = { onToggle(food) }) }
    }
}

/** One line per food in "Browse all": a tap ticks it, a long press edits it. Grows with large text, never clips. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactFoodRow(food: FoodEntity, onToggle: () -> Unit, onEdit: () -> Unit) {
    val c = Fit.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(ForgeShape.Md).background(c.card)
            .combinedClickable(onClick = onToggle, onLongClick = onEdit, onClickLabel = stringResource(R.string.cd_select), onLongClickLabel = stringResource(R.string.cd_edit))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(food.name, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.food_kcal, food.kcal) + " · " + stringResource(R.string.food_g_protein, food.protein.roundToInt()),
            style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FoodRow(food: FoodEntity, qty: Float?, onToggle: () -> Unit, onQty: (Float) -> Unit, onPhoto: () -> Unit, onEdit: () -> Unit, onParts: (() -> Unit)?) {
    val c = Fit.colors
    val selected = qty != null
    val bg by animateColorAsState(if (selected) c.tealTrack else c.card, label = "bg")
    Row(
        Modifier.fillMaxWidth().clip(ForgeShape.Md).background(bg)
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
        // Only foods that have a photo show one; adding a photo lives in the editor (long-press the row).
        if (food.photoPath != null) {
            Box(Modifier.size(48.dp).clip(ForgeShape.Sm).background(c.cardHigh).clickable(onClick = onPhoto), contentAlignment = Alignment.Center) {
                AsyncImage(model = File(food.photoPath), contentDescription = food.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (food.pinned) {
                    Icon(Icons.Rounded.PushPin, contentDescription = stringResource(R.string.cd_pinned), tint = c.gold, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(food.name, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                    modifier = Modifier.padding(top = 2.dp).clip(ForgeShape.Sm).clickable(onClick = onParts).padding(vertical = 4.dp),
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
private fun MealRow(meal: MealEntity, onAgain: () -> Unit, onDelete: () -> Unit) {
    val c = Fit.colors
    val time = Instant.ofEpochMilli(meal.loggedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
    Row(
        Modifier.fillMaxWidth().clip(ForgeShape.Md).background(c.card).padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (meal.photoPath != null && meal.photoPath != SCANNED_NO_PHOTO) {
            AsyncImage(File(meal.photoPath), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(36.dp).clip(ForgeShape.Sm))
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
        IconButton(onClick = onAgain) { Icon(Icons.Rounded.Replay, contentDescription = stringResource(R.string.food_log_again), tint = c.textSecondary, modifier = Modifier.size(18.dp)) }
        IconButton(onClick = onDelete) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_remove), tint = c.textSecondary, modifier = Modifier.size(18.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoSourceSheet(onUri: (Uri) -> Unit, onBitmap: (Bitmap) -> Unit, onRemove: (() -> Unit)?, onDismiss: () -> Unit) {
    val c = Fit.colors
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) onUri(uri) else onDismiss() }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp -> if (bmp != null) onBitmap(bmp) else onDismiss() }
    ModalBottomSheet(sheetState = rememberFullSheetState(), onDismissRequest = onDismiss, containerColor = c.card) {
        Column(Modifier.padding(horizontal = ForgeSpace.Gutter).padding(bottom = 32.dp)) {
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
    Row(Modifier.fillMaxWidth().clip(ForgeShape.Sm).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = c.teal)
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoodEditorSheet(food: FoodEntity, places: List<String>, carbsOn: Boolean, estimate: (suspend (String) -> com.forgequest.app.data.FoodScan)?, onSave: (FoodEntity) -> Unit, onDelete: (() -> Unit)?, onPhoto: (() -> Unit)?, onDismiss: () -> Unit) {
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
    ModalBottomSheet(sheetState = rememberFullSheetState(), onDismissRequest = onDismiss, containerColor = c.card) {
        Column(Modifier.padding(horizontal = ForgeSpace.Gutter).padding(bottom = 32.dp)) {
            Text(if (food.id == 0L) stringResource(R.string.food_add_title) else stringResource(R.string.food_edit_title), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(name, { name = it.take(48) }, label = { Text(stringResource(R.string.label_name)) }, singleLine = true, shape = ForgeShape.Md, colors = fieldColors, modifier = Modifier.fillMaxWidth())
            if (parts.isNotEmpty()) {
                // With parts, the numbers come from the ticked parts: change a part's grams instead of typing totals.
                val on = parts.filter { it.on }
                Spacer(Modifier.height(12.dp))
                Column(Modifier.fillMaxWidth().clip(ForgeShape.Md).background(c.cardHigh).clickable { editParts = true }.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(
                        listOf(stringResource(R.string.food_kcal, on.sumOf { it.kcal }), stringResource(R.string.food_g_protein, on.sumOf { it.protein.toDouble() }.roundToInt())).joinToString(" · "),
                        style = MaterialTheme.typography.bodyLarge, color = c.textPrimary,
                    )
                    Text(partsCount(parts) + " · " + stringResource(R.string.food_parts_change), style = MaterialTheme.typography.labelMedium, color = c.teal)
                }
            } else {
                EstimateButton(name, estimate) { k, p, cb -> kcal = k.toString(); protein = p; carbs = cb }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(kcal, { kcal = it.filter(Char::isDigit).take(5) }, label = { Text(stringResource(R.string.home_legend_calories)) }, singleLine = true, shape = ForgeShape.Md, colors = fieldColors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(protein, { protein = it.filter { ch -> ch.isDigit() || ch == '.' }.take(5) }, label = { Text(stringResource(R.string.home_legend_protein)) }, singleLine = true, shape = ForgeShape.Md, colors = fieldColors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
                if (carbsOn) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(carbs, { carbs = it.filter { ch -> ch.isDigit() || ch == '.' }.take(5) }, label = { Text(stringResource(R.string.label_carbs_g)) }, singleLine = true, shape = ForgeShape.Md, colors = fieldColors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(12.dp))
            PlaceField(place, { place = it }, places)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(serving, { serving = it.take(32) }, label = { Text(stringResource(R.string.food_serving_hint)) }, singleLine = true, shape = ForgeShape.Md, colors = fieldColors, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            SegmentedTabs(FoodCategory.entries.map { it.localLabel() }, FoodCategory.entries.indexOf(category), { category = FoodCategory.entries[it] })
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.food_pin_top), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
                FitSwitch(pinned, { pinned = it })
            }
            if (onPhoto != null) {
                Spacer(Modifier.height(12.dp))
                TonalPill(stringResource(if (food.photoPath != null) R.string.food_photo_change else R.string.cd_add_photo), onClick = onPhoto, icon = Icons.Rounded.PhotoCamera)
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
    if (estimate == null) return
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
        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = c.teal, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(if (busy) R.string.food_estimating else R.string.food_estimate), color = c.purple)
    }
    error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.coral) }
    basis?.let { Text(stringResource(R.string.food_estimate_basis, it), style = MaterialTheme.typography.bodySmall, color = c.textSecondary) }
}
