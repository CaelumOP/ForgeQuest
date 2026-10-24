package com.forgequest.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.forgequest.app.R
import com.forgequest.app.data.ProgressSnapshot
import com.forgequest.app.data.health.HealthState
import com.forgequest.app.data.prefs.SettingsRepository
import com.forgequest.app.ui.CalorieCard
import com.forgequest.app.ui.CalorieUi
import com.forgequest.app.ui.components.FitCard
import com.forgequest.app.ui.components.IconCircle
import com.forgequest.app.ui.components.TonalPill
import com.forgequest.app.ui.labelRes
import com.forgequest.app.ui.theme.Fit
import kotlinx.coroutines.launch

/**
 * The one calorie card on Home (calorie plan v2): the one-time "more accurate target", the learned real burn, or a
 * signal from the logs. Each has one main action and a quiet "Not now". Web: ui/home/CalorieCard.tsx.
 */
@Composable
fun CalorieCardView(s: ProgressSnapshot, health: HealthState?, settings: SettingsRepository) {
    val card = remember(s, health) { CalorieUi.card(s, health) } ?: return
    val c = Fit.colors
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    fun run(job: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch { try { job() } finally { busy = false } }
    }
    fun n(x: Int) = "%,d".format(x)

    val title: String
    val body: String
    val main: String
    var other: String? = stringResource(R.string.cal_not_now)
    when (card) {
        is CalorieCard.Migration -> {
            title = stringResource(R.string.cal_v2_title); body = stringResource(R.string.cal_v2_body, n(card.to), n(card.from))
            main = stringResource(R.string.cal_use, n(card.to)); other = stringResource(R.string.cal_keep, n(card.from))
        }
        is CalorieCard.Adaptive -> {
            title = stringResource(R.string.cal_adaptive_title); body = stringResource(R.string.cal_adaptive_body, n(card.burn), n(card.to))
            main = stringResource(R.string.cal_update)
        }
        is CalorieCard.UnderFuelled -> {
            title = stringResource(R.string.cal_underfuel_title)
            body = stringResource(R.string.cal_underfuel_body, n(CalorieUi.gentlerTarget(s, health, card.pace)))
            main = stringResource(R.string.cal_switch, stringResource(card.pace.labelRes))
        }
        is CalorieCard.WeightChanged -> {
            title = stringResource(R.string.cal_weight_title); body = stringResource(R.string.cal_weight_body, n(card.to))
            main = stringResource(R.string.cal_use, n(card.to))
        }
        is CalorieCard.ScaleCreep -> {
            title = stringResource(R.string.cal_creep_title); body = stringResource(R.string.cal_creep_body, n(card.to))
            main = stringResource(R.string.cal_use, n(card.to))
        }
        CalorieCard.StepDrop -> {
            // A note, not a change: OK hides it like "Not now".
            title = stringResource(R.string.cal_steps_title); body = stringResource(R.string.cal_steps_body)
            main = stringResource(R.string.cal_ok); other = null
        }
    }

    FitCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconCircle(Icons.Rounded.LocalFireDepartment, c.gold, c.goldTrack, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            other?.let {
                TextButton(onClick = { run { CalorieUi.snooze(settings, migration = card is CalorieCard.Migration) } }, enabled = !busy) {
                    Text(it, color = c.textSecondary)
                }
                Spacer(Modifier.width(8.dp))
            }
            TonalPill(main, onClick = { run { CalorieUi.accept(card, s, health, settings) } })
        }
    }
    Spacer(Modifier.height(12.dp))
}
