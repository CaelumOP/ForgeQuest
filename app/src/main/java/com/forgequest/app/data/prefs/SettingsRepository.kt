package com.forgequest.app.data.prefs

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.forgequest.app.domain.Equipment
import com.forgequest.app.domain.inferEquipmentFromText
import com.forgequest.app.domain.parseEquipmentSet
import com.forgequest.app.domain.CutPace
import com.forgequest.app.domain.DayActivity
import com.forgequest.app.domain.GainPace
import com.forgequest.app.domain.NutritionGoal
import com.forgequest.app.domain.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class BodyType { MALE, FEMALE }

/** AI is on if the person chose so; with no choice yet it is on only for people who already saved a key. */
fun resolveAiOn(optIn: Boolean?, hasKey: Boolean): Boolean = optIn ?: hasKey

/** Chat is the fourth tab (and "ask the coach" shows) only with AI on and a key saved; AI on alone can have no key. */
fun chatIsTab(aiOn: Boolean, apiKey: String): Boolean = aiOn && apiKey.isNotBlank()

data class Settings(
    val onboarded: Boolean = false,
    val name: String = "",
    val bodyType: BodyType = BodyType.MALE,
    val kcalGoal: Int = 2200,
    val proteinGoal: Int = 140,
    /** Planned sessions per week that count as "hit". 0 means: use the plan's training days. */
    val weeklyTargetOverride: Int = 0,
    val handLengthCm: Float = 0f,
    val palmWidthCm: Float = 0f,
    val fastModel: String = "",
    val smartModel: String = "",
    /** Newest Flash-Lite: first words in about a second, used for everyday chat. */
    val liteModel: String = "",
    /** When the model list was last read from Google; new models ship often, so it's refreshed weekly. */
    val modelsCheckedAt: Long = 0,
    val lastCheckinWeek: Long = -1,
    val driveEmail: String = "",
    val lastBackupAt: Long = 0,
    /** Owner-only Gemini app bridge (see data/bridge). */
    val bridgeEnabled: Boolean = false,
    val bridgeDataId: String = "",
    val bridgeLastWrite: Long = 0,
    val bridgeLastReplyModified: Long = 0,
    val bridgeLastReadAt: Long = 0,
    val bridgeToday: String = "",
    val bridgeIssues: String = "",
    /** Meals from the owner's "ForgeQuest Food" Gem, read from a Drive Doc into the Food tab inbox. */
    val geminiMealsEnabled: Boolean = false,
    val lastDocImportModified: Long = 0,
    /** The person separately allowed reading their Drive to import a "Workout Plan" doc. */
    val driveDocsEnabled: Boolean = false,
    /** Lasting facts for the coach (injuries, equipment, preferences), one per line. */
    val coachNotes: String = "",
    val theme: String = "system",
    /** Insight comments shown lately, "kind:variant:epochDay;..." (domain/Insights.kt). Disposable: not in the backup. */
    val insightHistory: String = "",
    /** The one-time unlock of the second badge set has run (see ProgressRepository.evaluateBadges). Not in the backup. */
    val badgesV2Backfill: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val unit: String = "kg",
    val reminderHour: Int = -1,
    val biometricLock: Boolean = false,
    /** Optional, for the calorie estimate. 0 means not given. */
    val heightCm: Float = 0f,
    val birthYear: Int = 0,
    /** Carbs are off unless the person turns them on in Profile -> Goals. */
    val trackCarbs: Boolean = false,
    /** Fitbit readiness on Home through Health Connect; off hides the card everywhere. */
    val healthEnabled: Boolean = true,
    /** Health Connect has been read at least once; until then missing access means "never connected", not "taken back". */
    val healthConnected: Boolean = false,
    val carbsGoal: Int = 250,
    /** Grams of protein per kg of body weight behind the protein suggestion. */
    val proteinPerKg: Float = 1.8f,
    /** Share of calories kept for fat when suggesting carbs, in percent. */
    val fatSharePct: Int = 25,
    /** Comma-separated Equipment enum names or preset id; empty means not yet specified. */
    val equipment: String = "",
    /** Optional body fat percentage (4..60) for Katch-McArdle lean-mass BMR; 0 means not set. */
    val bodyFatPct: Float = 0f,
    /** Cut, Maintain or Gain: drives the calorie and protein suggestions and what the coach aims for. */
    val nutritionGoal: NutritionGoal = NutritionGoal.MAINTAIN,
    /** Epoch day the goal was last changed, so the weight-trend note waits for real data under the new goal. */
    val goalSetDay: Long = 0,
    /** How active a normal day is outside workouts (calorie plan v2); watch steps override it on Android. */
    val dayActivity: DayActivity = DayActivity.SITTING,
    val cutPace: CutPace = CutPace.STANDARD,
    val gainPace: GainPace = GainPace.STANDARD,
    /** Body weight when the calorie target was last set, so a 2.5 kg change can offer a fresh suggestion. 0 = unknown. */
    val targetWeightKg: Float = 0f,
    /** Epoch day the calorie target last changed (by hand or by a tap on a suggestion): updates wait 14 days after it. */
    val targetSetDay: Long = 0,
    /** Calorie suggestion cards on Home stay hidden until this epoch day ("Not now"). */
    val calorieSnoozeUntil: Long = 0,
    /** The one-time "more accurate calorie target" card was answered. */
    val calorieV2Seen: Boolean = false,
    /** The one-time "make N your weekly target?" card on Home was answered (either way). */
    val targetNudgeAnswered: Boolean = false,
    /** "Just log" mode: hides streaks, levels, badges, readiness and consistency reminders. Nothing is deleted; XP still accrues. */
    val justLog: Boolean = false,
    /** Null until the person flips the AI switch; then the key check below decides (so people with a saved key stay on). */
    val aiOptIn: Boolean? = null,
    /** The plan builder's last answers as JSON (see PlanAnswers), so a rebuild starts from them. Not part of backups. */
    val planBuilderAnswers: String? = null,
) {
    val weightUnit: WeightUnit
        get() = WeightUnit.fromCodeOrLabel(unit)

    /** Available equipment set from explicit setting or inferred from coachNotes; empty if unknown. */
    val configuredEquipment: Set<Equipment>
        get() = parseEquipmentSet(equipment)
            .ifEmpty { inferEquipmentFromText(coachNotes).orEmpty() }
}

