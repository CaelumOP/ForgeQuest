package com.forgequest.ai.ui.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Centralized Hardware Haptics Controller for ForgeQuest AI.
 * Strictly zero audio/sound dependencies (no MediaPlayer, no SoundPool, no audio assets).
 * Uses Android Vibrator / VibratorManager and VibrationEffect.Composition with graceful
 * fallback to one-shot / waveform vibration on older or lower-end devices.
 */
class HapticsManager(context: Context) {

    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (_: Throwable) {
        null
    }

    private fun hasVibrator(): Boolean = try {
        vibrator?.hasVibrator() == true
    } catch (_: Throwable) {
        false
    }

    /**
     * Crisp tactile tick (`PRIMITIVE_CLICK`) on `+ / -` quantity and rep steppers.
     */
    fun tick() {
        val vib = vibrator ?: return
        if (!hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                vib.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)
            ) {
                val effect = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.75f)
                    .compose()
                vib.vibrate(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                vib.vibrate(VibrationEffect.createOneShot(18L, 120))
            }
        } catch (_: Throwable) {
            // Safe no-op in unit tests or unsupported hardware
        }
    }

    /**
     * Double `PRIMITIVE_THUD` when checking off a workout set or completing a rest timer.
     */
    fun setComplete() {
        val vib = vibrator ?: return
        if (!hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                vib.areAllPrimitivesSupported(
                    VibrationEffect.Composition.PRIMITIVE_THUD,
                    VibrationEffect.Composition.PRIMITIVE_CLICK
                )
            ) {
                val effect = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.85f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f, 75)
                    .compose()
                vib.vibrate(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                vib.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)
            ) {
                val effect = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.85f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 75)
                    .compose()
                vib.vibrate(effect)
            } else {
                val timings = longArrayOf(0L, 35L, 55L, 50L)
                val amplitudes = intArrayOf(0, 180, 0, 255)
                vib.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            }
        } catch (_: Throwable) {
            // Safe no-op
        }
    }

    /**
     * Multi-step rising sequence (`PRIMITIVE_QUICK_RISE` + `PRIMITIVE_SPIN` + `PRIMITIVE_THUD`)
     * when submitting a workout ("Quest Clear"), unlocking a PR, or reigniting a dying streak flame.
     */
    fun questClearAndPr() {
        val vib = vibrator ?: return
        if (!hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                vib.areAllPrimitivesSupported(
                    VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
                    VibrationEffect.Composition.PRIMITIVE_SPIN,
                    VibrationEffect.Composition.PRIMITIVE_THUD
                )
            ) {
                val effect = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.9f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_SPIN, 0.95f, 90)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f, 110)
                    .compose()
                vib.vibrate(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                vib.areAllPrimitivesSupported(
                    VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
                    VibrationEffect.Composition.PRIMITIVE_CLICK
                )
            ) {
                val effect = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.95f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 100)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 90)
                    .compose()
                vib.vibrate(effect)
            } else {
                val timings = longArrayOf(0L, 45L, 45L, 65L, 50L, 95L)
                val amplitudes = intArrayOf(0, 140, 0, 210, 0, 255)
                vib.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            }
        } catch (_: Throwable) {
            // Safe no-op
        }
    }
}

val LocalHapticsManager = staticCompositionLocalOf<HapticsManager?> { null }
