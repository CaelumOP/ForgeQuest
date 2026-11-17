package com.forgequest.app.ui.workouts

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forgequest.app.R
import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.SessionEntity
import com.forgequest.app.data.db.SetEntity
import com.forgequest.app.domain.ExerciseKind
import com.forgequest.app.domain.WeightUnit
import com.forgequest.app.ui.components.SegmentedTabs
import com.forgequest.app.ui.components.TonalPill
import com.forgequest.app.ui.components.reflowText
import com.forgequest.app.ui.components.rememberFullSheetState
import com.forgequest.app.ui.exerciseCues
import com.forgequest.app.ui.theme.Fit
import com.forgequest.app.ui.theme.ForgeShape
import com.forgequest.app.ui.theme.tabular
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class PeekSessionHistory(
    val session: SessionEntity,
    val sets: List<SetEntity>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuickExercisePeekSheet(
    exercise: ExerciseEntity,
    unit: WeightUnit,
    history: List<PeekSessionHistory>,
    canUseLastTime: Boolean,
    onDismiss: () -> Unit,
    onWatchForm: () -> Unit,
    onEditSetupNote: () -> Unit,
    onUseLastTime: () -> Unit,
    onFullStats: () -> Unit,
) {
    val c = Fit.colors
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    val mediaKey = exercise.mediaKey.ifBlank { exercise.id }
    var startBmp by remember(mediaKey) { mutableStateOf<ImageBitmap?>(null) }
    var finishBmp by remember(mediaKey) { mutableStateOf<ImageBitmap?>(null) }
    var mediaLoaded by remember(mediaKey) { mutableStateOf(false) }

    LaunchedEffect(mediaKey) {
        mediaLoaded = false
        val loaded = withContext(Dispatchers.IO) {
            fun loadAsset(path: String): ImageBitmap? = runCatching {
                context.assets.open(path).use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()
            loadAsset("ex_media/${mediaKey}_0.webp") to loadAsset("ex_media/${mediaKey}_1.webp")
        }
        startBmp = loaded.first
        finishBmp = loaded.second
        mediaLoaded = true
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberFullSheetState(),
        containerColor = c.card,
        shape = RoundedCornerShape(topStart = ForgeShape.LgRadius, topEnd = ForgeShape.LgRadius),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        exercise.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = c.textPrimary,
                    )
                    val subtitle = listOfNotNull(
                        exercise.kind.label(),
                        exercise.musclesLabel().ifBlank { null },
                    ).joinToString(" · ")
                    if (subtitle.isNotBlank()) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_close), tint = c.textSecondary)
                }
            }

            Spacer(Modifier.height(12.dp))
            SegmentedTabs(
                options = listOf(
                    stringResource(R.string.live41_peek_tab_form),
                    stringResource(R.string.live41_peek_tab_history),
                ),
                selected = tab,
                onSelect = { tab = it },
                wrap = true,
            )
            Spacer(Modifier.height(16.dp))

            if (tab == 0) {
                // Start / Finish photos or neutral placeholder
                if (startBmp != null || finishBmp != null) {
                    val stack = reflowText()
                    if (stack) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            PeekMediaTile(startBmp, stringResource(R.string.live41_peek_start), Modifier.fillMaxWidth())
                            PeekMediaTile(finishBmp, stringResource(R.string.live41_peek_finish), Modifier.fillMaxWidth())
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PeekMediaTile(startBmp, stringResource(R.string.live41_peek_start), Modifier.weight(1f))
                            PeekMediaTile(finishBmp, stringResource(R.string.live41_peek_finish), Modifier.weight(1f))
                        }
                    }
                } else if (mediaLoaded) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(ForgeShape.Md)
                            .background(c.cardHigh)
                            .padding(vertical = 20.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.FitnessCenter, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.live41_peek_no_media),
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textSecondary,
                            )
                        }
                    }
                }

                // Pinned setup note
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(ForgeShape.Md)
                        .background(c.cardHigh)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.live41_setup_note_label), style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                        Text(
                            exercise.setupNote.ifBlank { stringResource(R.string.live41_setup_note_hint) },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (exercise.setupNote.isBlank()) c.textSecondary else c.textPrimary,
                        )
                    }
                    IconButton(onClick = onEditSetupNote) {
                        Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.live41_setup_note_edit), tint = c.teal)
                    }
                }

                // Form cues
                Spacer(Modifier.height(12.dp))
                val cues = exerciseCues(exercise.id)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(ForgeShape.Md)
                        .background(c.cardHigh)
                        .padding(14.dp),
                ) {
                    Text(stringResource(R.string.live41_peek_cues), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                    Spacer(Modifier.height(6.dp))
                    if (cues != null) {
                        Text("• ${stringResource(cues.first)}", style = MaterialTheme.typography.bodySmall, color = c.textPrimary)
                        Spacer(Modifier.height(4.dp))
                        Text("• ${stringResource(cues.second)}", style = MaterialTheme.typography.bodySmall, color = c.textPrimary)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.live41_peek_mistake), style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                        Text(stringResource(cues.third), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    } else {
                        Text(stringResource(R.string.live41_peek_no_cues), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TonalPill(
                        stringResource(R.string.cd_watch_form),
                        onClick = onWatchForm,
                        icon = Icons.Rounded.PlayArrow,
                        tint = c.teal,
                        modifier = Modifier.weight(1f),
                    )
                    TonalPill(
                        stringResource(R.string.live41_peek_full_stats),
                        onClick = onFullStats,
                        icon = Icons.AutoMirrored.Rounded.OpenInNew,
                        tint = c.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                if (history.isEmpty()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(ForgeShape.Md)
                            .background(c.cardHigh)
                            .padding(20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(R.string.live41_peek_no_history), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                    }
                } else {
                    val sUnit = stringResource(R.string.unit_s)
                    val arabic = Locale.getDefault().language == "ar"
                    val fmtLocale = if (arabic) Locale.forLanguageTag("ar-u-nu-latn") else Locale.US
                    val dateFmt = remember(arabic) {
                        DateTimeFormatter.ofPattern(if (arabic) "d MMM" else "MMM d", fmtLocale)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        history.take(3).forEach { entry ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(ForgeShape.Md)
                                    .background(c.cardHigh)
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    LocalDate.ofEpochDay(entry.session.epochDay).format(dateFmt),
                                    style = MaterialTheme.typography.labelLarge.tabular(),
                                    color = c.textSecondary,
                                    modifier = Modifier.width(68.dp),
                                )
                                Text(
                                    entry.sets.joinToString("  ") { set ->
                                        when (exercise.kind) {
                                            ExerciseKind.HOLD -> "${set.seconds ?: 0}$sUnit"
                                            ExerciseKind.WEIGHTED -> "${set.reps ?: 0}×${unit.number(set.weightKg ?: 0f)}"
                                            ExerciseKind.REPS -> "${set.reps ?: 0}"
                                        }
                                    },
                                    style = MaterialTheme.typography.bodyMedium.tabular(),
                                    color = c.textPrimary,
                                    modifier = Modifier.weight(1f),
                                )
                                if (entry.sets.any { it.isPr }) {
                                    Spacer(Modifier.width(8.dp))
                                    PrTag()
                                }
                            }
                        }
                    }
                    if (canUseLastTime) {
                        Spacer(Modifier.height(12.dp))
                        TonalPill(
                            stringResource(R.string.live41_peek_use_last),
                            onClick = onUseLastTime,
                            tint = c.teal,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onFullStats, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.live41_peek_full_stats), color = c.teal)
                }
            }
        }
    }
}

@Composable
private fun PeekMediaTile(
    bmp: ImageBitmap?,
    caption: String,
    modifier: Modifier = Modifier,
) {
    val c = Fit.colors
    Column(
        modifier
            .clip(ForgeShape.Md)
            .background(c.cardHigh)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(ForgeShape.Sm)
                .background(c.card),
            contentAlignment = Alignment.Center,
        ) {
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = caption,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(Icons.Rounded.FitnessCenter, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            caption,
            style = MaterialTheme.typography.labelMedium,
            color = c.textSecondary,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
