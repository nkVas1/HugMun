/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.engine.psychophysics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random

/**
 * These tests exist because the staircase produces the number the whole product is
 * judged on. A silent bias here would not crash anything — it would just quietly make
 * every reported threshold wrong, in a way no one would notice.
 */
class WeightedUpDownStaircaseTest {

    @Test
    fun `step ratio satisfies the zero-drift condition for the target accuracy`() {
        for (target in listOf(0.60, 0.70, 0.75, 0.80, 0.90)) {
            val config = StaircaseConfig(targetAccuracy = target, stepUpLog10 = 0.1)
            // At equilibrium: p * stepDown == (1 - p) * stepUp
            val drift = target * config.stepDownLog10 - (1.0 - target) * config.stepUpLog10
            assertEquals("zero-drift violated at p=$target", 0.0, drift, 1e-12)
        }
    }

    @Test
    fun `UFOV criterion gives a down step of exactly one third of the up step`() {
        val config = StaircaseConfig(targetAccuracy = 0.75, stepUpLog10 = 0.12)
        assertEquals(0.04, config.stepDownLog10, 1e-12)
    }

    @Test
    fun `discarded trials do not move the level and do not count as reversals`() {
        val staircase = WeightedUpDownStaircase(StaircaseConfig(startFrames = 30))

        val first = staircase.nextFrames()
        val step = staircase.record(TrialResponse.DISCARDED)

        assertEquals(step.levelBefore, step.levelAfter, 0.0)
        assertFalse(step.isReversal)
        assertEquals(1, staircase.discardedTrials)
        assertEquals(0, staircase.scoredTrials)
        assertEquals("level must be unchanged", first, staircase.nextFrames())
    }

    @Test
    fun `nextFrames is idempotent until a response is recorded`() {
        val staircase = WeightedUpDownStaircase()
        val a = staircase.nextFrames()
        val b = staircase.nextFrames()
        assertEquals(a, b)
    }

    @Test
    fun `record without a pending trial fails loudly`() {
        val staircase = WeightedUpDownStaircase()
        val error = runCatching { staircase.record(TrialResponse.CORRECT) }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
    }

    @Test
    fun `correct responses shorten the stimulus and errors lengthen it`() {
        val staircase = WeightedUpDownStaircase(StaircaseConfig(startFrames = 40))

        val start = staircase.nextFrames()
        staircase.record(TrialResponse.CORRECT)
        val afterCorrect = staircase.nextFrames()
        assertTrue("expected a shorter stimulus, $afterCorrect !< $start", afterCorrect < start)

        staircase.record(TrialResponse.INCORRECT)
        val afterError = staircase.nextFrames()
        assertTrue("expected a longer stimulus, $afterError !> $afterCorrect", afterError > afterCorrect)
    }

    @Test
    fun `direction changes are recorded as reversals`() {
        val staircase = WeightedUpDownStaircase(StaircaseConfig(startFrames = 40))

        staircase.nextFrames()
        val down = staircase.record(TrialResponse.CORRECT)
        assertFalse("the first step has no previous direction", down.isReversal)

        staircase.nextFrames()
        val up = staircase.record(TrialResponse.INCORRECT)
        assertTrue("down then up is a reversal", up.isReversal)

        staircase.nextFrames()
        val stillUp = staircase.record(TrialResponse.INCORRECT)
        assertFalse("up then up is not a reversal", stillUp.isReversal)
    }

    @Test
    fun `level never leaves the configured bounds`() {
        val config = StaircaseConfig(startFrames = 4, minFrames = 2, maxFrames = 8)
        val staircase = WeightedUpDownStaircase(config)

        repeat(200) { index ->
            val frames = staircase.nextFrames()
            assertTrue("frames=$frames below floor", frames >= config.minFrames)
            assertTrue("frames=$frames above ceiling", frames <= config.maxFrames)
            staircase.record(if (index % 2 == 0) TrialResponse.CORRECT else TrialResponse.INCORRECT)
        }
    }

