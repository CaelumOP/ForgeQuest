package com.forgequest.app.ui.food

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forgequest.app.R
import com.forgequest.app.data.db.FoodPart
import com.forgequest.app.domain.FoodSearch
import com.forgequest.app.domain.Nutrition
import com.forgequest.app.ui.components.ChoiceChip
import com.forgequest.app.ui.components.NumberEntryDialog
import com.forgequest.app.ui.components.PillButton
import com.forgequest.app.ui.theme.Fit
import kotlin.math.roundToInt

/** "Parts: 4 of 5" for a food with parts. */
@Composable
fun partsCount(parts: List<FoodPart>): String = stringResource(R.string.food_parts_count, parts.count { it.on }, parts.size)

/** A part moved to [grams], its calories, protein and carbs scaled with it. */
fun FoodPart.withGrams(grams: Int): FoodPart {
    val factor = if (this.grams > 0) grams.toFloat() / this.grams else 1f
    return copy(grams = grams, kcal = Nutrition.scaledKcal(kcal, factor), protein = protein * factor, carbs = carbs * factor)
}

/**
 * Ticks for the parts of one food (bread, chicken, ketchup) and a tap on a part to set its grams. [onSave] gets the
 * parts back, unticked ones switched off rather than removed so they can be ticked again next time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartsSheet(foodName: String, initial: List<FoodPart>, carbsOn: Boolean, onSave: (List<FoodPart>) -> Unit, onDismiss: () -> Unit) {
    val c = Fit.colors
    var parts by remember { mutableStateOf(initial) }
    var editing by remember { mutableStateOf<Int?>(null) }
    val on = parts.filter { it.on }
    ModalBottomSheet(sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), onDismissRequest = onDismiss, containerColor = c.card) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.food_parts_title, foodName), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.food_parts_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.food_kcal, on.sumOf { it.kcal }), style = MaterialTheme.typography.titleMedium, color = c.blue)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.food_g_protein, on.sumOf { it.protein.toDouble() }.roundToInt()), style = MaterialTheme.typography.labelLarge, color = c.teal)
                if (carbsOn) {
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.food_g_carbs, on.sumOf { it.carbs.toDouble() }.roundToInt()), style = MaterialTheme.typography.labelLarge, color = c.purple)
                }
            }
            Spacer(Modifier.height(8.dp))
            parts.forEachIndexed { i, part ->
                PartRow(part, onToggle = { parts = parts.toMutableList().also { it[i] = part.copy(on = !part.on) } }, onGrams = { editing = i })
            }
            Spacer(Modifier.height(16.dp))
            PillButton(stringResource(R.string.action_save), onClick = { onSave(parts) }, enabled = on.isNotEmpty(), modifier = Modifier.fillMaxWidth())
        }
    }
    editing?.let { i ->
        val part = parts[i]
        NumberEntryDialog(
            title = stringResource(R.string.scan_grams_title, part.name), initial = part.grams.toFloat().coerceAtLeast(1f), unit = stringResource(R.string.unit_g),
            decimals = 0, min = 1f, max = 3000f,
            onDismiss = { editing = null },
            onConfirm = { g -> parts = parts.toMutableList().also { it[i] = part.withGrams(g.roundToInt()) }; editing = null },
        )
    }
}

@Composable
private fun PartRow(part: FoodPart, onToggle: () -> Unit, onGrams: () -> Unit) {
    val c = Fit.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(16.dp)).background(c.cardHigh).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onToggle), contentAlignment = Alignment.Center) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(if (part.on) c.teal else c.card), contentAlignment = Alignment.Center) {
                if (part.on) Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.cd_included), tint = c.onTeal, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f).padding(end = 12.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onGrams).padding(vertical = 4.dp)) {
            Text(part.name, style = MaterialTheme.typography.bodyMedium, color = if (part.on) c.textPrimary else c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (part.grams > 0) stringResource(R.string.scan_about_grams, part.grams) else stringResource(R.string.scan_set_grams), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(stringResource(R.string.food_kcal, part.kcal), style = MaterialTheme.typography.labelLarge, color = if (part.on) c.blue else c.textSecondary)
            Text(stringResource(R.string.food_g, part.protein.roundToInt()), style = MaterialTheme.typography.labelMedium, color = if (part.on) c.teal else c.textSecondary)
        }
    }
}

/**
 * The optional restaurant field. While typing, restaurants already used come up as "Same as …?" (spelling variants and
 * venue words like كفتيريا or مطعم aside); a tap uses that exact name, so one place isn't saved under two spellings.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaceField(value: String, onChange: (String) -> Unit, places: List<String>) {
    val c = Fit.colors
    val colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.teal, unfocusedBorderColor = c.divider, focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary, focusedLabelColor = c.teal, unfocusedLabelColor = c.textSecondary)
    OutlinedTextField(value, { onChange(it.take(48).replace("\n", "")) }, label = { Text(stringResource(R.string.food_place)) }, singleLine = true, shape = RoundedCornerShape(16.dp), colors = colors, modifier = Modifier.fillMaxWidth())
    val typed = value.trim()
    val suggestions = remember(typed, places) {
        if (typed.length < 2) emptyList()
        else places.filter { it != typed && (FoodSearch.score(typed, "", it) > 0f || FoodSearch.samePlace(typed, it, places)) }.take(3)
    }
    if (suggestions.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.forEach { p -> ChoiceChip(stringResource(R.string.food_place_same_as, p), false, { onChange(p) }) }
        }
    }
}
