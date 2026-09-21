/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.engine.visuals

import com.hugmun.engine.psychophysics.DisplayTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The safety envelope, asserted.
 *
 * Every rule in `docs/research/03-safety-and-regulatory.md` §3.3 that can be checked
 * without a device is checked here. These are not unit tests of convenience — they are
 * the mechanism by which the safety policy survives future edits.
 */
class PhoticStimulusTest {

    @Test
    fun `the display gate admits only integer multiples of 40 Hz`() {
        assertFalse("60 Hz cannot render 40 Hz", PhoticStimulus.canRender(DisplayTiming(60.0)))
        assertFalse("90 Hz cannot render 40 Hz", PhoticStimulus.canRender(DisplayTiming(90.0)))
        assertFalse("144 Hz cannot render 40 Hz", PhoticStimulus.canRender(DisplayTiming(144.0)))

        assertTrue(PhoticStimulus.canRender(DisplayTiming(80.0)))
        assertTrue(PhoticStimulus.canRender(DisplayTiming(120.0)))
        assertTrue(PhoticStimulus.canRender(DisplayTiming(240.0)))
    }

    @Test
    fun `depth cannot be pushed outside the unit interval`() {
        assertEquals(1.0, PhoticStimulus.create(depth = 5.0).depth, 1e-12)
        assertEquals(0.0, PhoticStimulus.create(depth = -1.0).depth, 1e-12)
    }

    /**
     * The area limit. A caller asking for a full-screen flash gets a disc.
     */
    @Test
    fun `the stimulus can never fill the screen`() {
        val greedy = PhoticStimulus.create(discRadiusFraction = 1.0)
        assertEquals(
            PhoticStimulus.MAX_DISC_RADIUS_FRACTION,
            greedy.discRadiusFraction,
            1e-12,
        )
    }

    /** The onset ramp is a floor, not a default a caller can shorten. */
    @Test
    fun `the onset ramp cannot be shortened below twenty seconds`() {
        assertEquals(PhoticStimulus.MIN_RAMP_SECONDS, PhoticStimulus.create(rampSeconds = 0.0).rampSeconds, 1e-12)
        assertEquals(PhoticStimulus.MIN_RAMP_SECONDS, PhoticStimulus.create(rampSeconds = 3.0).rampSeconds, 1e-12)
        assertEquals(60.0, PhoticStimulus.create(rampSeconds = 60.0).rampSeconds, 1e-12)
    }

    @Test
    fun `the ramp starts at zero and reaches the target smoothly`() {
        val stimulus = PhoticStimulus.create(depth = 0.5, rampSeconds = 20.0)

        assertEquals("must start from darkness", 0.0, stimulus.rampedDepth(0.0), 1e-12)
        assertEquals("must reach the target", 0.5, stimulus.rampedDepth(20.0), 1e-12)
        assertEquals("and stay there", 0.5, stimulus.rampedDepth(120.0), 1e-12)

        // Monotonic, with no step at either end.
        var previous = -1.0
        for (step in 0..200) {
            val depth = stimulus.rampedDepth(step / 10.0)
            assertTrue("ramp went backwards at ${step / 10.0}s", depth >= previous - 1e-12)
            previous = depth
        }
    }

    @Test
    fun `luminance never leaves the unit interval`() {
        val stimulus = PhoticStimulus.create(depth = 1.0)
        for (step in 0..1_000) {
            val phase = step / 1_000.0
            val luminance = stimulus.luminanceAt(phase, currentDepth = 1.0)
            assertTrue("luminance $luminance left [0, 1] at phase $phase", luminance in 0.0..1.0 + 1e-12)
        }
    }

    /**
     * A raised cosine, so the perceived flicker rate is the modulation rate.
     *
     * A plain sinusoid crossing zero would produce two luminance minima per cycle and
     * the light would appear to flicker at 80 Hz, not 40.
     */
    @Test
    fun `there is exactly one luminance minimum per cycle`() {
        val stimulus = PhoticStimulus.create(depth = 1.0)
        val samples = (0 until 360).map { stimulus.luminanceAt(it / 360.0, 1.0) }

        val minima = samples.indices.count { index ->
            val previous = samples[(index - 1 + samples.size) % samples.size]
            val next = samples[(index + 1) % samples.size]
            samples[index] < previous && samples[index] <= next
        }
        assertEquals("a raised cosine has one trough per cycle", 1, minima)
    }

    @Test
    fun `zero depth is a steady light rather than a flicker`() {
        val stimulus = PhoticStimulus.create(depth = 0.0)
        for (step in 0..100) {
            assertEquals(1.0, stimulus.luminanceAt(step / 100.0, 0.0), 1e-12)
        }
    }

    /**
     * Saturated red is unreachable.
     *
     * It is the most provocative colour for photosensitive responses, so it is excluded
     * at the only place the stimulus colour is produced rather than by asking callers
     * not to pass one.
     */
    @Test
    fun `the stimulus is amber-white and never saturated red`() {
        val stimulus = PhoticStimulus.create()

        for (step in 0..100) {
            val level = step / 100.0
            val colour = stimulus.colourAt(level)

            // At every level, including near-black, the colour must not be a saturated
            // red — a bright red field is the single most provocative stimulus for a
            // photosensitive response.
            val isSaturatedRed = colour.red > SATURATED_RED_FLOOR &&
                colour.green < LOW_CHANNEL &&
                colour.blue < LOW_CHANNEL
            assertFalse("saturated red at level $level: $colour", isSaturatedRed)

            // The hue itself is only meaningful once there is light to have a hue.
            // Compose packs sRGB colours at 8 bits per channel, so below a few percent
            // the ratios are quantisation artefacts rather than a colour decision.
            if (level >= HUE_MEANINGFUL_ABOVE) {
                assertTrue(
                    "green must track red closely at level $level: $colour",
                    colour.green >= colour.red * MIN_GREEN_RATIO,
                )
                assertTrue(
                    "blue must be present at level $level: $colour",
                    colour.blue >= colour.red * MIN_BLUE_RATIO,
                )
            }
        }
    }

    @Test
    fun `availability reports a specific reason rather than a bare false`() {
        val blocked = PhoticAvailability.Blocked(PhoticBlockReason.DISPLAY_CANNOT_RENDER)
        assertFalse(blocked.isAvailable)
        assertEquals(PhoticBlockReason.DISPLAY_CANNOT_RENDER, blocked.reason)
        assertTrue(PhoticAvailability.Available.isAvailable)
    }

    @Test
    fun `the frequency is fixed at forty hertz`() {
        assertEquals(40.0, PhoticStimulus.create().frequencyHz, 1e-12)
        assertEquals(40.0, PhoticStimulus.GAMMA_HZ, 1e-12)
    }

    private companion object {
        /** Amber-white: green close behind red, blue clearly present. */
        const val MIN_GREEN_RATIO = 0.7f
        const val MIN_BLUE_RATIO = 0.4f

        /** Below a few percent of full scale, 8-bit packing dominates the ratios. */
        const val HUE_MEANINGFUL_ABOVE = 0.1

        const val SATURATED_RED_FLOOR = 0.5f
        const val LOW_CHANNEL = 0.2f
    }
}
