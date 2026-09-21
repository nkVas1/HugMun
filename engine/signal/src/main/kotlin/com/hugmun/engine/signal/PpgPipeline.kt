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
 * Turning a camera's view of a fingertip into a beat-to-beat interval series.
 *
 * The pipeline is the one every open-source PPG project converges on — spatial mean of a
 * colour channel per frame, detrend, band-pass, peak detect — but with two differences
 * that matter, both learned from reading those projects:
 *
 * 1. **Auto-exposure must be locked upstream.** Auto-exposure continuously compensates
 *    for the very brightness change we are measuring, and with it enabled the pulsatile
 *    component is largely cancelled. That is a camera-configuration concern, handled in
 *    the feature module, but it is the single most common reason a PPG implementation
 *    quietly produces plausible-looking noise.
 * 2. **Signal quality is assessed and reported.** When the finger moves, the app says it
 *    cannot see a pulse rather than showing a number. A fabricated heart rate in a
 *    biofeedback loop is worse than no heart rate.
 *
 * Pure Kotlin, so the whole chain is tested against synthetic signals with a known rate.
 */
public object PpgPipeline {

    /** One frame's worth of measurement. */
    public data class Sample(
        /** Spatial mean of the chosen channel, in arbitrary units. */
        public val value: Double,
        /** Frame timestamp, nanoseconds, monotonic. */
        public val timestampNanos: Long,
    )

    /** A detected beat. */
    public data class Beat(
        public val timestampNanos: Long,
        /** Interval since the previous beat, in milliseconds. Null for the first beat. */
        public val intervalMillis: Double?,
    )

    /**
     * Plausible human inter-beat intervals.
     *
     * 300 ms is 200 bpm and 1500 ms is 40 bpm. Anything outside is an artefact rather
     * than a heartbeat, and in an 83-year-old at rest the true range is much narrower
     * still — but a wide gate here and a strict artefact filter afterwards is more robust
     * than a narrow gate that silently drops real ectopic beats.
     */
    public const val MIN_INTERVAL_MILLIS: Double = 300.0
    public const val MAX_INTERVAL_MILLIS: Double = 1_500.0

    /** Pass band for the pulsatile component: 42–210 bpm. */
    public const val BAND_LOW_HZ: Double = 0.7
    public const val BAND_HIGH_HZ: Double = 3.5

    /**
     * Removes the slow baseline drift that dominates a raw PPG trace.
     *
     * The fingertip warms, pressure changes, the torch's output settles — all of which
     * move the mean by far more than the pulse does. A moving-average subtraction is
     * enough and, unlike a high-pass IIR, introduces no phase distortion that would shift
     * the detected beat times.
     */
    public fun detrend(values: DoubleArray, windowSize: Int): DoubleArray {
        require(windowSize > 0) { "windowSize must be positive" }
        if (values.isEmpty()) return DoubleArray(0)

        val output = DoubleArray(values.size)
        val half = windowSize / 2

        for (i in values.indices) {
            val from = maxOf(0, i - half)
            val to = minOf(values.size - 1, i + half)
            var sum = 0.0
            for (j in from..to) sum += values[j]
            output[i] = values[i] - sum / (to - from + 1)
        }
        return output
    }

    /**
     * Zero-phase band-pass: forward then reverse through the same filter.
     *
     * Filtering twice squares the magnitude response but cancels the phase response
     * exactly, which is what keeps the detected peak times aligned with the real beats.
     * Beat *timing* is the entire measurement here — an interval series shifted by a
     * frequency-dependent delay would corrupt every HRV metric downstream.
     */
    public fun bandPassZeroPhase(
        values: DoubleArray,
        sampleRateHz: Double,
        lowHz: Double = BAND_LOW_HZ,
        highHz: Double = BAND_HIGH_HZ,
    ): DoubleArray {
        if (values.size < MIN_SAMPLES_FOR_FILTER) return values.copyOf()

        val centre = sqrt(lowHz * highHz)
        val q = centre / (highHz - lowHz)

        val forward = BiquadSection.bandPass(sampleRateHz, centre, q)
        val reverse = BiquadSection.bandPass(sampleRateHz, centre, q)

        val once = DoubleArray(values.size) { forward.process(values[it]) }
        val twice = DoubleArray(values.size)
        for (i in values.indices.reversed()) {
            twice[i] = reverse.process(once[i])
        }
        return twice
    }

