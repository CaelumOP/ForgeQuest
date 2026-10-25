package com.forgequest.app.ui.profile

import com.forgequest.app.R
import com.forgequest.app.ui.localTitle
import com.forgequest.app.ui.localDescription
import com.forgequest.app.ui.localLabel

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.forgequest.app.domain.BadgeCategory
import com.forgequest.app.domain.Badges
import com.forgequest.app.ui.appViewModel
import com.forgequest.app.ui.components.FitCard
import com.forgequest.app.ui.theme.Fit

/** All badges by category. Opening it marks new ones as seen. */
@Composable
fun BadgesScreen(nav: NavHostController) {
    val vm = appViewModel { ProfileViewModel(it) }
    val snap by vm.snapshot.collectAsState()
    val c = Fit.colors
    DisposableEffect(Unit) { onDispose { vm.markBadgesSeen() } }
    val s = snap ?: return Box(Modifier.fillMaxSize().background(c.background))
    val owned = s.badges.associateBy { it.id }

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = c.textPrimary) }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.profile_badges), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                Text(stringResource(R.string.profile_badges_unlocked, s.badges.size, Badges.all.size), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            // The closest locked badge, so there's always a clear next goal.
            Badges.all.filter { it.id !in owned }
                .mapNotNull { b -> Badges.progress(b.id, s.stats)?.let { b to it } }
                .maxByOrNull { (_, p) -> p.first.toFloat() / p.second }
                ?.let { (b, p) ->
                    FitCard {
                        Text(stringResource(R.string.badges_next_up), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                        Text(b.localTitle() + " · " + b.localDescription(), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                        Spacer(Modifier.height(8.dp))
                        com.forgequest.app.ui.components.ProgressLine(p.first.toFloat() / p.second, c.gold, c.goldTrack, height = 8.dp)
                        Text("⁦${p.first} / ${p.second}⁩", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                    Spacer(Modifier.height(12.dp))
                }
            BadgeCategory.entries.forEach { cat ->
                FitCard {
                    Text(cat.localLabel(), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                    Spacer(Modifier.height(10.dp))
                    Badges.all.filter { it.category == cat }.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                            row.forEach { b ->
                                val got = owned[b.id]
                                BadgeChip(b.localTitle(), b.localDescription(), badgeIcon(cat), unlocked = got != null, isNew = got?.seen == false, modifier = Modifier.weight(1f), progress = Badges.progress(b.id, s.stats))
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
