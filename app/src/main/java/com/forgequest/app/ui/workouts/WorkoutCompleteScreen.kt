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
import com.forgequest.app.domain.DayType
import com.forgequest.app.domain.Insight
import com.forgequest.app.domain.InsightKind
import com.forgequest.app.domain.MILESTONE_KINDS
import com.forgequest.app.domain.Slot
import com.forgequest.app.ui.headlineRes
import com.forgequest.app.ui.insightRes
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
    onRemind: () -> Unit,
    reminded: Boolean = false,
    onDone: () -> Unit,
) {
    val c = Fit.colors
    var filled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { filled = true }
    val leveledUp = sum.after.level > sum.before.level
    val context = LocalContext.current
    val res = context.resources
    val locale = androidx.compose.ui.platform.LocalConfiguration.current
    val names = sum.exerciseNames
    val lines = remember(sum, locale) { sum.debrief?.let { d -> DebriefText.render(d, res, { id -> names[id] ?: id }) { kg -> sum.unit.format(kg) } } }
    val headline = if (sum.justLog) null else sum.headline?.let { h -> headlineRes(h.type, h.variant)?.let { res.getString(it, h.up, h.compared) } } ?: lines?.headline
    val type = sum.dayType
    // The animation is for a record or a milestone only.
    val big = type == DayType.PR || type == DayType.MILESTONE
    val milestone = if (type == DayType.MILESTONE) sum.insights.firstOrNull { it.kind in MILESTONE_KINDS } else null
    val insights = sum.insights.filter { it !== milestone }.take(2)
    BackHandler(onBack = onDone)
    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.live_complete), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
        Text(sum.title, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        Spacer(Modifier.height(20.dp))
        // The top of the screen is whatever is most notable about this workout.
        val hero = sum.prHero
        if (type == DayType.PR && hero != null) {
            Text(names[hero.win.exerciseId] ?: hero.win.exerciseId, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.riseIn(big, filled, 0))
            prValue(res, sum.unit, hero.win.kind, hero.win.weightKg, hero.win.reps, hero.win.seconds)?.let {
                Text(it, style = MaterialTheme.typography.headlineMedium, color = c.gold, modifier = Modifier.riseIn(big, filled, 1))
            }
            prValue(res, sum.unit, hero.win.kind, hero.oldKg, hero.oldReps, hero.oldSeconds)?.let {
                Text(stringResource(R.string.finish_pr_was, it), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.riseIn(big, filled, 2))
            }
            headline?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, modifier = Modifier.padding(top = 12.dp)) }
        } else if (milestone != null) {
            Text(insightText(milestone, res, names, sum.unit), style = MaterialTheme.typography.headlineSmall, color = c.textPrimary, modifier = Modifier.riseIn(big, filled, 0))
            headline?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.padding(top = 12.dp)) }
        } else if (type == DayType.ROUGH) {
            headline?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary) }
        } else {
            headline?.let { Text(it, style = MaterialTheme.typography.headlineSmall, color = c.textPrimary) }
            if (type == DayType.ORDINARY) {
                val comparison = sum.debrief?.volumeChange?.let { volumeText(it) }
                    ?: sum.score?.takeIf { it.compared > 0 }?.let { stringResource(R.string.score_up, it.up, it.compared) }
                comparison?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.padding(top = 6.dp)) }
            }
        }
        // The word for the day, with the number small beside it; a welcome or a rough day gets no score.
        val score = sum.score
        if (sum.tier != null && score != null && score.showNumber && type != DayType.FIRST_OR_BACK && type != DayType.ROUGH) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(stringResource(sum.tier.labelRes), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.debrief_score, score.score), style = MaterialTheme.typography.labelMedium.tabular(), color = c.textSecondary,
                    modifier = Modifier.padding(bottom = 2.dp).semantics { contentDescription = res.getString(R.string.debrief_score, score.score) },
                )
            }
        }
        if (lines != null) {
            Spacer(Modifier.height(8.dp))
            // Green for what went well, gold for what to watch, blue for next time.
            lines.wins.forEachIndexed { i, it -> Text("• $it", style = MaterialTheme.typography.bodySmall, color = c.teal, modifier = Modifier.riseIn(big, filled, 3 + i).padding(top = 4.dp)) }
            lines.watch.forEachIndexed { i, it -> Text("• $it", style = MaterialTheme.typography.bodySmall, color = c.gold, modifier = Modifier.riseIn(big, filled, 3 + lines.wins.size + i).padding(top = 4.dp)) }
            lines.next.forEachIndexed { i, it -> Text(it, style = MaterialTheme.typography.bodySmall, color = c.blue, modifier = Modifier.riseIn(big, filled, 3 + lines.wins.size + lines.watch.size + i).padding(top = 4.dp)) }
            lines.sameRest?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 4.dp)) }
        }
        val badges = if (sum.justLog) emptyList() else sum.newBadges
        badges.forEach { badge ->
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = c.gold, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.live_badge_unlocked, badge.localTitle()), style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
            }
        }
        if (insights.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            insights.forEach { Text(insightText(it, res, names, sum.unit), style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, modifier = Modifier.padding(top = 6.dp)) }
        }
        lines?.footnote?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = c.textSecondary, modifier = Modifier.padding(top = 12.dp)) }
        if (sum.offerReminder) {
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Notifications, contentDescription = null, tint = c.teal, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(if (reminded) R.string.live_reminder_on else R.string.live_reminder_title),
                    style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, modifier = Modifier.weight(1f),
                )
                if (!reminded) TextButton(onClick = onRemind) {
                    Text(stringResource(R.string.live_reminder_button), color = c.teal)
                }
            }
        }
        // What comes next, so finishing points forward (Fitbod ends every workout with the next one).
        sum.nextUp?.let { (dow, title) ->
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = c.teal, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.live_next_up, com.forgequest.app.domain.Dates.dayName(dow), title), style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
            }
        }
        // Sets and time, one small line.
        Spacer(Modifier.height(20.dp))
        Text(
            listOf(pluralStringResource(R.plurals.hist_sets, sum.doneSets, sum.doneSets), stringResource(R.string.hist_minutes, sum.minutes)).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
        )
        sum.clippedEndAt?.let { end ->
            Text(
                stringResource(R.string.live_ended_at_last_set, java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(end - com.forgequest.app.domain.WorkoutEnd.GRACE_MS))),
                style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
            )
        }
    }
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp)) {
        // "Just log" mode: XP still accrues, but the level strip stays out of sight.
        if (!sum.justLog) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (leveledUp) stringResource(R.string.live_level_up, sum.after.level) else stringResource(R.string.live_level, sum.after.level),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (leveledUp) c.gold else c.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text("⁦+${sum.xpGained}⁩ ${stringResource(R.string.live_xp)}", style = MaterialTheme.typography.labelMedium.tabular(), color = c.textSecondary)
            }
            Spacer(Modifier.height(6.dp))
            val from = if (leveledUp) 0f else sum.before.progress
            ProgressLine(if (filled) sum.after.progress else from, c.blue, c.blueTrack, height = 4.dp)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.profile_xp_line, sum.after.xpIntoLevel, sum.after.xpForNextLevel, sum.after.level + 1), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
            Spacer(Modifier.height(12.dp))
        }
        PillButton(stringResource(R.string.home_done), onClick = onDone, modifier = Modifier.fillMaxWidth())
    }
    }
}

