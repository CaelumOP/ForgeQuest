package com.forgequest.app.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/** One spring: how bouncy ([dampingRatio], 1 = no overshoot) and how quick ([stiffness]). */
@Immutable
class SpringToken(val dampingRatio: Float, val stiffness: Float) {
    fun <T> spec(visibilityThreshold: T? = null): SpringSpec<T> = spring(dampingRatio, stiffness, visibilityThreshold)
}

/**
 * Motion tokens. Spatial springs move things (position, size, corners) and may overshoot a little; effects springs
 * change colour and alpha and never overshoot. Easing and duration tokens are for timed sweeps and screen changes.
 * The web app has the same tokens as CSS variables in web/src/ui/kit/kit.css.
 */
object ForgeMotion {
    object Springs {
        val FastSpatial = SpringToken(0.6f, 800f)
        val DefaultSpatial = SpringToken(0.7f, 380f)
        val SlowSpatial = SpringToken(0.8f, 200f)
        val FastEffects = SpringToken(1f, 3800f)
        val DefaultEffects = SpringToken(1f, 1600f)
        val SlowEffects = SpringToken(1f, 800f)
    }

    object Easing {
        val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
        val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
        val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
        val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    }

    object Duration {
        const val SHORT3 = 150
        const val SHORT4 = 200
        const val MEDIUM1 = 250
        const val MEDIUM2 = 300
        const val MEDIUM4 = 400
        const val LONG2 = 500
    }

    /** With reduced motion on, effects become a short linear fade of this length. */
    const val REDUCED_FADE_MS = 120

    /** A spatial spring, or an instant snap when [reduced]: nothing slides, bounces or morphs. */
    fun <T> spatial(reduced: Boolean, token: SpringToken = Springs.DefaultSpatial, visibilityThreshold: T? = null): FiniteAnimationSpec<T> =
        if (reduced) snap() else token.spec(visibilityThreshold)

    /** An effects spring, or a 120 ms fade when [reduced], so a change of state is still visible. */
    fun <T> effects(reduced: Boolean, token: SpringToken = Springs.DefaultEffects, visibilityThreshold: T? = null): FiniteAnimationSpec<T> =
        if (reduced) tween(REDUCED_FADE_MS, easing = LinearEasing) else token.spec(visibilityThreshold)

    /** A timed animation, or a 120 ms fade when [reduced]. */
    fun <T> timed(reduced: Boolean, durationMillis: Int, easing: androidx.compose.animation.core.Easing): FiniteAnimationSpec<T> =
        if (reduced) tween(REDUCED_FADE_MS, easing = LinearEasing) else tween(durationMillis, easing = easing)
}

/** True when the system's animator duration scale is 0 (Settings → Accessibility → Remove animations). */
val LocalReducedMotion = compositionLocalOf { false }

/** [ForgeMotion.spatial] for the current reduced-motion setting. */
@Composable
@ReadOnlyComposable
fun <T> spatialSpec(token: SpringToken = ForgeMotion.Springs.DefaultSpatial, visibilityThreshold: T? = null): FiniteAnimationSpec<T> =
    ForgeMotion.spatial(LocalReducedMotion.current, token, visibilityThreshold)

/** [ForgeMotion.effects] for the current reduced-motion setting. */
@Composable
@ReadOnlyComposable
fun <T> effectsSpec(token: SpringToken = ForgeMotion.Springs.DefaultEffects, visibilityThreshold: T? = null): FiniteAnimationSpec<T> =
    ForgeMotion.effects(LocalReducedMotion.current, token, visibilityThreshold)

fun readReducedMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** Provides [LocalReducedMotion], read again every time the app comes back to the foreground. */
@Composable
fun ProvideReducedMotion(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var reduced by remember { mutableStateOf(readReducedMotion(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { reduced = readReducedMotion(context) }
    CompositionLocalProvider(LocalReducedMotion provides reduced, content = content)
}

/** [LazyItemScope.animateItem] on the tokens: placement on the default spatial spring, fades on the default effects spring. */
@Composable
fun LazyItemScope.itemMotion(modifier: Modifier = Modifier): Modifier {
    val reduced = LocalReducedMotion.current
    return modifier.animateItem(
        fadeInSpec = ForgeMotion.effects(reduced),
        placementSpec = ForgeMotion.spatial(reduced, visibilityThreshold = IntOffset.VisibilityThreshold),
        fadeOutSpec = ForgeMotion.effects(reduced),
    )
}
