package com.forgequest.app.ui.food

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forgequest.app.R
import com.forgequest.app.domain.Catalog
import com.forgequest.app.domain.CatalogAmount
import com.forgequest.app.domain.CatalogFood
import com.forgequest.app.ui.components.ChoiceChip
import com.forgequest.app.ui.components.PillButton
import com.forgequest.app.ui.components.rememberFullSheetState
import com.forgequest.app.ui.isArabic
import com.forgequest.app.ui.theme.Fit
import com.forgequest.app.ui.theme.ForgeShape
import com.forgequest.app.ui.theme.ForgeSpace
import com.forgequest.app.ui.theme.tabular

/** One item of the built-in food list in search results; tap to choose the amount. */
@Composable
fun CatalogRow(food: CatalogFood, onClick: () -> Unit) {
    val c = Fit.colors
    val ar = isArabic()
    Row(
        Modifier.fillMaxWidth().clip(ForgeShape.Md).background(c.card).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(food.name(ar), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val sub = listOf(food.brand(ar), stringResource(R.string.food_per_100g, Catalog.formatNumber(food.kcal))).filter { it.isNotBlank() }.joinToString(" · ")
            Text(sub, style = MaterialTheme.typography.bodySmall.tabular(), color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private data class Choice(val label: String, val gramsEach: Float, val defaultCount: Float)

/** Pick how much: grams, a household unit or the usual serving. Shows kcal, protein and carbs for it before logging. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogAmountSheet(food: CatalogFood, onLog: (CatalogAmount, String) -> Unit, onDismiss: () -> Unit) {
    val c = Fit.colors
    val ar = isArabic()
    val gramsLabel = stringResource(R.string.food_unit_grams)
    val servingLabel = stringResource(R.string.food_unit_serving)
    val choices = remember(food, ar, gramsLabel, servingLabel) {
        buildList {
            food.servingG?.takeIf { it > 0f }?.let { add(Choice(servingLabel, it, 1f)) }
            food.units.forEach { add(Choice(it.label(ar), it.grams, 1f)) }
            add(Choice(gramsLabel, 1f, 100f))
        }
    }
    var picked by remember { mutableStateOf(0) }
    val choice = choices[picked]
    var text by remember(picked) { mutableStateOf(Catalog.formatNumber(choice.defaultCount)) }
    val count = text.toFloatOrNull() ?: 0f
    val amount = CatalogAmount(count, choice.gramsEach)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = c.teal, unfocusedBorderColor = c.divider, focusedTextColor = c.textPrimary,
        unfocusedTextColor = c.textPrimary, focusedLabelColor = c.teal, unfocusedLabelColor = c.textSecondary,
    )
    ModalBottomSheet(sheetState = rememberFullSheetState(), onDismissRequest = onDismiss, containerColor = c.card) {
        Column(Modifier.padding(horizontal = ForgeSpace.Gutter).padding(bottom = 32.dp)) {
            Text(food.name(ar), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
            if (food.brand(ar).isNotBlank()) Text(food.brand(ar), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.forEachIndexed { i, ch ->
                    val g = Catalog.formatNumber(ch.gramsEach)
                    ChoiceChip(if (ch.gramsEach == 1f) ch.label else "${ch.label} · $g g", i == picked, { picked = i })
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                text, { text = it.filter { ch -> ch.isDigit() || ch == '.' }.take(6) },
                label = { Text(if (choice.gramsEach == 1f) stringResource(R.string.food_unit_grams) else stringResource(R.string.food_amount_count, choice.label)) },
                singleLine = true, shape = ForgeShape.Md, colors = fieldColors,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            val grams = amount.grams
            Text(
                stringResource(
                    R.string.food_amount_summary, Catalog.kcalFor(food, grams), Catalog.formatNumber(Catalog.proteinFor(food, grams)),
                    Catalog.formatNumber(Catalog.carbsFor(food, grams)), Catalog.formatNumber(grams),
                ),
                style = MaterialTheme.typography.titleMedium.tabular(), color = c.textPrimary,
            )
            Spacer(Modifier.height(16.dp))
            PillButton(stringResource(R.string.food_log_it), onClick = { onLog(amount, choice.label) }, enabled = count > 0f, modifier = Modifier.fillMaxWidth())
        }
    }
}
