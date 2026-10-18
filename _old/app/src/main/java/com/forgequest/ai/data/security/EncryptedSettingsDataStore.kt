package com.forgequest.ai.data.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import com.forgequest.ai.domain.model.AiModelTier
import com.forgequest.ai.domain.model.AppSettings
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class EncryptedSettingsDataStore(context: Context) {

    private val appContext = context.applicationContext

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val aead: Aead? by lazy {
        runCatching {
            AeadConfig.register()
            val keysetHandle = AndroidKeysetManager.Builder()
                .withSharedPref(
                    appContext,
                    KEYSET_NAME,
                    PREF_FILE_NAME
                )
                .withKeyTemplate(KeyTemplates.get(listOf("AES256", "GCM").joinToString("_")))
                .withMasterKeyUri(MASTER_KEY_URI)
                .build()
                .keysetHandle
            keysetHandle.getPrimitive(Aead::class.java)
        }.getOrNull()
    }

    private val serializer = object : Serializer<AppSettings> {
        override val defaultValue: AppSettings = AppSettings()

        override suspend fun readFrom(input: InputStream): AppSettings {
            val encryptedBytes = input.readBytes()
            if (encryptedBytes.isEmpty()) return defaultValue
            return runCatching {
                val decryptedBytes = decryptBytes(encryptedBytes)
                json.decodeFromString<AppSettings>(decryptedBytes.decodeToString())
            }.getOrDefault(defaultValue)
        }

        override suspend fun writeTo(t: AppSettings, output: OutputStream) {
            val plainBytes = json.encodeToString(t).encodeToByteArray()
            val encryptedBytes = encryptBytes(plainBytes)
            output.write(encryptedBytes)
        }
    }

    private val dataStore: DataStore<AppSettings> = DataStoreFactory.create(
        serializer = serializer,
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        produceFile = { appContext.dataStoreFile(DATASTORE_FILE_NAME) }
    )

    val settingsFlow: Flow<AppSettings> = dataStore.data

    suspend fun getSettings(): AppSettings = dataStore.data.first()

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        dataStore.updateData { current -> transform(current) }
    }

    suspend fun updateGeminiApiKey(apiKey: String) {
        dataStore.updateData { it.copy(geminiApiKey = apiKey.trim()) }
    }

    suspend fun updateActiveModel(model: AiModelTier) {
        dataStore.updateData { it.copy(activeModel = model) }
    }

    suspend fun updateHandMeasurements(handLengthCm: Float, palmWidthCm: Float) {
        dataStore.updateData {
            it.copy(
                handLengthCm = handLengthCm.coerceIn(12f, 28f),
                palmWidthCm = palmWidthCm.coerceIn(5f, 15f)
            )
        }
    }

    suspend fun updateMacroGoals(dailyCalorieGoal: Int, dailyProteinGoal: Int) {
        dataStore.updateData {
            it.copy(
                dailyCalorieGoal = dailyCalorieGoal.coerceIn(1000, 6000),
                dailyProteinGoal = dailyProteinGoal.coerceIn(40, 400)
            )
        }
    }

    suspend fun updateNutritionStreakEnabled(enabled: Boolean) {
        dataStore.updateData { it.copy(enableDailyNutritionStreak = enabled) }
    }

    suspend fun updateGoogleDriveAccount(email: String?) {
        dataStore.updateData { it.copy(googleDriveAccountEmail = email) }
    }

    suspend fun updateDriveSyncMetadata(
        lastModifiedMillis: Long? = null,
        statusMessage: String
    ) {
        dataStore.updateData { current ->
            current.copy(
                lastImportedDriveDocModifiedMillis = lastModifiedMillis ?: current.lastImportedDriveDocModifiedMillis,
                lastDriveSyncStatus = statusMessage
            )
        }
    }

    private fun encryptBytes(plainText: ByteArray): ByteArray {
        val tinkAead = aead
        if (tinkAead != null) {
            return tinkAead.encrypt(plainText, ASSOCIATED_DATA)
        }
        // Fallback JVM AES-GCM encryption when Android Keystore is unavailable (e.g., local unit tests)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = FALLBACK_IV
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(FALLBACK_KEY, "AES"), spec)
        cipher.updateAAD(ASSOCIATED_DATA)
        return cipher.doFinal(plainText)
    }

    private fun decryptBytes(cipherText: ByteArray): ByteArray {
        val tinkAead = aead
        if (tinkAead != null) {
            return tinkAead.decrypt(cipherText, ASSOCIATED_DATA)
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, FALLBACK_IV)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(FALLBACK_KEY, "AES"), spec)
        cipher.updateAAD(ASSOCIATED_DATA)
        return cipher.doFinal(cipherText)
    }

    companion object {
        const val DATASTORE_FILE_NAME = "encrypted_forgequest_secrets.pb"
        private const val KEYSET_NAME = "forgequest_tink_keyset"
        private const val PREF_FILE_NAME = "encrypted_forgequest_secrets"
        private const val MASTER_KEY_URI = "android-keystore://forgequest_master_key"
        private val ASSOCIATED_DATA = "ForgeQuestAI-v1-Secrets".encodeToByteArray()

        // Only used in non-Android-Keystore JVM unit test environments
        private val FALLBACK_KEY = "ForgeQuestFallbackKey32Bytes1234".encodeToByteArray()
        private val FALLBACK_IV = "ForgeQuestIV".encodeToByteArray()

        @Volatile
        private var INSTANCE: EncryptedSettingsDataStore? = null

        fun getInstance(context: Context): EncryptedSettingsDataStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: EncryptedSettingsDataStore(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
}
