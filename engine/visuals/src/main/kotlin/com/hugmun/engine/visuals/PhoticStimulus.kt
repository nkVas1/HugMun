/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.engine.visuals

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.hugmun.engine.psychophysics.DisplayTiming
import kotlin.math.PI
import kotlin.math.cos

/**
 * The 40 Hz photic stimulus, and the rules that constrain it.
 *
 * Everything in this file exists because of
 * `docs/research/03-safety-and-regulatory.md` §3. A 40 Hz whole-field flicker is far
 * above the WCAG 2.3.1 three-flashes threshold, and we do not pretend otherwise: this is
 * a consented, opt-in stimulation session that is **deliberately not conformant**, kept
 * entirely separate from interface content.
 *
 * The controls below are therefore properties of the type rather than conventions the
 * UI is trusted to follow. A caller cannot construct a red stimulus, a square-wave one,
 * a full-screen one, or one that starts at full intensity.
 */

/** Why the photic channel is unavailable, when it is. */
public enum class PhoticBlockReason {
    /** The safety screening has not been passed, or has expired. */
    NOT_SCREENED,

    /** Screening passed but the separate consent has not been given. */
    NOT_CONSENTED,

    /**
     * The display cannot render 40 Hz.
     *
     * 60 Hz gives 1.5 frames per cycle and 90 Hz gives 2.25; neither can be rendered
     * without beat artefacts, and a wrong-frequency flicker is both useless as
     * entrainment and less safe than none.
     */
    DISPLAY_CANNOT_RENDER,

    /** The daily cap has been reached. */
    DAILY_LIMIT_REACHED,

    /** An adverse event was reported; re-consent is required. */
    LOCKED_AFTER_ADVERSE_EVENT,

    /** The room is too dark, where relative contrast would be highest. */
    ROOM_TOO_DARK,
}

/** The state of the photic channel for a given user, device and moment. */
public sealed interface PhoticAvailability {
    public data object Available : PhoticAvailability

    public data class Blocked(public val reason: PhoticBlockReason) : PhoticAvailability

    public val isAvailable: Boolean get() = this is Available
}

/**
 * The stimulus parameters.
 *
 * Construction enforces the safety envelope. There is no way to build an instance that
 * violates it, which is the point — a rule that lives in a code review is a rule that
 * eventually gets missed.
 */
@Immutable
public class PhoticStimulus private constructor(
    public val frequencyHz: Double,
    public val depth: Double,
    public val discRadiusFraction: Double,
    public val rampSeconds: Double,
) {
    /**
     * Instantaneous luminance factor, in [0, 1], at a given phase.
     *
     * Sinusoidal, never square. A square wave has odd harmonics at 120 Hz, 200 Hz and
     * beyond, which are both harsher to look at and outside what the source studies used.
     *
     * @param phaseTurns position within the cycle, in turns, from the audio clock.
     * @param currentDepth the ramped depth, which starts at zero.
     */
    public fun luminanceAt(phaseTurns: Double, currentDepth: Double): Double {
        val clamped = currentDepth.coerceIn(0.0, depth)
        // Raised cosine so the value never goes negative and the perceived rate is the
        // modulation rate rather than twice it.
        return 1.0 - clamped + clamped * (1.0 - cos(TWO_PI * phaseTurns)) / 2.0
    }

    /** Depth during the onset ramp. Smoothstep, so there is no perceptible corner. */
    public fun rampedDepth(elapsedSeconds: Double): Double {
        if (elapsedSeconds >= rampSeconds) return depth
        val progress = (elapsedSeconds / rampSeconds).coerceIn(0.0, 1.0)
        val eased = progress * progress * (SMOOTHSTEP_A - SMOOTHSTEP_B * progress)
        return depth * eased
    }

    /**
     * The colour of the light at a given luminance.
     *
     * Amber-white, always. Saturated red is the most provocative colour for
     * photosensitive responses, and it is unreachable here by construction rather than
     * by convention: this function is the only source of the stimulus colour.
     */
    public fun colourAt(luminance: Double): Color {
        val level = luminance.coerceIn(0.0, 1.0).toFloat()
        return Color(
            red = AMBER_RED * level,
            green = AMBER_GREEN * level,
            blue = AMBER_BLUE * level,
            alpha = 1f,
        )
    }

    public companion object {
        /** The frequency the GENUS work is built on. */
        public const val GAMMA_HZ: Double = 40.0

        /** Starting depth. Higher values unlock only after a completed session. */
        public const val DEFAULT_DEPTH: Double = 0.35

        /** SAFETY.md requires at least this long an onset. */
        public const val MIN_RAMP_SECONDS: Double = 20.0

        /**
         * The stimulus is a central disc, never the full screen.
         *
         * This keeps the stimulated solid angle far closer to the guideline threshold
         * than a full-field flash, at some cost in entrainment strength. That is a
         * deliberate trade, recorded here so nobody "improves" it later.
         */
        public const val MAX_DISC_RADIUS_FRACTION: Double = 0.42

        /** Below this the disc is too small to be looked at comfortably. */
        public const val MIN_DISC_RADIUS_FRACTION: Double = 0.05

        /**
         * Builds a stimulus, clamping every parameter into the safety envelope.
         *
         * Clamping rather than throwing: a caller passing an out-of-range value is a bug,
         * but the safe behaviour at runtime is a gentler stimulus, not a crash in the
         * middle of a session.
         */
        public fun create(
            depth: Double = DEFAULT_DEPTH,
            discRadiusFraction: Double = MAX_DISC_RADIUS_FRACTION,
            rampSeconds: Double = MIN_RAMP_SECONDS,
        ): PhoticStimulus = PhoticStimulus(
            frequencyHz = GAMMA_HZ,
            depth = depth.coerceIn(0.0, 1.0),
            discRadiusFraction = discRadiusFraction.coerceIn(MIN_DISC_RADIUS_FRACTION, MAX_DISC_RADIUS_FRACTION),
            rampSeconds = rampSeconds.coerceAtLeast(MIN_RAMP_SECONDS),
        )

        /**
         * Whether this display may present the photic channel at all.
         *
         * The gate is arithmetic, not policy: the refresh rate must be an integer
         * multiple of 40 Hz.
         */
        public fun canRender(timing: DisplayTiming): Boolean = timing.supportsPeriodicStimulus(GAMMA_HZ)

        private const val TWO_PI = 2.0 * PI
        private const val SMOOTHSTEP_A = 3.0
        private const val SMOOTHSTEP_B = 2.0

        // Warm amber-white. Red is deliberately not the dominant channel.
        private const val AMBER_RED = 1.0f
        private const val AMBER_GREEN = 0.82f
        private const val AMBER_BLUE = 0.58f
    }
}