private val Context.dataStore by preferencesDataStore("settings")

class SettingsRepository(private val context: Context) {

    private object K {
        val onboarded = booleanPreferencesKey("onboarded")
        val name = stringPreferencesKey("name")
        val bodyType = stringPreferencesKey("body_type")
        val kcal = intPreferencesKey("kcal_goal")
        val protein = intPreferencesKey("protein_goal")
        val weeklyTarget = intPreferencesKey("weekly_target")
        val hand = floatPreferencesKey("hand_length_cm")
        val palm = floatPreferencesKey("palm_width_cm")
        val fastModel = stringPreferencesKey("fast_model")
        val smartModel = stringPreferencesKey("smart_model")
        val liteModel = stringPreferencesKey("lite_model")
        val modelsCheckedAt = longPreferencesKey("models_checked_at")
        val lastCheckin = longPreferencesKey("last_checkin_week")
        val driveEmail = stringPreferencesKey("drive_email")
        val lastBackup = longPreferencesKey("last_backup_at")
        val lastDocImport = longPreferencesKey("last_doc_import_modified")
        val bridgeEnabled = booleanPreferencesKey("bridge_enabled")
        val bridgeDataId = stringPreferencesKey("bridge_data_id")
        val bridgeLastWrite = longPreferencesKey("bridge_last_write")
        val bridgeLastReplyModified = longPreferencesKey("bridge_last_reply_modified")
        val bridgeLastReadAt = longPreferencesKey("bridge_last_read_at")
        val bridgeToday = stringPreferencesKey("bridge_today")
        val bridgeIssues = stringPreferencesKey("bridge_issues")
        val geminiMeals = booleanPreferencesKey("gemini_meals_enabled")
        val coachNotes = stringPreferencesKey("coach_notes")
        val driveDocs = booleanPreferencesKey("drive_docs_enabled")
        val theme = stringPreferencesKey("theme")
        val insightHistory = stringPreferencesKey("insight_history")
        val badgesV2Backfill = booleanPreferencesKey("badges_v2_backfill")
        val haptics = booleanPreferencesKey("haptics_enabled")
        val unit = stringPreferencesKey("weight_unit")
        val reminderHour = intPreferencesKey("reminder_hour")
        val biometric = booleanPreferencesKey("biometric_lock")
        val height = floatPreferencesKey("height_cm")
        val birthYear = intPreferencesKey("birth_year")
        val trackCarbs = booleanPreferencesKey("track_carbs")
        val healthEnabled = booleanPreferencesKey("health_enabled")
        val healthConnected = booleanPreferencesKey("health_connected")
        val carbsGoal = intPreferencesKey("carbs_goal")
        val proteinPerKg = floatPreferencesKey("protein_per_kg")
        val fatShare = intPreferencesKey("fat_share_pct")
        val equipment = stringPreferencesKey("equipment")
        val bodyFatPct = floatPreferencesKey("body_fat_pct")
        val nutritionGoal = stringPreferencesKey("nutrition_goal")
        val goalSetDay = longPreferencesKey("goal_set_day")
        val dayActivity = stringPreferencesKey("day_activity")
        val cutPace = stringPreferencesKey("cut_pace")
        val gainPace = stringPreferencesKey("gain_pace")
        val targetWeightKg = floatPreferencesKey("target_weight_kg")
        val targetSetDay = longPreferencesKey("target_set_day")
        val calorieSnoozeUntil = longPreferencesKey("calorie_snooze_until")
        val calorieV2Seen = booleanPreferencesKey("calorie_v2_seen")
        val targetNudgeAnswered = booleanPreferencesKey("target_nudge_answered")
        val justLog = booleanPreferencesKey("just_log")
        val aiEnabled = booleanPreferencesKey("ai_enabled")
        val planBuilderAnswers = stringPreferencesKey("plan_builder_answers")
    }

