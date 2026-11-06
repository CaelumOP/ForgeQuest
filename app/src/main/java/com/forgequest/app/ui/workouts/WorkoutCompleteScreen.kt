package com.forgequest.app.ui.workouts

import com.forgequest.app.ui.theme.ForgeShape
import com.forgequest.app.ui.theme.tabular
import com.forgequest.app.ui.theme.itemMotion
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.icons.rounded.Star
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import com.forgequest.app.ui.theme.ForgeMotion
import com.forgequest.app.container
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import com.forgequest.app.ui.theme.LocalReducedMotion
import com.forgequest.app.ui.theme.effectsSpec
import com.forgequest.app.ui.theme.spatialSpec
import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.map
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.forgequest.app.R
import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.SetEntity
import com.forgequest.app.data.DebriefText
import com.forgequest.app.domain.WeightUnit
import com.forgequest.app.domain.WorkoutDebriefMath
import com.forgequest.app.ui.goToTab
import kotlinx.coroutines.launch
import com.forgequest.app.domain.ExerciseKind
import com.forgequest.app.domain.SetEffort
import com.forgequest.app.domain.WorkoutScore
import com.forgequest.app.ui.labelRes
import com.forgequest.app.ui.Routes
import com.forgequest.app.ui.appViewModel
import com.forgequest.app.ui.blockLabel
import com.forgequest.app.ui.components.ChoiceChip
import com.forgequest.app.ui.components.FitCard
import com.forgequest.app.ui.components.toast
import com.forgequest.app.ui.components.IconCircle
import com.forgequest.app.ui.components.NumberStepper
import com.forgequest.app.ui.components.PillButton
import com.forgequest.app.ui.components.steadyToggleable
import com.forgequest.app.ui.components.ProgressLine
import com.forgequest.app.ui.components.TonalPill
import com.forgequest.app.ui.localTitle
import com.forgequest.app.ui.theme.Fit
import com.forgequest.app.ui.workouts.LiveWorkoutViewModel.Field
import java.util.Locale

@Composable
internal fun WorkoutCompleteScreen(
    sum: WorkoutSummary,
    onAskCoach: (() -> Unit)?,
    onRemind: () -> Unit,
    onDone: () -> Unit,
) {
    val c = Fit.colors
    var filled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { filled = true }
    val leveledUp = sum.after.level > sum.before.level
    BackHandler(onBack = onDone)
    Column(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        IconCircle(Icons.Rounded.Check, c.onTeal, c.teal, 88.dp)
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.live_complete), style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)
        Text(sum.title, style = MaterialTheme.typography.bodyLarge, color = c.textSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        Row(Modifier.fillMaxWidth()) {
            com.forgequest.app.ui.components.StatColumn("${sum.doneSets}", stringResource(R.string.live_sets), c.teal, Modifier.weight(1f))
            com.forgequest.app.ui.components.StatColumn(stringResource(R.string.hist_minutes, sum.minutes), stringResource(R.string.profile_time), c.blue, Modifier.weight(1f))
            if (!sum.justLog) com.forgequest.app.ui.components.StatColumn("⁦+${sum.xpGained}⁩", stringResource(R.string.live_xp), c.gold, Modifier.weight(1f))
        }
        sum.clippedEndAt?.let { end ->
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.live_ended_at_last_set, java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(end - com.forgequest.app.domain.WorkoutEnd.GRACE_MS))),
                style = MaterialTheme.typography.bodySmall, color = c.textSecondary, textAlign = TextAlign.Center,
            )
        }
        // "Just log" mode: XP still accrues, but the level card stays out of sight.
        if (!sum.justLog) {
        Spacer(Modifier.height(24.dp))
        FitCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (leveledUp) stringResource(R.string.live_level_up, sum.after.level) else stringResource(R.string.live_level, sum.after.level),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (leveledUp) c.gold else c.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(stringResource(R.string.live_rank, sum.after.rank.letter, sum.after.rank.localTitle()), style = MaterialTheme.typography.labelLarge, color = c.blue)
            }
            Spacer(Modifier.height(12.dp))
            val from = if (leveledUp) 0f else sum.before.progress
            ProgressLine(if (filled) sum.after.progress else from, c.blue, c.blueTrack, height = 8.dp)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.profile_xp_line, sum.after.xpIntoLevel, sum.after.xpForNextLevel, sum.after.level + 1), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
        }
        val badges = if (sum.justLog) emptyList() else sum.newBadges
        if (sum.prs.isNotEmpty() || badges.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            FitCard {
                sum.prs.forEach { name ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        PrTag()
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.live_new_record, name), style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
                    }
                }
                badges.forEach { badge ->
                    val title = badge.localTitle()
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = c.gold, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.live_badge_unlocked, title), style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
                    }
                }
            }
        }
        if (sum.offerReminder) {
            var reminded by remember { mutableStateOf(false) }
            Spacer(Modifier.height(12.dp))
            FitCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconCircle(Icons.Rounded.Notifications, c.teal, c.tealTrack)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(if (reminded) R.string.live_reminder_on else R.string.live_reminder_title), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                        Text(stringResource(R.string.live_reminder_body), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                    if (!reminded) {
                        Spacer(Modifier.width(8.dp))
                        PillButton(stringResource(R.string.live_reminder_button), onClick = { onRemind(); reminded = true })
                    }
                }
            }
        }
        // What comes next, so finishing points forward (Fitbod ends every workout with the next one).
        sum.nextUp?.let { (dow, title) ->
            Spacer(Modifier.height(12.dp))
            FitCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = c.teal, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.live_next_up, com.forgequest.app.domain.Dates.dayName(dow), title), style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
                }
            }
        }
        if (sum.score != null || sum.debrief != null) {
            Spacer(Modifier.height(12.dp))
            DebriefCard(sum.debrief, sum.score, sum.exerciseNames, sum.unit, onAskCoach, celebrate = true)
        }
        Spacer(Modifier.height(24.dp))
        PillButton(stringResource(R.string.home_done), onClick = onDone, modifier = Modifier.fillMaxWidth())
    }
}

