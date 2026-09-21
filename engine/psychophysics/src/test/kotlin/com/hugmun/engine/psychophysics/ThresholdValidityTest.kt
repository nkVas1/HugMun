/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.engine.psychophysics

import kotlin.math.abs
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the defect found on the first real device run.
 *
 * A session of sixty trials answered essentially at random produced four reversals, and
 * the app reported a threshold together with the sentence "this is how long you needed to
 * identify both pictures correctly about three times in four" — after the participant had
 * been correct three times in sixty. The reversal count alone is not a sufficient gate,
 * because a staircase pinned at its ceiling generates reversals indefinitely.
 */
class ThresholdValidityTest {

    @Test
    fun `binomial mass sums to one`() {
        val total = (0..40).sumOf { BinomialTest.probabilityMass(it, 40, GUESS) }
        assertTrue("mass summed to $total", abs(total - 1.0) < 1e-12)
    }

    @Test
    fun `binomial tail agrees with a direct sum in both branches`() {
        // The implementation switches tails at the mean; both sides must agree with the
        // naive computation, which is the whole risk of that optimisation.
        for (k in 0..30) {
            val direct = (k..30).sumOf { BinomialTest.probabilityMass(it, 30, 0.25) }
            val tail = BinomialTest.upperTailProbability(k, 30, 0.25)
            assertTrue("k=$k direct=$direct tail=$tail", abs(direct - tail) < 1e-10)
        }
    }

    @Test
    fun `the session recorded on the device is rejected as chance performance`() {
        // Exactly the numbers the result screen showed: 60 scored, 3 correct.
        val result = resultOf(scored = 60, correct = 3, thresholdFrames = 30.0)
        assertEquals(ThresholdValidity.AT_CHANCE, result.validity)
        assertFalse(result.isAboveChance)
        assertFalse(result.validity.isTrendEligible)
    }

    @Test
    fun `chance performance is rejected however many reversals were collected`() {
        // Four reversals passed the old gate. The point is that reversal count carries
        // no information about whether the staircase was tracking anything.
        val result = resultOf(scored = 60, correct = 3, thresholdFrames = 30.0, reversals = 8)
        assertEquals(ThresholdValidity.AT_CHANCE, result.validity)
    }

    @Test
    fun `genuine performance well above the guess rate is accepted`() {
        val result = resultOf(scored = 60, correct = 45, thresholdFrames = 12.0)
        assertEquals(ThresholdValidity.USABLE, result.validity)
        assertTrue(result.isAboveChance)
    }

    @Test
    fun `a slow but real observer is kept, not discarded`() {
        // 12 of 60 is poor, and four times the guess rate. Someone performing like this
        // is doing the task; throwing their session away would be the opposite error.
        val result = resultOf(scored = 60, correct = 12, thresholdFrames = 20.0)
        assertTrue("p = ${result.chanceProbability}", result.isAboveChance)
        assertEquals(ThresholdValidity.USABLE, result.validity)
    }

    @Test
    fun `a threshold resting against the ceiling is flagged but still counted`() {
        val result = resultOf(scored = 60, correct = 40, thresholdFrames = CEILING_FRAMES.toDouble())
        assertEquals(ThresholdValidity.CEILING_LIMITED, result.validity)
        assertTrue(
            "a ceiling-limited threshold is a real bound and must stay in the trend",
            result.validity.isTrendEligible,
        )
    }

    @Test
    fun `dropped frames outrank a ceiling reading`() {
        val result = resultOf(
            scored = 60,
            correct = 40,
            thresholdFrames = CEILING_FRAMES.toDouble(),
            discarded = 40,
        )
        assertEquals(ThresholdValidity.DISPLAY_UNRELIABLE, result.validity)
        assertFalse(result.validity.isTrendEligible)
    }

    @Test
    fun `the guess rate matches the task actually presented`() {
        // If either alternative count changes, the chance test must follow it. Written as
        // an independent computation rather than a literal so this test can fail.
        val expected = 1.0 / (CentralFigure.entries.size * Direction.COUNT)
        assertEquals(expected, UfovSessionResult.GUESS_RATE, 1e-12)
    }

