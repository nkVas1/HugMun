/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.engine.signal

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Heart-rate variability metrics.
 *
 * Only the ones this product actually uses. HRV has a large and partly disreputable
 * literature of derived indices; adding measures we cannot interpret would be adding
 * numbers the app would then have to pretend to understand.
 *
 * What is here, and why:
 *
 * - **RMSSD** — short-term, beat-to-beat variation, dominated by vagal activity. The
 *   standard time-domain measure for short recordings.
 * - **SDNN** — overall variability. Reported alongside RMSSD because their ratio says
 *   something about where the variability is coming from.
 * - **RSA amplitude** — the peak-to-trough heart-rate swing within each breath. This is
 *   the quantity resonance breathing maximises, and therefore the one the resonance
 *   sweep is scored on.
 * - **LF power** — spectral power in 0.04–0.15 Hz, which is the band the ~0.1 Hz
 *   baroreflex resonance lives in.
 */
public object HeartRateVariability {

    /** The low-frequency band, where the baroreflex resonance appears. */
    public const val LF_LOW_HZ: Double = 0.04
    public const val LF_HIGH_HZ: Double = 0.15

    /** Root mean square of successive differences, in milliseconds. */
    public fun rmssd(intervalsMillis: List<Double>): Double {
        if (intervalsMillis.size < 2) return 0.0
        var sum = 0.0
        for (i in 1 until intervalsMillis.size) {
            val diff = intervalsMillis[i] - intervalsMillis[i - 1]
            sum += diff * diff
        }
        return sqrt(sum / (intervalsMillis.size - 1))
    }

    /**
     * Standard deviation of the interval series, in milliseconds.
     *
     * Bessel-corrected: these series are short — a two-minute epoch at 60 bpm gives about
     * 120 intervals, and at the shorter windows used during the resonance sweep, fewer.
     */
    public fun sdnn(intervalsMillis: List<Double>): Double {
        if (intervalsMillis.size < 2) return 0.0
        val mean = intervalsMillis.average()
        val sumSquares = intervalsMillis.sumOf { (it - mean) * (it - mean) }
        return sqrt(sumSquares / (intervalsMillis.size - 1))
    }

    /** Mean heart rate in beats per minute. */
    public fun meanHeartRate(intervalsMillis: List<Double>): Double {
        if (intervalsMillis.isEmpty()) return 0.0
        return MILLIS_PER_MINUTE / intervalsMillis.average()
    }

    /**
     * Respiratory sinus arrhythmia amplitude, in beats per minute.
     *
     * Computed as the mean peak-to-trough excursion of instantaneous heart rate within
     * each breath cycle. This is the "peak-valley" method, which is the one the HRV
     * biofeedback literature uses when the breathing rate is known and paced — as it is
     * here, because the app is the thing setting the pace.
     *
     * @param breathPeriodSeconds the paced breath period, which defines the cycles.
     */
    public fun rsaAmplitude(intervalsMillis: List<Double>, breathPeriodSeconds: Double): Double {
        require(breathPeriodSeconds > 0.0) { "breathPeriodSeconds must be positive" }
        if (intervalsMillis.size < MIN_INTERVALS) return 0.0

        // Walk the interval series in time, slicing it into breath-length windows.
        val excursions = mutableListOf<Double>()
        var windowStartMillis = 0.0
        var elapsedMillis = 0.0
        var windowMin = Double.MAX_VALUE
        var windowMax = -Double.MAX_VALUE
        val windowMillis = breathPeriodSeconds * MILLIS_PER_SECOND

        for (interval in intervalsMillis) {
            val instantaneousRate = MILLIS_PER_MINUTE / interval
            if (instantaneousRate < windowMin) windowMin = instantaneousRate
            if (instantaneousRate > windowMax) windowMax = instantaneousRate

            elapsedMillis += interval
            if (elapsedMillis - windowStartMillis >= windowMillis) {
                if (windowMax > windowMin) excursions += windowMax - windowMin
                windowStartMillis = elapsedMillis
                windowMin = Double.MAX_VALUE
                windowMax = -Double.MAX_VALUE
            }
        }

        return if (excursions.isEmpty()) 0.0 else excursions.average()
    }

    /**
     * Spectral power of the interval series in a frequency band.
     *
     * The series is resampled onto an even time base first, because an interval series is
     * inherently unevenly sampled and feeding it to a transform as though it were evenly
     * spaced is a common and quietly wrong shortcut.
     */
    public fun bandPower(
        intervalsMillis: List<Double>,
        lowHz: Double = LF_LOW_HZ,
        highHz: Double = LF_HIGH_HZ,
        resampleHz: Double = DEFAULT_RESAMPLE_HZ,
    ): Double {
        val resampled = resampleEvenly(intervalsMillis, resampleHz)
        if (resampled.size < MIN_SAMPLES_FOR_SPECTRUM) return 0.0

        val mean = resampled.average()
        val centred = DoubleArray(resampled.size) { resampled[it] - mean }

        var power = 0.0
        var frequency = lowHz
        while (frequency <= highHz) {
            val magnitude = goertzel(centred, resampleHz, frequency)
            power += magnitude * magnitude
            frequency += SPECTRUM_STEP_HZ
        }
        return power
    }

