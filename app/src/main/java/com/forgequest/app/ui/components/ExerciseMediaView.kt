package com.forgequest.app.ui.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.forgequest.app.R
import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.seed.ExerciseMedia
import com.forgequest.app.ui.theme.Fit
import com.forgequest.app.ui.theme.ForgeShape

fun exerciseMediaKey(ex: ExerciseEntity): String? =
    ex.mediaKey.ifBlank { ExerciseMedia.byId[ex.id].orEmpty() }.takeIf { it.isNotBlank() }

/** 48x48 rounded neutral thumbnail for exercise lists (start pose or neutral icon placeholder). */
@Composable
fun ExerciseThumbnail(
    exercise: ExerciseEntity,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val c = Fit.colors
    val context = LocalContext.current
    val key = remember(exercise.id, exercise.mediaKey) { exerciseMediaKey(exercise) }
    var failed by remember(key) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(size)
            .clip(ForgeShape.Sm)
            .background(c.cardHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (key != null && !failed) {
            val req = remember(key) {
                ImageRequest.Builder(context)
                    .data("file:///android_asset/ex_media/${key}_0.webp")
                    .crossfade(false)
                    .build()
            }
            AsyncImage(
                model = req,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { failed = true },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.FitnessCenter,
                contentDescription = null,
                tint = c.textSecondary,
                modifier = Modifier.size(size * 0.45f),
            )
        }
    }
}

/** Start -> finish photo pair card for ExerciseDetailScreen; stacks vertically at large text. */
@Composable
fun ExerciseMediaPair(
    exercise: ExerciseEntity,
    modifier: Modifier = Modifier,
) {
    val c = Fit.colors
    val key = remember(exercise.id, exercise.mediaKey) { exerciseMediaKey(exercise) }
    val largeText = LocalConfiguration.current.fontScale >= 1.4f

    if (key == null) {
        FitCard(modifier) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(48.dp).clip(ForgeShape.Sm).background(c.cardHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.FitnessCenter, contentDescription = null, tint = c.textSecondary)
                }
                Text(
                    stringResource(R.string.detail_no_media),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary,
                )
            }
        }
        return
    }

    FitCard(modifier) {
        if (largeText) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MediaFrameTile(key, frame = 0, label = stringResource(R.string.detail_start_pose), Modifier.fillMaxWidth())
                MediaFrameTile(key, frame = 1, label = stringResource(R.string.detail_finish_pose), Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                MediaFrameTile(key, frame = 0, label = stringResource(R.string.detail_start_pose), Modifier.weight(1f))
                MediaFrameTile(key, frame = 1, label = stringResource(R.string.detail_finish_pose), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MediaFrameTile(
    mediaKey: String,
    frame: Int,
    label: String,
    modifier: Modifier = Modifier,
) {
    val c = Fit.colors
    val context = LocalContext.current
    var failed by remember(mediaKey, frame) { mutableStateOf(false) }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(ForgeShape.Md)
                .background(c.cardHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (!failed) {
                val req = remember(mediaKey, frame) {
                    ImageRequest.Builder(context)
                        .data("file:///android_asset/ex_media/${mediaKey}_$frame.webp")
                        .crossfade(false)
                        .build()
                }
                AsyncImage(
                    model = req,
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    onError = { failed = true },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(Icons.Rounded.FitnessCenter, contentDescription = null, tint = c.textSecondary)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
    }
}
