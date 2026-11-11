package com.forgequest.app.ui

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Feedback through the vibration motor. Touch feedback follows the "Vibration" setting; the one exception is
 * [restOver], which is an alert and not feedback, so it always plays.
 */
class Haptics(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /** Mirrors Settings.hapticsEnabled; kept here so every tick doesn't read the DataStore. */
    @Volatile var enabled: Boolean = true

    private fun play(composition: () -> VibrationEffect?, fallbackMs: Long) {
        if (!enabled) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val effect = if (Build.VERSION.SDK_INT >= 30) composition() else null
        v.vibrate(effect ?: VibrationEffect.createOneShot(fallbackMs, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun supported(vararg primitives: Int): Boolean =
        Build.VERSION.SDK_INT >= 30 && vibrator?.areAllPrimitivesSupported(*primitives) == true

    /** Steppers, chips, toggles. */
    fun tick() = play({
        if (supported(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
            VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.4f).compose()
        } else VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
    }, 8)

    /** Ticking a set off: one crisp click. */
    fun check() = play({
        if (supported(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
            VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f).compose()
        } else VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
    }, 12)

    /** A lighter tick than [tick]: each step of a stepper, each slot passed while dragging. */
    fun lightTick() = play({
        if (supported(VibrationEffect.Composition.PRIMITIVE_TICK)) {
            VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f).compose()
        } else VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
    }, 5)

    /** A set ticked off that beats the person's best: a double thud. Older phones get a two-pulse waveform. */
    fun pr() {
        if (!enabled) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val effect = if (supported(VibrationEffect.Composition.PRIMITIVE_THUD)) {
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.8f, 90)
                .compose()
        } else VibrationEffect.createWaveform(longArrayOf(0, 35, 90, 35), -1)
        v.vibrate(effect)
    }

    /** The finish screen's score lands. */
    fun heavyClick() = play({ VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK) }, 30)

    /** Ticking off a set. */
    fun setDone() = play({
        if (supported(VibrationEffect.Composition.PRIMITIVE_THUD)) {
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.7f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.4f, 60)
                .compose()
        } else VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
    }, 25)

    /**
     * The rest between sets is over: three long buzzes, strong enough to feel with the phone on a bench or in a pocket.
     * Played as an alarm, so "touch vibration off" and Do Not Disturb's default rules do not swallow it, and it ignores
     * [enabled]: someone who turned touch feedback off still wants to know the rest ended.
     */
    fun restOver() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val effect = VibrationEffect.createWaveform(REST_OVER_PATTERN, -1)
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            }
        }
    }

    /** Workout finished, PR, badge, level up. */
    fun celebrate() = play({
        if (supported(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, VibrationEffect.Composition.PRIMITIVE_CLICK)) {
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.8f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, 80)
                .compose()
        } else VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
    }, 40)

    /** Something failed: a short double buzz. */
    fun error() = play({
        if (supported(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f, 90)
                .compose()
        } else VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
    }, 30)

    companion object {
        /** Wait, buzz, pause, buzz, pause, long buzz (milliseconds). The rest-over notification channel uses the same one. */
        val REST_OVER_PATTERN = longArrayOf(0, 400, 150, 400, 150, 600)
    }
}
