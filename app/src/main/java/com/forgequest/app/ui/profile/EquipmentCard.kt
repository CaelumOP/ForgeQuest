package com.forgequest.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forgequest.app.R
import com.forgequest.app.domain.Equipment
import com.forgequest.app.domain.EquipmentPreset
import com.forgequest.app.ui.components.FitCard
import com.forgequest.app.ui.components.IconCircle
import com.forgequest.app.ui.theme.Fit
import com.forgequest.app.ui.workouts.localName
import java.util.Locale

/** "Home gym" out of "Home gym (barbell, dumbbells, bar)": the items are listed on their own line. */
internal fun EquipmentPreset.shortLabel(): String =
    (if (Locale.getDefault().language == "ar") labelAr else labelEn).substringBefore(" (")

/**
 * Equipment in two clearly separate steps: (1) one ready-made setup, picked like a radio button,
 * (2) the single items, ticked like checkboxes. Ticking an item that no setup matches shows "Custom mix".
 */
@Composable
internal fun EquipmentCard(active: EquipmentPreset?, configured: Set<Equipment>, onPreset: (EquipmentPreset) -> Unit, onItem: (Equipment) -> Unit) {
    val c = Fit.colors
    FitCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconCircle(Icons.Rounded.FitnessCenter, c.teal, c.tealTrack)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.equipment_title), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.equipment_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
        }

        EquipmentStep(1, stringResource(R.string.equipment_step_setup), stringResource(R.string.equipment_step_setup_hint))
        Column(Modifier.selectableGroup()) {
            EquipmentPreset.entries.forEach { preset ->
                val items = preset.equipment.filter { it != Equipment.BODYWEIGHT }
                ChoiceRow(
                    title = preset.shortLabel(),
                    subtitle = if (items.isEmpty()) stringResource(R.string.equipment_no_items) else items.joinToString(" · ") { it.localName() },
                    selected = active == preset,
                    radio = true,
                    onClick = { onPreset(preset) },
                )
            }
            if (active == null && configured.isNotEmpty()) {
                ChoiceRow(stringResource(R.string.equipment_custom), stringResource(R.string.equipment_custom_hint), selected = true, radio = true, onClick = null)
            }
        }

        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.HorizontalDivider(color = c.divider)
        EquipmentStep(2, stringResource(R.string.equipment_step_items), stringResource(R.string.equipment_step_items_hint))
        Equipment.entries.forEach { eq ->
            val always = eq == Equipment.BODYWEIGHT
            ChoiceRow(
                title = eq.localName(),
                subtitle = if (always) stringResource(R.string.equipment_always) else null,
                selected = always || eq in configured,
                radio = false,
                onClick = if (always) null else ({ onItem(eq) }),
            )
        }
    }
}

@Composable
internal fun EquipmentStep(number: Int, title: String, hint: String) {
    val c = Fit.colors
    Row(Modifier.padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(c.tealTrack), contentAlignment = Alignment.Center) {
            Text("$number", style = MaterialTheme.typography.labelMedium, color = c.teal, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, modifier = Modifier.semantics { heading() })
            Text(hint, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
    }
}
