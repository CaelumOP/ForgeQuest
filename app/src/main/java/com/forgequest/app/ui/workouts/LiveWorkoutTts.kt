package com.forgequest.app.ui.workouts

import android.content.Context
import android.speech.tts.TextToSpeech
import com.forgequest.app.R
import java.util.Locale

internal enum class HoldCueEvent {
    READY_3,
    READY_2,
    READY_1,
    START,
    HALFWAY,
    COUNT_3,
    COUNT_2,
    COUNT_1,
    DONE,
}

/** Pure mapping from hold timer elapsed seconds to a spoken/haptic cue event. */
internal fun holdCueEvent(elapsedSeconds: Int, targetSeconds: Int): HoldCueEvent? {
    val left = targetSeconds - elapsedSeconds
    return when {
        elapsedSeconds == -3 -> HoldCueEvent.READY_3
        elapsedSeconds == -2 -> HoldCueEvent.READY_2
        elapsedSeconds == -1 -> HoldCueEvent.READY_1
        elapsedSeconds == 0 -> HoldCueEvent.START
        elapsedSeconds == targetSeconds -> HoldCueEvent.DONE
        left == 3 && targetSeconds > 3 -> HoldCueEvent.COUNT_3
        left == 2 && targetSeconds > 2 -> HoldCueEvent.COUNT_2
        left == 1 && targetSeconds > 1 -> HoldCueEvent.COUNT_1
        targetSeconds >= 10 && elapsedSeconds == targetSeconds / 2 -> HoldCueEvent.HALFWAY
        else -> null
    }
}

/**
 * Foreground-only spoken cues for live workouts (rest end + hold countdown) in EN/AR.
 * Lazily initializes [TextToSpeech] only when enabled, and invokes [onFallback] with a
 * chime when no matching voice is available.
 */
internal class LiveWorkoutTts(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var voiceAvailable = false
    private var initLang: String? = null

    fun ensureInitialized(arabic: Boolean) {
        val wantedLang = if (arabic) "ar" else "en"
        if (tts != null && initLang == wantedLang) return
        shutdown()
        initLang = wantedLang
        runCatching {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status != TextToSpeech.SUCCESS) {
                    ready = false
                    voiceAvailable = false
                    return@TextToSpeech
                }
                val engine = tts ?: return@TextToSpeech
                val locale = if (arabic) Locale.forLanguageTag("ar") else Locale.US
                val res = engine.setLanguage(locale)
                ready = true
                voiceAvailable = res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }.onFailure {
            ready = false
            voiceAvailable = false
        }
    }

    fun speak(
        text: String,
        arabic: Boolean,
        enabled: Boolean,
        foreground: Boolean,
        onFallback: () -> Unit,
    ) {
        if (!enabled || !foreground || text.isBlank()) return
        ensureInitialized(arabic)
        val engine = tts
        if (!ready || !voiceAvailable || engine == null) {
            onFallback()
            return
        }
        val code = runCatching {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "fq_${System.currentTimeMillis()}")
        }.getOrDefault(TextToSpeech.ERROR)
        if (code != TextToSpeech.SUCCESS) {
            onFallback()
        }
    }

    fun holdCueText(event: HoldCueEvent, str: (Int) -> String): String = when (event) {
        HoldCueEvent.READY_3, HoldCueEvent.COUNT_3 -> "3"
        HoldCueEvent.READY_2, HoldCueEvent.COUNT_2 -> "2"
        HoldCueEvent.READY_1, HoldCueEvent.COUNT_1 -> "1"
        HoldCueEvent.START -> str(R.string.live41_tts_hold_start)
        HoldCueEvent.HALFWAY -> str(R.string.live41_tts_hold_halfway)
        HoldCueEvent.DONE -> str(R.string.live41_tts_hold_done)
    }

    fun stop() {
        runCatching { tts?.stop() }
    }

    fun shutdown() {
        ready = false
        voiceAvailable = false
        initLang = null
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
    }
}