    /** A damaged settings file reads as defaults rather than crashing every screen that watches settings. */
    val settings: Flow<Settings> = context.dataStore.data
        .catch { e -> if (e is java.io.IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw e }
        .map { p -> p.toSettings() }
        .distinctUntilChanged()

    suspend fun current(): Settings = settings.first()

    private fun Preferences.toSettings(): Settings {
        val rawUnit = this[K.unit] ?: "kg"
        val normalizedUnit = WeightUnit.fromCodeOrLabel(rawUnit).code
        return Settings(
            onboarded = this[K.onboarded] ?: false,
            name = this[K.name] ?: "",
            bodyType = runCatching { BodyType.valueOf(this[K.bodyType] ?: "MALE") }.getOrDefault(BodyType.MALE),
            kcalGoal = (this[K.kcal] ?: 2200).coerceAtLeast(1200),
            proteinGoal = this[K.protein] ?: 140,
            weeklyTargetOverride = this[K.weeklyTarget] ?: 0,
            handLengthCm = this[K.hand] ?: 0f,
            palmWidthCm = this[K.palm] ?: 0f,
            fastModel = this[K.fastModel] ?: "",
            smartModel = this[K.smartModel] ?: "",
            liteModel = this[K.liteModel] ?: "",
            modelsCheckedAt = this[K.modelsCheckedAt] ?: 0,
            lastCheckinWeek = this[K.lastCheckin] ?: -1,
            driveEmail = this[K.driveEmail] ?: "",
            lastBackupAt = this[K.lastBackup] ?: 0,
            bridgeEnabled = this[K.bridgeEnabled] ?: false,
            bridgeDataId = this[K.bridgeDataId] ?: "",
            bridgeLastWrite = this[K.bridgeLastWrite] ?: 0,
            bridgeLastReplyModified = this[K.bridgeLastReplyModified] ?: 0,
            bridgeLastReadAt = this[K.bridgeLastReadAt] ?: 0,
            bridgeToday = this[K.bridgeToday] ?: "",
            bridgeIssues = this[K.bridgeIssues] ?: "",
            geminiMealsEnabled = this[K.geminiMeals] ?: false,
            lastDocImportModified = this[K.lastDocImport] ?: 0,
            coachNotes = this[K.coachNotes] ?: "",
            driveDocsEnabled = this[K.driveDocs] ?: false,
            theme = this[K.theme] ?: "system",
            insightHistory = this[K.insightHistory] ?: "",
            badgesV2Backfill = this[K.badgesV2Backfill] ?: false,
            hapticsEnabled = this[K.haptics] ?: true,
            unit = normalizedUnit,
            reminderHour = this[K.reminderHour] ?: -1,
            biometricLock = this[K.biometric] ?: false,
            heightCm = this[K.height] ?: 0f,
            birthYear = this[K.birthYear] ?: 0,
            trackCarbs = this[K.trackCarbs] ?: false,
            healthEnabled = this[K.healthEnabled] ?: true,
            healthConnected = this[K.healthConnected] ?: false,
            carbsGoal = this[K.carbsGoal] ?: 250,
            proteinPerKg = this[K.proteinPerKg] ?: 1.8f,
            fatSharePct = this[K.fatShare] ?: 25,
            equipment = this[K.equipment] ?: "",
            bodyFatPct = this[K.bodyFatPct] ?: 0f,
            nutritionGoal = runCatching { NutritionGoal.valueOf(this[K.nutritionGoal] ?: "MAINTAIN") }.getOrDefault(NutritionGoal.MAINTAIN),
            goalSetDay = this[K.goalSetDay] ?: 0,
            dayActivity = DayActivity.parse(this[K.dayActivity]),
            cutPace = CutPace.parse(this[K.cutPace]),
            gainPace = GainPace.parse(this[K.gainPace]),
            targetWeightKg = this[K.targetWeightKg] ?: 0f,
            targetSetDay = this[K.targetSetDay] ?: 0,
            calorieSnoozeUntil = this[K.calorieSnoozeUntil] ?: 0,
            calorieV2Seen = this[K.calorieV2Seen] ?: false,
            targetNudgeAnswered = this[K.targetNudgeAnswered] ?: false,
            justLog = this[K.justLog] ?: false,
            aiOptIn = this[K.aiEnabled],
            planBuilderAnswers = this[K.planBuilderAnswers],
        )
    }

    suspend fun update(transform: (Settings) -> Settings) {
        context.dataStore.edit { p ->
            val s = transform(p.toSettings())
            p[K.onboarded] = s.onboarded
            p[K.name] = s.name
            p[K.bodyType] = s.bodyType.name
            p[K.kcal] = s.kcalGoal.coerceAtLeast(1200)
            p[K.protein] = s.proteinGoal
            p[K.weeklyTarget] = s.weeklyTargetOverride
            p[K.hand] = s.handLengthCm
            p[K.palm] = s.palmWidthCm
            p[K.fastModel] = s.fastModel
            p[K.smartModel] = s.smartModel
            p[K.liteModel] = s.liteModel
            p[K.modelsCheckedAt] = s.modelsCheckedAt
            p[K.lastCheckin] = s.lastCheckinWeek
            p[K.driveEmail] = s.driveEmail
            p[K.lastBackup] = s.lastBackupAt
            p[K.bridgeEnabled] = s.bridgeEnabled
            p[K.bridgeDataId] = s.bridgeDataId
            p[K.bridgeLastWrite] = s.bridgeLastWrite
            p[K.bridgeLastReplyModified] = s.bridgeLastReplyModified
            p[K.bridgeLastReadAt] = s.bridgeLastReadAt
            p[K.bridgeToday] = s.bridgeToday
            p[K.bridgeIssues] = s.bridgeIssues
            p[K.geminiMeals] = s.geminiMealsEnabled
            p[K.lastDocImport] = s.lastDocImportModified
            p[K.coachNotes] = s.coachNotes
            p[K.driveDocs] = s.driveDocsEnabled
            p[K.theme] = s.theme
            p[K.insightHistory] = s.insightHistory
            p[K.badgesV2Backfill] = s.badgesV2Backfill
            p[K.haptics] = s.hapticsEnabled
            p[K.unit] = WeightUnit.fromCodeOrLabel(s.unit).code
            p[K.reminderHour] = s.reminderHour
            p[K.biometric] = s.biometricLock
            p[K.height] = s.heightCm
            p[K.birthYear] = s.birthYear
            p[K.trackCarbs] = s.trackCarbs
            p[K.healthEnabled] = s.healthEnabled
            p[K.healthConnected] = s.healthConnected
            p[K.carbsGoal] = s.carbsGoal
            p[K.proteinPerKg] = s.proteinPerKg
            p[K.fatShare] = s.fatSharePct
            p[K.equipment] = s.equipment
            p[K.bodyFatPct] = s.bodyFatPct
            p[K.nutritionGoal] = s.nutritionGoal.name
            p[K.goalSetDay] = s.goalSetDay
            p[K.dayActivity] = s.dayActivity.name
            p[K.cutPace] = s.cutPace.name
            p[K.gainPace] = s.gainPace.name
            p[K.targetWeightKg] = s.targetWeightKg
            p[K.targetSetDay] = s.targetSetDay
            p[K.calorieSnoozeUntil] = s.calorieSnoozeUntil
            p[K.calorieV2Seen] = s.calorieV2Seen
            p[K.targetNudgeAnswered] = s.targetNudgeAnswered
            p[K.justLog] = s.justLog
            s.aiOptIn?.let { p[K.aiEnabled] = it }
            s.planBuilderAnswers?.let { p[K.planBuilderAnswers] = it }
        }
    }

    // --- Weekly streak targets of finished weeks, keyed by Monday's epoch day. Restore clears them. ---

    private val streakPrefs = context.getSharedPreferences("streak_targets", Context.MODE_PRIVATE)

    fun weekTargets(): Map<Long, Int> =
        streakPrefs.all.mapNotNull { (week, target) -> week.toLongOrNull()?.let { w -> (target as? Int)?.let { w to it } } }.toMap()

    fun saveWeekTargets(targets: Map<Long, Int>) =
        streakPrefs.edit().apply { targets.forEach { (week, target) -> putInt(week.toString(), target) } }.apply()

    fun clearWeekTargets() = streakPrefs.edit().clear().apply()

    // --- Gemini API key: encrypted with a hardware-backed Keystore key, kept out of cloud backups. ---

    private val keyPrefs = context.getSharedPreferences("secure_key", Context.MODE_PRIVATE)
    private val _apiKey = MutableStateFlow(readApiKey())
    val apiKey: StateFlow<String> = _apiKey

    fun setApiKey(value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            keyPrefs.edit().remove("gemini").apply()
        } else {
            keyPrefs.edit().putString("gemini", encrypt(trimmed)).apply()
        }
        _apiKey.value = trimmed
    }

