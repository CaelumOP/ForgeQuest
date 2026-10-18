package com.forgequest.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forgequest.ai.data.local.entity.BodyWeightLogEntity
import com.forgequest.ai.data.local.entity.UserProfileEntity
import com.forgequest.ai.domain.model.AiModelTier
import com.forgequest.ai.domain.model.AppSettings
import com.forgequest.ai.ui.haptics.HapticsManager
import com.forgequest.ai.ui.theme.BackgroundCanvas
import com.forgequest.ai.ui.theme.OnPrimaryTeal
import com.forgequest.ai.ui.theme.PastelPurple
import com.forgequest.ai.ui.theme.PastelPurpleTrack
import com.forgequest.ai.ui.theme.PrimaryTeal
import com.forgequest.ai.ui.theme.PrimaryTealTrack
import com.forgequest.ai.ui.theme.SecondaryBlue
import com.forgequest.ai.ui.theme.SecondaryBlueTrack
import com.forgequest.ai.ui.theme.SurfaceCard
import com.forgequest.ai.ui.theme.SurfaceContainerHigh
import com.forgequest.ai.ui.theme.TextPrimary
import com.forgequest.ai.ui.theme.TextSecondary
import com.forgequest.ai.ui.theme.WarmAmberTrack
import com.forgequest.ai.ui.theme.WarmGold
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SettingsScreen(
    settings: AppSettings,
    liveProgressSummaryMarkdown: String = "",
    userProfile: UserProfileEntity = UserProfileEntity(),
    weightLogs: List<BodyWeightLogEntity> = emptyList(),
    haptics: HapticsManager?,
    onSaveSettings: (AppSettings) -> Unit,
    onLogBodyWeight: (Float) -> Unit = {},
    onConnectGoogleDrive: () -> Unit,
    onSyncWorkoutPlanDocNow: () -> Unit,
    onBackupToDriveNow: () -> Unit,
    onRestoreFromDriveBackup: () -> Unit,
    modifier: Modifier = Modifier
) {
    var apiKey by remember(settings.geminiApiKey) { mutableStateOf(settings.geminiApiKey) }
    var showApiKey by remember { mutableStateOf(false) }
    var activeModel by remember(settings.activeModel) { mutableStateOf(settings.activeModel) }
    var handLength by remember(settings.handLengthCm) { mutableStateOf(settings.handLengthCm.toString()) }
    var palmWidth by remember(settings.palmWidthCm) { mutableStateOf(settings.palmWidthCm.toString()) }
    var calorieGoal by remember(settings.dailyCalorieGoal) { mutableStateOf(settings.dailyCalorieGoal.toString()) }
    var proteinGoal by remember(settings.dailyProteinGoal) { mutableStateOf(settings.dailyProteinGoal.toString()) }
    var enableNutritionStreak by remember(settings.enableDailyNutritionStreak) {
        mutableStateOf(settings.enableDailyNutritionStreak)
    }

    var currentWeightDraft by remember(userProfile.currentWeightKg) {
        mutableFloatStateOf(userProfile.currentWeightKg)
    }
    var savedBanner by remember { mutableStateOf(false) }

    val currentRank = remember(userProfile.level) {
        com.forgequest.ai.domain.model.HunterRank.forLevel(userProfile.level)
    }
    val rankLabel = currentRank.rankCode
    val rankTitle = currentRank.title
    val rankLetter = rankLabel.first().toString()
    val xpWithinLevel = (userProfile.totalXp % 500L).toInt()
    val nextLevel = userProfile.level + 1
    val xpProgress = (xpWithinLevel.toFloat() / 500f).coerceIn(0f, 1f)

    val friendlySyncStatus = remember(settings.lastDriveSyncStatus) {
        val raw = settings.lastDriveSyncStatus.trim()
        when {
            raw.isBlank() || raw.contains("Never", ignoreCase = true) || raw.contains("Not synced", ignoreCase = true) ->
                "Not synced yet"
            raw.startsWith("Last synced", ignoreCase = true) -> raw
            raw.contains("Synced", ignoreCase = true) || raw.contains("Backup", ignoreCase = true) ->
                "Last synced today"
            else -> raw
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundCanvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Screen Title: "Profile" (28.sp, Sentence case)
        Text(
            text = "Profile",
            style = MaterialTheme.typography.headlineLarge,
            color = TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Normal
        )

        // 1. Hunter Rank Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(SecondaryBlueTrack),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = rankLetter,
                            color = SecondaryBlue,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "$rankLabel · $rankTitle",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Level ${userProfile.level} · $xpWithinLevel / 500 XP to Level $nextLevel",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }

                LinearProgressIndicator(
                    progress = { xpProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = SecondaryBlue,
                    trackColor = SecondaryBlueTrack,
                    strokeCap = StrokeCap.Round
                )
            }
        }

        // 2. Body Weight History & Quick Log Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(SecondaryBlueTrack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MonitorWeight,
                            contentDescription = null,
                            tint = SecondaryBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Body weight",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Track daily weight and recent history",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(SurfaceContainerHigh)
                                .clickable {
                                    haptics?.tick()
                                    currentWeightDraft = ((currentWeightDraft - 0.1f).coerceAtLeast(30f) * 10f).toInt() / 10f
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "−",
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Text(
                            text = String.format(Locale.US, "%.1f kg", currentWeightDraft),
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(SurfaceContainerHigh)
                                .clickable {
                                    haptics?.tick()
                                    currentWeightDraft = ((currentWeightDraft + 0.1f).coerceAtMost(250f) * 10f).toInt() / 10f
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+",
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Button(
                        onClick = {
                            haptics?.setComplete()
                            onLogBodyWeight(currentWeightDraft)
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryTeal,
                            contentColor = OnPrimaryTeal
                        )
                    ) {
                        Text(
                            text = "Save",
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (weightLogs.isNotEmpty()) {
                    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM d", Locale.US) }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Recent entries",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                        weightLogs.take(4).forEach { log ->
                            val dateStr = runCatching {
                                LocalDate.ofEpochDay(log.epochDay).format(dateFormatter)
                            }.getOrDefault("Recent")
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SurfaceContainerHigh)
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = dateStr,
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = String.format(Locale.US, "%.1f kg", log.weightKg),
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3A. Teal Circle (#1C2E2B fill, #5CD6C0 key icon) — AI key & engine
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(PrimaryTealTrack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = PrimaryTeal,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AI key & engine",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Saved privately on your phone",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        savedBanner = false
                    },
                    label = { Text("Gemini API key") },
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle key visibility"
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Default response mode",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    fontSize = 13.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val isFast = activeModel == AiModelTier.GEMINI_3_8_FLASH
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isFast) PrimaryTeal else SurfaceContainerHigh)
                            .clickable {
                                haptics?.tick()
                                activeModel = AiModelTier.GEMINI_3_8_FLASH
                                savedBanner = false
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Fast",
                            color = if (isFast) OnPrimaryTeal else TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    val isDeep = activeModel == AiModelTier.GEMINI_4_ARGON
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDeep) SecondaryBlue else SurfaceContainerHigh)
                            .clickable {
                                haptics?.tick()
                                activeModel = AiModelTier.GEMINI_4_ARGON
                                savedBanner = false
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Deep",
                            color = if (isDeep) OnPrimaryTeal else TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 3B. Blue Circle (#1D2536 fill, #8AB4F8 hand icon) — Hand scale calibration
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(SecondaryBlueTrack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PanTool,
                            contentDescription = null,
                            tint = SecondaryBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hand scale calibration",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Used when scanning meals to estimate portion size",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = handLength,
                        onValueChange = {
                            handLength = it
                            savedBanner = false
                        },
                        label = { Text("Hand length (cm)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = palmWidth,
                        onValueChange = {
                            palmWidth = it
                            savedBanner = false
                        },
                        label = { Text("Palm width (cm)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 3C. Amber Circle (#2D2618 fill, #FDD663 target icon) — Daily nutrition goals
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(WarmAmberTrack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrackChanges,
                            contentDescription = null,
                            tint = WarmGold,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Daily nutrition goals",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Customize your daily protein and calorie targets",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = calorieGoal,
                        onValueChange = {
                            calorieGoal = it
                            savedBanner = false
                        },
                        label = { Text("Daily calories") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = proteinGoal,
                        onValueChange = {
                            proteinGoal = it
                            savedBanner = false
                        },
                        label = { Text("Daily protein (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Show nutrition streak",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Display a daily nutrition badge alongside your workout streak",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                    Switch(
                        checked = enableNutritionStreak,
                        onCheckedChange = {
                            haptics?.tick()
                            enableNutritionStreak = it
                            savedBanner = false
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = OnPrimaryTeal,
                            checkedTrackColor = PrimaryTeal
                        )
                    )
                }

                Button(
                    onClick = {
                        haptics?.setComplete()
                        val updated = settings.copy(
                            geminiApiKey = apiKey.trim(),
                            activeModel = activeModel,
                            handLengthCm = (handLength.toFloatOrNull() ?: 18.5f).coerceIn(10f, 30f),
                            palmWidthCm = (palmWidth.toFloatOrNull() ?: 8.5f).coerceIn(5f, 18f),
                            dailyCalorieGoal = (calorieGoal.toIntOrNull() ?: 2100).coerceIn(800, 8000),
                            dailyProteinGoal = (proteinGoal.toIntOrNull() ?: 140).coerceIn(30, 450),
                            enableDailyNutritionStreak = enableNutritionStreak
                        )
                        onSaveSettings(updated)
                        savedBanner = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryTeal,
                        contentColor = OnPrimaryTeal
                    )
                ) {
                    Text(
                        text = if (savedBanner) "Settings saved" else "Save settings",
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 3D. Purple Circle (#261E35 fill, #C58AF9 cloud icon) — Backup & Google Drive sync
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(PastelPurpleTrack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = PastelPurple,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Backup & Google Drive sync",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = friendlySyncStatus,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }

                if (settings.googleDriveAccountEmail != null) {
                    Text(
                        text = "Connected as ${settings.googleDriveAccountEmail}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PrimaryTeal,
                        fontSize = 13.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            haptics?.tick()
                            onConnectGoogleDrive()
                        }
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (settings.googleDriveAccountEmail == null) {
                                "Connect Google Drive"
                            } else {
                                "Switch Google Drive account"
                            },
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Button(
                    onClick = {
                        haptics?.tick()
                        onSyncWorkoutPlanDocNow()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryTeal,
                        contentColor = OnPrimaryTeal
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Import workout plan from Docs",
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceContainerHigh,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                haptics?.tick()
                                onBackupToDriveNow()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                tint = PrimaryTeal,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Backup now",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceContainerHigh,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                haptics?.tick()
                                onRestoreFromDriveBackup()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = SecondaryBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Restore backup",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
