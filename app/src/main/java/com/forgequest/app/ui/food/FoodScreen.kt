package com.forgequest.app.ui.food

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
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Restaurant
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
import com.forgequest.app.data.ProgressSnapshot
import com.forgequest.app.data.db.FoodCategory
import com.forgequest.app.data.db.FoodEntity
import com.forgequest.app.data.db.MealEntity
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@OptIn(ExperimentalCoroutinesApi::class)
class FoodViewModel(val app: AppContainer) : ViewModel() {
    val foods: StateFlow<List<FoodEntity>> = app.food.foods.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val meals: StateFlow<List<MealEntity>> = app.food.mealsOn(Dates.today()).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val snapshot: StateFlow<ProgressSnapshot?> = app.progress.snapshot.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val category = MutableStateFlow(app.food.categoryForNow())

    /** foodId → quantity for items ticked but not yet logged. */
    val selection = MutableStateFlow<Map<Long, Float>>(emptyMap())
    val message = MutableStateFlow<String?>(null)

    fun toggle(food: FoodEntity) {
        app.haptics.tick()
        selection.update { if (food.id in it) it - food.id else it + (food.id to 1f) }
    }

    fun setQty(food: FoodEntity, qty: Float) {
        app.haptics.tick()
        selection.update { if (qty <= 0f) it - food.id else it + (food.id to qty) }
    }

    fun logSelected() {
        val foodsById = foods.value.associateBy { it.id }
        val picked = selection.value.mapNotNull { (id, q) -> foodsById[id]?.let { it to q } }
        if (picked.isEmpty()) return
        viewModelScope.launch {
            app.food.logFoods(picked)
            selection.value = emptyMap()
            app.haptics.setDone()
            val badges = app.progress.evaluateBadges()
            message.value = "Logged ${picked.size} item${if (picked.size > 1) "s" else ""}" +
                if (badges.isNotEmpty()) " · Badge unlocked: ${badges.first()}" else ""
        }
    }

    fun save(food: FoodEntity) = viewModelScope.launch { app.food.saveFood(food) }
    fun delete(food: FoodEntity) = viewModelScope.launch { app.food.deleteFood(food.id); selection.update { it - food.id } }
    /** Deleted right away; [undo] puts it back with the same id. */
    val undo = MutableStateFlow<Pair<String, suspend () -> Unit>?>(null)

    fun deleteMeal(meal: MealEntity) = viewModelScope.launch {
        app.food.deleteMeal(meal.id)
        undo.value = "Removed ${meal.name}" to { app.food.restoreMeal(meal) }
    }

    fun runUndo() = viewModelScope.launch { undo.value?.second?.invoke(); undo.value = null }

