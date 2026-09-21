/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.engine.signal

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pipeline is tested against synthetic pulses with a known rate, because the only
 * way to know a heart-rate estimate is right is to already know the answer.
 *
 * The synthetic signal deliberately includes the two things that break naive
 * implementations: a large slow baseline drift, and a dicrotic notch after each systolic
 * peak.
 */
class PpgPipelineTest {

    private val frameRate = 30.0

    /**
     * A plausible fingertip PPG.
     *
     * Each beat is a sharp systolic peak followed by a smaller dicrotic notch, riding on
     * a baseline that drifts by far more than the pulse amplitude — which is what a real
     * trace looks like as the finger warms and pressure settles.
     */
    private fun syntheticPpg(
        seconds: Double,
        heartRateBpm: Double,
        noise: Double = 0.0,
        seed: Long = 42L,
    ): Pair<DoubleArray, LongArray> {
        val random = Random(seed)
        val count = (seconds * frameRate).toInt()
        val beatPeriod = 60.0 / heartRateBpm

        val values = DoubleArray(count)
        val timestamps = LongArray(count)

        for (i in 0 until count) {
            val t = i / frameRate
            val phase = (t % beatPeriod) / beatPeriod

            val systolic = exp(-((phase - SYSTOLIC_CENTRE) / SYSTOLIC_WIDTH).let { it * it })
            val dicrotic = DICROTIC_HEIGHT * exp(-((phase - DICROTIC_CENTRE) / DICROTIC_WIDTH).let { it * it })

            // Baseline drift an order of magnitude larger than the pulse itself.
            val drift = BASELINE + DRIFT_AMPLITUDE * sin(2.0 * PI * t / DRIFT_PERIOD_SECONDS)

            val jitter = if (noise > 0.0) random.nextDouble(-noise, noise) else 0.0
            values[i] = drift + systolic + dicrotic + jitter
            timestamps[i] = (t * NANOS_PER_SECOND).toLong()
        }
        return values to timestamps
    }

    private fun estimateHeartRate(values: DoubleArray, timestamps: LongArray): Double {
        val detrended = PpgPipeline.detrend(values, windowSize = frameRate.toInt())
        val filtered = PpgPipeline.bandPassZeroPhase(detrended, frameRate)
        val beats = PpgPipeline.detectBeats(filtered, timestamps)
        val intervals = beats.mapNotNull { it.intervalMillis }
        val corrected = PpgPipeline.correctArtefacts(intervals)
        return HeartRateVariability.meanHeartRate(corrected.intervalsMillis)
    }

    @Test
    fun `recovers a known heart rate through the whole chain`() {
        for (trueRate in listOf(52.0, 60.0, 72.0, 88.0)) {
            val (values, timestamps) = syntheticPpg(seconds = 30.0, heartRateBpm = trueRate)
            val estimated = estimateHeartRate(values, timestamps)
            val error = abs(estimated - trueRate)
            assertTrue(
                "at $trueRate bpm the estimate was $estimated (error $error)",
                error < TOLERANCE_BPM,
            )
        }
    }

    /**
     * The dicrotic notch is the classic cause of a doubled heart rate. If the refractory
     * period ever regressed, this test would report roughly twice the true rate.
     */
    @Test
    fun `the dicrotic notch is not counted as a second beat`() {
        val trueRate = 60.0
        val (values, timestamps) = syntheticPpg(seconds = 30.0, heartRateBpm = trueRate)
        val estimated = estimateHeartRate(values, timestamps)

        assertTrue("estimate $estimated looks doubled", estimated < trueRate * 1.4)
        assertTrue("estimate $estimated looks halved", estimated > trueRate * 0.7)
    }

    @Test
    fun `survives realistic sensor noise`() {
        val (values, timestamps) = syntheticPpg(seconds = 30.0, heartRateBpm = 66.0, noise = 0.25)
        val estimated = estimateHeartRate(values, timestamps)
        assertTrue("noisy estimate was $estimated", abs(estimated - 66.0) < TOLERANCE_BPM * 2)
    }

