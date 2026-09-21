/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.engine.psychophysics

/**
 * Drives one «Зоркость» session: generate a trial, take a response, move the staircase,
 * stop when the procedure or the time budget says so.
 *
 * Deliberately a plain state machine with no coroutines, no Flow and no Android. The
 * feature module owns presentation and timing; this owns the procedure. That split is
 * what lets the entire session logic be exercised in a unit test with a simulated
 * observer, which is how we know a threshold is a threshold and not an artefact.
 */
public class UfovSession(
    public val level: UfovLevel = UfovLevel.PRIMARY,
    public val timing: DisplayTiming,
    seed: Long,
    staircaseConfig: StaircaseConfig = defaultConfigFor(timing),
    private val budget: SessionBudget = SessionBudget(),
) {
    private val staircase = WeightedUpDownStaircase(staircaseConfig)
    private val generator = UfovTrialGenerator(level = level, seed = seed)

    private val completed = mutableListOf<CompletedTrial>()
    private var pending: UfovTrialSpec? = null
    private var elapsedMillis: Long = 0L

    /** Every scored and discarded trial, in order, for storage and offline re-analysis. */
    public val trials: List<CompletedTrial> get() = completed.toList()

    public val config: StaircaseConfig get() = staircase.config

    public val isFinished: Boolean
        get() = staircase.isFinished || elapsedMillis >= budget.maxDurationMillis

    /**
     * The next trial to present, or null if the session is over.
     *
     * Calling twice without recording a response returns the same trial.
     */
    public fun nextTrial(): UfovTrialSpec? {
        if (isFinished) return null
        pending?.let { return it }

        val frames = staircase.nextFrames()
        val spec = generator.next(index = completed.size, stimulusFrames = frames)
        pending = spec
        return spec
    }

    /** Records what happened and advances the procedure. */
    public fun record(response: UfovResponse): CompletedTrial {
        val spec = checkNotNull(pending) { "record() called without a pending trial" }
        pending = null

        val outcome = UfovScoring.score(spec, response)
        val step = staircase.record(outcome)

        elapsedMillis += estimatedTrialMillis(spec, response)

        val trial = CompletedTrial(
            spec = spec,
            response = response,
            outcome = outcome,
            presentedMillis = timing.framesToMillis(spec.stimulusFrames),
            isReversal = step.isReversal,
        )
        completed += trial
        return trial
    }

    /** The session outcome, or null if too little usable data was collected. */
    public fun result(): UfovSessionResult? {
        val threshold = staircase.threshold() ?: return null
        val scored = completed.count { it.outcome != TrialResponse.DISCARDED }
        val correct = completed.count { it.outcome == TrialResponse.CORRECT }

        return UfovSessionResult(
            level = level,
            timing = timing,
            thresholdFrames = threshold.frames,
            thresholdMillis = threshold.millis(timing),
            isFloorLimited = threshold.isFloorLimited(staircase.config),
            reversalsUsed = threshold.reversalsUsed,
            scoredTrials = scored,
            correctTrials = correct,
            discardedTrials = staircase.discardedTrials,
            reversalFrames = staircase.reversals,
        )
    }

    /**
     * Why the session ended.
     *
     * Worth distinguishing: a session that ran out of time before converging is not a
     * failure, but its threshold is less precise, and the user is told so rather than
     * being shown a confident number.
     */
    public fun stopReason(): StopReason = when {
        staircase.isFinished && staircase.reversals.size >= staircase.config.reversalsToStop ->
            StopReason.CONVERGED
        staircase.scoredTrials >= staircase.config.maxTrials -> StopReason.TRIAL_LIMIT
        elapsedMillis >= budget.maxDurationMillis -> StopReason.TIME_LIMIT
        else -> StopReason.IN_PROGRESS
    }

    private fun estimatedTrialMillis(spec: UfovTrialSpec, response: UfovResponse): Long = spec.fixationMillis.toLong() +
        timing.framesToMillis(spec.stimulusFrames).toLong() +
        spec.maskMillis.toLong() +
        response.latencyMillis

    public enum class StopReason {
        IN_PROGRESS,

        /** The staircase collected its full complement of reversals. Best case. */
        CONVERGED,

        /** The trial cap was reached first. */
        TRIAL_LIMIT,

        /** The time budget was reached first. */
        TIME_LIMIT,
    }

    /**
     * The session time budget.
     *
     * 12 minutes of trials, matching the dose in `docs/research/02-intervention-specs.md`
     * §1.5. ACTIVE sessions ran 60–75 minutes including group instruction; 12 minutes of
     * pure adaptive trials is our documented equivalent for the active ingredient, and it
     * is what an 83-year-old will actually finish.
     */
    public data class SessionBudget(public val maxDurationMillis: Long = DEFAULT_BUDGET_MILLIS) {
        public companion object {
            public const val DEFAULT_BUDGET_MILLIS: Long = 12L * 60L * 1_000L
        }
    }

    public companion object {
        /**
         * A starting configuration scaled to the display.
         *
         * The ceiling is fixed in *time*, not frames: 500 ms is the top of the classical
         * UFOV range, and it must mean the same thing on a 60 Hz panel as on a 120 Hz one.
         */
        public fun defaultConfigFor(timing: DisplayTiming): StaircaseConfig {
            val maxFrames = timing.millisToFrames(CEILING_MILLIS)
            val startFrames = timing.millisToFrames(START_MILLIS).coerceAtMost(maxFrames)
            return StaircaseConfig(
                startFrames = startFrames,
                minFrames = 1,
                maxFrames = maxFrames,
            )
        }

        /** Top of the classical UFOV presentation range. */
        private const val CEILING_MILLIS = 500.0

        /**
         * Comfortably visible for almost anyone, so the first few trials are successes.
         * Starting near threshold would be more efficient and considerably more
         * discouraging.
         */
        private const val START_MILLIS = 250.0
    }
}

/** One trial as stored. */
public data class CompletedTrial(
    public val spec: UfovTrialSpec,
    public val response: UfovResponse,
    public val outcome: TrialResponse,
    public val presentedMillis: Double,
    public val isReversal: Boolean,
)

/** The result of one «Зоркость» session. */
public data class UfovSessionResult(
    public val level: UfovLevel,
    public val timing: DisplayTiming,
    public val thresholdFrames: Double,
    public val thresholdMillis: Double,
    /** True when the estimate sits in the display's bottom quantisation bin. */
    public val isFloorLimited: Boolean,
    public val reversalsUsed: Int,
    public val scoredTrials: Int,
    public val correctTrials: Int,
    public val discardedTrials: Int,
    public val reversalFrames: List<Int>,
) {
    public val accuracy: Double
        get() = if (scoredTrials == 0) 0.0 else correctTrials.toDouble() / scoredTrials

    /**
     * Whether enough of the session presented correctly for the threshold to be trusted.
     *
     * A device that dropped a fifth of its stimulus frames was not running the experiment
     * we designed, and the app says so instead of quietly reporting the number.
     */
    public val isQualityAcceptable: Boolean
        get() {
            val total = scoredTrials + discardedTrials
            if (total == 0) return false
            return discardedTrials.toDouble() / total <= MAX_DISCARD_FRACTION
        }

    public companion object {
        public const val MAX_DISCARD_FRACTION: Double = 0.20
    }
}
