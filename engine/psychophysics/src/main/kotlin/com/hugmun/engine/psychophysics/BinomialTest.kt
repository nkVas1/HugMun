/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.engine.psychophysics

import kotlin.math.exp
import kotlin.math.ln

/**
 * Exact one-sided binomial test, used to ask whether a session measured anything at all.
 *
 * An adaptive staircase assumes the observer's accuracy responds to the stimulus level.
 * When it does not — the person cannot see the target at any duration the display can
 * produce, misunderstood the task, or was tapping through it — the level runs to the
 * ceiling and oscillates there. Reversals still accumulate, so the staircase happily
 * terminates and the geometric mean of those reversals is a perfectly well-formed number
 * that means nothing except "the top of the range".
 *
 * The only way to tell that apart from a genuine high threshold is to check performance
 * against the guess rate. That is a hypothesis test, so it is done properly here rather
 * than with an invented cut-off.
 *
 * The exact test is used rather than a normal approximation because the cases that matter
 * are exactly the ones where it is least valid: few trials, small success probability.
 */
public object BinomialTest {

    /**
     * `P(X ≥ successes)` for `X ~ Binomial(trials, probability)`.
     *
     * Computed in log space and summed from whichever tail is shorter, so that a session
     * of sixty trials at p = 1/16 does not lose precision to underflow.
     *
     * @return a p-value in `[0, 1]`; `1.0` when [successes] is zero or below, because
     *   observing at least nothing is certain.
     */
    public fun upperTailProbability(successes: Int, trials: Int, probability: Double): Double {
        require(trials >= 0) { "trials must not be negative" }
        require(probability in 0.0..1.0) { "probability must lie in [0, 1]" }
        if (successes <= 0) return 1.0
        if (successes > trials) return 0.0

        // Sum the shorter tail: below the mean the lower tail is cheaper and better
        // conditioned, and the complement is exact because the two partition the mass.
        val mean = trials * probability
        return if (successes > mean) {
            (successes..trials).sumOf { probabilityMass(it, trials, probability) }
        } else {
            val lower = (0 until successes).sumOf { probabilityMass(it, trials, probability) }
            (1.0 - lower).coerceIn(0.0, 1.0)
        }
    }

    /** `P(X = successes)` for `X ~ Binomial(trials, probability)`. */
    public fun probabilityMass(successes: Int, trials: Int, probability: Double): Double {
        if (successes < 0 || successes > trials) return 0.0
        if (probability <= 0.0) return if (successes == 0) 1.0 else 0.0
        if (probability >= 1.0) return if (successes == trials) 1.0 else 0.0

        val logP = logBinomialCoefficient(trials, successes) +
            successes * ln(probability) +
            (trials - successes) * ln(1.0 - probability)
        return exp(logP)
    }

    private fun logBinomialCoefficient(n: Int, k: Int): Double {
        // Multiplicative form in log space. n never exceeds a session's trial count, so
        // the loop is short and an exact sum beats a lgamma approximation here.
        val smaller = minOf(k, n - k)
        var total = 0.0
        for (i in 1..smaller) {
            total += ln((n - smaller + i).toDouble()) - ln(i.toDouble())
        }
        return total
    }
}
