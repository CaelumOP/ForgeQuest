package com.forgequest.app.data.ai

import com.forgequest.app.data.prefs.SettingsRepository

/**
 * Which Gemini models this key uses. Google ships new Flash versions every few weeks and retires old ones,
 * so the choice made when the key was saved is re-checked once a week (and right away if Flash-Lite,
 * added later, is still unknown).
 */
class ModelStore(private val settings: SettingsRepository, private val gemini: GeminiClient) {

    suspend fun current(): GeminiModels {
        val s = settings.current()
        val stale = s.liteModel.isBlank() || s.fastModel.isBlank() || System.currentTimeMillis() - s.modelsCheckedAt > WEEK_MS
        if (stale && settings.apiKey.value.isNotBlank()) {
            // A failed check (offline, bad key) keeps the old choice; the call that follows reports the real error.
            gemini.discoverModels(settings.apiKey.value).getOrNull()?.let { save(it); return it }
        }
        return GeminiModels(
            fast = s.fastModel.ifBlank { GeminiClient.FALLBACK_FAST },
            smart = s.smartModel.ifBlank { GeminiClient.FALLBACK_FAST },
            lite = s.liteModel.ifBlank { GeminiClient.FALLBACK_LITE },
        )
    }

    suspend fun save(models: GeminiModels) = settings.update {
        it.copy(fastModel = models.fast, smartModel = models.smart, liteModel = models.lite, modelsCheckedAt = System.currentTimeMillis())
    }

    private companion object {
        const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
    }
}
