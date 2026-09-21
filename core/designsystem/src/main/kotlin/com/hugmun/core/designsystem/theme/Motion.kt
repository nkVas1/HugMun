/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Motion in HugMun is slow, brief and never decorative.
 *
 * The one signature is **settling**: content arrives from slightly below with a gentle
 * deceleration, like a bird landing. It never overshoots, never bounces, never springs —
 * bounce reads as playful, and playful reads as childish to the person using this.
 *
 * Every duration here collapses to [reducedMotionMillis] when the system reduce-motion
 * setting is on. That is handled centrally in `HugMunTheme`, not per call site.
 */
@Immutable
public data class HugMunMotion(
    public val screenTransitionMillis: Int = 420,
    public val elementEnterMillis: Int = 320,
    public val stateChangeMillis: Int = 180,
    public val reducedMotionMillis: Int = 120,

    /** Distance content travels when settling into place. */
    public val settleDistance: Dp = 12.dp,

    public val isReduced: Boolean = false,
) {
    /** Deceleration curve shared by every entrance and screen transition. */
    public val settle: Easing get() = SettleEasing

    /** Standard curve for in-place state changes. */
    public val standard: Easing get() = StandardEasing

    public fun <T> screenTransition(): FiniteAnimationSpec<T> =
        tween(durationMillis = effective(screenTransitionMillis), easing = effectiveEasing())

    public fun <T> elementEnter(): FiniteAnimationSpec<T> =
        tween(durationMillis = effective(elementEnterMillis), easing = effectiveEasing())

    public fun <T> stateChange(): FiniteAnimationSpec<T> =
        tween(durationMillis = effective(stateChangeMillis), easing = standard)

    /** Travel distance, collapsed to zero under reduce-motion so nothing slides. */
    public val effectiveSettleDistance: Dp get() = if (isReduced) 0.dp else settleDistance

    private fun effective(millis: Int): Int = if (isReduced) reducedMotionMillis else millis

    private fun effectiveEasing(): Easing = if (isReduced) StandardEasing else SettleEasing

    private companion object {
        val SettleEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
        val StandardEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    }
}

public val DefaultMotion: HugMunMotion = HugMunMotion()
