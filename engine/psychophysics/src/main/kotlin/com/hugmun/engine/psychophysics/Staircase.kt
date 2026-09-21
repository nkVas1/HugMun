/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.engine.psychophysics

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * How a presented trial was scored.
 *
 * [DISCARDED] exists because of frame drops. If the display failed to hold the stimulus
 * for the intended number of frames, the trial did not measure what it was supposed to
 * measure, and letting it move the staircase would silently corrupt the threshold. Such
 * trials are recorded (they are a quality metric) but they do not advance the procedure.
 */
public enum class TrialResponse {
    CORRECT,
    INCORRECT,
    DISCARDED,
}

/**
 * Configuration for a [WeightedUpDownStaircase].
 *
 * @property startFrames first stimulus duration, in frames.
 * @property minFrames floor; one frame is the physical limit of the display.
 * @property maxFrames ceiling; beyond this the task stops being a speed measure.
 * @property stepUpLog10 the *upward* (easier) step, in log10 units of frame count.
 * @property targetAccuracy the point on the psychometric function to converge on.
 * @property reversalsToStop how many reversals end the run.
 * @property reversalsForThreshold how many of the final reversals enter the estimate.
 * @property maxTrials hard cap, so a session cannot run past its time budget.
 */
public data class StaircaseConfig(
    public val startFrames: Int = DEFAULT_START_FRAMES,
    public val minFrames: Int = 1,
    public val maxFrames: Int = DEFAULT_MAX_FRAMES,
    public val stepUpLog10: Double = DEFAULT_STEP_UP_LOG10,
    public val targetAccuracy: Double = DEFAULT_TARGET_ACCURACY,
    public val reversalsToStop: Int = DEFAULT_REVERSALS_TO_STOP,
    public val reversalsForThreshold: Int = DEFAULT_REVERSALS_FOR_THRESHOLD,
    public val maxTrials: Int = DEFAULT_MAX_TRIALS,
) {
    init {
        require(minFrames >= 1) { "minFrames must be at least 1" }
        require(maxFrames >= minFrames) { "maxFrames must not be below minFrames" }
        require(startFrames in minFrames..maxFrames) { "startFrames must lie within [minFrames, maxFrames]" }
        require(stepUpLog10 > 0.0) { "stepUpLog10 must be positive" }
        require(targetAccuracy > 0.0 && targetAccuracy < 1.0) { "targetAccuracy must lie in (0, 1)" }
        require(reversalsForThreshold in 1..reversalsToStop) {
            "reversalsForThreshold must be in 1..reversalsToStop"
        }
        require(maxTrials > 0) { "maxTrials must be positive" }
    }

    /**
     * The downward (harder) step size.
     *
     * Derivation — this is the whole point of the weighted up–down method (Kaernbach,
     * 1991). At the convergence level the expected change in stimulus level must be zero:
     *
     * ```
     *   p · (−Δ_down) + (1 − p) · (+Δ_up) = 0
     *   ⇒ Δ_down / Δ_up = (1 − p) / p
     * ```
     *
     * For the UFOV criterion p = 0.75 this gives exactly 1/3. A 3-down-1-up transformed
     * staircase would instead converge on 0.794, which is not the criterion the UFOV
     * literature uses, and needs more trials to get there.
     *
     * Caveat worth knowing: with *fixed* step sizes the achieved convergence point is
     * biased away from the nominal target by an amount that depends on the slope of the
     * psychometric function (García-Pérez, 1998). We accept that bias because it is
     * consistent within a person across sessions, and this measure is used for
     * within-person tracking, never for comparison against a norm.
     */
    public val stepDownLog10: Double
        get() = stepUpLog10 * (1.0 - targetAccuracy) / targetAccuracy

    public companion object {
        public const val DEFAULT_START_FRAMES: Int = 30
        public const val DEFAULT_MAX_FRAMES: Int = 60
        public const val DEFAULT_STEP_UP_LOG10: Double = 0.10
        public const val DEFAULT_TARGET_ACCURACY: Double = 0.75
        public const val DEFAULT_REVERSALS_TO_STOP: Int = 8
        public const val DEFAULT_REVERSALS_FOR_THRESHOLD: Int = 6
        public const val DEFAULT_MAX_TRIALS: Int = 60
    }
}

/** One completed step of the staircase, kept for audit and offline re-analysis. */
public data class StaircaseStep(
    public val trialIndex: Int,
    public val presentedFrames: Int,
    public val response: TrialResponse,
    public val levelBefore: Double,
    public val levelAfter: Double,
    public val isReversal: Boolean,
)

/** The outcome of a completed run. */
public data class ThresholdEstimate(
    /** Geometric mean of the reversal frame counts. */
    public val frames: Double,
    public val reversalsUsed: Int,
    public val totalTrials: Int,
    public val discardedTrials: Int,
) {
    /** Converts the estimate to milliseconds on the display it was measured on. */
    public fun millis(timing: DisplayTiming): Double = timing.framesToMillis(frames)

    /**
     * Whether the estimate falls inside the bottom quantisation bin of the display.
     *
     * When true, the participant is performing at or near the shortest stimulus this
     * screen can produce, so their real threshold is not resolvable here — it could be
     * anywhere below. Sessions in this state are flagged rather than celebrated: a
     * "better" number on a faster display would not mean the person improved.
     */
    public fun isFloorLimited(config: StaircaseConfig): Boolean = frames < config.minFrames + 1.0
}

