/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.engine.psychophysics

import kotlin.random.Random

/**
 * The Useful Field of View paradigm — the trained task from the ACTIVE speed-of-processing
 * arm, which is the only cognitive-training protocol in this project with long-term
 * real-world outcome data behind it.
 *
 * The task is dual: identify a briefly presented central figure **and** localise a
 * simultaneous peripheral marker. Presentation duration is under adaptive control; the
 * measure is the duration at which the participant is 75 % correct on both subtasks
 * together. It is a perceptual threshold, not a reaction time — responses are untimed on
 * purpose, and hurrying the participant would measure something else entirely.
 *
 * Everything in this file is pure Kotlin: no Android, no rendering, no timing. The
 * feature module draws what these types describe and reports back what happened.
 */

/** The three difficulty levels of the classical paradigm. */
public enum class UfovLevel(
    public val id: Int,
    public val hasPeripheralTask: Boolean,
    public val distractorCount: Int,
) {
    /** Central identification only: processing speed. */
    SPEED(id = 1, hasPeripheralTask = true, distractorCount = 0),

    /** Central identification plus peripheral localisation: divided attention. */
    DIVIDED(id = 2, hasPeripheralTask = true, distractorCount = 0),

    /** As above, with a field of distractors: selective attention. */
    SELECTIVE(id = 3, hasPeripheralTask = true, distractorCount = 23),
    ;

    public companion object {
        /**
         * The level the app trains by default and reports as progress.
         *
         * Level 2 is the workhorse of the ACTIVE speed arm and the one whose threshold is
         * comparable across sessions.
         */
        public val PRIMARY: UfovLevel = DIVIDED

        public fun fromId(id: Int): UfovLevel? = entries.firstOrNull { it.id == id }
    }
}

/**
 * The central figure, as a two-alternative forced choice.
 *
 * A raven and an owl rather than the car and truck of the original. The discrimination is
 * equally coarse at short durations — wide-horizontal against compact-upright, which is
 * what survives a 25 ms presentation — and it carries the project's one mythological
 * thread into the place the user looks most.
 *
 * Both are drawn into the same square at the same nominal size, so overall extent carries
 * no information. Their filled areas are close but not photometrically matched; that
 * difference is constant across trials and is therefore not something the staircase can
 * move on, but exact luminance matching remains an open item rather than a claim.
 */
public enum class CentralFigure {
    RAVEN,
    OWL,
}

/**
 * Peripheral eccentricity, as a fraction of the usable field radius.
 *
 * Three rings rather than a continuum, because the threshold is already being estimated
 * along the time axis and adding a second adaptive dimension would need far more trials
 * than a 12-minute session allows.
 */
public enum class Eccentricity(public val normalisedRadius: Float) {
    NEAR(NEAR_RADIUS),
    MID(MID_RADIUS),
    FAR(FAR_RADIUS),
}

/**
 * Ring radii as a fraction of the usable field.
 *
 * Spaced so the three rings are about equally discriminable rather than equally spaced
 * in distance: peripheral acuity falls off faster than linearly, so equal steps in
 * radius would make the outer ring disproportionately harder.
 */
private const val NEAR_RADIUS = 0.36f
private const val MID_RADIUS = 0.63f
private const val FAR_RADIUS = 0.88f

/** One of eight radial directions, 45° apart, numbered clockwise from straight up. */
@JvmInline
public value class Direction(public val index: Int) {
    init {
        require(index in 0 until COUNT) { "direction index must be in 0..${COUNT - 1}, was $index" }
    }

    /** Degrees clockwise from twelve o'clock. */
    public val degrees: Float get() = index * DEGREES_PER_STEP

    public companion object {
        public const val COUNT: Int = 8
        private const val DEGREES_PER_STEP = 360f / COUNT

        public val ALL: List<Direction> = (0 until COUNT).map(::Direction)
    }
}

/** A marker placed in the periphery. */
public data class PeripheralMarker(public val direction: Direction, public val eccentricity: Eccentricity)

/**
 * Everything the renderer needs to present one trial, and everything the analysis needs
 * to interpret it afterwards.
 */
public data class UfovTrialSpec(
    public val index: Int,
    public val level: UfovLevel,
    public val centralFigure: CentralFigure,
    /** Null only at [UfovLevel.SPEED], which has no peripheral subtask. */
    public val target: PeripheralMarker?,
    public val distractors: List<PeripheralMarker>,
    /** Stimulus duration in display frames. Quantised; see [DisplayTiming]. */
    public val stimulusFrames: Int,
    /** Fixation duration, jittered to prevent temporal prediction. */
    public val fixationMillis: Int,
    /**
     * Mask duration.
     *
     * The mask is not decoration: without it the stimulus duration stops being the
     * limiting variable, because the afterimage keeps the figure legible and the measured
     * threshold collapses toward the display floor.
     */
    public val maskMillis: Int,
)

