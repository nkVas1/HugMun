/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.engine.signal

/**
 * Finding the breathing rate at which this person's cardiovascular system resonates.
 *
 * Slow breathing near ~0.1 Hz maximally amplifies respiratory sinus arrhythmia and
 * baroreflex gain, but the exact rate is individual — typically between 4.5 and 6.5
 * breaths per minute. The clinical protocol is to sweep those rates and measure which
 * one produces the largest response (Lehrer et al., 2020).
 *
 * **Consumer breathing apps almost universally skip this step** and pick a single rate
 * for everyone, usually 6 breaths per minute or something rounder. That is a plausible
 * reason such implementations underperform the clinical protocol, and it is the main
 * thing HugMun does differently here.
 *
 * Pure Kotlin. Scoring is separated from acquisition so that the decision can be
 * re-derived from stored epochs if the scoring ever changes.
 */
public object ResonanceFrequency {

    /**
     * The rates to sweep, in breaths per minute, in the order they are presented.
     *
     * Descending, as in the published protocol: starting slow and getting slower is
     * harder than easing down, and an uncomfortable epoch produces a poor measurement
     * for reasons that have nothing to do with resonance.
     */
    public val SWEEP_RATES: List<Double> = listOf(6.5, 6.0, 5.5, 5.0, 4.5)

    /** Each epoch runs two minutes, with a minute of free breathing between. */
    public const val EPOCH_SECONDS: Int = 120
    public const val REST_SECONDS: Int = 60

    /**
     * The rate used when no usable pulse signal was obtained.
     *
     * 5.5 br/min is the modal resonance frequency in adults. When this is used the app
     * says so, rather than presenting an assumption as a measurement.
     */
    public const val FALLBACK_RATE: Double = 5.5

    /** One epoch of the sweep. */
    public data class Epoch(
        public val rateBreathsPerMinute: Double,
        public val intervalsMillis: List<Double>,
        /** Fraction of the epoch where the pulse signal was usable, in [0, 1]. */
        public val signalQuality: Double,
    ) {
        public val breathPeriodSeconds: Double get() = SECONDS_PER_MINUTE / rateBreathsPerMinute

        private companion object {
            const val SECONDS_PER_MINUTE = 60.0
        }
    }

    /** What one epoch scored. */
    public data class EpochScore(
        public val rateBreathsPerMinute: Double,
        public val rsaAmplitude: Double,
        public val lowFrequencyPower: Double,
        public val coherence: Double,
        /** Normalised composite in [0, 1]; only comparable within one sweep. */
        public val composite: Double,
        public val isUsable: Boolean,
    )

    public data class Outcome(
        public val chosenRateBreathsPerMinute: Double,
        public val scores: List<EpochScore>,
        /** True when no epoch was usable and [FALLBACK_RATE] was used instead. */
        public val usedFallback: Boolean,
    )

    /**
     * Scores a completed sweep and picks a rate.
     *
     * The composite weights the three markers equally after normalising each to its own
     * maximum across the sweep. Equal weights rather than fitted ones: we have five
     * epochs from one person, which is nowhere near enough to justify tuning, and an
     * invented weighting would look more precise than it is.
     */
    public fun score(epochs: List<Epoch>): Outcome {
        val usable = epochs.filter { it.signalQuality >= MIN_QUALITY && it.intervalsMillis.size >= MIN_INTERVALS }

        if (usable.isEmpty()) {
            return Outcome(
                chosenRateBreathsPerMinute = FALLBACK_RATE,
                scores = epochs.map { it.emptyScore() },
                usedFallback = true,
            )
        }

        val raw = usable.map { epoch ->
            Triple(
                HeartRateVariability.rsaAmplitude(epoch.intervalsMillis, epoch.breathPeriodSeconds),
                HeartRateVariability.bandPower(epoch.intervalsMillis),
                HeartRateVariability.breathHeartCoherence(epoch.intervalsMillis, epoch.breathPeriodSeconds),
            )
        }

        val maxRsa = raw.maxOf { it.first }.takeIf { it > 0.0 } ?: 1.0
        val maxPower = raw.maxOf { it.second }.takeIf { it > 0.0 } ?: 1.0
        val maxCoherence = raw.maxOf { it.third }.takeIf { it > 0.0 } ?: 1.0

        val scored = usable.mapIndexed { index, epoch ->
            val (rsa, power, coherence) = raw[index]
            EpochScore(
                rateBreathsPerMinute = epoch.rateBreathsPerMinute,
                rsaAmplitude = rsa,
                lowFrequencyPower = power,
                coherence = coherence,
                composite = (rsa / maxRsa + power / maxPower + coherence / maxCoherence) / MARKER_COUNT,
                isUsable = true,
            )
        }

        val unusable = unusableScores(epochs, usable)

        val best = scored.maxWith(
            // Ties resolve toward the *higher* rate: it is easier to sustain, and a
            // protocol the person abandons has no effect at all.
            compareBy<EpochScore> { it.composite }.thenBy { it.rateBreathsPerMinute },
        )

        return Outcome(
            chosenRateBreathsPerMinute = best.rateBreathsPerMinute,
            scores = (scored + unusable).sortedByDescending { it.rateBreathsPerMinute },
            usedFallback = false,
        )
    }

    /** Epochs that were swept but could not be scored, kept so the UI can show the gap. */
    private fun unusableScores(all: List<Epoch>, usable: List<Epoch>): List<EpochScore> {
        val usableRates = usable.map { it.rateBreathsPerMinute }.toSet()
        return all.filterNot { it.rateBreathsPerMinute in usableRates }.map { it.emptyScore() }
    }

    private fun Epoch.emptyScore() = EpochScore(
        rateBreathsPerMinute = rateBreathsPerMinute,
        rsaAmplitude = 0.0,
        lowFrequencyPower = 0.0,
        coherence = 0.0,
        composite = 0.0,
        isUsable = false,
    )

    private const val MIN_QUALITY = 0.5
    private const val MIN_INTERVALS = 30
    private const val MARKER_COUNT = 3.0
}
