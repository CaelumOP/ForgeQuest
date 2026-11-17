package com.forgequest.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.forgequest.app.R
import com.forgequest.app.ui.LocalSnackbar
import com.forgequest.app.ui.Routes
import com.forgequest.app.ui.components.FitCard
import com.forgequest.app.ui.components.IconCircle
import com.forgequest.app.ui.components.TonalPill
import com.forgequest.app.ui.goToTab
import com.forgequest.app.ui.onboarding.AppTour
import com.forgequest.app.ui.theme.Fit
import com.forgequest.app.ui.theme.ForgeSpace
import kotlinx.coroutines.launch

private data class GuideItem(
    val icon: ImageVector,
    val titleRes: Int,
    val bodyRes: Int,
    val onShowMe: (NavHostController) -> Unit,
)

@Composable
fun GuideScreen(nav: NavHostController) {
    val c = Fit.colors
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val resetMessage = stringResource(R.string.v42_guide_tips_reset)

    val items = listOf(
        GuideItem(Icons.Rounded.FitnessCenter, R.string.v42_guide_workout_t, R.string.v42_guide_workout_b) { it.goToTab(Routes.WORKOUTS) },
        GuideItem(Icons.Rounded.Restaurant, R.string.v42_guide_food_t, R.string.v42_guide_food_b) { it.goToTab(Routes.FOOD) },
        GuideItem(Icons.Rounded.MonitorWeight, R.string.v42_guide_body_t, R.string.v42_guide_body_b) { it.goToTab(Routes.PROGRESS) },
        GuideItem(Icons.AutoMirrored.Rounded.ShowChart, R.string.v42_guide_progress_t, R.string.v42_guide_progress_b) { it.goToTab(Routes.PROGRESS) },
        GuideItem(Icons.Rounded.Group, R.string.v42_guide_friends_t, R.string.v42_guide_friends_b) { it.goToTab(Routes.FRIENDS) },
        GuideItem(Icons.Rounded.Cloud, R.string.v42_guide_backup_t, R.string.v42_guide_backup_b) { it.navigate(Routes.settingsGroup("backup")) },
    )

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = c.textPrimary)
            }
            Text(
                stringResource(R.string.v42_guide_title),
                style = MaterialTheme.typography.titleLarge,
                color = c.textPrimary,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ForgeSpace.Gutter),
            verticalArrangement = Arrangement.spacedBy(ForgeSpace.Sm),
        ) {
            Spacer(Modifier.height(ForgeSpace.Xxs))
            items.forEach { item ->
                FitCard {
                    Row(verticalAlignment = Alignment.Top) {
                        IconCircle(item.icon, c.teal, c.tealTrack)
                        Spacer(Modifier.width(ForgeSpace.Sm))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(item.titleRes),
                                style = MaterialTheme.typography.titleMedium,
                                color = c.textPrimary,
                                modifier = Modifier.semantics { heading() },
                            )
                            Spacer(Modifier.height(ForgeSpace.Xxs))
                            Text(
                                stringResource(item.bodyRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textSecondary,
                            )
                        }
                    }
                    Spacer(Modifier.height(ForgeSpace.Xs))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(
                            onClick = { item.onShowMe(nav) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text(stringResource(R.string.v42_guide_show_me), color = c.teal)
                        }
                    }
                }
            }
            Spacer(Modifier.height(ForgeSpace.Xxs))
            TonalPill(
                text = stringResource(R.string.v42_guide_tips_again),
                onClick = {
                    AppTour.resetTips(context)
                    scope.launch { snackbar.showSnackbar(resetMessage) }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(ForgeSpace.Lg))
        }
    }
}