/** What the participant did. */
public data class UfovResponse(
    public val figure: CentralFigure?,
    public val direction: Direction?,
    /**
     * False when the display failed to hold the stimulus for [UfovTrialSpec.stimulusFrames].
     * Such trials are discarded rather than scored — see ADR 0004.
     */
    public val presentationAccurate: Boolean,
    public val latencyMillis: Long,
)

/** Scoring, kept separate from the staircase so it can be re-run over stored trials. */
public object UfovScoring {
    public fun score(spec: UfovTrialSpec, response: UfovResponse): TrialResponse {
        if (!response.presentationAccurate) return TrialResponse.DISCARDED

        val centralCorrect = response.figure == spec.centralFigure
        val peripheralCorrect = when (val target = spec.target) {
            null -> true
            else -> response.direction == target.direction
        }

        return if (centralCorrect && peripheralCorrect) {
            TrialResponse.CORRECT
        } else {
            TrialResponse.INCORRECT
        }
    }

    /**
     * Chance performance for a level, used when interpreting a session.
     *
     * At the primary level both subtasks must be right, so chance is 1/2 × 1/8 = 0.0625 —
     * comfortably below the 75 % criterion, which is what makes the criterion meaningful.
     */
    public fun chanceLevel(level: UfovLevel): Double =
        if (level.hasPeripheralTask) CENTRAL_CHANCE / Direction.COUNT else CENTRAL_CHANCE

    private const val CENTRAL_CHANCE = 0.5
}

/**
 * Builds trials.
 *
 * Two properties matter and are tested:
 *
 * - **Balance.** Central figures alternate in a shuffled-block design rather than being
 *   drawn independently, so a run of one figure cannot make the staircase look like a
 *   perceptual change when it is really a response bias.
 * - **Reproducibility.** Given a seed, the sequence is deterministic, so a stored session
 *   can be replayed exactly — which is what makes offline re-analysis possible.
 *
 * The seed also drives the novelty rotation: sessions differ visually from one another
 * while the psychophysics stays fixed, per the sustained-engagement literature.
 */
public class UfovTrialGenerator(
    private val level: UfovLevel = UfovLevel.PRIMARY,
    seed: Long,
    private val fixationMillis: Int = DEFAULT_FIXATION_MILLIS,
    private val fixationJitterMillis: Int = DEFAULT_FIXATION_JITTER_MILLIS,
    private val maskMillis: Int = DEFAULT_MASK_MILLIS,
) {
    private val random = Random(seed)
    private var figureBlock: MutableList<CentralFigure> = mutableListOf()
    private var directionBlock: MutableList<Direction> = mutableListOf()

    public fun next(index: Int, stimulusFrames: Int): UfovTrialSpec {
        val figure = nextFigure()
        val target = if (level.hasPeripheralTask) {
            PeripheralMarker(
                direction = nextDirection(),
                eccentricity = Eccentricity.entries.random(random),
            )
        } else {
            null
        }

        return UfovTrialSpec(
            index = index,
            level = level,
            centralFigure = figure,
            target = target,
            distractors = buildDistractors(target),
            stimulusFrames = stimulusFrames,
            fixationMillis = fixationMillis + random.nextInt(-fixationJitterMillis, fixationJitterMillis + 1),
            maskMillis = maskMillis,
        )
    }

    private fun nextFigure(): CentralFigure {
        if (figureBlock.isEmpty()) {
            figureBlock = CentralFigure.entries.shuffled(random).toMutableList()
        }
        return figureBlock.removeAt(figureBlock.lastIndex)
    }

    private fun nextDirection(): Direction {
        if (directionBlock.isEmpty()) {
            directionBlock = Direction.ALL.shuffled(random).toMutableList()
        }
        return directionBlock.removeAt(directionBlock.lastIndex)
    }

    /**
     * Distractors fill the field without ever occupying the target's own cell, so the
     * target is never occluded and the 8-alternative response stays well defined.
     */
    private fun buildDistractors(target: PeripheralMarker?): List<PeripheralMarker> {
        if (level.distractorCount == 0) return emptyList()

        val cells = Direction.ALL.flatMap { direction ->
            Eccentricity.entries.map { PeripheralMarker(direction, it) }
        }.filterNot { it == target }

        return cells.shuffled(random).take(level.distractorCount)
    }

    public companion object {
        public const val DEFAULT_FIXATION_MILLIS: Int = 500
        public const val DEFAULT_FIXATION_JITTER_MILLIS: Int = 100
        public const val DEFAULT_MASK_MILLIS: Int = 300
    }
}
