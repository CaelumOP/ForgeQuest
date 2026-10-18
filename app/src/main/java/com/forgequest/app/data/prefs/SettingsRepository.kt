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
import com.forgequest.app.domain.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class BodyType { MALE, FEMALE }

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
    /** Which model the coach uses: "auto", "lite", "flash" or "pro". */
    val coachModel: String = "auto",
    /** Groq chat model picked for the saved Groq key. */
    val groqModel: String = "",
    val lastCheckinWeek: Long = -1,
    val driveEmail: String = "",
    val lastBackupAt: Long = 0,
    val lastDocImportModified: Long = 0,
    /** The person separately allowed reading their Drive to import a "Workout Plan" doc. */
    val driveDocsEnabled: Boolean = false,
    /** Lasting facts for the coach (injuries, equipment, preferences), one per line. */
    val coachNotes: String = "",
    val theme: String = "system",
    val hapticsEnabled: Boolean = true,
    val unit: String = "kg",
    val reminderHour: Int = -1,
    val biometricLock: Boolean = false,
    /** Optional, for the calorie estimate. 0 means not given. */
    val heightCm: Float = 0f,
    val birthYear: Int = 0,
    /** Carbs are off unless the person turns them on in Profile â†’ Goals. */
    val trackCarbs: Boolean = false,
    /** Fitbit readiness on Home through Health Connect; off hides the card everywhere. */
    val healthEnabled: Boolean = true,
    val carbsGoal: Int = 250,
    /** Grams of protein per kg of body weight behind the protein suggestion. */
    val proteinPerKg: Float = 1.8f,
    /** Share of calories kept for fat when suggesting carbs, in percent. */
    val fatSharePct: Int = 25,
) {
    val weightUnit: WeightUnit
        get() = if (unit.equals("lb", ignoreCase = true)) WeightUnit.LB else WeightUnit.KG
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
        val coachModel = stringPreferencesKey("coach_model")
        val groqModel = stringPreferencesKey("groq_model")
        val lastCheckin = longPreferencesKey("last_checkin_week")
        val driveEmail = stringPreferencesKey("drive_email")
        val lastBackup = longPreferencesKey("last_backup_at")
        val lastDocImport = longPreferencesKey("last_doc_import_modified")
        val coachNotes = stringPreferencesKey("coach_notes")
        val driveDocs = booleanPreferencesKey("drive_docs_enabled")
        val theme = stringPreferencesKey("theme")
        val haptics = booleanPreferencesKey("haptics_enabled")
        val unit = stringPreferencesKey("weight_unit")
        val reminderHour = intPreferencesKey("reminder_hour")
        val biometric = booleanPreferencesKey("biometric_lock")
        val height = floatPreferencesKey("height_cm")
        val birthYear = intPreferencesKey("birth_year")
        val trackCarbs = booleanPreferencesKey("track_carbs")
        val healthEnabled = booleanPreferencesKey("health_enabled")
        val carbsGoal = intPreferencesKey("carbs_goal")
        val proteinPerKg = floatPreferencesKey("protein_per_kg")
        val fatShare = intPreferencesKey("fat_share_pct")
    }

    /** A damaged settings file reads as defaults rather than crashing every screen that watches settings. */
    val settings: Flow<Settings> = context.dataStore.data
        .catch { e -> if (e is java.io.IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw e }
        .map { p -> p.toSettings() }
        .distinctUntilChanged()

    suspend fun current(): Settings = settings.first()

    private fun Preferences.toSettings() = Settings(
        onboarded = this[K.onboarded] ?: false,
        name = this[K.name] ?: "",
        bodyType = runCatching { BodyType.valueOf(this[K.bodyType] ?: "MALE") }.getOrDefault(BodyType.MALE),
        kcalGoal = this[K.kcal] ?: 2200,
        proteinGoal = this[K.protein] ?: 140,
        weeklyTargetOverride = this[K.weeklyTarget] ?: 0,
        handLengthCm = this[K.hand] ?: 0f,
        palmWidthCm = this[K.palm] ?: 0f,
        fastModel = this[K.fastModel] ?: "",
        smartModel = this[K.smartModel] ?: "",
        liteModel = this[K.liteModel] ?: "",
        modelsCheckedAt = this[K.modelsCheckedAt] ?: 0,
        coachModel = this[K.coachModel] ?: "auto",
        groqModel = this[K.groqModel] ?: "",
        lastCheckinWeek = this[K.lastCheckin] ?: -1,
        driveEmail = this[K.driveEmail] ?: "",
        lastBackupAt = this[K.lastBackup] ?: 0,
        lastDocImportModified = this[K.lastDocImport] ?: 0,
        coachNotes = this[K.coachNotes] ?: "",
        driveDocsEnabled = this[K.driveDocs] ?: false,
        theme = this[K.theme] ?: "system",
        hapticsEnabled = this[K.haptics] ?: true,
        unit = this[K.unit] ?: "kg",
        reminderHour = this[K.reminderHour] ?: -1,
        biometricLock = this[K.biometric] ?: false,
        heightCm = this[K.height] ?: 0f,
        birthYear = this[K.birthYear] ?: 0,
        trackCarbs = this[K.trackCarbs] ?: false,
        healthEnabled = this[K.healthEnabled] ?: true,
        carbsGoal = this[K.carbsGoal] ?: 250,
        proteinPerKg = this[K.proteinPerKg] ?: 1.8f,
        fatSharePct = this[K.fatShare] ?: 25,
    )

    suspend fun update(transform: (Settings) -> Settings) {
        context.dataStore.edit { p ->
            val s = transform(p.toSettings())
            p[K.onboarded] = s.onboarded
            p[K.name] = s.name
            p[K.bodyType] = s.bodyType.name
            p[K.kcal] = s.kcalGoal
            p[K.protein] = s.proteinGoal
            p[K.weeklyTarget] = s.weeklyTargetOverride
            p[K.hand] = s.handLengthCm
            p[K.palm] = s.palmWidthCm
            p[K.fastModel] = s.fastModel
            p[K.smartModel] = s.smartModel
            p[K.liteModel] = s.liteModel
            p[K.modelsCheckedAt] = s.modelsCheckedAt
            p[K.coachModel] = s.coachModel
            p[K.groqModel] = s.groqModel
            p[K.lastCheckin] = s.lastCheckinWeek
            p[K.driveEmail] = s.driveEmail
            p[K.lastBackup] = s.lastBackupAt
            p[K.lastDocImport] = s.lastDocImportModified
            p[K.coachNotes] = s.coachNotes
            p[K.driveDocs] = s.driveDocsEnabled
            p[K.theme] = s.theme
            p[K.haptics] = s.hapticsEnabled
            p[K.unit] = s.unit
            p[K.reminderHour] = s.reminderHour
            p[K.biometric] = s.biometricLock
            p[K.height] = s.heightCm
            p[K.birthYear] = s.birthYear
            p[K.trackCarbs] = s.trackCarbs
            p[K.healthEnabled] = s.healthEnabled
            p[K.carbsGoal] = s.carbsGoal
            p[K.proteinPerKg] = s.proteinPerKg
            p[K.fatShare] = s.fatSharePct
        }
    }

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

    // --- Optional Groq key for fast chat, same encryption, also kept out of backups. ---

    private val _groqKey = MutableStateFlow(readKey("groq"))
    val groqKey: StateFlow<String> = _groqKey

    fun setGroqKey(value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) keyPrefs.edit().remove("groq").apply() else keyPrefs.edit().putString("groq", encrypt(trimmed)).apply()
        _groqKey.value = trimmed
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