/** A record's value the way the person reads it: "62.5 kg × 5", "12 reps" or "45 seconds"; null with nothing to show. */
private fun prValue(res: android.content.res.Resources, unit: WeightUnit, kind: WorkoutScore.PrKind, kg: Float?, reps: Int?, seconds: Int?): String? = when {
    kind == WorkoutScore.PrKind.HOLD && seconds != null -> res.getQuantityString(R.plurals.debrief_n_seconds, seconds, seconds)
    kg != null && reps != null -> res.getString(R.string.finish_value_weight_reps, unit.format(kg), reps)
    reps != null -> res.getQuantityString(R.plurals.debrief_n_reps, reps, reps)
    seconds != null -> res.getQuantityString(R.plurals.debrief_n_seconds, seconds, seconds)
    else -> null
}

/** One insight as a sentence: its slots become the sentence's arguments in order (a plural's quantity is the first count). */
internal fun insightText(i: Insight, res: android.content.res.Resources, names: Map<String, String>, unit: WeightUnit): String {
    val pounds = unit == WeightUnit.LB
    val r = insightRes(i.kind, i.variant, pounds)
    val args = i.slots.map { slot ->
        when (slot) {
            // Lifetime kilograms are round kg marks; for pounds the same mark is shown converted.
            is Slot.Count -> if (pounds && i.kind == InsightKind.CROSS_KG) kotlin.math.floor(slot.n * 2.2046225).toInt() else slot.n
            is Slot.Kg -> unit.format(slot.kg)
            is Slot.Seconds -> res.getQuantityString(R.plurals.debrief_n_seconds, slot.s, slot.s)
            is Slot.Exercise -> names[slot.id] ?: slot.id
            is Slot.Cue -> com.forgequest.app.ui.exerciseCues(slot.id)?.toList()?.getOrNull(slot.line)?.let { res.getString(it) }.orEmpty()
            is Slot.Group -> res.getString(slot.muscle.labelRes)
            is Slot.Weekday -> com.forgequest.app.domain.Dates.dayName(slot.day)
        }
    }.toTypedArray()
    // The plural form follows the number actually shown (the lb figure for CROSS_KG in pounds).
    val quantity = i.slots.indexOfFirst { it is Slot.Count }.takeIf { it >= 0 }?.let { args[it] as Int }
    return if (r.plural && quantity != null) res.getQuantityString(r.id, quantity, *args) else res.getString(r.id, *args)
}

/** "Volume up 8%": total volume against the last workout of this plan day. Words, not a +/- sign, so RTL never flips it. */
@Composable
private fun volumeText(p: Int): String = when {
    p > 0 -> stringResource(R.string.score_volume_up, p)
    p < 0 -> stringResource(R.string.score_volume_down, -p)
    else -> stringResource(R.string.score_volume_same)
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
        if (score != null) Text(listOfNotNull(scoreReasons(score), debrief?.volumeChange?.let { volumeText(it) }).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 2.dp))
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
