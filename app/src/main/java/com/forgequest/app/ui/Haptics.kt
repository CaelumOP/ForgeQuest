package com.forgequest.app.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Silent feedback through the vibration motor. No sounds anywhere in the app. */
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

    /** Ticking off a set. */
    fun setDone() = play({
        if (supported(VibrationEffect.Composition.PRIMITIVE_THUD)) {
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.7f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.4f, 60)
                .compose()
        } else VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
    }, 25)

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
}