/**
 * Weighted up–down adaptive staircase operating on stimulus duration in display frames.
 *
 * The level is tracked as `log10(frames)` so that steps are multiplicative, which matches
 * the roughly logarithmic spacing of perceptual discriminability far better than linear
 * milliseconds would. The level is quantised to whole frames only at presentation time,
 * and the *presented* frame count — not the requested one — is what enters the threshold.
 *
 * Typical use:
 * ```
 * val staircase = WeightedUpDownStaircase(config)
 * while (!staircase.isFinished) {
 *     val frames = staircase.nextFrames()
 *     val outcome = presentTrial(frames)          // may return DISCARDED on a frame drop
 *     staircase.record(outcome)
 * }
 * val threshold = staircase.threshold()
 * ```
 *
 * This class is not thread-safe; drive it from a single coroutine.
 */
public class WeightedUpDownStaircase(public val config: StaircaseConfig = StaircaseConfig()) {
    private val minLevel = log10(config.minFrames.toDouble())
    private val maxLevel = log10(config.maxFrames.toDouble())

    private var level: Double = log10(config.startFrames.toDouble())
    private var lastDirection: Direction = Direction.NONE
    private var pendingFrames: Int? = null

    private val recordedSteps = mutableListOf<StaircaseStep>()
    private val reversalFrames = mutableListOf<Int>()

    /** Every step taken so far, in order. */
    public val steps: List<StaircaseStep> get() = recordedSteps.toList()

    /** Frame counts at which the staircase changed direction, in order. */
    public val reversals: List<Int> get() = reversalFrames.toList()

    /** Trials that were presented but not scored because the display dropped frames. */
    public var discardedTrials: Int = 0
        private set

    /** Trials that actually moved the staircase. */
    public val scoredTrials: Int get() = recordedSteps.count { it.response != TrialResponse.DISCARDED }

    public val isFinished: Boolean
        get() = reversalFrames.size >= config.reversalsToStop || scoredTrials >= config.maxTrials

    /**
     * The stimulus duration for the next trial, in frames.
     *
     * Calling this twice without an intervening [record] returns the same value: the
     * staircase does not advance until it has been told what happened.
     */
    public fun nextFrames(): Int {
        pendingFrames?.let { return it }
        val frames = levelToFrames(level)
        pendingFrames = frames
        return frames
    }

    /**
     * Records the outcome of the trial most recently handed out by [nextFrames].
     *
     * @throws IllegalStateException if called without a pending trial.
     */
    public fun record(response: TrialResponse): StaircaseStep {
        val presented = checkNotNull(pendingFrames) {
            "record() called without a pending trial; call nextFrames() first"
        }
        pendingFrames = null

        if (response == TrialResponse.DISCARDED) {
            discardedTrials++
            val step = StaircaseStep(
                trialIndex = recordedSteps.size,
                presentedFrames = presented,
                response = response,
                levelBefore = level,
                levelAfter = level,
                isReversal = false,
            )
            recordedSteps += step
            return step
        }

        val levelBefore = level
        val direction = if (response == TrialResponse.CORRECT) Direction.DOWN else Direction.UP
        val delta = if (direction == Direction.DOWN) -config.stepDownLog10 else config.stepUpLog10
        level = (level + delta).coerceIn(minLevel, maxLevel)

        val isReversal = lastDirection != Direction.NONE && direction != lastDirection
        if (isReversal) {
            // The reversal is recorded at the level that produced the change, which is
            // the level actually presented, not the one we are about to move to.
            reversalFrames += presented
        }
        lastDirection = direction

        val step = StaircaseStep(
            trialIndex = recordedSteps.size,
            presentedFrames = presented,
            response = response,
            levelBefore = levelBefore,
            levelAfter = level,
            isReversal = isReversal,
        )
        recordedSteps += step
        return step
    }

    /**
     * The threshold estimate, or `null` if too few reversals have been collected for the
     * estimate to mean anything.
     *
     * The geometric mean is used because the staircase steps multiplicatively; an
     * arithmetic mean of reversal durations would be biased upward.
     */
    public fun threshold(): ThresholdEstimate? {
        val usable = reversalFrames.takeLast(config.reversalsForThreshold)
        if (usable.size < MIN_REVERSALS_FOR_ESTIMATE) return null

        val meanLog = usable.sumOf { log10(it.toDouble()) } / usable.size
        return ThresholdEstimate(
            frames = TEN.pow(meanLog),
            reversalsUsed = usable.size,
            totalTrials = recordedSteps.size,
            discardedTrials = discardedTrials,
        )
    }

    private fun levelToFrames(logLevel: Double): Int {
        val raw = TEN.pow(logLevel).roundToInt()
        return min(config.maxFrames, max(config.minFrames, raw))
    }

    private enum class Direction { NONE, UP, DOWN }

    public companion object {
        /**
         * Below four reversals the geometric mean is dominated by the initial descent
         * rather than by the participant's threshold, so no estimate is reported at all.
         * Saying "not enough data" is better than reporting a number that is mostly noise.
         */
        public const val MIN_REVERSALS_FOR_ESTIMATE: Int = 4

        private const val TEN = 10.0
    }
}
