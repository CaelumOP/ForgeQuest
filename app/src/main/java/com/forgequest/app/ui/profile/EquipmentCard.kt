package com.forgequest.app.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forgequest.app.R
import com.forgequest.app.container
import com.forgequest.app.domain.Equipment
import com.forgequest.app.domain.EquipmentGroup
import com.forgequest.app.domain.EquipmentPreset
import com.forgequest.app.domain.TrainingPlace
import com.forgequest.app.domain.expandLegacyEquipment
import com.forgequest.app.ui.components.IconCircle
import com.forgequest.app.ui.components.PillButton
import com.forgequest.app.ui.components.rememberFullSheetState
import com.forgequest.app.ui.theme.Fit
import com.forgequest.app.ui.theme.ForgeShape
import com.forgequest.app.ui.theme.ForgeSpace
import com.forgequest.app.ui.workouts.localName
import java.util.Locale

/** "Home gym" out of "Home gym (barbell, dumbbells, bar)": used by Onboarding chips. */
internal fun EquipmentPreset.shortLabel(): String =
    (if (Locale.getDefault().language == "ar") labelAr else labelEn).substringBefore(" (")

internal fun TrainingPlace.localName(): String = if (Locale.getDefault().language == "ar") labelAr else labelEn
internal fun TrainingPlace.localDesc(): String = if (Locale.getDefault().language == "ar") descAr else descEn
internal fun EquipmentGroup.localName(): String = if (Locale.getDefault().language == "ar") labelAr else labelEn

internal fun encodeEquipmentSet(equipment: Set<Equipment>): String {
    val clean = (expandLegacyEquipment(equipment) + Equipment.BODYWEIGHT).filter { !it.legacy }
    return clean.joinToString(",") { it.name }
}

/** The saved equipment after ticking or unticking [eq]: bodyweight always stays in. Null when nothing changes. */
internal fun toggledEquipment(eq: Equipment, current: Set<Equipment>): String? {
    if (eq == Equipment.BODYWEIGHT) return null
    val base = expandLegacyEquipment(current).ifEmpty { setOf(Equipment.BODYWEIGHT) }
    val updated = (if (eq in base) base - eq else base + eq) + Equipment.BODYWEIGHT
    return encodeEquipmentSet(updated)
}

/** One line for the current setup: place name, "Custom · N items", or not set. */
@Composable
internal fun equipmentSummary(configured: Set<Equipment>, encoded: String): String {
    val clean = expandLegacyEquipment(configured)
    if (clean.isEmpty()) return stringResource(R.string.summary_equipment_unset)
    TrainingPlace.match(clean)?.let { return it.localName() }
    val count = (clean - Equipment.BODYWEIGHT).count { !it.legacy }
    val countLabel = pluralStringResource(R.plurals.equipment_item_count, count, count)
    return if (count == 0) countLabel else "${stringResource(R.string.equipment_custom)} · $countLabel"
}

/** Summary line for Plan Builder's "Customize gear · <place> (N items)" row. */
@Composable
internal fun gearCustomizeSummary(equipment: Set<Equipment>): String {
    val clean = expandLegacyEquipment(equipment)
    val placeLabel = TrainingPlace.match(clean)?.localName() ?: stringResource(R.string.equipment_custom)
    val count = (clean - Equipment.BODYWEIGHT).count { !it.legacy }
    val countLabel = pluralStringResource(R.plurals.equipment_item_count, count, count)
    return stringResource(R.string.equipment_customize_row, placeLabel, countLabel)
}