    /**
     * Adaptive-threshold peak detection with a refractory period.
     *
     * The threshold follows a running estimate of the signal's own amplitude rather than
     * being fixed, because PPG amplitude varies by an order of magnitude with finger
     * pressure, skin and temperature. The refractory period is what stops the dicrotic
     * notch — the small secondary bump in every arterial pulse — from being counted as a
     * second beat, which is the classic way to report a doubled heart rate.
     */
    public fun detectBeats(
        filtered: DoubleArray,
        timestampsNanos: LongArray,
        refractoryMillis: Double = DEFAULT_REFRACTORY_MILLIS,
    ): List<Beat> {
        require(filtered.size == timestampsNanos.size) { "signal and timestamps must align" }
        if (filtered.size < MIN_SAMPLES_FOR_FILTER) return emptyList()

        val amplitude = robustAmplitude(filtered)
        if (amplitude <= 0.0) return emptyList()
        val threshold = amplitude * THRESHOLD_FRACTION

        val beats = mutableListOf<Beat>()
        var lastBeatNanos = Long.MIN_VALUE
        var previousBeatNanos: Long? = null

        for (i in 1 until filtered.size - 1) {
            val nanos = timestampsNanos[i]
            val isPeak = filtered[i] > filtered[i - 1] &&
                filtered[i] >= filtered[i + 1] &&
                filtered[i] >= threshold
            val isAfterRefractory = lastBeatNanos == Long.MIN_VALUE ||
                (nanos - lastBeatNanos) / NANOS_PER_MILLI >= refractoryMillis

            if (isPeak && isAfterRefractory) {
                val interval = previousBeatNanos?.let { (nanos - it) / NANOS_PER_MILLI }
                beats += Beat(timestampNanos = nanos, intervalMillis = interval)
                previousBeatNanos = nanos
                lastBeatNanos = nanos
            }
        }

        return beats
    }

    /**
     * Rejects implausible intervals and interpolates short gaps.
     *
     * An interval that deviates more than [ARTEFACT_TOLERANCE] from the local median is a
     * missed or doubled beat, not a physiological event. At most
     * [MAX_CONSECUTIVE_INTERPOLATIONS] in a row are repaired: beyond that the segment is
     * dropped, because a long stretch of invented intervals would look like beautifully
     * regular data.
     */
    public fun correctArtefacts(intervalsMillis: List<Double>): CorrectedIntervals {
        if (intervalsMillis.size < MIN_INTERVALS_FOR_CORRECTION) {
            return CorrectedIntervals(intervalsMillis, rejected = 0, interpolated = 0)
        }

        val output = mutableListOf<Double>()
        var rejected = 0
        var interpolated = 0
        var consecutive = 0

        for (index in intervalsMillis.indices) {
            val value = intervalsMillis[index]
            val local = localMedian(intervalsMillis, index)
            val plausible = value in MIN_INTERVAL_MILLIS..MAX_INTERVAL_MILLIS &&
                abs(value - local) <= local * ARTEFACT_TOLERANCE

            if (plausible) {
                output += value
                consecutive = 0
            } else {
                rejected++
                consecutive++
                if (consecutive <= MAX_CONSECUTIVE_INTERPOLATIONS) {
                    output += local
                    interpolated++
                }
            }
        }

        return CorrectedIntervals(output, rejected, interpolated)
    }

    public data class CorrectedIntervals(
        public val intervalsMillis: List<Double>,
        public val rejected: Int,
        public val interpolated: Int,
    ) {
        /**
         * Whether the corrected series is trustworthy enough to show the user a number.
         *
         * Below this the app says "не вижу пульс" and the breathing pacer continues
         * without biofeedback.
         */
        public val isUsable: Boolean
            get() {
                val total = intervalsMillis.size + rejected
                if (intervalsMillis.size < MIN_INTERVALS_FOR_CORRECTION) return false
                return rejected.toDouble() / total <= MAX_REJECTED_FRACTION
            }
    }

    /**
     * A signal-quality index for one window, in [0, 1].
     *
     * Combines how periodic the signal is with how much of its energy lies in the
     * plausible heart-rate band. A finger that has moved produces a high-amplitude,
     * broadband, aperiodic trace, which scores near zero — which is exactly the case a
     * naive amplitude check would score as excellent.
     */
    public fun signalQuality(filtered: DoubleArray, sampleRateHz: Double): Double {
        if (filtered.size < MIN_SAMPLES_FOR_FILTER) return 0.0

        // Numerator and denominator use the *same* estimator at the same bin spacing, so
        // the ratio is a genuine fraction. Mixing a spectral numerator with a
        // time-domain denominator would leave an arbitrary scale factor in the result --
        // which is exactly the bug the pulse-versus-movement test caught.
        val nyquist = sampleRateHz / 2.0
        val inBand = spectralEnergy(filtered, sampleRateHz, BAND_LOW_HZ, minOf(BAND_HIGH_HZ, nyquist))
        val total = spectralEnergy(filtered, sampleRateHz, SPECTRUM_STEP_HZ, nyquist - SPECTRUM_STEP_HZ)
        if (total <= 0.0) return 0.0

        val bandRatio = (inBand / total).coerceIn(0.0, 1.0)
        val periodicity = peakAutocorrelation(filtered, sampleRateHz).coerceIn(0.0, 1.0)

        // Geometric mean: a signal must be both in-band and periodic. Either one alone
        // is satisfied by artefacts -- band-passed movement noise is in-band, and a
        // steady flicker is periodic.
        return sqrt(bandRatio * periodicity)
    }

