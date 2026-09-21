/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.engine.psychophysics

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UfovTrialGeneratorTest {

    @Test
    fun `the same seed produces the same session`() {
        fun run(): List<UfovTrialSpec> {
            val generator = UfovTrialGenerator(seed = 42L)
            return (0 until 40).map { generator.next(it, stimulusFrames = 10) }
        }
        assertEquals(run(), run())
    }

    @Test
    fun `different seeds produce different sessions`() {
        val a = UfovTrialGenerator(seed = 1L).let { g -> (0 until 40).map { g.next(it, 10) } }
        val b = UfovTrialGenerator(seed = 2L).let { g -> (0 until 40).map { g.next(it, 10) } }
        assertTrue("novelty rotation should make sessions differ", a != b)
    }

    /**
     * Figures come from shuffled blocks rather than independent draws. Without this, a
     * chance run of one figure can masquerade as a perceptual change when it is really a
     * response bias.
     */
    @Test
    fun `central figures are balanced`() {
        val generator = UfovTrialGenerator(seed = 7L)
        val figures = (0 until 200).map { generator.next(it, 10).centralFigure }
        val ravens = figures.count { it == CentralFigure.RAVEN }
        assertEquals("exact balance is expected from block randomisation", 100, ravens)
    }

    @Test
    fun `directions are balanced across blocks of eight`() {
        val generator = UfovTrialGenerator(seed = 11L)
        val directions = (0 until 160).mapNotNull { generator.next(it, 10).target?.direction?.index }
        val counts = directions.groupingBy { it }.eachCount()
        assertEquals("all eight directions should appear", Direction.COUNT, counts.size)
        counts.forEach { (direction, count) ->
            assertEquals("direction $direction should appear exactly 20 times", 20, count)
        }
    }

    @Test
    fun `the selective level fills the field without covering the target`() {
        val generator = UfovTrialGenerator(level = UfovLevel.SELECTIVE, seed = 3L)
        repeat(50) { index ->
            val spec = generator.next(index, 10)
            assertEquals(UfovLevel.SELECTIVE.distractorCount, spec.distractors.size)
            assertFalse(
                "a distractor must never occupy the target cell",
                spec.distractors.contains(spec.target),
            )
        }
    }

    @Test
    fun `fixation is jittered so the onset cannot be anticipated`() {
        val generator = UfovTrialGenerator(seed = 5L)
        val fixations = (0 until 50).map { generator.next(it, 10).fixationMillis }.toSet()
        assertTrue("fixation should vary, saw ${fixations.size} distinct values", fixations.size > 1)
    }

    @Test
    fun `chance level at the primary level is one in sixteen`() {
        assertEquals(0.0625, UfovScoring.chanceLevel(UfovLevel.DIVIDED), 1e-12)
    }
}

class UfovScoringTest {

    private val spec = UfovTrialSpec(
        index = 0,
        level = UfovLevel.DIVIDED,
        centralFigure = CentralFigure.RAVEN,
        target = PeripheralMarker(Direction(3), Eccentricity.MID),
        distractors = emptyList(),
        stimulusFrames = 5,
        fixationMillis = 500,
        maskMillis = 300,
    )

    @Test
    fun `both subtasks must be right for the trial to count as correct`() {
        val bothRight = UfovResponse(CentralFigure.RAVEN, Direction(3), true, 1_200)
        assertEquals(TrialResponse.CORRECT, UfovScoring.score(spec, bothRight))

        val centralWrong = UfovResponse(CentralFigure.OWL, Direction(3), true, 1_200)
        assertEquals(TrialResponse.INCORRECT, UfovScoring.score(spec, centralWrong))

        val peripheralWrong = UfovResponse(CentralFigure.RAVEN, Direction(4), true, 1_200)
        assertEquals(TrialResponse.INCORRECT, UfovScoring.score(spec, peripheralWrong))
    }

    /**
     * The rule from ADR 0004: a trial the display did not present correctly did not
     * measure anything, and must not be scored either way.
     */
    @Test
    fun `an inaccurate presentation is discarded rather than scored`() {
        val correctButDropped = UfovResponse(CentralFigure.RAVEN, Direction(3), false, 1_200)
        assertEquals(TrialResponse.DISCARDED, UfovScoring.score(spec, correctButDropped))

        val wrongAndDropped = UfovResponse(CentralFigure.OWL, null, false, 1_200)
        assertEquals(TrialResponse.DISCARDED, UfovScoring.score(spec, wrongAndDropped))
    }
}

class UfovSessionTest {

    @Test
    fun `the presentation ceiling is 500 ms regardless of refresh rate`() {
        val at60 = UfovSession.defaultConfigFor(DisplayTiming.HZ_60)
        val at120 = UfovSession.defaultConfigFor(DisplayTiming.HZ_120)

        assertEquals(500.0, DisplayTiming.HZ_60.framesToMillis(at60.maxFrames), 17.0)
        assertEquals(500.0, DisplayTiming.HZ_120.framesToMillis(at120.maxFrames), 9.0)
        assertTrue("a faster display gets more frames for the same time", at120.maxFrames > at60.maxFrames)
    }

    @Test
    fun `nextTrial is idempotent until a response is recorded`() {
        val session = UfovSession(timing = DisplayTiming.HZ_120, seed = 1L)
        assertEquals(session.nextTrial(), session.nextTrial())
    }

