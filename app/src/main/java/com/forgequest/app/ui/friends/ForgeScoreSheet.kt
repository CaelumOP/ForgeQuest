package com.forgequest.app.ui.friends

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.forgequest.app.R
import com.forgequest.app.domain.ForgeScore
import com.forgequest.app.domain.ForgeScoreBreakdown
import com.forgequest.app.ui.components.rememberFullSheetState
import com.forgequest.app.ui.theme.Fit
import com.forgequest.app.ui.theme.ForgeSpace

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgeScoreSheet(
    done: Int, target: Int, prCount: Int, proteinDays: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    breakdown: ForgeScoreBreakdown = ForgeScore.breakdown(done, target, prCount, proteinDays),
) {
    val c = Fit.colors
    ModalBottomSheet(modifier = modifier, onDismissRequest = onDismiss, sheetState = rememberFullSheetState(), containerColor = c.card) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(horizontal = ForgeSpace.Gutter).padding(bottom = ForgeSpace.Lg),
            verticalArrangement = Arrangement.spacedBy(ForgeSpace.Md),
        ) {
            Text(stringResource(R.string.score41_title), style = MaterialTheme.typography.headlineSmall,
                color = c.textPrimary, modifier = Modifier.semantics { heading() })
            if (target == 0) Text(stringResource(R.string.score41_target), color = c.textPrimary)
            Text(stringResource(R.string.score41_workouts, done.toString(), target.toString(), breakdown.base.toString()), color = c.textPrimary)
            if (breakdown.prBonus > 0) Text(stringResource(R.string.score41_prs, prCount.toString(), breakdown.prBonus.toString()), color = c.textPrimary)
            if (breakdown.proteinBonus > 0) Text(stringResource(R.string.score41_protein, proteinDays.toString(), breakdown.proteinBonus.toString()), color = c.textPrimary)
            if (breakdown.extraBonus > 0) Text(stringResource(R.string.score41_extra), color = c.textPrimary)
            if (breakdown.prBonus + breakdown.proteinBonus + breakdown.extraBonus > breakdown.bonus)
                Text(stringResource(R.string.score41_cap), color = c.textSecondary)
            Text(stringResource(R.string.score41_total, breakdown.base.toString(), breakdown.bonus.toString(), breakdown.score.toString()),
                style = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Ltr), color = c.textPrimary)
            HorizontalDivider()
            Text(stringResource(R.string.score41_rule_base), color = c.textSecondary)
            Text(stringResource(R.string.score41_rule_bonus), color = c.textSecondary)
            Text(stringResource(R.string.score41_rule_tie), color = c.textSecondary)
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.score41_close))
            }
        }
    }
}

@Composable
fun ForgeScoreInfoButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
        Icon(Icons.Rounded.Info, contentDescription = stringResource(R.string.score41_title), tint = Fit.colors.teal)
    }
}

@Composable
fun ForgeScoreButton(score: Int?, done: Int, target: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val spoken = stringResource(R.string.score41_score_label, score?.toString() ?: "–")
    TextButton(onClick = onClick, modifier = modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = spoken }) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(score?.toString() ?: "–", style = MaterialTheme.typography.titleLarge)
            Text("$done/$target", style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr))
        }
    }
}