    fun setPhotoFromUri(food: FoodEntity, uri: Uri) = viewModelScope.launch {
        runCatching { app.food.importPhoto(uri) }.onSuccess { app.food.saveFood(food.copy(photoPath = it)) }
            .onFailure { message.value = "Couldn't use that photo." }
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
    var confirmDeleteFood by remember { mutableStateOf<FoodEntity?>(null) }

    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value = null } }
    LaunchedEffect(undo) {
        undo?.let { (text, _) ->
            val result = snackbar.showSnackbar(text, actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) vm.runUndo() else if (vm.undo.value?.first == text) vm.undo.value = null
        }
    }

    val inCategory = foods.filter { it.category == category }
    val selectedFoods = foods.filter { it.id in selection }
    val selKcal = selectedFoods.sumOf { (it.kcal * (selection[it.id] ?: 1f)).toDouble() }.roundToInt()
    val selProtein = selectedFoods.sumOf { (it.protein * (selection[it.id] ?: 1f)).toDouble() }.roundToInt()

    Box(Modifier.fillMaxSize().background(c.background)) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = if (selection.isEmpty()) 24.dp else 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Food", style = MaterialTheme.typography.headlineMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
                    PillButton("Scan meal", onClick = { nav.navigate(Routes.SCAN) }, icon = Icons.Rounded.CameraAlt)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(44.dp).clip(CircleShape).background(c.cardHigh).clickable {
                        editing = FoodEntity(name = "", category = category, kcal = 0, protein = 0f, createdAt = System.currentTimeMillis())
                    }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add food", tint = c.textPrimary)
                    }
                }
            }
            snap?.let { s -> item { TodayTotals(s) } }
            item {
                val withSelection = FoodCategory.entries.indices.filter { i -> foods.any { it.category == FoodCategory.entries[i] && it.id in selection } }.toSet()
                SegmentedTabs(
                    FoodCategory.entries.map { it.label }, FoodCategory.entries.indexOf(category),
                    onSelect = { vm.category.value = FoodCategory.entries[it] }, dots = withSelection,
                )
            }
            if (inCategory.isEmpty()) {
                item {
                    FitCard {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            IconCircle(Icons.Rounded.Restaurant, c.teal, c.tealTrack, 56.dp)
                            Spacer(Modifier.height(12.dp))
                            Text("No ${category.label.lowercase()} foods yet", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                            Spacer(Modifier.height(4.dp))
                            Text("Scan a meal or add the foods you eat often. Next time it's one tap.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(14.dp))
                            TonalPill("Add a food", onClick = {
                                editing = FoodEntity(name = "", category = category, kcal = 0, protein = 0f, createdAt = System.currentTimeMillis())
                            }, icon = Icons.Rounded.Add)
                        }
                    }
                }
            }
            items(inCategory, key = { it.id }) { food ->
                FoodRow(
                    food = food,
                    qty = selection[food.id],
                    onToggle = { vm.toggle(food) },
                    onQty = { vm.setQty(food, it) },
                    onPhoto = { photoFor = food },
                    onEdit = { editing = food },
                )
            }
            if (meals.isNotEmpty()) {
                item {
                    Text("Logged today", style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.padding(top = 16.dp))
                }
                items(meals, key = { "m${it.id}" }) { meal -> MealRow(meal, onDelete = { vm.deleteMeal(meal) }) }
            }
        }

        AnimatedVisibility(
            selection.isNotEmpty(),
            enter = slideInVertically { it }, exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Box(Modifier.fillMaxWidth().background(c.background).padding(16.dp)) {
                PillButton(
                    "Log ${selection.size} item${if (selection.size > 1) "s" else ""} · $selKcal kcal · $selProtein g protein",
                    onClick = vm::logSelected,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    editing?.let { food ->
        FoodEditorSheet(food, onSave = { vm.save(it); editing = null }, onDelete = if (food.id != 0L) ({ confirmDeleteFood = food; editing = null }) else null, onDismiss = { editing = null })
    }
    confirmDeleteFood?.let { food ->
        AlertDialog(
            onDismissRequest = { confirmDeleteFood = null },
            containerColor = c.card,
            title = { Text("Delete ${food.name}?", color = c.textPrimary) },
            text = { Text("It leaves your food list. Meals you already logged with it stay.", color = c.textSecondary) },
            confirmButton = { TextButton(onClick = { vm.delete(food); confirmDeleteFood = null }) { Text("Delete", color = c.coral) } },
            dismissButton = { TextButton(onClick = { confirmDeleteFood = null }) { Text("Cancel", color = c.textSecondary) } },
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

@Composable
private fun TodayTotals(s: ProgressSnapshot) {
    val c = Fit.colors
    val over = s.today.kcal > s.settings.kcalGoal
    FitCard(padding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)) {
        Row {
            Column(Modifier.weight(1f)) {
                Text("${s.today.protein.roundToInt()} / ${s.settings.proteinGoal} g", style = MaterialTheme.typography.titleSmall, color = c.teal)
                Text("Protein", style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                Spacer(Modifier.height(6.dp))
                ProgressLine(s.today.protein / s.settings.proteinGoal.coerceAtLeast(1), c.teal, c.tealTrack, height = 5.dp)
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text("%,d / %,d".format(s.today.kcal, s.settings.kcalGoal), style = MaterialTheme.typography.titleSmall, color = if (over) c.gold else c.blue)
                Text(if (over) "Calories · surplus" else "Calories", style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                Spacer(Modifier.height(6.dp))
                ProgressLine(s.today.kcal.toFloat() / s.settings.kcalGoal.coerceAtLeast(1), if (over) c.gold else c.blue, if (over) c.goldTrack else c.blueTrack, height = 5.dp)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FoodRow(food: FoodEntity, qty: Float?, onToggle: () -> Unit, onQty: (Float) -> Unit, onPhoto: () -> Unit, onEdit: () -> Unit) {
    val c = Fit.colors
    val selected = qty != null
    val bg by animateColorAsState(if (selected) c.tealTrack else c.card, label = "bg")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(bg)
            .combinedClickable(onClick = onToggle, onLongClick = onEdit)
            .padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(if (selected) c.teal else c.cardHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = c.onTeal, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(c.cardHigh).clickable(onClick = onPhoto), contentAlignment = Alignment.Center) {
            if (food.photoPath != null) {
                AsyncImage(model = File(food.photoPath), contentDescription = food.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Rounded.PhotoCamera, contentDescription = "Add photo", tint = c.textSecondary, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (food.pinned) {
                    Icon(Icons.Rounded.PushPin, contentDescription = "Pinned", tint = c.gold, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(food.name, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                listOfNotNull("${food.kcal} kcal", "${food.protein.roundToInt()} g protein", food.serving.ifBlank { null }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
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
                    title = "Servings of ${food.name}", initial = qty, unit = "×", decimals = 2, min = 0.25f, max = 20f,
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
        if (meal.photoPath != null) {
            AsyncImage(File(meal.photoPath), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)))
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                (if (meal.quantity != 1f) "${if (meal.quantity % 1f == 0f) meal.quantity.toInt() else meal.quantity}× " else "") + meal.name,
                style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text("$time · ${meal.category.label}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${meal.kcal} kcal", style = MaterialTheme.typography.labelLarge, color = c.blue)
            Text("${meal.protein.roundToInt()} g", style = MaterialTheme.typography.labelMedium, color = c.teal)
        }
        IconButton(onClick = onDelete) { Icon(Icons.Rounded.Close, contentDescription = "Remove", tint = c.textSecondary, modifier = Modifier.size(18.dp)) }
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
            Text("Food photo", style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
            Spacer(Modifier.height(12.dp))
            SheetAction(Icons.Rounded.PhotoLibrary, "Choose from gallery") { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            SheetAction(Icons.Rounded.PhotoCamera, "Take a photo") { camera.launch(null) }
            if (onRemove != null) SheetAction(Icons.Rounded.Close, "Remove photo", onRemove)
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
private fun FoodEditorSheet(food: FoodEntity, onSave: (FoodEntity) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    val c = Fit.colors
    var name by remember { mutableStateOf(food.name) }
    var kcal by remember { mutableStateOf(if (food.kcal == 0) "" else food.kcal.toString()) }
    var protein by remember { mutableStateOf(if (food.protein == 0f) "" else food.protein.toString().removeSuffix(".0")) }
    var serving by remember { mutableStateOf(food.serving) }
    var category by remember { mutableStateOf(food.category) }
    var pinned by remember { mutableStateOf(food.pinned) }
    val fieldColors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.teal, unfocusedBorderColor = c.divider, focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary, focusedLabelColor = c.teal, unfocusedLabelColor = c.textSecondary)
    ModalBottomSheet(sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), onDismissRequest = onDismiss, containerColor = c.card) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text(if (food.id == 0L) "Add food" else "Edit food", style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(name, { name = it.take(48) }, label = { Text("Name") }, singleLine = true, shape = RoundedCornerShape(16.dp), colors = fieldColors, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(kcal, { kcal = it.filter(Char::isDigit).take(5) }, label = { Text("Calories") }, singleLine = true, shape = RoundedCornerShape(16.dp), colors = fieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                OutlinedTextField(protein, { protein = it.filter { ch -> ch.isDigit() || ch == '.' }.take(5) }, label = { Text("Protein (g)") }, singleLine = true, shape = RoundedCornerShape(16.dp), colors = fieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(serving, { serving = it.take(32) }, label = { Text("Serving (e.g. 1 cup)") }, singleLine = true, shape = RoundedCornerShape(16.dp), colors = fieldColors, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            SegmentedTabs(FoodCategory.entries.map { it.label }, FoodCategory.entries.indexOf(category), { category = FoodCategory.entries[it] })
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Pin to top", style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
                Switch(pinned, { pinned = it }, colors = SwitchDefaults.colors(checkedTrackColor = c.teal, checkedThumbColor = c.card))
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = c.coral) }
                Spacer(Modifier.weight(1f))
                PillButton(
                    "Save",
                    onClick = {
                        onSave(food.copy(name = name.trim(), kcal = kcal.toIntOrNull() ?: 0, protein = protein.toFloatOrNull() ?: 0f, serving = serving.trim(), category = category, pinned = pinned))
                    },
                    enabled = name.isNotBlank() && kcal.isNotBlank(),
                )
            }
        }
    }
}