    @Test
    fun `detrending removes the baseline without removing the pulse`() {
        val (values, _) = syntheticPpg(seconds = 20.0, heartRateBpm = 60.0)
        val detrended = PpgPipeline.detrend(values, windowSize = frameRate.toInt())

        assertTrue("baseline should be near zero", abs(detrended.average()) < 0.05)
        val span = detrended.max() - detrended.min()
        assertTrue("pulse should survive, span was $span", span > 0.3)
    }

    /**
     * Signal quality is what stops the app inventing a heart rate.
     *
     * A moving finger produces a large, aperiodic, broadband trace, which an amplitude
     * check would happily score as a strong signal.
     */
    @Test
    fun `quality separates a real pulse from movement noise`() {
        val (pulseRaw, _) = syntheticPpg(seconds = 20.0, heartRateBpm = 64.0)
        val pulse = PpgPipeline.bandPassZeroPhase(
            PpgPipeline.detrend(pulseRaw, frameRate.toInt()),
            frameRate,
        )

        val random = Random(7L)
        val movementRaw = DoubleArray((20 * frameRate).toInt()) { random.nextDouble(-3.0, 3.0) }
        val movement = PpgPipeline.bandPassZeroPhase(
            PpgPipeline.detrend(movementRaw, frameRate.toInt()),
            frameRate,
        )

        val pulseQuality = PpgPipeline.signalQuality(pulse, frameRate)
        val movementQuality = PpgPipeline.signalQuality(movement, frameRate)

        assertTrue("a real pulse should score well, got $pulseQuality", pulseQuality > 0.4)
        assertTrue(
            "movement ($movementQuality) must score below a real pulse ($pulseQuality)",
            movementQuality < pulseQuality,
        )
    }

    @Test
    fun `artefact correction rejects implausible intervals`() {
        val clean = List(20) { 900.0 + (it % 3) * 10.0 }
        val withArtefacts = clean.toMutableList().apply {
            this[5] = 120.0 // a doubled detection
            this[11] = 2_400.0 // a missed beat
        }

        val corrected = PpgPipeline.correctArtefacts(withArtefacts)

        assertEquals("both artefacts should be caught", 2, corrected.rejected)
        assertTrue("the series should still be usable", corrected.isUsable)
        assertTrue(
            "no implausible interval should survive",
            corrected.intervalsMillis.all { it in PpgPipeline.MIN_INTERVAL_MILLIS..PpgPipeline.MAX_INTERVAL_MILLIS },
        )
    }

    @Test
    fun `a mostly corrupt series is reported as unusable`() {
        val mostlyRubbish = List(20) { if (it % 2 == 0) 900.0 else 100.0 }
        val corrected = PpgPipeline.correctArtefacts(mostlyRubbish)
        assertTrue("should not be usable", !corrected.isUsable)
    }

    @Test
    fun `zero-phase filtering does not shift beat times`() {
        val (values, timestamps) = syntheticPpg(seconds = 20.0, heartRateBpm = 60.0)
        val detrended = PpgPipeline.detrend(values, frameRate.toInt())
        val filtered = PpgPipeline.bandPassZeroPhase(detrended, frameRate)

        val beats = PpgPipeline.detectBeats(filtered, timestamps)
        assertTrue("should find roughly 20 beats, found ${beats.size}", beats.size in 17..23)

        // At 60 bpm the beats should be a second apart to within a frame or two.
        val intervals = beats.mapNotNull { it.intervalMillis }
        assertTrue("intervals were $intervals", intervals.all { abs(it - 1_000.0) < 120.0 })
    }

    @Test
    fun `an empty or tiny signal yields nothing rather than guessing`() {
        assertTrue(PpgPipeline.detectBeats(DoubleArray(0), LongArray(0)).isEmpty())
        assertTrue(PpgPipeline.detectBeats(DoubleArray(4), LongArray(4)).isEmpty())
        assertEquals(0.0, PpgPipeline.signalQuality(DoubleArray(4), frameRate), 0.0)
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val TOLERANCE_BPM = 3.0

        const val BASELINE = 12.0
        const val DRIFT_AMPLITUDE = 4.0
        const val DRIFT_PERIOD_SECONDS = 11.0

        const val SYSTOLIC_CENTRE = 0.18
        const val SYSTOLIC_WIDTH = 0.07
        const val DICROTIC_CENTRE = 0.42
        const val DICROTIC_WIDTH = 0.08
        const val DICROTIC_HEIGHT = 0.45
    }
}
