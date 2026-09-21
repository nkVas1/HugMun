/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.engine.scheduling

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * FSRS-6 — the Free Spaced Repetition Scheduler, implemented from the published
 * specification.
 *
 * Attribution: the algorithm and its default parameters are the work of the Open Spaced
 * Repetition community (https://github.com/open-spaced-repetition). HugMun reimplements
 * it rather than depending on a port for three reasons, set out in
 * `docs/research/04-prior-art.md`:
 *
 *  1. we need clamps the reference implementations deliberately do not have;
 *  2. our grade is derived from cue level and latency, not self-reported;
 *  3. the scheduler sits on the measurement path, and nothing there may be a black box.
 *
 * The model is DSR: a memory has a **difficulty** `D ∈ [1, 10]`, a **stability** `S`
 * (the interval in days at which recall probability is 90 %), and a **retrievability**
 * `R(t, S)` that decays from 1 as time passes.
 */
public object Fsrs {

    /**
     * How well an item was retrieved. Mapped from behaviour, never asked of the user —
     * see [gradeFor].
     */
    public enum class Grade(public val value: Int) {
        /** Not recalled. In HugMun this means the answer had to be revealed. */
        AGAIN(1),

        /** Recalled, but slowly or with a cue still visible. */
        HARD(2),

        /** Recalled unaided, at a normal pace. */
        GOOD(3),

        /** Recalled unaided and immediately. */
        EASY(4),
    }

    /**
     * The remembered state of one item.
     *
     * @property stabilityDays interval at which recall probability is 90 %.
     * @property difficulty intrinsic difficulty of the item, 1 (easy) to 10 (hard).
     * @property reviewCount how many scheduled reviews this item has had.
     * @property lapseCount how many times it was forgotten.
     */
    public data class MemoryState(
        public val stabilityDays: Double,
        public val difficulty: Double,
        public val reviewCount: Int = 0,
        public val lapseCount: Int = 0,
    ) {
        init {
            require(stabilityDays > 0.0) { "stabilityDays must be positive, was $stabilityDays" }
            require(difficulty in MIN_DIFFICULTY..MAX_DIFFICULTY) {
                "difficulty must lie in [$MIN_DIFFICULTY, $MAX_DIFFICULTY], was $difficulty"
            }
        }
    }

    /**
     * The 21 FSRS-6 weights.
     *
     * The defaults were fitted by the Open Spaced Repetition project on a corpus of
     * roughly 727 million reviews. HugMun does not refit them: a single user cannot
     * produce enough reviews for optimisation to beat a well-fitted prior, and pretending
     * otherwise would be overfitting dressed up as personalisation.
     */
    @JvmInline
    public value class Parameters(public val w: DoubleArray) {
        init {
            require(w.size == PARAMETER_COUNT) {
                "FSRS-6 takes $PARAMETER_COUNT parameters, got ${w.size}"
            }
        }

        public operator fun get(index: Int): Double = w[index]
    }

    /**
     * Policy knobs, deliberately different from the FSRS defaults for this population.
     *
     * @property desiredRetention 0.95 rather than the usual 0.90. For a learner, a lapse
     *   is a useful signal; for an 83-year-old trying to hold on to a grandchild's name,
     *   a lapse is distressing. We buy retention with more frequent, shorter reviews.
     * @property maximumIntervalDays 45 rather than years. The published long intervals
     *   assume a healthy adult learner and tolerate the resulting lapse rate.
     * @property minimumIntervalDays never schedule the same item twice in one day
     *   through the long-term scheduler; within-session spacing is handled separately by
     *   [ExpandingRetrieval].
     */
    public data class Policy(
        public val desiredRetention: Double = DEFAULT_DESIRED_RETENTION,
        public val maximumIntervalDays: Long = DEFAULT_MAXIMUM_INTERVAL_DAYS,
        public val minimumIntervalDays: Long = 1L,
    ) {
        init {
            require(desiredRetention > 0.0 && desiredRetention < 1.0) {
                "desiredRetention must lie in (0, 1)"
            }
            require(minimumIntervalDays >= 1L) { "minimumIntervalDays must be at least 1" }
            require(maximumIntervalDays >= minimumIntervalDays) {
                "maximumIntervalDays must not be below minimumIntervalDays"
            }
        }
    }

    // --- Core model ---------------------------------------------------------------

    /**
     * Probability of recall `t` days after the last review.
     *
     * `R(t, S) = (1 + factor · t / S)^(−decay)` with `factor` chosen so that
     * `R(S, S) = 0.9` exactly.
     */
    public fun retrievability(
        elapsedDays: Double,
        stabilityDays: Double,
        parameters: Parameters = DEFAULT_PARAMETERS,
    ): Double {
        require(elapsedDays >= 0.0) { "elapsedDays must not be negative" }
        require(stabilityDays > 0.0) { "stabilityDays must be positive" }
        val decay = parameters[DECAY_INDEX]
        val factor = decayFactor(decay)
        return (1.0 + factor * elapsedDays / stabilityDays).pow(-decay)
    }

    /**
     * The interval that brings recall probability down to [Policy.desiredRetention].
     *
     * Inverse of [retrievability]: `I(r, S) = S / factor · (r^(−1/decay) − 1)`.
     */
    public fun intervalDays(
        stabilityDays: Double,
        policy: Policy = Policy(),
        parameters: Parameters = DEFAULT_PARAMETERS,
    ): Long {
        val decay = parameters[DECAY_INDEX]
        val factor = decayFactor(decay)
        val raw = stabilityDays / factor * (policy.desiredRetention.pow(-1.0 / decay) - 1.0)
        return raw.roundToLong().coerceIn(policy.minimumIntervalDays, policy.maximumIntervalDays)
    }

    /** Memory state after the very first review of an item. */
    public fun initialState(
        grade: Grade,
        parameters: Parameters = DEFAULT_PARAMETERS,
    ): MemoryState = MemoryState(
        stabilityDays = clampStability(parameters[grade.value - 1]),
        difficulty = clampDifficulty(initialDifficulty(grade, parameters)),
        reviewCount = 1,
        lapseCount = if (grade == Grade.AGAIN) 1 else 0,
    )

    /**
     * Memory state after a subsequent review.
     *
     * @param elapsedDays days since the previous review. Zero means a same-day repeat,
     *   which uses the short-term formula.
     */
    public fun nextState(
        current: MemoryState,
        grade: Grade,
        elapsedDays: Double,
        parameters: Parameters = DEFAULT_PARAMETERS,
    ): MemoryState {
        require(elapsedDays >= 0.0) { "elapsedDays must not be negative" }

        val difficulty = clampDifficulty(nextDifficulty(current.difficulty, grade, parameters))

        val stability = if (elapsedDays < SAME_DAY_THRESHOLD_DAYS) {
            shortTermStability(current.stabilityDays, grade, parameters)
        } else {
            val r = retrievability(elapsedDays, current.stabilityDays, parameters)
            if (grade == Grade.AGAIN) {
                postLapseStability(current.stabilityDays, current.difficulty, r, parameters)
            } else {
                recallStability(current.stabilityDays, current.difficulty, r, grade, parameters)
            }
        }

        return MemoryState(
            stabilityDays = clampStability(stability),
            difficulty = difficulty,
            reviewCount = current.reviewCount + 1,
            lapseCount = current.lapseCount + if (grade == Grade.AGAIN) 1 else 0,
        )
    }

    // --- Grade derivation ----------------------------------------------------------

    /**
     * Derives a [Grade] from what the user actually did, rather than asking them.
     *
     * Self-rating ("how well did you know that?") is an extra metacognitive demand and is
     * unreliable in exactly the population this module serves. Behaviour is not.
     *
     * @param recalledUnaided false when the answer had to be revealed, which under
     *   errorless learning is the closest thing to a failure we allow to occur.
     * @param cueLevel 0 = answer was on screen, 3 = no cue at all.
     * @param latencyMillis time from prompt to response.
     */
    public fun gradeFor(
        recalledUnaided: Boolean,
        cueLevel: Int,
        latencyMillis: Long,
    ): Grade = when {
        !recalledUnaided -> Grade.AGAIN
        cueLevel <= CUE_LEVEL_ASSISTED -> Grade.HARD
        latencyMillis <= FAST_RESPONSE_MILLIS -> Grade.EASY
        latencyMillis <= NORMAL_RESPONSE_MILLIS -> Grade.GOOD
        else -> Grade.HARD
    }

    // --- Internals -----------------------------------------------------------------

    private fun decayFactor(decay: Double): Double = NINETY_PERCENT.pow(-1.0 / decay) - 1.0

    private fun initialDifficulty(grade: Grade, p: Parameters): Double =
        p[4] - exp(p[5] * (grade.value - 1)) + 1.0

    private fun nextDifficulty(current: Double, grade: Grade, p: Parameters): Double {
        val delta = -p[6] * (grade.value - GOOD_GRADE)
        // Linear damping: difficulty moves less the closer it already is to the ceiling.
        val damped = current + delta * (MAX_DIFFICULTY - current) / DIFFICULTY_DAMPING_SPAN
        // Mean reversion toward the difficulty an "easy" first answer would have produced,
        // which is what stops long streaks from driving an item to trivially easy.
        return p[7] * initialDifficulty(Grade.EASY, p) + (1.0 - p[7]) * damped
    }

    private fun recallStability(
        stability: Double,
        difficulty: Double,
        retrievability: Double,
        grade: Grade,
        p: Parameters,
    ): Double {
        val hardPenalty = if (grade == Grade.HARD) p[15] else 1.0
        val easyBonus = if (grade == Grade.EASY) p[16] else 1.0
        val increase = exp(p[8]) *
            (DIFFICULTY_TERM_BASE - difficulty) *
            stability.pow(-p[9]) *
            (exp(p[10] * (1.0 - retrievability)) - 1.0) *
            hardPenalty *
            easyBonus
        return stability * (increase + 1.0)
    }

    private fun postLapseStability(
        stability: Double,
        difficulty: Double,
        retrievability: Double,
        p: Parameters,
    ): Double {
        val raw = p[11] *
            difficulty.pow(-p[12]) *
            ((stability + 1.0).pow(p[13]) - 1.0) *
            exp(p[14] * (1.0 - retrievability))
        // HugMun clamp, not part of the published formula: forgetting an item must never
        // be rewarded with a longer interval than remembering it would have produced.
        return minOf(raw, stability)
    }

    private fun shortTermStability(stability: Double, grade: Grade, p: Parameters): Double =
        stability * exp(p[17] * (grade.value - GOOD_GRADE + p[18])) * stability.pow(-p[19])

    private fun clampStability(value: Double): Double =
        value.coerceIn(MIN_STABILITY_DAYS, MAX_STABILITY_DAYS)

    private fun clampDifficulty(value: Double): Double =
        value.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    /** Natural-log helper kept for readability in tests. */
    internal fun lnSafe(value: Double): Double = ln(value.coerceAtLeast(MIN_STABILITY_DAYS))

    // --- Constants -----------------------------------------------------------------

    public const val PARAMETER_COUNT: Int = 21

    /** FSRS-6 defaults, as published by Open Spaced Repetition. */
    public val DEFAULT_PARAMETERS: Parameters = Parameters(
        doubleArrayOf(
            0.2120, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.0010,
            1.8722, 0.1666, 0.7960, 1.4835, 0.0614, 0.2629, 1.6483, 0.6014,
            1.8729, 0.5425, 0.0912, 0.0658, 0.1542,
        ),
    )

    public const val MIN_DIFFICULTY: Double = 1.0
    public const val MAX_DIFFICULTY: Double = 10.0
    public const val MIN_STABILITY_DAYS: Double = 0.001
    public const val MAX_STABILITY_DAYS: Double = 36_500.0

    public const val DEFAULT_DESIRED_RETENTION: Double = 0.95
    public const val DEFAULT_MAXIMUM_INTERVAL_DAYS: Long = 45L

    private const val DECAY_INDEX = 20
    private const val NINETY_PERCENT = 0.9
    private const val GOOD_GRADE = 3
    private const val DIFFICULTY_TERM_BASE = 11.0
    private const val DIFFICULTY_DAMPING_SPAN = 9.0
    private const val SAME_DAY_THRESHOLD_DAYS = 1.0
    private const val CUE_LEVEL_ASSISTED = 1
    private const val FAST_RESPONSE_MILLIS = 2_500L
    private const val NORMAL_RESPONSE_MILLIS = 6_000L
}
