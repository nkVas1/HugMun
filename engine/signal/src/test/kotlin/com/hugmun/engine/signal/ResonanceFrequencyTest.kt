/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.engine.signal

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The resonance sweep is the step consumer breathing apps skip.
 *
 * These tests build a synthetic person whose cardiovascular system resonates at a known
 * rate — their heart-rate oscillation is largest when paced at that rate — and check
 * that the sweep finds it.
 */
class ResonanceFrequencyTest {

    /**
     * An interval series for someone breathing at [pacedRate] whose resonance is at
     * [resonantRate].
     *
     * Respiratory sinus arrhythmia amplitude peaks when the two coincide and falls off
     * either side, which is the behaviour the protocol exploits.
     */
    private fun syntheticEpoch(
        pacedRate: Double,
        resonantRate: Double,
        meanIntervalMillis: Double = 950.0,
        peakAmplitudeMillis: Double = 90.0,
        quality: Double = 1.0,
        seconds: Double = 120.0,
    ): ResonanceFrequency.Epoch {
        val detuning = abs(pacedRate - resonantRate)
        // A resonance curve: full amplitude on tune, falling away either side.
        val gain = 1.0 / (1.0 + (detuning / BANDWIDTH) * (detuning / BANDWIDTH))
        val amplitude = peakAmplitudeMillis * gain
        val breathPeriod = 60.0 / pacedRate

        val intervals = mutableListOf<Double>()
        var elapsed = 0.0
        while (elapsed < seconds) {
            val phase = 2.0 * PI * elapsed / breathPeriod
            val interval = meanIntervalMillis + amplitude * sin(phase)
            intervals += interval
            elapsed += interval / 1_000.0
        }

        return ResonanceFrequency.Epoch(
            rateBreathsPerMinute = pacedRate,
            intervalsMillis = intervals,
            signalQuality = quality,
        )
    }

    private fun sweepFor(resonantRate: Double) =
        ResonanceFrequency.SWEEP_RATES.map { syntheticEpoch(pacedRate = it, resonantRate = resonantRate) }

    @Test
    fun `the sweep covers the published range in descending order`() {
        assertEquals(listOf(6.5, 6.0, 5.5, 5.0, 4.5), ResonanceFrequency.SWEEP_RATES)
        assertTrue(
            "descending order is part of the protocol",
            ResonanceFrequency.SWEEP_RATES.zipWithNext().all { (a, b) -> a > b },
        )
    }

    @Test
    fun `finds a resonance at five and a half breaths per minute`() {
        val outcome = ResonanceFrequency.score(sweepFor(resonantRate = 5.5))
        assertEquals(5.5, outcome.chosenRateBreathsPerMinute, 1e-9)
        assertTrue("a real measurement should not use the fallback", !outcome.usedFallback)
    }

    @Test
    fun `finds a resonance at the fast end of the range`() {
        val outcome = ResonanceFrequency.score(sweepFor(resonantRate = 6.5))
        assertEquals(6.5, outcome.chosenRateBreathsPerMinute, 1e-9)
    }

    @Test
    fun `finds a resonance at the slow end of the range`() {
        val outcome = ResonanceFrequency.score(sweepFor(resonantRate = 4.5))
        assertEquals(4.5, outcome.chosenRateBreathsPerMinute, 1e-9)
    }

    /**
     * Without a usable pulse the app must say so rather than presenting the modal adult
     * value as though it had been measured.
     */
    @Test
    fun `falls back and admits it when no epoch is usable`() {
        val unusable = ResonanceFrequency.SWEEP_RATES.map {
            syntheticEpoch(pacedRate = it, resonantRate = 5.5, quality = 0.1)
        }
        val outcome = ResonanceFrequency.score(unusable)

        assertEquals(ResonanceFrequency.FALLBACK_RATE, outcome.chosenRateBreathsPerMinute, 1e-9)
        assertTrue("the fallback must be declared", outcome.usedFallback)
        assertTrue("no epoch should be marked usable", outcome.scores.none { it.isUsable })
    }

    @Test
    fun `an epoch with too few beats is not scored`() {
        val epochs = ResonanceFrequency.SWEEP_RATES.map { rate ->
            if (rate == 6.5) {
                syntheticEpoch(pacedRate = rate, resonantRate = 5.5, seconds = 10.0)
            } else {
                syntheticEpoch(pacedRate = rate, resonantRate = 5.5)
            }
        }
        val outcome = ResonanceFrequency.score(epochs)
        val shortEpoch = outcome.scores.first { it.rateBreathsPerMinute == 6.5 }
        assertTrue("a ten-second epoch cannot be scored", !shortEpoch.isUsable)
    }

    @Test
    fun `every swept rate appears in the result`() {
        val outcome = ResonanceFrequency.score(sweepFor(resonantRate = 5.0))
        assertEquals(
            ResonanceFrequency.SWEEP_RATES.sortedDescending(),
            outcome.scores.map { it.rateBreathsPerMinute },
        )
    }

    /**
     * RSA amplitude must actually respond to the thing it is supposed to measure,
     * otherwise the sweep is scoring noise.
     */
    @Test
    fun `RSA amplitude rises with the size of the heart rate oscillation`() {
        val small = syntheticEpoch(5.5, 5.5, peakAmplitudeMillis = 20.0)
        val large = syntheticEpoch(5.5, 5.5, peakAmplitudeMillis = 120.0)

        val smallRsa = HeartRateVariability.rsaAmplitude(small.intervalsMillis, small.breathPeriodSeconds)
        val largeRsa = HeartRateVariability.rsaAmplitude(large.intervalsMillis, large.breathPeriodSeconds)

        assertTrue("$largeRsa should exceed $smallRsa", largeRsa > smallRsa * 2)
    }

    @Test
    fun `coherence is high when breath and heart rate are locked`() {
        val locked = syntheticEpoch(5.5, 5.5)
        val coherence = HeartRateVariability.breathHeartCoherence(
            locked.intervalsMillis,
            locked.breathPeriodSeconds,
        )
        assertTrue("locked breathing should be coherent, got $coherence", coherence > 0.5)
    }

    @Test
    fun `RMSSD and SDNN behave sensibly on a known series`() {
        val steady = List(60) { 900.0 }
        assertEquals(0.0, HeartRateVariability.rmssd(steady), 1e-12)
        assertEquals(0.0, HeartRateVariability.sdnn(steady), 1e-12)

        val alternating = List(60) { if (it % 2 == 0) 880.0 else 920.0 }
        assertEquals("successive differences are all 40 ms", 40.0, HeartRateVariability.rmssd(alternating), 1e-9)
        assertTrue("SDNN should be positive", HeartRateVariability.sdnn(alternating) > 0.0)
    }

    @Test
    fun `mean heart rate inverts the mean interval`() {
        assertEquals(60.0, HeartRateVariability.meanHeartRate(List(10) { 1_000.0 }), 1e-9)
        assertEquals(0.0, HeartRateVariability.meanHeartRate(emptyList()), 0.0)
    }

    private companion object {
        /** Width of the synthetic resonance curve, in breaths per minute. */
        const val BANDWIDTH = 0.8
    }
}