    @Test
    fun `no threshold is reported before enough reversals exist`() {
        val staircase = WeightedUpDownStaircase()
        staircase.nextFrames()
        staircase.record(TrialResponse.CORRECT)
        staircase.nextFrames()
        staircase.record(TrialResponse.INCORRECT)
        assertNull(staircase.threshold())
    }

    /**
     * The test that actually matters.
     *
     * A simulated observer with a known logistic psychometric function is run through the
     * staircase many times. If the procedure is correct, the recovered threshold should
     * land near the duration at which that observer is 75 % correct — which is what the
     * UFOV criterion means.
     */
    @Test
    fun `recovers the 75 percent point of a simulated observer`() {
        val trueThresholdFrames = 9.0
        val slope = 3.0
        val random = Random(seed = 20260921)

        val recovered = mutableListOf<Double>()
        repeat(RUNS) {
            val staircase = WeightedUpDownStaircase(
                StaircaseConfig(
                    startFrames = 45,
                    minFrames = 1,
                    maxFrames = 60,
                    reversalsToStop = 12,
                    reversalsForThreshold = 10,
                    maxTrials = 200,
                ),
            )
            while (!staircase.isFinished) {
                val frames = staircase.nextFrames()
                val pCorrect = observerAccuracy(frames.toDouble(), trueThresholdFrames, slope)
                val response = if (random.nextDouble() < pCorrect) {
                    TrialResponse.CORRECT
                } else {
                    TrialResponse.INCORRECT
                }
                staircase.record(response)
            }
            staircase.threshold()?.let { recovered += it.frames }
        }

        assertTrue("expected estimates from every run", recovered.size == RUNS)

        val meanEstimate = exp(recovered.sumOf { ln(it) } / recovered.size)
        val relativeError = abs(meanEstimate - trueThresholdFrames) / trueThresholdFrames

        assertTrue(
            "mean recovered threshold $meanEstimate should be within 20% of $trueThresholdFrames " +
                "(relative error ${"%.3f".format(relativeError)})",
            relativeError < 0.20,
        )
    }

    @Test
    fun `a flawless observer produces no reversals and therefore no threshold`() {
        val staircase = WeightedUpDownStaircase(StaircaseConfig(startFrames = 20, minFrames = 1))

        repeat(60) {
            staircase.nextFrames()
            staircase.record(TrialResponse.CORRECT)
        }

        assertEquals("a monotonic descent has no direction changes", 0, staircase.reversals.size)
        assertNull("without reversals there is nothing to estimate from", staircase.threshold())
        assertEquals("and it should be sitting on the floor", 1, staircase.nextFrames())
    }

    @Test
    fun `threshold lands at the floor for an observer who only fails at one frame`() {
        val config = StaircaseConfig(startFrames = 20, minFrames = 1, reversalsToStop = 8)
        val staircase = WeightedUpDownStaircase(config)

        while (!staircase.isFinished) {
            val frames = staircase.nextFrames()
            staircase.record(if (frames <= 1) TrialResponse.INCORRECT else TrialResponse.CORRECT)
        }

        val threshold = requireNotNull(staircase.threshold())
        assertTrue("expected an estimate in the bottom bin, got ${threshold.frames}", threshold.frames < 2.0)
        assertTrue("estimate should report that it is floor-limited", threshold.isFloorLimited(config))
    }

    /**
     * Logistic psychometric function on log duration, with a lower asymptote at chance.
     *
     * Chance for the level-2 UFOV trial is 0.0625: the participant must get both a 2AFC
     * identification and an 8AFC localisation right.
     */
    private fun observerAccuracy(frames: Double, threshold: Double, slope: Double): Double {
        val chance = 1.0 / 2.0 * 1.0 / 8.0
        val x = slope * (ln(frames) - ln(threshold))
        val performance = 1.0 / (1.0 + exp(-x))
        // Scale so that performance == 0.75 exactly at the threshold.
        val atThreshold = 0.5
        val scale = (0.75 - chance) / (atThreshold - chance).coerceAtLeast(1e-9)
        return (chance + (performance - chance) * scale).coerceIn(chance, 1.0)
    }

    private companion object {
        const val RUNS = 200
    }
}