    private fun readApiKey(): String = readKey("gemini")

    private fun readKey(name: String): String {
        val stored = keyPrefs.getString(name, null) ?: return ""
        return runCatching { decrypt(stored) }.getOrDefault("")
    }

    /** Removes the key of the retired fast-chat provider once; a no-op when it is already gone. */
    fun clearRetiredKeys() {
        if (keyPrefs.contains("groq")) keyPrefs.edit().remove("groq").apply()
    }

    // --- AI opt-in: off for new people, on for anyone who already saved a key. ---

    private val aiScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
    private val _aiOptIn = MutableStateFlow<Boolean?>(
        runCatching { kotlinx.coroutines.runBlocking { context.dataStore.data.first()[K.aiEnabled] } }.getOrNull()
    )

    private fun hasAnyKey() = _apiKey.value.isNotBlank()

    /** Same answer as [aiOn], read right now (the flow below can lag a moment behind a key change). */
    fun isAiOn(): Boolean = resolveAiOn(_aiOptIn.value, hasAnyKey())

    val aiOn: StateFlow<Boolean> = kotlinx.coroutines.flow.combine(_aiOptIn, _apiKey) { o, a ->
        resolveAiOn(o, a.isNotBlank())
    }.distinctUntilChanged().stateIn(aiScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, isAiOn())

    /** [chatIsTab] as a flow. */
    val chatTab: StateFlow<Boolean> = kotlinx.coroutines.flow.combine(aiOn, _apiKey) { o, k -> chatIsTab(o, k) }
        .distinctUntilChanged().stateIn(aiScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, chatIsTab(isAiOn(), _apiKey.value))

    suspend fun setBadgesV2Backfill(done: Boolean) {
        update { it.copy(badgesV2Backfill = done) }
    }

    suspend fun setInsightHistory(value: String) {
        update { it.copy(insightHistory = value) }
    }

    suspend fun setAiEnabled(on: Boolean) {
        _aiOptIn.value = on
        update { it.copy(aiOptIn = on) }
    }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val body = Base64.encodeToString(cipher.doFinal(plain.toByteArray()), Base64.NO_WRAP)
        return "$iv:$body"
    }

    private fun decrypt(stored: String): String {
        val (iv, body) = stored.split(":")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        return String(cipher.doFinal(Base64.decode(body, Base64.NO_WRAP)))
    }

    private companion object {
        const val ALIAS = "forgequest_gemini_key"
    }
}
