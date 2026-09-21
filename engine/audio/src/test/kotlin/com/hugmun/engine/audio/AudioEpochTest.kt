/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.engine.audio

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audio–visual phase alignment.
 *
 * This is the arithmetic that makes the light and the sound one combined stimulus rather
 * than two that drift apart. It is pure, so it is tested here rather than on a device.
 */
class AudioEpochTest {

    private val sampleRate = 48_000
    private val nanosPerSecond = 1_000_000_000L

    private fun epoch(frame: Long = 0L, nano: Long = 0L) =
        GammaStimulusPlayer.AudioEpoch(framePosition = frame, nanoTime = nano, sampleRate = sampleRate)

    @Test
    fun `phase is zero at the epoch itself`() {
        assertEquals(0.0, epoch().phaseAt(0L, 40.0), 1e-12)
    }

    @Test
    fun `phase returns to zero after a whole number of cycles`() {
        // One 40 Hz cycle is 25 ms.
        val oneCycleNanos = nanosPerSecond / 40
        for (cycles in 1..20) {
            val phase = epoch().phaseAt(oneCycleNanos * cycles, 40.0)
            val distanceFromZero = minOf(phase, 1.0 - phase)
            assertTrue("after $cycles cycles the phase was $phase", distanceFromZero < 1e-6)
        }
    }

    @Test
    fun `phase is one half a half-cycle later`() {
        val halfCycleNanos = nanosPerSecond / 80
        assertEquals(0.5, epoch().phaseAt(halfCycleNanos, 40.0), 1e-6)
    }

    @Test
    fun `phase always lies in the unit interval`() {
        val epoch = epoch(frame = 123_456L, nano = 987_654_321L)
        for (step in 0..500) {
            val phase = epoch.phaseAt(987_654_321L + step * 1_234_567L, 40.0)
            assertTrue("phase $phase left [0, 1)", phase >= 0.0 && phase < 1.0)
        }
    }

    @Test
    fun `a frame offset in the epoch shifts the phase accordingly`() {
        // Half a cycle at 40 Hz is 600 frames at 48 kHz.
        val atZero = epoch(frame = 0L).phaseAt(0L, 40.0)
        val shifted = epoch(frame = 600L).phaseAt(0L, 40.0)
        assertEquals(0.5, abs(shifted - atZero), 1e-9)
    }

    @Test
    fun `a system time before the epoch still yields a valid phase`() {
        // getTimestamp can report an epoch slightly in the future relative to the caller.
        val phase = epoch(nano = 1_000_000_000L).phaseAt(999_000_000L, 40.0)
        assertTrue("phase $phase left [0, 1)", phase >= 0.0 && phase < 1.0)
    }
}