    /**
     * Resamples an interval series onto an even time base by linear interpolation.
     *
     * The value at each instant is the instantaneous heart rate implied by the interval
     * in force at that moment, interpolated between beats.
     */
    public fun resampleEvenly(intervalsMillis: List<Double>, resampleHz: Double): DoubleArray {
        if (intervalsMillis.size < 2) return DoubleArray(0)

        val beatTimes = DoubleArray(intervalsMillis.size)
        val rates = DoubleArray(intervalsMillis.size)
        var cumulative = 0.0
        for (i in intervalsMillis.indices) {
            cumulative += intervalsMillis[i] / MILLIS_PER_SECOND
            beatTimes[i] = cumulative
            rates[i] = MILLIS_PER_MINUTE / intervalsMillis[i]
        }

        val duration = beatTimes.last() - beatTimes.first()
        if (duration <= 0.0) return DoubleArray(0)

        val count = (duration * resampleHz).toInt()
        if (count < 2) return DoubleArray(0)

        val output = DoubleArray(count)
        var cursor = 0
        for (i in 0 until count) {
            val t = beatTimes.first() + i / resampleHz
            while (cursor < beatTimes.size - 2 && beatTimes[cursor + 1] < t) cursor++

            val t0 = beatTimes[cursor]
            val t1 = beatTimes[cursor + 1]
            val fraction = if (t1 > t0) ((t - t0) / (t1 - t0)).coerceIn(0.0, 1.0) else 0.0
            output[i] = rates[cursor] + (rates[cursor + 1] - rates[cursor]) * fraction
        }
        return output
    }

    /**
     * Phase coherence between the paced breath and the heart-rate oscillation.
     *
     * At the resonance frequency the two lock together; away from it they drift apart.
     * Measured as the normalised cross-correlation at the lag that maximises it, so a
     * constant physiological delay between breath and heart-rate response does not look
     * like incoherence.
     */
    public fun breathHeartCoherence(
        intervalsMillis: List<Double>,
        breathPeriodSeconds: Double,
        resampleHz: Double = DEFAULT_RESAMPLE_HZ,
    ): Double {
        val rate = resampleEvenly(intervalsMillis, resampleHz)
        if (rate.size < MIN_SAMPLES_FOR_SPECTRUM) return 0.0

        val mean = rate.average()
        val centred = DoubleArray(rate.size) { rate[it] - mean }
        val breath = DoubleArray(rate.size) { sin(2.0 * PI * it / (breathPeriodSeconds * resampleHz)) }

        val maxLag = (breathPeriodSeconds * resampleHz).toInt().coerceAtMost(rate.size / 2)
        var best = 0.0
        for (lag in 0 until maxLag) {
            var numerator = 0.0
            var rateEnergy = 0.0
            var breathEnergy = 0.0
            for (i in 0 until rate.size - lag) {
                numerator += centred[i + lag] * breath[i]
                rateEnergy += centred[i + lag] * centred[i + lag]
                breathEnergy += breath[i] * breath[i]
            }
            val denominator = sqrt(rateEnergy * breathEnergy)
            if (denominator > 0.0) {
                val correlation = abs(numerator / denominator)
                if (correlation > best) best = correlation
            }
        }
        return best.coerceIn(0.0, 1.0)
    }

    private fun goertzel(values: DoubleArray, sampleRateHz: Double, frequencyHz: Double): Double {
        val omega = 2.0 * PI * frequencyHz / sampleRateHz
        val coefficient = 2.0 * cos(omega)
        var s1 = 0.0
        var s2 = 0.0
        for (value in values) {
            val s0 = value + coefficient * s1 - s2
            s2 = s1
            s1 = s0
        }
        val real = s1 - s2 * cos(omega)
        val imaginary = s2 * sin(omega)
        return sqrt(real * real + imaginary * imaginary) / values.size
    }

    private const val MILLIS_PER_MINUTE = 60_000.0
    private const val MILLIS_PER_SECOND = 1_000.0
    private const val MIN_INTERVALS = 5
    private const val MIN_SAMPLES_FOR_SPECTRUM = 32
    private const val DEFAULT_RESAMPLE_HZ = 4.0
    private const val SPECTRUM_STEP_HZ = 0.005
}
