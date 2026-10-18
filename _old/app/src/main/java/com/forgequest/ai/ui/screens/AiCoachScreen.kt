package com.forgequest.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forgequest.ai.data.local.entity.ChatMessageEntity
import com.forgequest.ai.domain.model.AiModelTier
import com.forgequest.ai.ui.components.FormattedMarkdownText
import com.forgequest.ai.ui.haptics.HapticsManager
import com.forgequest.ai.ui.theme.BackgroundCanvas
import com.forgequest.ai.ui.theme.OnPrimaryTeal
import com.forgequest.ai.ui.theme.PrimaryTeal
import com.forgequest.ai.ui.theme.PrimaryTealTrack
import com.forgequest.ai.ui.theme.SecondaryBlue
import com.forgequest.ai.ui.theme.SecondaryBlueTrack
import com.forgequest.ai.ui.theme.SurfaceCard
import com.forgequest.ai.ui.theme.SurfaceContainerHigh
import com.forgequest.ai.ui.theme.TextPrimary
import com.forgequest.ai.ui.theme.TextSecondary

private val COACH_QUICK_PROMPTS = listOf(
    "Build a 4-day calisthenics & hypertrophy plan",
    "Review my weekly protein & workout consistency",
    "Create an Upper/Lower + Core split with pull-up progression",
    "Suggest high-protein post-workout meals"
)

@Composable
fun AiCoachScreen(
    messages: List<ChatMessageEntity>,
    activeModelTier: AiModelTier,
    isSendingMessage: Boolean,
    progressSummaryTokenEstimate: Int = 0,
    haptics: HapticsManager?,
    onSwitchModelTier: (AiModelTier) -> Unit,
    onSendMessage: (String) -> Unit,
    onApplyProposedPlan: (ChatMessageEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var inputPrompt by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundCanvas)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        // Clean Header: Title "Coach" (28.sp, Sentence case) + Fast | Deep Segmented Pill Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Coach",
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Normal
            )

            // 2-option segmented pill toggle in #282A2C
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(SurfaceContainerHigh)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isFast = activeModelTier == AiModelTier.GEMINI_3_8_FLASH
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isFast) PrimaryTeal else SurfaceContainerHigh)
                        .clickable {
                            haptics?.tick()
                            onSwitchModelTier(AiModelTier.GEMINI_3_8_FLASH)
                        }
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Fast",
                        color = if (isFast) OnPrimaryTeal else TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (isFast) FontWeight.Medium else FontWeight.Normal
                    )
                }

                val isDeep = activeModelTier == AiModelTier.GEMINI_4_ARGON
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isDeep) SecondaryBlue else SurfaceContainerHigh)
                        .clickable {
                            haptics?.tick()
                            onSwitchModelTier(AiModelTier.GEMINI_4_ARGON)
                        }
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Deep",
                        color = if (isDeep) OnPrimaryTeal else TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (isDeep) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Quick-Prompt Pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            COACH_QUICK_PROMPTS.forEach { prompt ->
                Surface(
                    shape = CircleShape,
                    color = SurfaceContainerHigh,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            haptics?.tick()
                            onSendMessage(prompt)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = PrimaryTeal,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = prompt,
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Chat Message Stream
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (messages.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Personal fitness coach",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Ask for personalized workout routines, form cues, recovery guidance, or nutrition recommendations tailored to your recent progress.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                messages.forEach { msg ->
                    ChatMessageBubble(
                        message = msg,
                        onApplyPlan = {
                            haptics?.questClearAndPr()
                            onApplyProposedPlan(msg)
                        }
                    )
                }
            }

            if (isSendingMessage) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = PrimaryTeal,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Coach is thinking...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PrimaryTeal,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        // Bottom Chat Input Box
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = inputPrompt,
                onValueChange = { inputPrompt = it },
                placeholder = {
                    Text(
                        text = "Ask your coach or request a workout plan...",
                        color = TextSecondary
                    )
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                maxLines = 3
            )

            IconButton(
                onClick = {
                    val trimmed = inputPrompt.trim()
                    if (trimmed.isNotEmpty() && !isSendingMessage) {
                        haptics?.tick()
                        onSendMessage(trimmed)
                        inputPrompt = ""
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(PrimaryTeal, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = OnPrimaryTeal
                )
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessageEntity,
    onApplyPlan: () -> Unit
) {
    val isUser = message.role.equals("user", ignoreCase = true)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.85f else 0.96f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) SecondaryBlueTrack else SurfaceCard
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (isUser) "You" else "Coach",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isUser) SecondaryBlue else PrimaryTeal,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(6.dp))

                if (isUser) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                        fontSize = 15.sp
                    )
                } else {
                    FormattedMarkdownText(
                        markdown = message.content,
                        color = TextPrimary,
                        accentColor = PrimaryTeal
                    )
                }

                // 1-Tap Plan Apply card when proposedPlanJson is non-null
                if (!message.proposedPlanJson.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SurfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.FitnessCenter,
                                    contentDescription = null,
                                    tint = PrimaryTeal,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "7-day workout plan ready",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = onApplyPlan,
                                enabled = !message.isPlanApplied,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (message.isPlanApplied) PrimaryTealTrack else PrimaryTeal,
                                    contentColor = if (message.isPlanApplied) PrimaryTeal else OnPrimaryTeal,
                                    disabledContainerColor = PrimaryTealTrack,
                                    disabledContentColor = PrimaryTeal
                                )
                            ) {
                                Icon(
                                    imageVector = if (message.isPlanApplied) Icons.Default.CheckCircle else Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (message.isPlanApplied) {
                                        "Workout plan applied"
                                    } else {
                                        "Apply workout plan"
                                    },
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
