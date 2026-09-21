/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.engine.psychophysics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayTimingTest {

    @Test
    fun `frame period matches the refresh rate`() {
        assertEquals(16.667, DisplayTiming.HZ_60.frameMillis, 0.001)
        assertEquals(8.333, DisplayTiming.HZ_120.frameMillis, 0.001)
    }

    @Test
    fun `durations round to whole frames and never below one`() {
        val timing = DisplayTiming.HZ_120
        assertEquals(1, timing.millisToFrames(1.0))
        assertEquals(1, timing.millisToFrames(0.0))
        assertEquals(2, timing.millisToFrames(17.0))
        assertEquals(6, timing.millisToFrames(50.0))
    }

    /**
     * The gate that keeps the 40 Hz photic channel off displays that cannot render it.
     * 60 Hz gives 1.5 frames per cycle, which is not renderable; 90 Hz gives 2.25, which
     * would beat against the intended frequency.
     */
    @Test
    fun `only displays whose refresh is an integer multiple of 40 Hz may present it`() {
        val gamma = 40.0
        assertFalse("60 Hz must be rejected", DisplayTiming(60.0).supportsPeriodicStimulus(gamma))
        assertFalse("90 Hz must be rejected", DisplayTiming(90.0).supportsPeriodicStimulus(gamma))
        assertFalse("144 Hz must be rejected", DisplayTiming(144.0).supportsPeriodicStimulus(gamma))

        assertTrue("80 Hz is exactly 2 frames per cycle", DisplayTiming(80.0).supportsPeriodicStimulus(gamma))
        assertTrue("120 Hz is exactly 3 frames per cycle", DisplayTiming(120.0).supportsPeriodicStimulus(gamma))
        assertTrue("240 Hz is exactly 6 frames per cycle", DisplayTiming(240.0).supportsPeriodicStimulus(gamma))
    }

    @Test
    fun `slightly off nominal refresh rates are still accepted within tolerance`() {
        // Real panels report values like 119.94 Hz rather than a clean 120.
        assertTrue(DisplayTiming(119.94).supportsPeriodicStimulus(40.0))
    }

    @Test
    fun `a stimulus needing fewer than two frames per cycle is rejected`() {
        // 40 Hz on a 40 Hz panel would be one frame per cycle: nothing would be visible.
        assertFalse(DisplayTiming(40.0).supportsPeriodicStimulus(40.0))
    }

    @Test
    fun `non positive refresh rates are rejected at construction`() {
        val error = runCatching { DisplayTiming(0.0) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}
