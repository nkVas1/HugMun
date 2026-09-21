/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.core.domain

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Whether a change in a measurement is real, or just the person having a different day.
 *
 * This is the most ethically loaded piece of arithmetic in the project. An 83-year-old
 * watching a line on a screen for signs of decline needs that line to distinguish signal
 * from noise, because both errors are costly: a false negative is complacency and a false
 * positive is fear.
 *
 * ### Why this is not the textbook Reliable Change Index
 *
 * The classic RCI (Jacobson & Truax, 1991) compares two scores using
 * `S_diff = √2 · SD_norm · √(1 − r_tt)`, where `SD_norm` is the **between-person**
 * standard deviation of a normative sample and `r_tt` is test–retest reliability. It is
 * built for the situation where you have two measurements of a person and a published
 * norm.
 *
 * Our situation is the opposite. We have no normative sample for our own battery — and
 * say so in `docs/research/05-measurement.md` — but burst sampling gives us many
 * measurements of *this* person. Substituting a within-person SD into the RCI formula
 * would be a category error: within-person SD already contains measurement error plus
 * genuine day-to-day state variation, so multiplying it by `√(1 − r_tt)` shrinks the
 * error term and makes the test far too eager to declare a change. (This is not
 * hypothetical; it is exactly the bug the false-alarm test below caught.)
 *
 * So we ask the question that our data can actually answer:
 *
 * > Is today's value unusual **for this person**, relative to their own recent spread?
 *
 * which is a prediction interval for a new observation against the mean of `n` previous
 * ones:
 *
 * ```
 *   SE_pred = SD_within · √(1 + 1/n)
 *   index   = (x_new − mean) / SE_pred        ~ t(n − 1) under no change
 * ```
 *
 * The `t` critical value, rather than 1.96, matters here: with six observations the
 * two-tailed 95 % point is 2.571, so using the normal quantile would flag roughly twice
 * as many stable people as it should.
 *
 * `|index| < t_crit` is reported to the user, in plain Russian, as "within your usual
 * variation". That sentence is the product.
 */
public object ReliableChange {

    /** Two-tailed 95 %. */
    public const val ALPHA: Double = 0.05

    /**
     * Below this many prior observations we refuse to judge.
     *
     * With fewer than five, the estimate of the person's own spread is so poor that the
     * interval is either uselessly wide or accidentally narrow. Saying "not enough data
     * yet" is the honest output.
     */
    public const val MIN_OBSERVATIONS: Int = 5

    public enum class Verdict {
        /** Inside the interval. The honest answer most of the time. */
        WITHIN_USUAL_VARIATION,

        /** Outside the interval, in the better direction. */
        IMPROVED,

        /** Outside the interval, in the worse direction. */
        DECLINED,

        /** Not enough data to say anything. Stated plainly rather than hidden. */
        INSUFFICIENT_DATA,
    }

    public data class Result(
        public val verdict: Verdict,
        /** The standardised difference; `null` when no judgement was possible. */
        public val index: Double?,
        /** The person's own mean over [Result.observationsUsed] prior observations. */
        public val baseline: Double?,
        /** Standard error of a new observation against that mean. */
        public val standardError: Double?,
        /** Half-width of the "usual variation" band, in the units of the measure. */
        public val bandHalfWidth: Double?,
        public val observationsUsed: Int,
    ) {
        public val isJudged: Boolean get() = verdict != Verdict.INSUFFICIENT_DATA
    }

    /**
     * Judges the latest value against the person's own history.
     *
     * @param history earlier observations, oldest first. The value being judged must
     *   **not** be included.
     * @param latest the value being judged.
     * @param lowerIsBetter true for the «Зоркость» threshold, where a shorter duration
     *   is the better result.
     */
    public fun evaluate(
        history: List<Double>,
        latest: Double,
        lowerIsBetter: Boolean,
    ): Result {
        if (history.size < MIN_OBSERVATIONS) {
            return Result(Verdict.INSUFFICIENT_DATA, null, null, null, null, history.size)
        }

        val n = history.size
        val baseline = history.average()
        val sd = sampleStandardDeviation(history)
        if (sd <= 0.0 || !sd.isFinite()) {
            return Result(Verdict.INSUFFICIENT_DATA, null, baseline, null, null, n)
        }

        val standardError = sd * sqrt(1.0 + 1.0 / n)
        val critical = studentTTwoTailed95(degreesOfFreedom = n - 1)
        val rawChange = latest - baseline
        val index = rawChange / standardError

        val verdict = when {
            abs(index) < critical -> Verdict.WITHIN_USUAL_VARIATION
            (rawChange < 0.0) == lowerIsBetter -> Verdict.IMPROVED
            else -> Verdict.DECLINED
        }

        return Result(
            verdict = verdict,
            index = index,
            baseline = baseline,
            standardError = standardError,
            bandHalfWidth = critical * standardError,
            observationsUsed = n,
        )
    }

    /**
     * Sample standard deviation, with Bessel's correction.
     *
     * The correction is not a detail at this sample size: with six observations the
     * population formula understates the spread by about 9 %, which narrows the band and
     * makes the app claim changes that are not there.
     */
    private fun sampleStandardDeviation(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        val sumSquares = values.sumOf { (it - mean) * (it - mean) }
        return sqrt(sumSquares / (values.size - 1))
    }

    /**
     * Two-tailed 95 % critical value of Student's t.
     *
     * A short exact table rather than an approximation, because the values that matter
     * here are the small ones and they are the ones approximations get wrong. Beyond the
     * table the distribution is close enough to normal that the last entry is used.
     */
    internal fun studentTTwoTailed95(degreesOfFreedom: Int): Double {
        require(degreesOfFreedom >= 1) { "degrees of freedom must be at least 1" }
        return T_TABLE[degreesOfFreedom] ?: when {
            degreesOfFreedom <= 40 -> 2.021
            degreesOfFreedom <= 60 -> 2.000
            degreesOfFreedom <= 120 -> 1.980
            else -> NORMAL_QUANTILE_95
        }
    }

    /** The normal two-tailed 95 % quantile, i.e. the `t` limit as df → ∞. */
    public const val NORMAL_QUANTILE_95: Double = 1.960

    private val T_TABLE: Map<Int, Double> = mapOf(
        1 to 12.706, 2 to 4.303, 3 to 3.182, 4 to 2.776, 5 to 2.571,
        6 to 2.447, 7 to 2.365, 8 to 2.306, 9 to 2.262, 10 to 2.228,
        11 to 2.201, 12 to 2.179, 13 to 2.160, 14 to 2.145, 15 to 2.131,
        16 to 2.120, 17 to 2.110, 18 to 2.101, 19 to 2.093, 20 to 2.086,
        21 to 2.080, 22 to 2.074, 23 to 2.069, 24 to 2.064, 25 to 2.060,
        26 to 2.056, 27 to 2.052, 28 to 2.048, 29 to 2.045, 30 to 2.042,
    )
}
