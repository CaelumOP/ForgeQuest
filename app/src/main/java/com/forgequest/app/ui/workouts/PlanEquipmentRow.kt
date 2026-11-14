package com.forgequest.app.ui.workouts

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.forgequest.app.R
import com.forgequest.app.data.prefs.Settings
import com.forgequest.app.domain.Equipment
import com.forgequest.app.ui.profile.GearSheet
import com.forgequest.app.ui.profile.SettingsRow
import com.forgequest.app.ui.profile.equipmentSummary

/**
 * The plan editor's equipment row: what you train with, in one line, opening the shared Gear sheet.
 * Swaps and the exercise picker follow it.
 */
@Composable
internal fun PlanEquipmentRow(settings: Settings, onUpdate: (Set<Equipment>) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val configured = settings.configuredEquipment
    SettingsRow(
        Icons.Rounded.FitnessCenter, com.forgequest.app.ui.theme.Fit.colors.teal, com.forgequest.app.ui.theme.Fit.colors.tealTrack,
        stringResource(R.string.equipment_title),
        equipmentSummary(configured, settings.equipment) + " · " + stringResource(R.string.plan_equipment_hint),
    ) { open = true }
    if (open) {
        GearSheet(
            equipment = configured,
            onUpdate = onUpdate,
            onDismiss = { open = false },
        )
    }
}