/** "All 12 sets · Up on 3 of 4 · 1 PR": why the workout got its score. A rough day from pain alone says so. */
@Composable
fun scoreReasons(r: WorkoutScore.Result): String {
    val sets = when {
        r.plannedSets == null -> pluralStringResource(R.plurals.hist_sets, r.doneSets, r.doneSets)
        r.doneSets >= r.plannedSets -> pluralStringResource(R.plurals.score_sets_all, r.doneSets, r.doneSets)
        else -> stringResource(R.string.score_sets_of, r.doneSets, r.plannedSets)
    }
    val up = if (r.compared > 0) stringResource(R.string.score_up, r.up, r.compared) else null
    val prs = if (r.prs > 0) pluralStringResource(R.plurals.score_prs, r.prs, r.prs) else null
    val adjusted = if (r.rough) stringResource(if (r.lowRecovery) R.string.score_adjusted else R.string.score_adjusted_pain) else null
    return listOfNotNull(sets, up, prs, adjusted).joinToString(" · ")
}

/**
 * One debrief line fading in and rising 16 dp, [index] × 70 ms after [go] (DefaultSpatial). Off when not [enabled];
 * reduced motion makes it a short fade with no rise.
 */
@Composable
internal fun Modifier.riseIn(enabled: Boolean, go: Boolean, index: Int): Modifier {
    if (!enabled) return this
    val reduced = LocalReducedMotion.current
    val p = remember { Animatable(0f) }
    LaunchedEffect(go) {
        if (!go) return@LaunchedEffect
        delay(70L * index)
        p.animateTo(1f, if (reduced) tween(ForgeMotion.REDUCED_FADE_MS) else ForgeMotion.Springs.DefaultSpatial.spec())
    }
    return graphicsLayer {
        alpha = p.value.coerceIn(0f, 1f)
        translationY = if (reduced) 0f else (1f - p.value) * 16.dp.toPx()
    }
}

/** N/10 only from 8 up is gold; 4 to 7 stay quiet. */
@Composable
internal fun scoreColor(score: Int) = if (score >= 8) Fit.colors.gold else Fit.colors.textSecondary

/**
 * The workout debrief: the calculated score with its reasons, then the headline, what went well (teal), what to watch
 * (gold) and next time (blue), all worked out from the person's numbers ([DebriefText]). [onAskCoach] shows the coach
 * button; null hides it (no AI key). [names] maps an exercise id to its name.
 */
