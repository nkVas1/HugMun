/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.core.domain

import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The most ethically loaded arithmetic in the project.
 *
 * A false negative here is complacency and a false positive is fear, for a person who is
 * watching this line specifically because he is worried. The tests are therefore mostly
 * about the app's willingness to say "nothing happened".
 *
 * The false-alarm test below is the one that earns its keep: it caught a real statistical
 * error in the first implementation, which had substituted a within-person SD into the
 * classic Reliable Change Index formula and thereby shrunk the error term.
 */
class ReliableChangeTest {

    @Test
    fun `nothing is claimed without enough observations`() {
        val result = ReliableChange.evaluate(
            history = listOf(100.0, 98.0, 102.0),
            latest = 60.0,
            lowerIsBetter = true,
        )
        assertEquals(ReliableChange.Verdict.INSUFFICIENT_DATA, result.verdict)
        assertNull(result.index)
        assertNull(result.bandHalfWidth)
        assertTrue(!result.isJudged)
    }

    @Test
    fun `ordinary day-to-day wobble is reported as no change`() {
        val history = listOf(100.0, 96.0, 104.0, 99.0, 101.0, 97.0)
        val result = ReliableChange.evaluate(history, latest = 103.0, lowerIsBetter = true)

        assertEquals(ReliableChange.Verdict.WITHIN_USUAL_VARIATION, result.verdict)
        assertNotNull(result.bandHalfWidth)
    }

    @Test
    fun `a large drop in a lower-is-better measure counts as improvement`() {
        val history = listOf(100.0, 98.0, 102.0, 99.0, 101.0, 100.0)
        val result = ReliableChange.evaluate(history, latest = 70.0, lowerIsBetter = true)

        assertEquals(ReliableChange.Verdict.IMPROVED, result.verdict)
        assertTrue(result.index!! < 0.0)
    }

    @Test
    fun `the same drop in a higher-is-better measure counts as decline`() {
        val history = listOf(100.0, 98.0, 102.0, 99.0, 101.0, 100.0)
        val result = ReliableChange.evaluate(history, latest = 70.0, lowerIsBetter = false)

        assertEquals(ReliableChange.Verdict.DECLINED, result.verdict)
    }

    @Test
    fun `a more variable person needs a larger change before anything is claimed`() {
        val steady = listOf(100.0, 99.0, 101.0, 100.0, 99.5, 100.5)
        val erratic = listOf(100.0, 70.0, 130.0, 85.0, 120.0, 95.0)

        val steadyBand = ReliableChange.evaluate(steady, 100.0, lowerIsBetter = true).bandHalfWidth!!
        val erraticBand = ReliableChange.evaluate(erratic, 100.0, lowerIsBetter = true).bandHalfWidth!!

        assertTrue(
            "the erratic person's band ($erraticBand) must be wider than the steady one's ($steadyBand)",
            erraticBand > steadyBand,
        )
    }

    @Test
    fun `a person with no variability at all is treated as insufficient data`() {
        val flat = List(8) { 100.0 }
        val result = ReliableChange.evaluate(flat, latest = 100.0, lowerIsBetter = true)
        assertEquals(ReliableChange.Verdict.INSUFFICIENT_DATA, result.verdict)
    }

    @Test
    fun `the index is symmetric about the baseline`() {
        val history = listOf(100.0, 96.0, 104.0, 99.0, 101.0, 100.0)
        val baseline = history.average()
        val above = ReliableChange.evaluate(history, baseline + 20.0, lowerIsBetter = true).index!!
        val below = ReliableChange.evaluate(history, baseline - 20.0, lowerIsBetter = true).index!!

        assertEquals(above, -below, 1e-9)
    }

    @Test
    fun `more observations narrow the interval for the same spread`() {
        val short = List(6) { 100.0 + (it % 2) * 6.0 }
        val long = List(24) { 100.0 + (it % 2) * 6.0 }

        val shortBand = ReliableChange.evaluate(short, 100.0, true).bandHalfWidth!!
        val longBand = ReliableChange.evaluate(long, 100.0, true).bandHalfWidth!!

        assertTrue("more data should tighten the interval: $longBand !< $shortBand", longBand < shortBand)
    }

    /**
     * Using the normal quantile instead of Student's t at these sample sizes would flag
     * roughly twice as many stable people as it should.
     */
    @Test
    fun `the critical value comes from Student's t and converges on the normal quantile`() {
        assertEquals(2.571, ReliableChange.studentTTwoTailed95(5), 1e-9)
        assertEquals(2.228, ReliableChange.studentTTwoTailed95(10), 1e-9)
        assertEquals(2.042, ReliableChange.studentTTwoTailed95(30), 1e-9)
        assertEquals(ReliableChange.NORMAL_QUANTILE_95, ReliableChange.studentTTwoTailed95(5_000), 1e-9)

        assertTrue(
            "t must always be at least as wide as the normal quantile",
            (1..200).all { ReliableChange.studentTTwoTailed95(it) >= ReliableChange.NORMAL_QUANTILE_95 },
        )
    }

    /**
     * The number that matters.
     *
     * A stable person, measured repeatedly with realistic noise, must almost never be
     * told that something changed. The statistic is a two-tailed 95 % prediction
     * interval, so the nominal rate is 5 %; we assert comfortably under 10 % to leave
     * room for the small-sample estimate of spread.
     */
    @Test
    fun `a stable person is rarely told that anything changed`() {
        val random = Random(seed = 20260921)
        val trueMean = 100.0
        val trueSd = 8.0
        val runs = 4_000

        var falseAlarms = 0
        repeat(runs) {
            val history = List(6) { gaussian(random, trueMean, trueSd) }
            val latest = gaussian(random, trueMean, trueSd)
            val verdict = ReliableChange.evaluate(history, latest, lowerIsBetter = true).verdict
            if (verdict == ReliableChange.Verdict.IMPROVED || verdict == ReliableChange.Verdict.DECLINED) {
                falseAlarms++
            }
        }

        val rate = falseAlarms.toDouble() / runs
        assertTrue("false-alarm rate was %.3f, which is too high".format(rate), rate < 0.10)
    }

    /**
     * The other side of the trade: the interval must not be so wide that a real, large
     * decline goes unmentioned.
     */
    @Test
    fun `a genuine large decline is detected most of the time`() {
        val random = Random(seed = 777L)
        val trueSd = 8.0
        val runs = 2_000

        var detected = 0
        repeat(runs) {
            val history = List(8) { gaussian(random, 100.0, trueSd) }
            // A shift of three within-person standard deviations, in the worse direction
            // for a lower-is-better measure.
            val latest = gaussian(random, 100.0 + 3.0 * trueSd, trueSd)
            if (ReliableChange.evaluate(history, latest, lowerIsBetter = true).verdict ==
                ReliableChange.Verdict.DECLINED
            ) {
                detected++
            }
        }

        val power = detected.toDouble() / runs
        assertTrue("detected only %.2f of real 3-SD declines".format(power), power > 0.60)
    }

    /** Box–Muller; the standard library has no Gaussian on Random. */
    private fun gaussian(random: Random, mean: Double, sd: Double): Double {
        val u1 = random.nextDouble().coerceAtLeast(1e-12)
        val u2 = random.nextDouble()
        return mean + sd * sqrt(-2.0 * ln(u1)) * cos(2.0 * Math.PI * u2)
    }
}
