package com.backlognudge.app.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The mockup's motion, transcribed.
 *
 *  - [Pop] is `cubic-bezier(.3, 1.6, .5, 1)` — the overshoot the tick glyph
 *    rides in on (`.tick svg`, 250ms, from scale(.4)).
 *  - [Glide] is `cubic-bezier(.4, 0, .2, 1)` — the only other named curve in
 *    the file (the strike-through sweep).
 *
 * Everything else in the mockup is a plain `ease` over 200-400ms.
 */
val Pop: Easing = CubicBezierEasing(0.3f, 1.6f, 0.5f, 1f)
val Glide: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

/** The mockup's `.scene` / `.row` crossfade: `opacity .3s ease`. */
const val SceneFadeMs = 300

/** `@keyframes ring` — 2.6s, the second ring delayed half a cycle. */
const val RingCycleMs = 2600

/**
 * Mirrors the mockup's `@media (prefers-reduced-motion: reduce)` block.
 *
 * Android has no direct equivalent, but turning animations off in
 * Accessibility (or Developer options) sets the animator duration scale to 0,
 * which is the signal the platform gives us. Callers collapse their durations
 * to zero when this is true rather than skipping the state change.
 */
@Composable
@ReadOnlyComposable
fun reduceMotion(): Boolean {
    val context = LocalContext.current
    return runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }.getOrDefault(false)
}

/** A [tween] that honours the reduced-motion setting. */
@Composable
fun <T> motionTween(
    durationMillis: Int,
    easing: Easing = LinearEasing,
    delayMillis: Int = 0
): FiniteAnimationSpec<T> {
    val reduced = reduceMotion()
    return remember(durationMillis, easing, delayMillis, reduced) {
        tween(
            durationMillis = if (reduced) 0 else durationMillis,
            delayMillis = if (reduced) 0 else delayMillis,
            easing = easing
        )
    }
}