    @Test
    fun `no result is produced from an empty session`() {
        val session = UfovSession(timing = DisplayTiming.HZ_120, seed = 1L)
        assertNull(session.result())
        assertEquals(UfovSession.StopReason.IN_PROGRESS, session.stopReason())
    }

    @Test
    fun `discarded trials do not advance the staircase but are counted`() {
        val session = UfovSession(timing = DisplayTiming.HZ_120, seed = 1L)
        val first = session.nextTrial()!!

        session.record(UfovResponse(first.centralFigure, first.target?.direction, false, 800))

        val second = session.nextTrial()!!
        assertEquals("duration must not change after a discarded trial", first.stimulusFrames, second.stimulusFrames)
        assertEquals(1, session.trials.size)
        assertEquals(TrialResponse.DISCARDED, session.trials.first().outcome)
    }

    /**
     * End-to-end: a simulated observer with a known threshold is run through a whole
     * session, and the session must recover roughly the right duration and report a
     * sensible stop reason.
     */
    @Test
    fun `a full session recovers a simulated observer's threshold`() {
        val timing = DisplayTiming.HZ_120
        val trueThresholdMillis = 70.0
        val random = Random(seed = 4242L)

        val estimates = mutableListOf<Double>()
        repeat(RUNS) { run ->
            val session = UfovSession(timing = timing, seed = run.toLong())
            while (!session.isFinished) {
                val spec = session.nextTrial() ?: break
                val presentedMillis = timing.framesToMillis(spec.stimulusFrames)
                val pCorrect = accuracyAt(presentedMillis, trueThresholdMillis)
                val correct = random.nextDouble() < pCorrect
                session.record(
                    UfovResponse(
                        figure = if (correct) spec.centralFigure else other(spec.centralFigure),
                        direction = if (correct) spec.target?.direction else wrongDirection(spec, random),
                        presentationAccurate = true,
                        latencyMillis = 1_500,
                    ),
                )
            }
            session.result()?.let { estimates += it.thresholdMillis }
        }

        assertTrue("every run should produce an estimate", estimates.size == RUNS)
        val mean = exp(estimates.sumOf { ln(it) } / estimates.size)
        val error = abs(mean - trueThresholdMillis) / trueThresholdMillis
        assertTrue(
            "recovered %.1f ms against a true %.1f ms (error %.1f%%)".format(mean, trueThresholdMillis, error * 100),
            error < 0.25,
        )
    }

    @Test
    fun `a session reports its quality honestly when the display drops frames`() {
        val session = UfovSession(timing = DisplayTiming.HZ_120, seed = 9L)
        var index = 0
        while (!session.isFinished) {
            val spec = session.nextTrial() ?: break
            // One trial in three fails to present: a device under real strain.
            val accurate = index % 3 != 0
            // The observer has a genuine threshold, so the staircase still reverses and
            // still produces an estimate; the question is whether the session admits the
            // estimate came from degraded data.
            val correct = spec.stimulusFrames >= 4
            session.record(
                UfovResponse(
                    figure = if (correct) spec.centralFigure else other(spec.centralFigure),
                    direction = if (correct) {
                        spec.target?.direction
                    } else {
                        Direction((spec.target!!.direction.index + 1) % Direction.COUNT)
                    },
                    presentationAccurate = accurate,
                    latencyMillis = 1_200,
                ),
            )
            index++
        }

        val result = session.result()
        assertNotNull("the session still produces a threshold", result)
        assertFalse("but it must not claim the data are good", result!!.isQualityAcceptable)
        assertTrue(result.discardedTrials > 0)
    }

    @Test
    fun `the time budget ends a session that will not converge`() {
        val session = UfovSession(
            timing = DisplayTiming.HZ_120,
            seed = 2L,
            budget = UfovSession.SessionBudget(maxDurationMillis = 20_000L),
        )
        var guard = 0
        while (!session.isFinished && guard < 500) {
            val spec = session.nextTrial() ?: break
            session.record(UfovResponse(spec.centralFigure, spec.target?.direction, true, 4_000))
            guard++
        }
        assertEquals(UfovSession.StopReason.TIME_LIMIT, session.stopReason())
    }

    private fun other(figure: CentralFigure) =
        if (figure == CentralFigure.RAVEN) CentralFigure.OWL else CentralFigure.RAVEN

    private fun wrongDirection(spec: UfovTrialSpec, random: Random): Direction? {
        val target = spec.target ?: return null
        val candidates = Direction.ALL.filter { it != target.direction }
        return candidates.random(random)
    }

    /**
     * A logistic psychometric function on log duration, anchored so that performance is
     * exactly 75 % at the true threshold and falls to chance (1/16) at very short
     * durations.
     */
    private fun accuracyAt(presentedMillis: Double, thresholdMillis: Double): Double {
        val chance = UfovScoring.chanceLevel(UfovLevel.DIVIDED)
        val x = SLOPE * (ln(presentedMillis) - ln(thresholdMillis))
        val sigmoid = 1.0 / (1.0 + exp(-x))
        val scale = (0.75 - chance) / (0.5 - chance)
        return (chance + (sigmoid - chance) * scale).coerceIn(chance, 1.0)
    }

    private companion object {
        const val RUNS = 60
        const val SLOPE = 2.5
    }
}
