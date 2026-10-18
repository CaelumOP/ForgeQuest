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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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
    val lastCheckinWeek: Long = -1,
    val driveEmail: String = "",
    val lastBackupAt: Long = 0,
    val lastDocImportModified: Long = 0,
    /** The person separately allowed reading their Drive to import a "Workout Plan" doc. */
    val driveDocsEnabled: Boolean = false,
    /** Lasting facts for the coach (injuries, equipment, preferences), one per line. */
    val coachNotes: String = "",
)

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
        val lastCheckin = longPreferencesKey("last_checkin_week")
        val driveEmail = stringPreferencesKey("drive_email")
        val lastBackup = longPreferencesKey("last_backup_at")
        val lastDocImport = longPreferencesKey("last_doc_import_modified")
        val coachNotes = stringPreferencesKey("coach_notes")
        val driveDocs = booleanPreferencesKey("drive_docs_enabled")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p -> p.toSettings() }

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
        lastCheckinWeek = this[K.lastCheckin] ?: -1,
        driveEmail = this[K.driveEmail] ?: "",
        lastBackupAt = this[K.lastBackup] ?: 0,
        lastDocImportModified = this[K.lastDocImport] ?: 0,
        coachNotes = this[K.coachNotes] ?: "",
        driveDocsEnabled = this[K.driveDocs] ?: false,
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
            p[K.lastCheckin] = s.lastCheckinWeek
            p[K.driveEmail] = s.driveEmail
            p[K.lastBackup] = s.lastBackupAt
            p[K.lastDocImport] = s.lastDocImportModified
            p[K.coachNotes] = s.coachNotes
            p[K.driveDocs] = s.driveDocsEnabled
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

    private fun readApiKey(): String {
        val stored = keyPrefs.getString("gemini", null) ?: return ""
        return runCatching { decrypt(stored) }.getOrDefault("")
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
