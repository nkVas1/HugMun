/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.engine.psychophysics

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The timing reality of a particular display.
 *
 * A perceptual threshold reported in milliseconds is only meaningful if the stimulus was
 * genuinely on screen for that long. Nothing in this module ever expresses a duration in
 * milliseconds without going through here first, because the only durations a display can
 * actually produce are integer multiples of its frame period.
 *
 * @property refreshHz measured refresh rate of the active display mode.
 */
public data class DisplayTiming(public val refreshHz: Double) {
    init {
        require(refreshHz > 0.0) { "refreshHz must be positive, was $refreshHz" }
    }

    /** Duration of a single frame, in milliseconds. */
    public val frameMillis: Double get() = MILLIS_PER_SECOND / refreshHz

    /**
     * The shortest stimulus this display can present, in milliseconds.
     *
     * On a 60 Hz panel this is 16.7 ms; on 120 Hz it is 8.3 ms. Thresholds measured on
     * different displays are therefore not directly comparable at the fast end, which is
     * why every stored session records the display it was measured on.
     */
    public val floorMillis: Double get() = frameMillis

    /** Converts a frame count to the duration it will actually occupy. */
    public fun framesToMillis(frames: Int): Double = frames * frameMillis

    /** Converts a frame count to the duration it will actually occupy. */
    public fun framesToMillis(frames: Double): Double = frames * frameMillis

    /**
     * Rounds a requested duration to the nearest whole number of frames, never below one.
     */
    public fun millisToFrames(millis: Double): Int = max(1, (millis / frameMillis).roundToInt())

    /**
     * Whether this display can render a periodic stimulus at [frequencyHz] without beat
     * artefacts, i.e. whether the refresh rate is an integer multiple of the frequency.
     *
     * This is what disqualifies 60 Hz panels from the 40 Hz photic channel: 60 / 40 = 1.5
     * frames per cycle cannot be rendered, and an approximation would be both useless as
     * entrainment and harder to reason about for safety.
     */
    public fun supportsPeriodicStimulus(frequencyHz: Double, toleranceHz: Double = 0.5): Boolean {
        require(frequencyHz > 0.0) { "frequencyHz must be positive" }
        val framesPerCycle = refreshHz / frequencyHz
        val nearestWhole = framesPerCycle.roundToInt()
        if (nearestWhole < MIN_FRAMES_PER_CYCLE) return false
        val impliedRefresh = nearestWhole * frequencyHz
        return abs(impliedRefresh - refreshHz) <= toleranceHz
    }

    public companion object {
        private const val MILLIS_PER_SECOND = 1_000.0

        /**
         * Two frames per cycle is the Nyquist limit for a rendered periodic stimulus, and
         * at exactly two the waveform degenerates to a square wave. The photic channel
         * requires at least two, which puts its floor at an 80 Hz panel for 40 Hz output.
         */
        public const val MIN_FRAMES_PER_CYCLE: Int = 2

        public val HZ_60: DisplayTiming = DisplayTiming(60.0)
        public val HZ_90: DisplayTiming = DisplayTiming(90.0)
        public val HZ_120: DisplayTiming = DisplayTiming(120.0)
    }
}