    @Test
    fun `a guessing observer driving a real staircase is almost never reported`() {
        // The device run, reproduced through the actual staircase rather than asserted on
        // a hand-built result. Whether a guessing observer collects enough reversals to
        // produce an estimate is luck, which is exactly why the reversal count cannot be
        // the gate: across seeds it happens constantly, and every time it does the
        // staircase hands back a well-formed number that measured nothing.
        val timing = DisplayTiming(60.000004)
        val config = UfovSession.defaultConfigFor(timing)
        var estimatesProduced = 0
        var reportedAsUsable = 0

        repeat(SEEDS) { seed ->
            val random = Random(seed)
            val staircase = WeightedUpDownStaircase(config)
            var correct = 0
            var scored = 0
            while (!staircase.isFinished) {
                staircase.nextFrames()
                val hit = random.nextDouble() < GUESS
                staircase.record(if (hit) TrialResponse.CORRECT else TrialResponse.INCORRECT)
                scored++
                if (hit) correct++
            }

            val estimate = staircase.threshold() ?: return@repeat
            estimatesProduced++
            val result = resultOf(
                scored = scored,
                correct = correct,
                thresholdFrames = estimate.frames,
                timing = timing,
            )
            if (result.validity == ThresholdValidity.USABLE) reportedAsUsable++
        }

        assertTrue(
            "no seed produced an estimate, so this test proves nothing; check the config",
            estimatesProduced > SEEDS / 4,
        )
        // A significance test admits roughly alpha of the null by construction, so the
        // claim is a bound, not "never". The bound is what the product promises: a
        // person who cannot do the task is told so rather than given a number.
        val leakRate = reportedAsUsable.toDouble() / estimatesProduced
        assertTrue(
            "$reportedAsUsable of $estimatesProduced guessing sessions were reported as " +
                "real measurements (${(leakRate * PERCENT).toInt()} %)",
            leakRate <= MAX_LEAK_RATE,
        )
    }

    @Test
    fun `the ceiling flag is not a backstop against guessing`() {
        // Documents why the chance test has to carry this alone. A guessing run whose
        // early reversals land during the ascent from the starting level produces an
        // estimate well clear of the ceiling, so gating on the ceiling would miss it.
        val timing = DisplayTiming(60.000004)
        val config = UfovSession.defaultConfigFor(timing)
        val belowCeiling = (0 until SEEDS).count { seed ->
            val random = Random(seed)
            val staircase = WeightedUpDownStaircase(config)
            while (!staircase.isFinished) {
                staircase.nextFrames()
                staircase.record(
                    if (random.nextDouble() < GUESS) TrialResponse.CORRECT else TrialResponse.INCORRECT,
                )
            }
            staircase.threshold()?.isCeilingLimited(config) == false
        }
        assertTrue(
            "no guessing run escaped the ceiling flag; if this ever holds, the flag " +
                "could be promoted to a gate and this test should be revisited",
            belowCeiling > 0,
        )
    }

    private fun resultOf(
        scored: Int,
        correct: Int,
        thresholdFrames: Double,
        discarded: Int = 0,
        reversals: Int = 6,
        timing: DisplayTiming = DisplayTiming(60.0),
    ): UfovSessionResult = UfovSessionResult(
        level = UfovLevel.PRIMARY,
        timing = timing,
        thresholdFrames = thresholdFrames,
        thresholdMillis = timing.framesToMillis(thresholdFrames),
        isFloorLimited = thresholdFrames < 2.0,
        isCeilingLimited = thresholdFrames >= timing.millisToFrames(CEILING_MILLIS) * CEILING_MARGIN,
        reversalsUsed = reversals,
        scoredTrials = scored,
        correctTrials = correct,
        discardedTrials = discarded,
        reversalFrames = List(reversals) { thresholdFrames.toInt() },
    )

    private companion object {
        val GUESS = UfovSessionResult.GUESS_RATE
        const val CEILING_MILLIS = 500.0
        const val CEILING_FRAMES = 30
        const val SEEDS = 500

        /** One downward staircase step, as a multiplicative factor: 10^(-0.1/3). */
        const val CEILING_MARGIN = 0.9260

        /** A little above alpha, leaving room for the estimator's own noise. */
        const val MAX_LEAK_RATE = 0.03
        const val PERCENT = 100.0
    }
}
