package com.forgequest.ai.data.remote

import android.util.Base64
import com.forgequest.ai.domain.model.AiModelTier
import com.forgequest.ai.domain.model.VisionFoodAnalysisResult
import com.forgequest.ai.domain.model.WorkoutPlanPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class CoachChatResponse(
    val replyMarkdown: String,
    val modelUsed: String,
    val proposedPlanJson: String? = null
)

class GeminiApiClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    /**
     * Hand-Scaled AI Food Vision analysis using [modelTier].
     * Uses the user's physical hand calibration (`handLengthCm`, `palmWidthCm`) in the system prompt
     * to calibrate plate diameter, food volume, and gram weights via real HTTPS POST to Gemini.
     */
    suspend fun analyzeFoodPhoto(
        jpegBytes: ByteArray,
        apiKey: String,
        modelTier: AiModelTier,
        handLengthCm: Float,
        palmWidthCm: Float
    ): VisionFoodAnalysisResult? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || jpegBytes.isEmpty()) {
            return@withContext null
        }

        val calibrationPrompt = buildHandScaleSystemPrompt(handLengthCm, palmWidthCm)

        val base64Jpeg = runCatching {
            Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
        }.getOrElse {
            java.util.Base64.getEncoder().encodeToString(jpegBytes)
        }

        val requestPayload = buildJsonObject {
            put("systemInstruction", buildJsonObject {
                put("parts", buildJsonArray {
                    add(buildJsonObject { put("text", calibrationPrompt) })
                })
            })
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray {
                        add(buildJsonObject {
                            put(
                                "text",
                                "Analyze this meal photo using the hand reference in frame. " +
                                    "Return ONLY valid JSON matching VisionFoodAnalysisResult schema: " +
                                    "{\"dishName\":\"...\",\"confidence\":0.94,\"handScaleDetected\":true," +
                                    "\"calibrationNote\":\"...\",\"detectedItems\":[{\"name\":\"...\"," +
                                    "\"estimatedGrams\":180,\"calories\":320,\"proteinGrams\":32.5,\"selected\":true}]}"
                            )
                        })
                        add(buildJsonObject {
                            put("inlineData", buildJsonObject {
                                put("mimeType", "image/jpeg")
                                put("data", base64Jpeg)
                            })
                        })
                    })
                })
            })
            put("generationConfig", buildJsonObject {
                put("temperature", 0.2)
                put("responseMimeType", "application/json")
            })
        }

        val rawText = executeGeminiRequestWithFallback(
            modelId = modelTier.modelId,
            apiKey = apiKey,
            requestBodyJson = requestPayload.toString()
        )

        if (rawText != null) {
            val cleanedJson = extractJsonObjectText(rawText)
            val parsed = runCatching {
                json.decodeFromString<VisionFoodAnalysisResult>(cleanedJson)
            }.getOrNull()
            if (parsed != null && parsed.detectedItems.isNotEmpty()) {
                return@withContext parsed
            }
        }

        null
    }

    /**
     * Sends an in-app AI Coach chat message with silent progress summary context in systemInstruction
     * and parses any proposed 1-Tap [WorkoutPlanPayload] JSON block.
     */
    suspend fun sendCoachMessage(
        userPrompt: String,
        progressSummaryMarkdown: String,
        recentHistory: List<Pair<String, String>>,
        apiKey: String,
        modelTier: AiModelTier
    ): CoachChatResponse? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext null
        }

        val systemInstruction = buildCoachSystemInstruction(progressSummaryMarkdown)

        val requestPayload = buildJsonObject {
            put("systemInstruction", buildJsonObject {
                put("parts", buildJsonArray {
                    add(buildJsonObject { put("text", systemInstruction) })
                })
            })
            put("contents", buildJsonArray {
                recentHistory.takeLast(6).forEach { (role, text) ->
                    val geminiRole = if (role == "assistant") "model" else "user"
                    add(buildJsonObject {
                        put("role", geminiRole)
                        put("parts", buildJsonArray {
                            add(buildJsonObject { put("text", text) })
                        })
                    })
                }
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray {
                        add(buildJsonObject {
                            put("text", userPrompt)
                        })
                    })
                })
            })
            put("generationConfig", buildJsonObject {
                put("temperature", 0.5)
            })
        }

        val rawReply = executeGeminiRequestWithFallback(
            modelId = modelTier.modelId,
            apiKey = apiKey,
            requestBodyJson = requestPayload.toString()
        )

        if (!rawReply.isNullOrBlank()) {
            val extractedPlan = extractWorkoutPlanJsonBlock(rawReply)
            return@withContext CoachChatResponse(
                replyMarkdown = rawReply.trim(),
                modelUsed = modelTier.modelId,
                proposedPlanJson = extractedPlan
            )
        }

        null
    }

    fun buildHandScaleSystemPrompt(handLengthCm: Float, palmWidthCm: Float): String {
        return "Reference object in frame: User's flat hand (Length = ${"%.1f".format(handLengthCm)} cm, " +
            "Palm Width = ${"%.1f".format(palmWidthCm)} cm). Use this exact physical reference to calibrate " +
            "plate diameter, food volume, and gram weight before estimating total calories and protein."
    }

    fun extractWorkoutPlanJsonBlock(markdownText: String): String? {
        val fencedRegex = Regex("```(?:json)?\\s*(\\{[\\s\\S]*?\"days\"\\s*:\\s*\\[[\\s\\S]*?\\})\\s*```", RegexOption.IGNORE_CASE)
        val candidateJson = fencedRegex.find(markdownText)?.groupValues?.getOrNull(1)
            ?: run {
                val startIdx = markdownText.indexOf("{\"planName\"")
                    .takeIf { it >= 0 }
                    ?: markdownText.indexOf("{ \"planName\"")
                if (startIdx != null && startIdx >= 0) {
                    val endIdx = markdownText.lastIndexOf('}')
                    if (endIdx > startIdx) markdownText.substring(startIdx, endIdx + 1) else null
                } else {
                    null
                }
            } ?: return null

        return runCatching {
            val parsed = json.decodeFromString<WorkoutPlanPayload>(candidateJson)
            if (parsed.days.isNotEmpty()) {
                json.encodeToString(parsed)
            } else {
                null
            }
        }.getOrNull()
    }

    private fun executeGeminiRequestWithFallback(
        modelId: String,
        apiKey: String,
        requestBodyJson: String
    ): String? {
        val candidateModels = listOf(
            modelId,
            if (modelId == AiModelTier.GEMINI_4_ARGON.modelId) "gemini-2.5-pro" else "gemini-2.5-flash",
            "gemini-2.0-flash"
        ).distinct()

        for (candidate in candidateModels) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$candidate:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(requestBodyJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            val responseText = runCatching {
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val bodyStr = response.body?.string() ?: return@use null
                    parseCandidateTextFromGeminiResponse(bodyStr)
                }
            }.getOrNull()

            if (!responseText.isNullOrBlank()) {
                return responseText
            }
        }
        return null
    }

    private fun parseCandidateTextFromGeminiResponse(responseJson: String): String? {
        return runCatching {
            val root = json.parseToJsonElement(responseJson).jsonObject
            val candidates = root["candidates"]?.jsonArray ?: return null
            val firstCandidate = candidates.firstOrNull()?.jsonObject ?: return null
            val content = firstCandidate["content"]?.jsonObject ?: return null
            val parts = content["parts"]?.jsonArray ?: return null
            parts.mapNotNull { part ->
                (part as? JsonObject)?.get("text")?.jsonPrimitive?.content
            }.joinToString("\n").takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun extractJsonObjectText(raw: String): String {
        val trimmed = raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val firstBrace = trimmed.indexOf('{')
        val lastBrace = trimmed.lastIndexOf('}')
        return if (firstBrace >= 0 && lastBrace > firstBrace) {
            trimmed.substring(firstBrace, lastBrace + 1)
        } else {
            trimmed
        }
    }

    private fun buildCoachSystemInstruction(progressSummaryMarkdown: String): String {
        return """
            You are the ForgeQuest AI Coach (Calisthenics, Weighted Streetlifting & High-Protein Nutrition Specialist).
            Keep your coaching motivating, data-driven, and zero-guilt (celebrate surpluses as muscle-building fuel!).
            
            IMPORTANT — 1-TAP WORKOUT PLAN APPLICATION:
            Whenever the user asks to create, change, or update their workout plan/schedule/split, include a valid JSON block fenced in ```json ... ``` at the end of your message matching this exact schema:
            {
              "planName": "String",
              "description": "String",
              "days": [
                {
                  "dayOfWeek": 1,
                  "title": "Day 1 Title",
                  "isRestDay": false,
                  "exercises": [
                    {
                      "exerciseId": "pull_up_weighted",
                      "name": "Weighted Pull-Ups",
                      "category": "WEIGHTED",
                      "targetSets": 4,
                      "targetRepsOrSeconds": 8,
                      "restDurationSeconds": 90,
                      "primaryMuscles": ["LATS", "UPPER_BACK_TRAPS"],
                      "secondaryMuscles": ["BICEPS", "FOREARMS"]
                    }
                  ]
                }
              ]
            }
            Valid ExerciseCategory values: CALISTHENICS, WEIGHTED, ISOMETRIC.
            Valid MuscleGroup values: CHEST, FRONT_DELTS, SIDE_DELTS, BICEPS, FOREARMS, ABS_CORE, OBLIQUES, QUADS, CALVES, UPPER_BACK_TRAPS, LATS, REAR_DELTS, TRICEPS, LOWER_BACK, GLUTES, HAMSTRINGS.
            
            Current User Progress Summary:
            $progressSummaryMarkdown
        """.trimIndent()
    }
}