    // --- internals -----------------------------------------------------------------

    private fun robustAmplitude(values: DoubleArray): Double {
        val positives = values.filter { it > 0.0 }.sorted()
        if (positives.isEmpty()) return 0.0
        return positives[(positives.size * AMPLITUDE_PERCENTILE).toInt().coerceAtMost(positives.size - 1)]
    }

    private fun localMedian(values: List<Double>, index: Int): Double {
        val from = maxOf(0, index - MEDIAN_WINDOW / 2)
        val to = minOf(values.size - 1, index + MEDIAN_WINDOW / 2)
        val window = values.subList(from, to + 1).sorted()
        return window[window.size / 2]
    }

    /**
     * Energy between two frequencies, summed over evenly spaced bins.
     *
     * Deliberately unnormalised: it is only ever used as a ratio against itself over a
     * wider band, so any constant scale factor cancels.
     */
    private fun spectralEnergy(values: DoubleArray, sampleRateHz: Double, lowHz: Double, highHz: Double): Double {
        if (highHz <= lowHz) return 0.0
        var energy = 0.0
        var frequency = lowHz
        while (frequency <= highHz) {
            val magnitude = goertzel(values, sampleRateHz, frequency)
            energy += magnitude * magnitude
            frequency += SPECTRUM_STEP_HZ
        }
        return energy
    }

    /** Highest autocorrelation within the plausible beat-interval range. */
    private fun peakAutocorrelation(values: DoubleArray, sampleRateHz: Double): Double {
        val minLag = (MIN_INTERVAL_MILLIS / MILLIS_PER_SECOND * sampleRateHz).toInt()
        val maxLag = (MAX_INTERVAL_MILLIS / MILLIS_PER_SECOND * sampleRateHz).toInt()
        if (maxLag >= values.size || minLag >= maxLag) return 0.0

        val zeroLag = values.sumOf { it * it }
        if (zeroLag <= 0.0) return 0.0

        var best = 0.0
        for (lag in minLag..maxLag) {
            var sum = 0.0
            for (i in 0 until values.size - lag) {
                sum += values[i] * values[i + lag]
            }
            val normalised = sum / zeroLag
            if (normalised > best) best = normalised
        }
        return best
    }

    internal fun goertzel(values: DoubleArray, sampleRateHz: Double, frequencyHz: Double): Double {
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

    private const val NANOS_PER_MILLI = 1_000_000.0
    private const val MILLIS_PER_SECOND = 1_000.0
    private const val MIN_SAMPLES_FOR_FILTER = 16
    private const val MIN_INTERVALS_FOR_CORRECTION = 5
    private const val DEFAULT_REFRACTORY_MILLIS = 300.0
    private const val THRESHOLD_FRACTION = 0.45
    private const val AMPLITUDE_PERCENTILE = 0.75
    private const val ARTEFACT_TOLERANCE = 0.20
    private const val MAX_CONSECUTIVE_INTERPOLATIONS = 2
    private const val MAX_REJECTED_FRACTION = 0.20
    private const val MEDIAN_WINDOW = 5

    /** Bin spacing for the spectral-energy ratio. */
    private const val SPECTRUM_STEP_HZ = 0.1
}

/**
 * A second-order IIR section.
 *
 * Deliberately not shared with the identical section in `:engine:audio`. That module is
 * an Android library because it needs `AudioTrack`; this one is pure JVM so the whole
 * measurement chain can be tested without an emulator. Extracting twenty lines into a
 * third module, or making the measurement path depend on the stimulus path, would cost
 * more than the duplication does.
 */
public class BiquadSection(
    private val b0: Double,
    private val b1: Double,
    private val b2: Double,
    private val a1: Double,
    private val a2: Double,
) {
    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    public fun process(input: Double): Double {
        val output = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = input
        y2 = y1
        y1 = output
        return output
    }

    public fun reset() {
        x1 = 0.0
        x2 = 0.0
        y1 = 0.0
        y2 = 0.0
    }

    public companion object {
        public fun bandPass(sampleRateHz: Double, centreHz: Double, q: Double): BiquadSection {
            require(centreHz > 0.0 && centreHz < sampleRateHz / 2.0) { "centreHz must be below Nyquist" }
            require(q > 0.0) { "q must be positive" }

            val omega = 2.0 * PI * centreHz / sampleRateHz
            val alpha = sin(omega) / (2.0 * q)
            val a0 = 1.0 + alpha

            return BiquadSection(
                b0 = alpha / a0,
                b1 = 0.0,
                b2 = -alpha / a0,
                a1 = (-2.0 * cos(omega)) / a0,
                a2 = (1.0 - alpha) / a0,
            )
        }
    }
}