@Composable
fun DebriefCard(
    debrief: WorkoutDebriefMath.Result?,
    score: WorkoutScore.Result?,
    names: Map<String, String>,
    unit: WeightUnit,
    onAskCoach: (() -> Unit)? = null,
    /** True on the finish screen only: the score counts up and the lines rise in. Reopened from the journal it just shows. */
    celebrate: Boolean = false,
) {
    val c = Fit.colors
    val context = LocalContext.current
    // "Just log" mode drops the motivational headline; the score, reasons and facts stay.
    val justLog by remember { context.container.settings.settings.map { it.justLog } }.collectAsState(initial = false)
    val showHeadline = !justLog
    val res = context.resources
    val locale = androidx.compose.ui.platform.LocalConfiguration.current
    val lines = remember(debrief, names, unit, locale) { debrief?.let { d -> DebriefText.render(d, res, { id -> names[id] ?: id }) { kg -> unit.format(kg) } } }
    val reduced = LocalReducedMotion.current
    val counting = celebrate && score != null && score.showNumber
    // 0 -> score over 650 ms; the arc follows the same value. Reduced motion lands on the score at once.
    val count = remember { Animatable(if (counting && !reduced) 0f else (score?.score ?: 0).toFloat()) }
    // The lines below wait for the count to land (at once when there is no count).
    var linesGo by remember { mutableStateOf(!celebrate) }
    LaunchedEffect(Unit) {
        if (!celebrate) return@LaunchedEffect
        if (counting) {
            if (!reduced) count.animateTo(score!!.score.toFloat(), tween(650, easing = ForgeMotion.Easing.EmphasizedDecelerate))
            context.container.haptics.heavyClick()
        }
        linesGo = true
    }
    FitCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.debrief_title), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
            if (score != null && score.showNumber) {
                val arcColor = scoreColor(score.score)
                val track = MaterialTheme.colorScheme.surfaceContainerHighest
                Box(contentAlignment = Alignment.Center) {
                    // A thin arc that sweeps to score/10 with the count.
                    Canvas(Modifier.size(52.dp).clearAndSetSemantics {}) {
                        val w = 3.dp.toPx()
                        val inset = w / 2
                        val arcSize = androidx.compose.ui.geometry.Size(size.width - w, size.height - w)
                        val tl = androidx.compose.ui.geometry.Offset(inset, inset)
                        drawArc(track, 0f, 360f, false, tl, arcSize, style = Stroke(w))
                        drawArc(arcColor, -90f, 360f * (count.value / 10f).coerceIn(0f, 1f), false, tl, arcSize, style = Stroke(w, cap = StrokeCap.Round))
                    }
                    Text(
                        stringResource(R.string.debrief_score, count.value.roundToInt()),
                        style = MaterialTheme.typography.labelLarge.tabular(), color = arcColor,
                        modifier = Modifier.semantics { contentDescription = res.getString(R.string.debrief_score, score.score) },
                    )
                }
            }
        }
        // The first-ever and comeback headlines already say what this line says.
        val sameAsHeadline = debrief?.headline?.type.let { it == WorkoutDebriefMath.Headline.FIRST_EVER || it == WorkoutDebriefMath.Headline.AFTER_BREAK }
        if (score != null && !score.showNumber && !sameAsHeadline) {
            Text(stringResource(if (score.firstEver) R.string.score_first_ever else R.string.score_after_break), style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, modifier = Modifier.padding(top = 2.dp))
        }
        if (score != null) Text(scoreReasons(score), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 2.dp))
        if (lines != null) {
            Spacer(Modifier.height(8.dp))
            if (showHeadline) Text(lines.headline, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, modifier = Modifier.riseIn(celebrate, linesGo, 0))
            // Green for what went well, gold for what to watch, blue for next time.
            lines.wins.forEachIndexed { i, it -> Text("• $it", style = MaterialTheme.typography.bodySmall, color = c.teal, modifier = Modifier.riseIn(celebrate, linesGo, 1 + i).padding(top = 4.dp)) }
            lines.watch.forEachIndexed { i, it -> Text("• $it", style = MaterialTheme.typography.bodySmall, color = c.gold, modifier = Modifier.riseIn(celebrate, linesGo, 1 + lines.wins.size + i).padding(top = 4.dp)) }
            lines.next.forEachIndexed { i, it -> Text(it, style = MaterialTheme.typography.bodySmall, color = c.blue, modifier = Modifier.riseIn(celebrate, linesGo, 1 + lines.wins.size + lines.watch.size + i).padding(top = 4.dp)) }
            lines.sameRest?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 4.dp)) }
            lines.footnote?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp)) }
        }
        if (onAskCoach != null) {
            TextButton(onClick = onAskCoach, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text(stringResource(R.string.debrief_ask_coach), color = c.teal)
            }
        }
    }
}