/** Three clean training places: Gym, Home gym, No gear. */
@Composable
internal fun TrainingPlaceSelector(
    equipment: Set<Equipment>,
    onSelectPlace: (TrainingPlace) -> Unit,
) {
    val c = Fit.colors
    val haptics = LocalContext.current.container.haptics
    val matched = TrainingPlace.match(equipment)
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TrainingPlace.entries.forEach { place ->
            val selected = matched == place
            Surface(
                shape = ForgeShape.Sm,
                color = if (selected) c.tealTrack else c.cardHigh,
                border = BorderStroke(1.dp, if (selected) c.teal else c.divider),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .selectable(selected = selected, role = Role.RadioButton) {
                        haptics.lightTick()
                        onSelectPlace(place)
                    },
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (selected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (selected) c.teal else c.textSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            place.localName(),
                            style = MaterialTheme.typography.titleSmall,
                            color = c.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            place.localDesc(),
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Shared full Gear Page sheet (used from Plan Builder and Plan Editor): pick a place or customize individual
 * gear items grouped into 5 clean 2-column grids. Only shows gear backed by real exercises.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GearSheet(
    equipment: Set<Equipment>,
    onUpdate: (Set<Equipment>) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Fit.colors
    val haptics = LocalContext.current.container.haptics
    val clean = expandLegacyEquipment(equipment) + Equipment.BODYWEIGHT
    ModalBottomSheet(
        sheetState = rememberFullSheetState(),
        onDismissRequest = onDismiss,
        containerColor = c.background,
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = ForgeSpace.Gutter),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconCircle(Icons.Rounded.FitnessCenter, c.teal, c.tealTrack)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.equipment_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = c.textPrimary,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        equipmentSummary(clean, ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.teal,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_done), tint = c.textSecondary)
                }
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = c.divider)

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ForgeSpace.Gutter),
            ) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.equipment_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.equipment_always), style = MaterialTheme.typography.labelMedium, color = c.teal)
                Spacer(Modifier.height(16.dp))

                Text(
                    stringResource(R.string.pb_equipment),
                    style = MaterialTheme.typography.titleSmall,
                    color = c.textPrimary,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(8.dp))
                TrainingPlaceSelector(clean) { place -> onUpdate(place.equipment) }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = c.divider)

                EquipmentGroup.entries.forEach { group ->
                    val items = Equipment.selectableEntries.filter { it.group == group }
                    val selectedCount = items.count { it in clean }
                    val allSelected = selectedCount == items.size
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            group.localName(),
                            style = MaterialTheme.typography.titleSmall,
                            color = c.textPrimary,
                            modifier = Modifier.weight(1f).semantics { heading() },
                        )
                        Text(
                            stringResource(R.string.equipment_group_count, selectedCount, items.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = c.textSecondary,
                        )
                        Spacer(Modifier.width(4.dp))
                        TextButton(
                            onClick = {
                                haptics.lightTick()
                                val next = if (allSelected) clean - items.toSet() else clean + items
                                onUpdate(next + Equipment.BODYWEIGHT)
                            },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text(
                                stringResource(if (allSelected) R.string.action_clear else R.string.equipment_select_all),
                                color = c.teal,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items.chunked(2).forEach { rowItems ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                rowItems.forEach { eq ->
                                    val on = eq in clean
                                    GearTile(
                                        label = eq.localName(),
                                        selected = on,
                                        onToggle = {
                                            haptics.lightTick()
                                            val next = if (on) clean - eq else clean + eq
                                            onUpdate(next + Equipment.BODYWEIGHT)
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (rowItems.size == 1) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            HorizontalDivider(color = c.divider)
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ForgeSpace.Gutter, vertical = 12.dp)
                    .navigationBarsPadding(),
            ) {
                PillButton(
                    text = stringResource(R.string.cd_done),
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun GearTile(
    label: String,
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Fit.colors
    Surface(
        shape = ForgeShape.Sm,
        color = if (selected) c.tealTrack else c.card,
        border = BorderStroke(1.dp, if (selected) c.teal else c.divider),
        modifier = modifier
            .heightIn(min = 48.dp)
            .toggleable(value = selected, role = Role.Checkbox) { onToggle() },
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (selected) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                contentDescription = null,
                tint = if (selected) c.teal else c.textSecondary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) c.textPrimary else c.textSecondary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
